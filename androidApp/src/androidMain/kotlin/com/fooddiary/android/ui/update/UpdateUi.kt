package com.fooddiary.android.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fooddiary.android.BuildConfig
import com.fooddiary.android.data.AppSettings
import com.fooddiary.android.data.UpdateInfo
import com.fooddiary.android.data.UpdateManager
import com.fooddiary.android.data.UpdateState

/**
 * Put once at the root of the app: runs the daily automatic check on start
 * and shows the update window over any screen when there's something to offer.
 */
@Composable
fun UpdateHost(updates: UpdateManager) {
    LaunchedEffect(Unit) {
        // Debug builds are a separate app and can't take a release APK.
        if (!BuildConfig.DEBUG && updates.isAutoCheckDue()) updates.startCheck(manual = false)
    }
    val visible by updates.dialogVisible.collectAsState()
    val state by updates.state.collectAsState()
    if (visible) UpdateDialog(updates, state)
}

@Composable
private fun UpdateDialog(updates: UpdateManager, state: UpdateState) {
    // Set when «Обновить» had to send the user to the "install unknown apps" switch first.
    var askedForPermission by remember { mutableStateOf(false) }

    val info: UpdateInfo? = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.Installing -> state.info
        is UpdateState.Failed -> state.info
        else -> null
    }
    if (info == null) {
        // Nothing to show (e.g. a manual check said "up to date" — that's shown in Settings).
        if (state !is UpdateState.Checking) {
            LaunchedEffect(state) { updates.hideDialog() }
        }
        return
    }

    AlertDialog(
        onDismissRequest = { if (state !is UpdateState.Downloading) updates.hideDialog() },
        title = { Text("Доступна версия ${info.versionName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Сейчас установлена ${BuildConfig.VERSION_NAME}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (info.notes.isNotBlank()) {
                    Text(
                        info.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    )
                }
                when (state) {
                    is UpdateState.Downloading -> {
                        Text("Скачивание…", style = MaterialTheme.typography.bodySmall)
                        val p = state.progress
                        if (p != null) {
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is UpdateState.Installing -> Text(
                        "Открылось окно установки Android — нажмите в нём «Обновить». " +
                            "Данные сохранятся; перед установкой сделана резервная копия.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    is UpdateState.Failed -> Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Unit
                }
                if (BuildConfig.DEBUG) {
                    Text(
                        "Это тестовая версия: обновление ставится в рабочее приложение «Дневник питания». " +
                            "Откройте страницу релиза и скачайте APK там.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (askedForPermission && !updates.canInstallPackages()) {
                    Text(
                        "Включите «Разрешить установку из этого источника» для Дневника питания, " +
                            "вернитесь сюда и нажмите «Обновить» ещё раз.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            when {
                state is UpdateState.Downloading -> TextButton(onClick = updates::hideDialog) { Text("Скрыть") }
                state is UpdateState.Installing -> TextButton(onClick = updates::hideDialog) { Text("Закрыть") }
                BuildConfig.DEBUG -> TextButton(onClick = updates::openReleasesPage) { Text("Открыть страницу") }
                else -> TextButton(onClick = {
                    if (updates.canInstallPackages()) {
                        updates.startDownloadAndInstall(info)
                    } else {
                        askedForPermission = true
                        updates.openInstallPermissionSettings()
                    }
                }) { Text(if (state is UpdateState.Failed) "Повторить" else "Обновить") }
            }
        },
        dismissButton = {
            if (state is UpdateState.Available || state is UpdateState.Failed) {
                TextButton(onClick = { updates.later(info) }) { Text("Позже") }
            }
        },
    )
}

/** «О приложении» part: auto-check switch, «Проверить обновления», status. */
@Composable
fun UpdateSettingsSection(updates: UpdateManager, settings: AppSettings) {
    val state by updates.state.collectAsState()
    val autoCheck by settings.autoCheckUpdates.collectAsState()

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Проверять обновления при запуске (раз в сутки)", modifier = Modifier.weight(1f))
        Switch(checked = autoCheck, onCheckedChange = settings::setAutoCheckUpdates)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = { updates.startCheck(manual = true) },
            enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
        ) { Text("Проверить обновления") }
        if (state is UpdateState.Checking) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    }
    val status: Pair<String, Boolean>? = when (val s = state) {
        UpdateState.UpToDate -> "У вас последняя версия." to false
        is UpdateState.Available -> "Доступна версия ${s.info.versionName}." to false
        is UpdateState.Downloading -> "Скачивается версия ${s.info.versionName}…" to false
        is UpdateState.Failed -> s.message to true
        else -> null
    }
    status?.let { (text, isError) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (state is UpdateState.Available || state is UpdateState.Downloading) {
                TextButton(onClick = updates::showDialog) { Text("Подробнее") }
            }
        }
    }
    if (BuildConfig.DEBUG) {
        Text(
            "Тестовая версия: автоматическая проверка отключена, обновления ставятся в рабочее приложение.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
