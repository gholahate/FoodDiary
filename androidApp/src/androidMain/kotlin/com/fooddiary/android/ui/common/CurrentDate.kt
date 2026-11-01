package com.fooddiary.android.ui.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** Today's date in the phone's time zone, as "YYYY-MM-DD". */
fun todayIsoString(): String = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()

/**
 * Today's date ("YYYY-MM-DD") that keeps itself up to date while the app stays
 * open: when midnight passes, the value changes and every screen reading it
 * switches to the new day on its own (no app restart needed).
 *
 * Two independent sources, because neither alone is reliable:
 *  - system broadcasts: the once-a-minute clock tick, the date change at
 *    midnight, and manual time / time-zone changes;
 *  - returning to the app (ON_RESUME): covers the case where the phone slept
 *    through midnight and the broadcasts were delayed or skipped.
 * Writing the same date again does nothing, so frequent checks are free.
 */
@Composable
fun rememberCurrentDate(): State<String> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val date = remember { mutableStateOf(todayIsoString()) }

    DisposableEffect(context, lifecycleOwner) {
        fun refresh() {
            date.value = todayIsoString()
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) = refresh()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // System broadcasts are delivered even to a "not exported" receiver.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refresh()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return date
}
