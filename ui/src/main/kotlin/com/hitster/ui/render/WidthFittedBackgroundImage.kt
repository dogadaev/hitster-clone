package com.hitster.ui.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.SpriteBatch

/**
 * Draws a background image scaled to the full screen width and cropped vertically when needed.
 *
 * The game uses this for art-directed entry and lobby backdrops where the width should always
 * align to the landscape viewport, while the vertical crop anchor differs by screen.
 */
internal class WidthFittedBackgroundImage(
    private val assetPath: String,
    private val cropAnchor: VerticalCropAnchor,
) {
    private lateinit var texture: Texture

    fun load() {
        texture = Texture(Gdx.files.internal(assetPath)).also { loadedTexture ->
            loadedTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
        }
    }

    fun draw(batch: SpriteBatch, worldWidth: Float, worldHeight: Float, tint: Color = Color.WHITE) {
        if (!this::texture.isInitialized) {
            return
        }
        val drawHeight = worldWidth * texture.height.toFloat() / texture.width.toFloat()
        val drawY = when (cropAnchor) {
            VerticalCropAnchor.CENTER -> (worldHeight - drawHeight) / 2f
            VerticalCropAnchor.TOP -> worldHeight - drawHeight
        }
        batch.color = tint
        batch.draw(texture, 0f, drawY, worldWidth, drawHeight)
        batch.setColor(Color.WHITE)
    }

    fun dispose() {
        if (this::texture.isInitialized) {
            texture.dispose()
        }
    }
}

internal enum class VerticalCropAnchor {
    CENTER,
    TOP,
}
