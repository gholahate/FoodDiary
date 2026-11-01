package com.fooddiary.android.data

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.fooddiary.android.BuildConfig
import com.fooddiary.db.FoodDiaryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What a backup file contains — shown before the user confirms a restore. */
data class BackupSummary(
    val products: Long,
    val diaryEntries: Long,
    /** Latest diary date in the backup ("YYYY-MM-DD"), null if the diary is empty. */
    val lastDiaryDate: String?,
    /** From the backup's info file; null for a raw .db file (e.g. copied with adb). */
    val createdAt: String?,
    val appVersion: String?,
)

/** A checked backup unpacked into a temporary folder, waiting for "Заменить". */
class PreparedRestore internal constructor(
    internal val dbFile: File,
    internal val settings: Map<String, String>,
    val summary: BackupSummary,
)

class BackupException(message: String) : Exception(message)

/**
 * Backup of the whole diary to one file and back.
 *
 * Backup file = ZIP ("FoodDiary-backup-2026-10-30.zip") with:
 *  - fooddiary.db       — the SQLite database itself (products, diary, profile);
 *  - settings.json      — app settings (theme, chart style);
 *  - backup-info.json   — when / by which version it was made.
 * Restore also accepts a bare fooddiary.db (e.g. pulled from the phone with
 * adb). A backup from an OLDER app version is fine: the database is upgraded
 * by the migrations when it's opened. One from a NEWER version is refused.
 *
 * Restoring closes the database, swaps the file and restarts the app.
 */
