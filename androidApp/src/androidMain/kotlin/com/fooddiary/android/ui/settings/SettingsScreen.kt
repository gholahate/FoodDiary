package com.fooddiary.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.fooddiary.android.BuildConfig
import com.fooddiary.android.data.AppSettings
import com.fooddiary.android.data.BackupManager
import com.fooddiary.android.data.UpdateManager
import com.fooddiary.android.ui.update.UpdateSettingsSection
import com.fooddiary.android.data.ChartStyle
import com.fooddiary.android.data.ThemeMode
import androidx.compose.ui.unit.dp

/**
 * Settings screen. Currently holds placeholder items only — each is wired up
 * as its underlying feature is built. "Week starts on" is a stub for now: the
 * weekly progress bar on Today (see DiaryDayContent) is hardcoded to Monday
 * per the current agreement, and this setting will control that once
 * implemented.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    backup: BackupManager,
    updates: UpdateManager,
    modifier: Modifier = Modifier,
) {
    val themeMode by settings.themeMode.collectAsState()

    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // Appearance: applied instantly and remembered between launches.
        Text("Тема оформления", fontWeight = FontWeight.Bold)
        ThemeMode.entries.forEach { mode ->
            ChoiceRow(
                label = mode.label,
                selected = themeMode == mode,
                onClick = { settings.setThemeMode(mode) },
            )
        }
        Text(
            "«Системная» — как в настройках телефона.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        Text("Вид графиков", fontWeight = FontWeight.Bold)
        val chartStyle by settings.chartStyle.collectAsState()
        ChartStyle.entries.forEach { style ->
            ChoiceRow(
                label = style.label,
                selected = chartStyle == style,
                onClick = { settings.setChartStyle(style) },
            )
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        SettingsRow(
            label = "Неделя начинается с",
            valuePreview = "Понедельник",
            onClick = { /* TODO: not yet implemented — see design notes */ },
        )

        Text(
            "Пока не настраивается — неделя всегда считается с понедельника.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        BackupSection(backup)

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // «О приложении»: values are baked in at build time (see androidApp/build.gradle.kts).
        Text("О приложении", fontWeight = FontWeight.Bold)
        InfoRow("Приложение", "Дневник питания")
        InfoRow("Версия", "${BuildConfig.VERSION_NAME} (сборка ${BuildConfig.VERSION_CODE})")
        InfoRow("Дата сборки", BuildConfig.BUILD_TIME)
        UpdateSettingsSection(updates, settings)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SettingsRow(label: String, valuePreview: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        TextButton(onClick = onClick) { Text(valuePreview) }
    }
}
