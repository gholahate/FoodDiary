package com.fooddiary.android.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Light/dark appearance choice from «Настройки». */
enum class ThemeMode(val label: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
}

/** How the Charts tab draws values: columns, or a line through dots. */
enum class ChartStyle(val label: String) {
    BARS("Столбцы"),
    LINE("Линия с точками"),
}

/**
 * Small app preferences kept in Android's SharedPreferences (a separate file
 * from the diary database), so adding settings here never needs a database
 * migration. Values are exposed as StateFlow, so the UI reacts instantly.
 */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME, null)
            ?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } }
            ?: ThemeMode.SYSTEM,
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    private val _chartStyle = MutableStateFlow(
        prefs.getString(KEY_CHART_STYLE, null)
            ?.let { saved -> ChartStyle.entries.firstOrNull { it.name == saved } }
            ?: ChartStyle.BARS,
    )
    val chartStyle: StateFlow<ChartStyle> = _chartStyle

    fun setChartStyle(style: ChartStyle) {
        prefs.edit().putString(KEY_CHART_STYLE, style.name).apply()
        _chartStyle.value = style
    }

    /** For the backup file: all settings as simple name → value pairs. */
    fun exportValues(): Map<String, String> = mapOf(
        KEY_THEME to themeMode.value.name,
        KEY_CHART_STYLE to chartStyle.value.name,
    )

    /** Restores what [exportValues] wrote; unknown keys/values are ignored. */
    fun importValues(values: Map<String, String>) {
        val theme = values[KEY_THEME]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } }
        val chart = values[KEY_CHART_STYLE]?.let { v -> ChartStyle.entries.firstOrNull { it.name == v } }
        // commit(), not apply(): the app restarts right after a restore, and
        // apply() might not have reached the disk by then.
        prefs.edit().apply {
            theme?.let { putString(KEY_THEME, it.name) }
            chart?.let { putString(KEY_CHART_STYLE, it.name) }
        }.commit()
        theme?.let { _themeMode.value = it }
        chart?.let { _chartStyle.value = it }
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_CHART_STYLE = "chart_style"
    }
}
