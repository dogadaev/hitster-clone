package com.hitster.ui.render

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import io.nayuki.fastqrcodegen.QrCode

/**
 * Shared QR texture builder for the lobby join panel so host and guest devices can render the same
 * browser-join affordance from the advertised raw-IP URL.
 */
object LobbyQrCodeTextureFactory {
    fun createTexture(contents: String, size: Int = 384): Texture {
        val qrCode = QrCode.encodeText(contents, QrCode.Ecc.MEDIUM)
        val border = 1
        val moduleCount = qrCode.size + border * 2
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        try {
            pixmap.setColor(1f, 1f, 1f, 1f)
            pixmap.fill()
            for (y in 0 until size) {
                val moduleY = y * moduleCount / size - border
                for (x in 0 until size) {
                    val moduleX = x * moduleCount / size - border
                    val isDark = moduleX in 0 until qrCode.size &&
                        moduleY in 0 until qrCode.size &&
                        qrCode.getModule(moduleX, moduleY)
                    if (isDark) {
                        pixmap.drawPixel(x, y, 0x111111FF.toInt())
                    }
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
