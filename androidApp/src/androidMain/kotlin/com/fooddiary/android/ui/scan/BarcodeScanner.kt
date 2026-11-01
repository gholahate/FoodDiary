package com.fooddiary.android.ui.scan

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Opens Google's ready-made scanner screen (camera + viewfinder) on top of the
 * app and reports what it read. It runs inside Google Play services, so the
 * app itself needs no camera permission. Requires Google Play services on the
 * device (an emulator needs a "Google Play" system image). On first use the
 * scanner module may still be downloading — that comes back as [onFailed].
 */
fun startBarcodeScan(
    context: Context,
    onScanned: (rawValue: String) -> Unit,
    onCancelled: () -> Unit,
    onFailed: (message: String) -> Unit,
) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_DATA_MATRIX, // "Честный знак" square codes
        )
        .enableAutoZoom()
        .build()

    val scanner = runCatching { GmsBarcodeScanning.getClient(context, options) }.getOrElse {
        onFailed("Сканер недоступен на этом устройстве (нужны сервисы Google Play).")
        return
    }

    scanner.startScan()
        .addOnSuccessListener { barcode ->
            val raw = barcode.rawValue
            if (raw.isNullOrBlank()) onFailed("Код прочитан, но он пустой. Попробуйте ещё раз.") else onScanned(raw)
        }
        .addOnCanceledListener { onCancelled() }
        .addOnFailureListener { error ->
            onFailed(
                "Не удалось запустить сканер. Если это первый запуск, модуль сканера может ещё " +
                    "загружаться — подождите минуту и нажмите «Повторить».\n(${error.message ?: error.javaClass.simpleName})",
            )
        }
}