class BackupManager(
    private val context: Context,
    private val driver: SqlDriver,
    private val settings: AppSettings,
) {
    private val dbFile: File get() = context.getDatabasePath(DB_NAME)

    /** Suggested file name for the "save as" dialog. */
    fun suggestedFileName(): String =
        "FoodDiary-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.zip"

    /** Writes a backup to a file the user picked. Returns what was saved. */
    suspend fun exportTo(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri)
            ?: throw BackupException("Не удалось открыть файл для записи")
        out.use { writeBackup(it) }
    }

    /**
     * Safety copy inside the app's own folder (kept: the last [keep] files).
     * Made automatically before a restore replaces the data.
     */
    suspend fun saveInternalCopy(reason: String, keep: Int = 3): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(dir, "$reason-$stamp.zip")
        file.outputStream().use { writeBackup(it) }
        dir.listFiles { f -> f.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(keep)
            ?.forEach { it.delete() }
        file
    }

    private fun writeBackup(out: OutputStream): BackupSummary {
        // Move everything still sitting in the write-ahead log into the main
        // file, so copying that one file captures all data.
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA wal_checkpoint(FULL)",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        val db = dbFile
        if (!db.exists()) throw BackupException("База данных ещё не создана — сохранять нечего")
        val wal = File(db.path + "-wal")
        val createdAt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date())

        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(DB_NAME))
            db.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            // Normally empty after the checkpoint; included just in case it isn't.
            if (wal.exists() && wal.length() > 0) {
                zip.putNextEntry(ZipEntry("$DB_NAME-wal"))
                wal.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(SETTINGS_ENTRY))
            zip.write(JSONObject(settings.exportValues()).toString(2).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(INFO_ENTRY))
            val info = JSONObject()
                .put("app", "FoodDiary")
                .put("versionName", BuildConfig.VERSION_NAME)
                .put("versionCode", BuildConfig.VERSION_CODE)
                .put("schemaVersion", FoodDiaryDatabase.Schema.version)
                .put("createdAt", createdAt)
            zip.write(info.toString(2).toByteArray())
            zip.closeEntry()
        }
        return summarize(db, createdAt, BuildConfig.VERSION_NAME)
    }

    /**
     * Step 1 of a restore: copies the picked file into a temporary folder,
     * unpacks and checks it. Nothing is replaced yet.
     */
    suspend fun prepareRestore(uri: Uri): PreparedRestore = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "restore").apply {
            deleteRecursively()
            mkdirs()
        }
        val picked = File(workDir, "picked")
        (context.contentResolver.openInputStream(uri) ?: throw BackupException("Не удалось открыть файл"))
            .use { input -> picked.outputStream().use { input.copyTo(it) } }

        // (readNBytes would need Android 13+, so read the header by hand.)
        val header = ByteArray(16)
        val headerLength = picked.inputStream().use { it.read(header) }.coerceAtLeast(0)
        if (headerLength < 4) throw BackupException("Файл пустой или слишком маленький")
        val candidate = File(workDir, DB_NAME)
        var settingsValues = emptyMap<String, String>()
        var createdAt: String? = null
        var appVersion: String? = null

        when {
            header.startsWith(ZIP_MAGIC) -> {
                picked.inputStream().use { unzip(it, workDir) }
                if (!candidate.exists()) throw BackupException("В архиве нет базы $DB_NAME — это не копия FoodDiary")
                File(workDir, SETTINGS_ENTRY).takeIf { it.exists() }?.let { f ->
                    val json = JSONObject(f.readText())
                    settingsValues = json.keys().asSequence().associateWith { json.optString(it) }
                }
                File(workDir, INFO_ENTRY).takeIf { it.exists() }?.let { f ->
                    val json = JSONObject(f.readText())
                    createdAt = json.optString("createdAt").ifBlank { null }
                    appVersion = json.optString("versionName").ifBlank { null }
                }
            }
            header.startsWith(SQLITE_MAGIC) -> picked.renameTo(candidate)
            else -> throw BackupException("Это не копия FoodDiary: ожидается файл .zip из «Сохранить копию» или fooddiary.db")
        }

        val summary = checkDatabase(candidate, createdAt, appVersion)
        PreparedRestore(candidate, settingsValues, summary)
    }

    /**
     * Step 2 (after the user confirmed): saves a safety copy of the current
     * data, puts the backup next to the database as "fooddiary.db.pending"
     * and restarts the app. The actual swap happens on the next start, in
     * [applyPendingRestore], BEFORE anything opens the database — so no
     * screen can touch a half-replaced file. Doesn't return.
     */
    suspend fun applyRestore(prepared: PreparedRestore) {
        saveInternalCopy(reason = "before-restore")
        withContext(Dispatchers.IO) {
            val pending = File(dbFile.path + PENDING_SUFFIX)
            pending.parentFile?.mkdirs()
            prepared.dbFile.copyTo(pending, overwrite = true)
            settings.importValues(prepared.settings)
            File(context.cacheDir, "restore").deleteRecursively()
        }
        restartApp()
    }

    /** Opens a candidate file on its own (not the app's database) and checks it's really ours. */
    private fun checkDatabase(file: File, createdAt: String?, appVersion: String?): BackupSummary {
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        } catch (e: Exception) {
            throw BackupException("Файл повреждён или это не база данных")
        }
        try {
            // Folds a "-wal" file lying next to it (if any) into the main file.
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }

            val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            if (!tables.containsAll(REQUIRED_TABLES)) {
                throw BackupException("В файле нет таблиц FoodDiary — это база другого приложения")
            }
            val schema = db.version.toLong()
            if (schema > FoodDiaryDatabase.Schema.version) {
                throw BackupException(
                    "Копия сделана более новой версией приложения. Сначала обновите приложение, затем восстановите копию.",
                )
            }
        } finally {
            db.close()
        }
        return summarize(file, createdAt, appVersion)
    }

    private fun summarize(file: File, createdAt: String?, appVersion: String?): BackupSummary {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            fun count(table: String): Long =
                db.rawQuery("SELECT COUNT(*) FROM $table", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
            val lastDate = db.rawQuery("SELECT MAX(entryDate) FROM DiaryEntry", null).use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
            }
            return BackupSummary(count("Product"), count("DiaryEntry"), lastDate, createdAt, appVersion)
        } finally {
            db.close()
        }
    }

    private fun unzip(input: InputStream, dir: File) {
        val allowed = setOf(DB_NAME, "$DB_NAME-wal", SETTINGS_ENTRY, INFO_ENTRY)
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                // Only our known flat names: never write outside the folder.
                if (!entry.isDirectory && entry.name in allowed) {
                    File(dir, entry.name).outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
    }

    /** Starts the app again from scratch so everything opens the restored database. */
    private fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    companion object {
        /**
         * Called first thing in Application.onCreate: if a restore is waiting,
         * replace the database with it (old -wal/-shm files included, they
         * belong to the old data).
         */
        fun applyPendingRestore(context: Context) {
            val db = context.getDatabasePath(DB_NAME)
            val pending = File(db.path + PENDING_SUFFIX)
            if (!pending.exists()) return
            listOf("", "-wal", "-shm", "-journal").forEach { File(db.path + it).delete() }
            if (!pending.renameTo(db)) {
                pending.copyTo(db, overwrite = true)
                pending.delete()
            }
        }

        private const val PENDING_SUFFIX = ".pending"
        private const val DB_NAME = "fooddiary.db"
        private const val SETTINGS_ENTRY = "settings.json"
        private const val INFO_ENTRY = "backup-info.json"
        private val REQUIRED_TABLES = setOf("Product", "DiaryEntry", "UserProfile")
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        private val SQLITE_MAGIC = "SQLite format 3".toByteArray()
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
