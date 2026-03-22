package com.hitster.ui.render

/**
 * Reusable animated background renderer that gives every screen a single coherent moving atmosphere instead of patched panels.
 */

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

    fun drawShapes(shapeRenderer: ShapeRenderer, worldWidth: Float, worldHeight: Float, _outerMargin: Float) {
        fillGradientRect(
            shapeRenderer,
            0f,
            0f,
            worldWidth,
            worldHeight,
            0x040B14FF,
            0x091521FF,
            0x214D7CFF,
            0x13314FFF,
        )
        fillGradientRect(shapeRenderer, 0f, 0f, worldWidth, worldHeight, 0x060A103F, 0x0A10184F, 0x1F4C7C22, 0x295A902C)
        fillGradientRect(shapeRenderer, 0f, 0f, worldWidth, worldHeight, 0x020407B8, 0x05091194, 0x00000000, 0x00000000)
        fillGradientRect(shapeRenderer, 0f, 0f, worldWidth, worldHeight, 0x120E090E, 0x090E1302, 0x14315600, 0x224E821A)
        fillGradientRect(shapeRenderer, 0f, 0f, worldWidth, worldHeight, 0x00000000, 0x060B120C, 0x1A3A621A, 0x2C6CB022)
        fillGradientRect(shapeRenderer, 0f, 0f, worldWidth, worldHeight, 0x00000028, 0x00000010, 0x0E203A00, 0x17375E10)
    }

    fun drawTextures(batch: SpriteBatch, worldWidth: Float, worldHeight: Float, timeSeconds: Float, intensity: Float = 1f) {
        val leftBloomX = -worldWidth * 0.16f + sin(timeSeconds * 0.11f) * 120f
        val leftBloomY = worldHeight * 0.18f + cos(timeSeconds * 0.13f) * 36f
        val centerBloomX = worldWidth * 0.20f + cos(timeSeconds * 0.08f) * 140f
        val centerBloomY = worldHeight * 0.44f + sin(timeSeconds * 0.10f) * 44f
        val rightBloomX = worldWidth * 0.58f + sin(timeSeconds * 0.07f) * 148f
        val rightBloomY = worldHeight * 0.50f + cos(timeSeconds * 0.09f) * 34f
        val topBloomX = worldWidth * 0.32f + cos(timeSeconds * 0.05f) * 110f
        val topBloomY = worldHeight * 0.66f + sin(timeSeconds * 0.06f) * 30f
        val emberX = worldWidth * 0.72f + cos(timeSeconds * 0.06f) * 82f
        val emberY = -worldHeight * 0.08f + sin(timeSeconds * 0.08f) * 44f
        val sweepX = -worldWidth * 0.28f + sin(timeSeconds * 0.05f) * 84f
        val sweepY = worldHeight * 0.16f + cos(timeSeconds * 0.04f) * 28f

        drawGlow(batch, leftBloomX, leftBloomY, worldWidth * 0.62f, worldHeight * 0.54f, colorWithAlpha(0x2B77D6FF, 0.16f * intensity))
        drawGlow(batch, centerBloomX, centerBloomY, worldWidth * 0.78f, worldHeight * 0.56f, colorWithAlpha(0x72A7FFFF, 0.13f * intensity))
        drawGlow(batch, rightBloomX, rightBloomY, worldWidth * 0.64f, worldHeight * 0.48f, colorWithAlpha(0x1B5CAFFF, 0.14f * intensity))
        drawGlow(batch, topBloomX, topBloomY, worldWidth * 0.72f, worldHeight * 0.28f, colorWithAlpha(0xA7CEFFFF, 0.06f * intensity))
        drawGlow(batch, emberX, emberY, worldWidth * 0.54f, worldWidth * 0.54f, colorWithAlpha(0xF4B864FF, 0.08f * intensity))
        drawGlow(batch, sweepX, sweepY, worldWidth * 1.34f, worldHeight * 0.26f, colorWithAlpha(0xD8E9FFFF, 0.07f * intensity))

        drawRepeatedTexture(
            batch,
            grainTexture,
            0f,
            0f,
            worldWidth,
            worldHeight,
            colorWithAlpha(0xEEF5FFFF, 0.08f * intensity),
            worldWidth / 126f,
            worldHeight / 126f,
            timeSeconds * 0.010f,
            timeSeconds * 0.008f,
        )
        drawRepeatedTexture(
            batch,
            grainTexture,
            0f,
            0f,
            worldWidth,
            worldHeight,
            colorWithAlpha(0x7FAEDCFF, 0.04f * intensity),
            worldWidth / 82f,
            worldHeight / 82f,
            -timeSeconds * 0.006f,
            timeSeconds * 0.004f,
        )
        drawTexture(batch, vignetteTexture, 0f, 0f, worldWidth, worldHeight, color(0x000000A2))
        drawTexture(batch, vignetteTexture, 0f, 0f, worldWidth, worldHeight, colorWithAlpha(0x21304BFF, 0.11f * intensity))
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
