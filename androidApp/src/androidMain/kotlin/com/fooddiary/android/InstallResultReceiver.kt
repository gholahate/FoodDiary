package com.fooddiary.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * Receives what Android's installer says about our update session:
 *  - PENDING_USER_ACTION → show the system "Обновить приложение?" window;
 *  - SUCCESS → nothing to do (the app is replaced and restarted by the system);
 *  - anything else → explain in the app.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updates = (context.applicationContext as FoodDiaryApp).container.updates
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    updates.onInstallFailed("Android не показал окно установки. Попробуйте ещё раз.")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> updates.onInstallFailed("Установка отменена.")
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            -> updates.onInstallFailed(
                "Android не принял обновление: оно подписано другим ключом или несовместимо с установленной версией.",
            )
            PackageInstaller.STATUS_FAILURE_STORAGE -> updates.onInstallFailed("Не хватает места на телефоне для установки.")
            else -> {
                val details = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                updates.onInstallFailed("Не удалось установить обновление (код $status${details?.let { ": $it" } ?: ""}).")
            }
        }
    }
}
