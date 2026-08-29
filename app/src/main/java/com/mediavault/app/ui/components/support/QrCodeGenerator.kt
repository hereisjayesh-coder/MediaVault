package com.mediavault.app.ui.components.support

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Local, offline QR-code encoding only — no scanning, no camera, no network call. `zxing:core`
 * is a pure-JVM artifact (no Android dependency of its own), so this is the one small adapter
 * turning its output ([com.google.zxing.common.BitMatrix]) into an [android.graphics.Bitmap]
 * Compose's `Image`/`Icon` can render directly.
 *
 * [sizePx] defaults to 256 — comfortably above the 96dp display size this is actually shown at
 * (`SupportSection`) on any real device density, so there's no visible softness, while keeping
 * the pixel count (and therefore the one-time generation cost below) proportionate to what's
 * actually rendered rather than 4x oversized.
 *
 * Builds the whole pixel buffer as a plain [IntArray] and hands it to [Bitmap.createBitmap] in
 * one call, instead of one [Bitmap.setPixel] call per pixel (the original implementation) — each
 * `setPixel` crosses into native code individually, which measured as the actual cause of a real,
 * live-device-confirmed scroll hitch (up to ~150ms on a Pixel 7a) the first time this composable
 * re-entered a `LazyColumn`'s composition window after scrolling away and back, since Compose
 * discards a lazy item's `remember` cache once it leaves the list's retention window. Filling a
 * plain array first and constructing the `Bitmap` in a single bulk call removes that per-pixel
 * native-call overhead entirely — see PROJECT_MASTER.md's 2026-08-29 scrolling-performance
 * decision log entry for the full measurement.
 */
fun generateQrCodeBitmap(content: String, sizePx: Int = 256): Bitmap {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        val rowOffset = y * matrix.width
        for (x in 0 until matrix.width) {
            pixels[rowOffset + x] = if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.RGB_565)
}
