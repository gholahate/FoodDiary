package com.fooddiary.android.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.fooddiary.android.BuildConfig
import com.fooddiary.android.InstallResultReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Contents of version.json attached to every GitHub release (see .github/workflows/release.yml). */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState

    /** [progress] 0..1, or null while the size is unknown. */
    data class Downloading(val info: UpdateInfo, val progress: Float?) : UpdateState

    /** Handed over to the Android installer; its own window takes over from here. */
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}

/**
 * Updates from GitHub Releases (public repo [BuildConfig.GITHUB_REPO]).
 *
 * 1. Check: download `releases/latest/download/version.json` — GitHub redirects
 *    this to the file of the newest published release (no API key, no limits
 *    that matter) — and compare its versionCode with ours.
 * 2. Download the APK into the app's cache, with progress.
 * 3. Verify its SHA-256 against version.json: a broken download is never installed.
 * 4. Make a safety copy of the database (like before a restore).
 * 5. Install via Android's PackageInstaller: the system shows its own
 *    "Обновить приложение?" window; Android itself refuses an APK that isn't
 *    signed with our release key, so a substituted file can't be installed.
 *
 * Debug builds (separate app id + debug signature) can't be updated by a
 * release APK; there the button opens the release page in the browser.
 */
class UpdateManager(
    private val context: Context,
    private val settings: AppSettings,
    private val backup: BackupManager,
) {
    // Lives as long as the app: a download keeps going if the window is closed
    // or the screen is rotated.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /** The update window (shown over any screen, see UpdateDialog) is open. */
    private val _dialogVisible = MutableStateFlow(false)
    val dialogVisible: StateFlow<Boolean> = _dialogVisible

    fun showDialog() {
        _dialogVisible.value = true
    }

    /** Closes the window; a running download continues in the background. */
    fun hideDialog() {
        _dialogVisible.value = false
        val s = _state.value
        if (s is UpdateState.Failed || s is UpdateState.Installing) _state.value = UpdateState.Idle
    }

    /** «Позже»: close and don't pop up for this version for a day. */
    fun later(info: UpdateInfo) {
        snooze(info)
        _dialogVisible.value = false
    }

    val releasesPageUrl = "https://github.com/${BuildConfig.GITHUB_REPO}/releases/latest"
    private val versionJsonUrl = "https://github.com/${BuildConfig.GITHUB_REPO}/releases/latest/download/version.json"

    /** Automatic check on app start: only if enabled and the last check was over a day ago. */
    fun isAutoCheckDue(): Boolean =
        settings.autoCheckUpdates.value &&
            System.currentTimeMillis() - settings.lastUpdateCheckMillis() > DAY_MS

    /**
     * Should the "Доступна версия" window pop up by itself? Not again for a
     * version the user put off with «Позже» less than a day ago.
     */
    fun shouldPrompt(info: UpdateInfo): Boolean {
        val (code, at) = settings.updateSnooze()
        return !(code == info.versionCode && System.currentTimeMillis() - at < DAY_MS)
    }

    private fun snooze(info: UpdateInfo) {
        settings.setUpdateSnooze(info.versionCode, System.currentTimeMillis())
    }

    /**
     * [manual] = the «Проверить обновления» button: then a found update is
     * always shown; an automatic check respects «Позже».
     */
    fun startCheck(manual: Boolean) {
        scope.launch { check(manual) }
    }

    fun startDownloadAndInstall(info: UpdateInfo) {
        scope.launch { downloadAndInstall(info) }
    }

    private suspend fun check(manual: Boolean) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        val result = try {
            val info = fetchLatest()
            settings.setLastUpdateCheckMillis(System.currentTimeMillis())
            when {
                info == null -> UpdateState.UpToDate // no releases published yet
                info.versionCode > BuildConfig.VERSION_CODE -> UpdateState.Available(info)
                else -> UpdateState.UpToDate
            }
        } catch (e: IOException) {
            UpdateState.Failed("Не удалось проверить обновления: нет связи с GitHub. Проверьте интернет.")
        } catch (e: Exception) {
            UpdateState.Failed("Не удалось проверить обновления (${e.javaClass.simpleName}).")
        }
        // An automatic check stays silent unless it found something to offer.
        _state.value = if (!manual && result !is UpdateState.Available) UpdateState.Idle else result
        if (result is UpdateState.Available && (manual || shouldPrompt(result.info))) {
            _dialogVisible.value = true
        }
    }

    /** Can this app install APKs right now? (Android 8+: per-app "Install unknown apps" switch.) */
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen with the "Разрешить установку из этого источника" switch for our app. */
    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openReleasesPage() {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(releasesPageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Download → verify → safety copy → hand over to the installer. */
    private suspend fun downloadAndInstall(info: UpdateInfo) {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(info, null)
        try {
            val apk = download(info)
            backup.saveInternalCopy(reason = "before-update")
            // Set before committing: the installer's answer may arrive right away.
            _state.value = UpdateState.Installing(info)
            withContext(Dispatchers.IO) { installViaPackageInstaller(apk) }
        } catch (e: UpdateException) {
            _state.value = UpdateState.Failed(e.message ?: "Ошибка обновления", info)
        } catch (e: IOException) {
            _state.value = UpdateState.Failed("Не удалось скачать обновление: проверьте интернет и попробуйте ещё раз.", info)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Не удалось обновить (${e.javaClass.simpleName}: ${e.message}).", info)
        }
    }

    /** Called by [InstallResultReceiver] when the installer reports a final failure or cancel. */
    fun onInstallFailed(message: String) {
        val info = (_state.value as? UpdateState.Installing)?.info
        _state.value = UpdateState.Failed(message, info)
    }

    private suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val connection = open(versionJsonUrl)
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
            if (code !in 200..299) throw IOException("HTTP $code")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            UpdateInfo(
                versionName = json.getString("versionName"),
                versionCode = json.getInt("versionCode"),
                apkUrl = json.getString("apkUrl"),
                sha256 = json.getString("sha256").lowercase(),
                notes = json.optString("notes").trim(),
            )
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun download(info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply {
            deleteRecursively() // old, unused downloads
            mkdirs()
        }
        val file = File(dir, "FoodDiary-${info.versionName}.apk")
        val connection = open(info.apkUrl)
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 }
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReported = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        if (total != null) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                _state.value = UpdateState.Downloading(info, percent / 100f)
                            }
                        }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != info.sha256) {
                file.delete()
                throw UpdateException("Файл обновления скачался с ошибкой (контрольная сумма не совпала). Попробуйте ещё раз.")
            }
            file
        } finally {
            connection.disconnect()
        }
    }

    private fun installViaPackageInstaller(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("FoodDiary.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = Intent(context, InstallResultReceiver::class.java)
            // Mutable: the installer adds the result (status, confirm window) to this intent.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, callback, flags)
            session.commit(pending.intentSender)
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true // github.com → objects.githubusercontent.com (both https)
            setRequestProperty("User-Agent", "FoodDiary/${BuildConfig.VERSION_NAME} (Android)")
        }

    private class UpdateException(message: String) : Exception(message)

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
