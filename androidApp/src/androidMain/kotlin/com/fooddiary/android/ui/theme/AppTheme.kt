package com.fooddiary.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.fooddiary.android.data.ThemeMode

/** True when the app is currently drawn in the dark theme. */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * Fixed "meaning" colours (goal met / over / carried / didn't fit). They are
 * saturated enough to read on both light and dark backgrounds; only the
 * purple caption text gets a lighter shade in dark mode.
 */
object StatusColors {
    val ok = Color(0xFF4CAF50)      // green: within goal
    val over = Color(0xFFE53935)    // red: over goal
    val carry = Color(0xFFFF9800)   // orange: overflow carried from another day
    val unplaced = Color(0xFF9C27B0) // purple: didn't fit into the week

    @Composable
    fun unplacedText(): Color = if (LocalIsDarkTheme.current) Color(0xFFCE93D8) else unplaced
}

/**
 * One corner radius for all "frames": cards already use `medium` (12dp); text
 * fields (and dropdown menus) use `extraSmall`, which is 4dp by default —
 * raised to 12dp so fields and cards look the same everywhere.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
)

/**
 * Wraps the whole app: picks the light or dark Material colour scheme from
 * the setting (Системная follows the phone), paints the background, and
 * matches the status/navigation bar colours and icons to it.
 */
@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) darkColorScheme() else lightColorScheme()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.surface.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colors.surfaceContainer.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalIsDarkTheme provides dark) {
        MaterialTheme(colorScheme = colors, shapes = AppShapes) {
            // The window itself has a light background (manifest theme), so
            // every screen sits on a Surface of the theme's background colour.
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                content()
            }
        }
    }
}
