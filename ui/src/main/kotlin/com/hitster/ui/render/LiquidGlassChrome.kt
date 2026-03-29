package com.hitster.ui.render

/**
 * Shared soft-edged glass primitives used by the UI to avoid rigid framed boxes and sharp corners.
 *
 * The intent is not photo-real glass, but a lightweight rounded translucent treatment that keeps
 * background art visible while still giving panels and controls enough separation to remain legible.
 */

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal object LiquidGlassChrome {
    fun beginFilled(shapeRenderer: ShapeRenderer) {
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
    }

    fun endFilled(shapeRenderer: ShapeRenderer) {
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    fun drawRoundedShadow(
        shapeRenderer: ShapeRenderer,
        rect: Rectangle,
        radius: Float,
        spread: Float,
        rgba: Long,
    ) {
        drawRoundedShadow(shapeRenderer, rect.x, rect.y, rect.width, rect.height, radius, spread, rgba)
    }

    fun drawRoundedShadow(
        shapeRenderer: ShapeRenderer,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        spread: Float,
        rgba: Long,
    ) {
        repeat(5) { layer ->
            val progress = (layer + 1) / 5f
            val expansion = spread * progress
            val alpha = when (layer) {
                0 -> 0x26
                1 -> 0x1B
                2 -> 0x13
                3 -> 0x0B
                else -> 0x06
            }
            fillRoundedRect(
                shapeRenderer,
                x - expansion,
                y - expansion * 0.62f,
                width + expansion * 2f,
                height + expansion * 1.24f,
                radius + expansion * 0.72f,
                withAlpha(rgba, alpha),
            )
        }
    }

    fun fillRoundedRect(
        shapeRenderer: ShapeRenderer,
        rect: Rectangle,
        radius: Float,
        rgba: Long,
    ) {
        fillRoundedRect(shapeRenderer, rect.x, rect.y, rect.width, rect.height, radius, rgba)
    }

    fun fillRoundedRect(
        shapeRenderer: ShapeRenderer,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        rgba: Long,
    ) {
        if (width <= 0f || height <= 0f) {
            return
        }
        val resolvedRadius = min(radius, min(width, height) / 2f)
        val segments = max(18, min(48, (resolvedRadius * 0.9f).roundToInt() + 12))
        shapeRenderer.color = color(rgba)
        if (resolvedRadius <= 1f) {
            shapeRenderer.rect(x, y, width, height)
            return
        }

        val middleWidth = max(0f, width - resolvedRadius * 2f)
        val middleHeight = max(0f, height - resolvedRadius * 2f)
        if (middleWidth > 0f) {
            shapeRenderer.rect(x + resolvedRadius, y, middleWidth, height)
        }
        if (middleHeight > 0f) {
            shapeRenderer.rect(x, y + resolvedRadius, resolvedRadius, middleHeight)
            shapeRenderer.rect(x + width - resolvedRadius, y + resolvedRadius, resolvedRadius, middleHeight)
        }
        shapeRenderer.circle(x + resolvedRadius, y + resolvedRadius, resolvedRadius, segments)
        shapeRenderer.circle(x + width - resolvedRadius, y + resolvedRadius, resolvedRadius, segments)
        shapeRenderer.circle(x + resolvedRadius, y + height - resolvedRadius, resolvedRadius, segments)
        shapeRenderer.circle(x + width - resolvedRadius, y + height - resolvedRadius, resolvedRadius, segments)
    }

    fun color(rgba: Long): Color {
        return Color(
            (((rgba shr 24) and 0xFF) / 255f).toFloat(),
            (((rgba shr 16) and 0xFF) / 255f).toFloat(),
            (((rgba shr 8) and 0xFF) / 255f).toFloat(),
            ((rgba and 0xFF) / 255f).toFloat(),
        )
    }

    fun withAlpha(rgba: Long, alpha: Int): Long {
        return (rgba and 0xFFFFFF00) or (alpha.toLong() and 0xFF)
    }
}
