package com.hitster.ui.screen

/**
 * Lightweight transitional screen that moves host-controller creation off the role-button tap
 * path so Android can render feedback immediately instead of freezing on heavy setup work.
 */

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.hitster.ui.controller.HostedMatchController
import com.hitster.ui.render.AtmosphericBackdrop
import com.hitster.ui.render.LiquidGlassChrome
import com.hitster.ui.render.VerticalCropAnchor
import com.hitster.ui.render.WidthFittedBackgroundImage
import com.hitster.ui.theme.createUiFont

class HostPreparingScreen(
    private val createController: () -> HostedMatchController,
    private val onReady: (HostedMatchController) -> Unit,
) : ScreenAdapter() {
    private val camera = OrthographicCamera()
    private val viewport = ExtendViewport(1600f, 900f, camera)
    private val shapeRenderer = ShapeRenderer()
    private val batch = SpriteBatch()
    private val backdrop = AtmosphericBackdrop()
    private val backgroundImage = WidthFittedBackgroundImage("welcome-background.png", VerticalCropAnchor.CENTER)
    private lateinit var titleFont: BitmapFont
    private lateinit var bodyFont: BitmapFont
    private val titleLayout = GlyphLayout()
    private val titleRect = Rectangle()
    @Volatile
    private var controller: HostedMatchController? = null
    @Volatile
    private var startupError: String? = null
    private var transitionDispatched = false
    private var animationSeconds = 0f

    override fun show() {
        titleFont = createUiFont(72)
        bodyFont = createUiFont(36)
        backdrop.load()
        backgroundImage.load()
        Thread({
            runCatching { createController() }
                .onSuccess { createdController ->
                    controller = createdController
                }
                .onFailure { error ->
                    startupError = error.message ?: "Failed to start hosting."
                }
        }, "hitster-host-prepare").apply {
            isDaemon = true
            start()
        }
        updateLayout()
    }

    override fun render(delta: Float) {
        animationSeconds += delta
        controller?.takeIf { !transitionDispatched }?.let { readyController ->
            transitionDispatched = true
            onReady(readyController)
            return
        }

        updateLayout()
        viewport.apply()
        Gdx.gl.glClearColor(0.02f, 0.05f, 0.11f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        shapeRenderer.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        batch.begin()
        backgroundImage.draw(batch, viewport.worldWidth, viewport.worldHeight)
        batch.end()

        LiquidGlassChrome.beginFilled(shapeRenderer)
        fillPanel(titleRect)
        LiquidGlassChrome.endFilled(shapeRenderer)

        batch.begin()
        titleLayout.setText(titleFont, "Preparing Host")
        titleFont.color = color(0xFFF7F0E5)
        titleFont.draw(
            batch,
            titleLayout,
            (viewport.worldWidth - titleLayout.width) / 2f,
            titleRect.y + (titleRect.height + titleLayout.height) / 2f,
        )
        drawCenteredText(bodyFont, startupError ?: "Starting local session...", viewport.worldHeight * 0.42f)
        batch.end()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        updateLayout()
    }

    override fun dispose() {
        shapeRenderer.dispose()
        batch.dispose()
        backdrop.dispose()
        backgroundImage.dispose()
        if (this::titleFont.isInitialized) {
            titleFont.dispose()
        }
        if (this::bodyFont.isInitialized) {
            bodyFont.dispose()
        }
    }

    private fun updateLayout() {
        titleRect.set(44f, viewport.worldHeight - 132f, viewport.worldWidth - 88f, 90f)
    }

    private fun drawCenteredText(font: BitmapFont, text: String, y: Float) {
        val layout = GlyphLayout(font, text)
        font.color = color(0xFFF2E6D7)
        font.draw(batch, layout, (viewport.worldWidth - layout.width) / 2f, y)
    }

    private fun fillPanel(rect: Rectangle) {
        val radius = 34f
        LiquidGlassChrome.drawRoundedShadow(shapeRenderer, rect, radius, 28f, 0x09050664)
        LiquidGlassChrome.fillRoundedRect(shapeRenderer, rect, radius, 0x29151A72)
        LiquidGlassChrome.fillRoundedRect(
            shapeRenderer,
            rect.x + 5f,
            rect.y + 5f,
            rect.width - 10f,
            rect.height - 10f,
            radius - 4f,
            0x52262034,
        )
        LiquidGlassChrome.fillRoundedRect(
            shapeRenderer,
            rect.x + 12f,
            rect.y + rect.height * 0.54f,
            rect.width - 24f,
            rect.height * 0.24f,
            radius - 10f,
            0xFFF8F0E622,
        )
    }

    private fun fillGradientRect(x: Float, y: Float, width: Float, height: Float, bottomLeft: Long, bottomRight: Long, topRight: Long, topLeft: Long) {
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

    private fun fillRect(x: Float, y: Float, width: Float, height: Float, rgba: Long) {
        shapeRenderer.color = color(rgba)
        shapeRenderer.rect(x, y, width, height)
    }

    private fun drawDropShadow(rect: Rectangle, spread: Float, rgba: Long) {
        repeat(4) { layer ->
            val expansion = spread * (layer + 1) / 4f
            val alpha = when (layer) {
                0 -> 0x24L
                1 -> 0x18L
                2 -> 0x10L
                else -> 0x08L
            }
            val shadow = (rgba and 0xFFFFFF00) or alpha
            fillRect(
                rect.x - expansion,
                rect.y - expansion * 0.72f,
                rect.width + expansion * 2f,
                rect.height + expansion * 1.44f,
                shadow,
            )
        }
    }

    private fun drawFrame(x: Float, y: Float, width: Float, height: Float, rgba: Long, thickness: Float) {
        fillRect(x, y, width, thickness, rgba)
        fillRect(x, y + height - thickness, width, thickness, rgba)
        fillRect(x, y + thickness, thickness, height - thickness * 2f, rgba)
        fillRect(x + width - thickness, y + thickness, thickness, height - thickness * 2f, rgba)
    }

    private fun color(rgba: Long): Color {
        return Color(
            (((rgba shr 24) and 0xFF) / 255f).toFloat(),
            (((rgba shr 16) and 0xFF) / 255f).toFloat(),
            (((rgba shr 8) and 0xFF) / 255f).toFloat(),
            ((rgba and 0xFF) / 255f).toFloat(),
        )
    }
}
