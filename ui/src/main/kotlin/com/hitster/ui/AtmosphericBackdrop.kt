package com.hitster.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.Texture.TextureWrap
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal class AtmosphericBackdrop {
    private lateinit var grainTexture: Texture
    private lateinit var glowTexture: Texture
    private lateinit var vignetteTexture: Texture

    fun load() {
        grainTexture = createGrainTexture()
        glowTexture = createGlowTexture()
        vignetteTexture = createVignetteTexture()
    }

    fun dispose() {
        if (this::grainTexture.isInitialized) {
            grainTexture.dispose()
        }
        if (this::glowTexture.isInitialized) {
            glowTexture.dispose()
        }
        if (this::vignetteTexture.isInitialized) {
            vignetteTexture.dispose()
        }
    }

    fun drawShapes(shapeRenderer: ShapeRenderer, worldWidth: Float, worldHeight: Float, outerMargin: Float) {
        fillGradientRect(shapeRenderer, 0f, 0f, worldWidth, worldHeight, 0x04101AFF, 0x071522FF, 0x16355CFF, 0x102948FF)
        fillGradientRect(shapeRenderer, 0f, 0f, worldWidth, worldHeight, 0x02060C78, 0x050A104E, 0x00000000, 0x0F244000)
        fillGradientRect(
            shapeRenderer,
            0f,
            worldHeight * 0.54f,
            worldWidth,
            worldHeight * 0.46f,
            0x00000000,
            0x02060A00,
            0x3769AC82,
            0x284F845E,
        )
        fillGradientRect(
            shapeRenderer,
            0f,
            0f,
            worldWidth,
            worldHeight * 0.36f,
            0x10182724,
            0x0B132014,
            0x00000000,
            0x00000000,
        )

        val crownHeight = clamp(worldHeight * 0.09f, 58f, 76f)
        fillGradientRect(
            shapeRenderer,
            outerMargin,
            worldHeight - outerMargin - crownHeight,
            worldWidth - outerMargin * 2f,
            crownHeight,
            0x22345FFF,
            0x1A2B4EFF,
            0x395790FF,
            0x2C4475FF,
        )
        fillRect(
            shapeRenderer,
            outerMargin + 8f,
            worldHeight - outerMargin - crownHeight + 6f,
            worldWidth - outerMargin * 2f - 16f,
            2f,
            0xFFFFFF18,
        )
        fillRect(
            shapeRenderer,
            outerMargin + 8f,
            worldHeight - outerMargin - 10f,
            worldWidth - outerMargin * 2f - 16f,
            2f,
            0x00000038,
        )
    }

    fun drawTextures(batch: SpriteBatch, worldWidth: Float, worldHeight: Float, timeSeconds: Float, intensity: Float = 1f) {
        val leftBloomX = worldWidth * 0.04f + sin(timeSeconds * 0.11f) * 86f
        val leftBloomY = worldHeight * 0.35f + cos(timeSeconds * 0.15f) * 32f
        val centerBloomX = worldWidth * 0.34f + cos(timeSeconds * 0.08f) * 118f
        val centerBloomY = worldHeight * 0.58f + sin(timeSeconds * 0.10f) * 36f
        val rightBloomX = worldWidth * 0.64f + sin(timeSeconds * 0.07f) * 126f
        val rightBloomY = worldHeight * 0.54f + cos(timeSeconds * 0.09f) * 30f
        val emberX = -worldWidth * 0.05f + cos(timeSeconds * 0.06f) * 70f
        val emberY = -worldHeight * 0.03f + sin(timeSeconds * 0.08f) * 40f
        val sweepX = -worldWidth * 0.20f + sin(timeSeconds * 0.05f) * 68f
        val sweepY = worldHeight * 0.22f + cos(timeSeconds * 0.04f) * 24f

        drawGlow(batch, leftBloomX, leftBloomY, worldWidth * 0.46f, worldHeight * 0.34f, colorWithAlpha(0x2F89D8FF, 0.15f * intensity))
        drawGlow(batch, centerBloomX, centerBloomY, worldWidth * 0.66f, worldHeight * 0.42f, colorWithAlpha(0x6B98F3FF, 0.14f * intensity))
        drawGlow(batch, rightBloomX, rightBloomY, worldWidth * 0.52f, worldHeight * 0.36f, colorWithAlpha(0x1E5FA8FF, 0.14f * intensity))
        drawGlow(batch, emberX, emberY, worldWidth * 0.46f, worldWidth * 0.46f, colorWithAlpha(0xF2B457FF, 0.11f * intensity))
        drawGlow(batch, sweepX, sweepY, worldWidth * 1.12f, worldHeight * 0.22f, colorWithAlpha(0xCCE3FFFF, 0.08f * intensity))

        drawRepeatedTexture(
            batch,
            grainTexture,
            0f,
            0f,
            worldWidth,
            worldHeight,
            colorWithAlpha(0xEAF2FFFF, 0.08f * intensity),
            worldWidth / 114f,
            worldHeight / 114f,
            timeSeconds * 0.012f,
            timeSeconds * 0.010f,
        )
        drawRepeatedTexture(
            batch,
            grainTexture,
            0f,
            0f,
            worldWidth,
            worldHeight,
            colorWithAlpha(0x78AED7FF, 0.05f * intensity),
            worldWidth / 76f,
            worldHeight / 76f,
            -timeSeconds * 0.007f,
            timeSeconds * 0.005f,
        )
        drawTexture(batch, vignetteTexture, 0f, 0f, worldWidth, worldHeight, color(0x000000B2))
        drawTexture(batch, vignetteTexture, 0f, 0f, worldWidth, worldHeight, colorWithAlpha(0x21304BFF, 0.15f * intensity))
    }

    fun drawPanelTexture(batch: SpriteBatch, rect: Rectangle, tint: Color, timeSeconds: Float) {
        drawRepeatedTexture(
            batch,
            grainTexture,
            rect.x + 2f,
            rect.y + 2f,
            rect.width - 4f,
            rect.height - 4f,
            tint,
            max(1f, rect.width / 92f),
            max(1f, rect.height / 92f),
            timeSeconds * 0.006f,
            timeSeconds * 0.004f,
        )
    }

    private fun createGrainTexture(): Texture {
        val size = 96
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                val diagonal = (x + y) % 19 < 2
                val speck = ((x * 17 + y * 31) % 23) == 0
                val cross = x % 24 == 0 || y % 24 == 0
                val alpha = when {
                    diagonal -> 0.10f
                    speck -> 0.14f
                    cross -> 0.04f
                    else -> 0.015f
                }
                pixmap.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, alpha))
            }
        }
        return Texture(pixmap).also { texture ->
            pixmap.dispose()
            texture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
            texture.setWrap(TextureWrap.Repeat, TextureWrap.Repeat)
        }
    }

    private fun createGlowTexture(): Texture {
        val size = 192
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val center = (size - 1) / 2f
        val maxDistance = center * center * 2f
        for (x in 0 until size) {
            for (y in 0 until size) {
                val dx = x - center
                val dy = y - center
                val distance = (dx * dx + dy * dy) / maxDistance
                val alpha = clamp(1f - distance * 2.2f, 0f, 1f)
                pixmap.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, alpha * alpha))
            }
        }
        return Texture(pixmap).also { texture ->
            pixmap.dispose()
            texture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
        }
    }

    private fun createVignetteTexture(): Texture {
        val size = 192
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val center = (size - 1) / 2f
        val maxDistance = center * center * 2f
        for (x in 0 until size) {
            for (y in 0 until size) {
                val dx = x - center
                val dy = y - center
                val distance = (dx * dx + dy * dy) / maxDistance
                val alpha = clamp((distance - 0.12f) * 1.4f, 0f, 0.92f)
                pixmap.drawPixel(x, y, Color.rgba8888(0f, 0f, 0f, alpha))
            }
        }
        return Texture(pixmap).also { texture ->
            pixmap.dispose()
            texture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
        }
    }

    private fun drawTexture(batch: SpriteBatch, texture: Texture, x: Float, y: Float, width: Float, height: Float, tint: Color) {
        batch.color = tint
        batch.draw(texture, x, y, width, height)
        batch.setColor(Color.WHITE)
    }

    private fun drawRepeatedTexture(
        batch: SpriteBatch,
        texture: Texture,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        tint: Color,
        repeatX: Float,
        repeatY: Float,
        offsetX: Float,
        offsetY: Float,
    ) {
        batch.color = tint
        batch.draw(texture, x, y, width, height, offsetX, offsetY, offsetX + repeatX, offsetY + repeatY)
        batch.setColor(Color.WHITE)
    }

    private fun drawGlow(batch: SpriteBatch, x: Float, y: Float, width: Float, height: Float, tint: Color) {
        batch.flush()
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        batch.color = tint
        batch.draw(glowTexture, x, y, width, height)
        batch.flush()
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.setColor(Color.WHITE)
    }

    private fun fillGradientRect(
        shapeRenderer: ShapeRenderer,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        bottomLeft: Long,
        bottomRight: Long,
        topRight: Long,
        topLeft: Long,
    ) {
        shapeRenderer.rect(
            x,
            y,
            width,
            height,
            color(bottomLeft),
            color(bottomRight),
            color(topRight),
            color(topLeft),
        )
    }

    private fun fillRect(shapeRenderer: ShapeRenderer, x: Float, y: Float, width: Float, height: Float, rgba: Long) {
        shapeRenderer.color = color(rgba)
        shapeRenderer.rect(x, y, width, height)
    }

    private fun color(rgba: Long): Color {
        return Color(
            (((rgba shr 24) and 0xFF) / 255f).toFloat(),
            (((rgba shr 16) and 0xFF) / 255f).toFloat(),
            (((rgba shr 8) and 0xFF) / 255f).toFloat(),
            ((rgba and 0xFF) / 255f).toFloat(),
        )
    }

    private fun colorWithAlpha(rgba: Long, alphaMultiplier: Float): Color {
        return color(rgba).also { it.a *= clamp(alphaMultiplier, 0f, 1f) }
    }

    private fun clamp(value: Float, minValue: Float, maxValue: Float): Float {
        return max(minValue, min(value, maxValue))
    }
}
