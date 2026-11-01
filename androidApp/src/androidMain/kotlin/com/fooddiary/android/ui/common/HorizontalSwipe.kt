package com.fooddiary.android.ui.common

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Detects a horizontal swipe on this element: finger moves right → [onSwipeRight]
 * (used as "back": previous month / previous day), left → [onSwipeLeft]
 * ("forward"). A swipe counts once the finger travelled at least 72dp
 * sideways; vertical scrolling inside (lists) keeps working as before.
 */
@Composable
fun Modifier.horizontalSwipe(onSwipeLeft: () -> Unit, onSwipeRight: () -> Unit): Modifier {
    val thresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val left by rememberUpdatedState(onSwipeLeft)
    val right by rememberUpdatedState(onSwipeRight)
    return this.pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                when {
                    total <= -thresholdPx -> left()
                    total >= thresholdPx -> right()
                }
                total = 0f
            },
            onDragCancel = { total = 0f },
            onHorizontalDrag = { change, dragAmount ->
                total += dragAmount
                change.consume()
            },
        )
    }
}
