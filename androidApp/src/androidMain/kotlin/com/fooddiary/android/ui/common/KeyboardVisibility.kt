package com.fooddiary.android.ui.common

import android.graphics.Rect
import android.os.Build
import android.view.ViewTreeObserver
import android.view.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Whether the on-screen keyboard is currently open.
 *
 * Compose's own WindowInsets.ime is not usable here: the app uses the normal
 * (non edge-to-edge) window with adjustResize, and in that mode Android
 * consumes the keyboard inset before it reaches Compose, so it always reads as
 * "hidden". Instead this asks the window's root directly (Android 11+), and on
 * older versions compares the visible screen area with the full screen height.
 *
 * Used to hide the day totals, the weekly bar and the bottom menu while typing,
 * so the product list gets the freed space.
 */
@Composable
fun rememberKeyboardVisible(): State<Boolean> {
    val view = LocalView.current
    val visible = remember { mutableStateOf(false) }

    DisposableEffect(view) {
        fun keyboardOpen(): Boolean {
            if (Build.VERSION.SDK_INT >= 30) {
                return view.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
            }
            val frame = Rect()
            view.getWindowVisibleDisplayFrame(frame)
            val screenHeight = view.resources.displayMetrics.heightPixels
            return screenHeight - frame.bottom > screenHeight * 0.15
        }

        // The window is resized when the keyboard opens/closes, which triggers a layout pass.
        val listener = ViewTreeObserver.OnGlobalLayoutListener { visible.value = keyboardOpen() }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        visible.value = keyboardOpen()

        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return visible
}
