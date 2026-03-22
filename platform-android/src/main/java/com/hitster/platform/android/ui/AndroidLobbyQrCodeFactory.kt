package com.hitster.platform.android

/**
 * Builds the host-lobby QR texture that points guests at the phone-hosted raw-IP join URL.
 */

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

internal object AndroidLobbyQrCodeFactory {
    fun createTexture(contents: String, size: Int = 384): Texture {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        )
        val matrix = QRCodeWriter().encode(contents, BarcodeFormat.QR_CODE, size, size, hints)
        val pixmap = Pixmap(matrix.width, matrix.height, Pixmap.Format.RGBA8888)
        try {
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    pixmap.drawPixel(x, y, if (matrix.get(x, y)) 0x111111FF.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            return Texture(pixmap).apply {
                setFilter(TextureFilter.Nearest, TextureFilter.Nearest)
            }
        } finally {
            pixmap.dispose()
        }
    }
}
