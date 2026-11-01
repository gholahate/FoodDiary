package com.fooddiary.android.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fooddiary.android.data.BackupException
import com.fooddiary.android.data.BackupManager
import com.fooddiary.android.data.BackupSummary
import com.fooddiary.android.data.PreparedRestore
import kotlinx.coroutines.launch

/**
 * «Резервная копия»:
 *  - «Сохранить копию» → the system "save as" dialog (Загрузки, Google Диск…)
 *    → one .zip file with the whole diary and settings;
 *  - «Восстановить из копии» → pick a file → check it → show what's inside →
 *    confirm → current data is replaced and the app restarts.
 */
@Composable
fun BackupSection(backup: BackupManager) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<PreparedRestore?>(null) }

    fun report(text: String, error: Boolean) {
        message = text
        isError = error
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult // user cancelled
        busy = true
        scope.launch {
            try {
                val saved = backup.exportTo(uri)
                report("Копия сохранена: ${saved.describe()}.", error = false)
            } catch (e: BackupException) {
                report(e.message ?: "Не удалось сохранить копию", error = true)
            } catch (e: Exception) {
                report("Не удалось сохранить копию (${e.javaClass.simpleName}: ${e.message})", error = true)
            } finally {
                busy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                pendingRestore = backup.prepareRestore(uri)
                message = null
            } catch (e: BackupException) {
                report(e.message ?: "Файл не подходит", error = true)
            } catch (e: Exception) {
                report("Не удалось прочитать файл (${e.javaClass.simpleName}: ${e.message})", error = true)
            } finally {
                busy = false
            }
        }
    }

    pendingRestore?.let { prepared ->
        val s = prepared.summary
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Заменить данные?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("В копии: ${s.describe()}.")
                    if (s.createdAt != null || s.appVersion != null) {
                        Text(
                            listOfNotNull(
                                s.createdAt?.let { "сделана $it" },
                                s.appVersion?.let { "версия $it" },
                            ).joinToString(", ").replaceFirstChar { it.uppercase() } + ".",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "Все текущие продукты, записи и цели будут заменены данными из копии. " +
                            "Текущие данные перед этим сохранятся во внутреннюю папку приложения. " +
                            "Приложение перезапустится.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    busy = true
                    scope.launch {
                        try {
                            backup.applyRestore(prepared) // restarts the app on success
                        } catch (e: Exception) {
                            busy = false
                            report("Не удалось восстановить (${e.javaClass.simpleName}: ${e.message})", error = true)
                        }
                    }
                }) { Text("Заменить") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Отмена") } },
        )
    }

    Text("Резервная копия", fontWeight = FontWeight.Bold)
    Text(
        "Вся база (продукты, дневник, цели) и настройки — в один файл. Сохраните его в «Загрузки», " +
            "на Google Диск или отправьте себе в Telegram.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { exportLauncher.launch(backup.suggestedFileName()) }, enabled = !busy) {
            Text("Сохранить копию")
        }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !busy) {
            Text("Восстановить")
        }
        if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    }
    message?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun BackupSummary.describe(): String =
    "продуктов $products, записей в дневнике $diaryEntries" +
        (lastDiaryDate?.let { ", последняя запись $it" } ?: "")
