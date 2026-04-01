package com.hitster.ui.screen

/**
 * Entry screen that lets the user choose whether this device will host or join as a guest.
 */

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.hitster.ui.render.AtmosphericBackdrop
import com.hitster.ui.render.LiquidGlassStyle
import com.hitster.ui.render.LiquidGlassSurfaceRenderer
import com.hitster.ui.render.VerticalCropAnchor
import com.hitster.ui.render.WidthFittedBackgroundImage
import com.hitster.ui.theme.createUiFont

class RoleSelectionScreen(
    private val onHostSelected: () -> Unit,
    private val onGuestSelected: () -> Unit,
) : ScreenAdapter() {
    private val camera = OrthographicCamera()
    private val viewport = ExtendViewport(1600f, 900f, camera)
    private val shapeRenderer = ShapeRenderer()
    private val batch = SpriteBatch()
    private val touchPoint = Vector3()
    private val backdrop = AtmosphericBackdrop()
    private val backgroundImage = WidthFittedBackgroundImage("welcome-background.png", VerticalCropAnchor.CENTER)
    private val glassRenderer = LiquidGlassSurfaceRenderer()
    private lateinit var titleFont: BitmapFont
    private lateinit var buttonFont: BitmapFont
    private val titleLayout = GlyphLayout()
    private val titleRect = Rectangle()
    private val hostButtonRect = Rectangle()
    private val guestButtonRect = Rectangle()
    private var animationSeconds = 0f

    override fun show() {
        titleFont = createUiFont(96)
        buttonFont = createUiFont(42)
        backdrop.load()
        backgroundImage.load()
        glassRenderer.load()
        Gdx.input.inputProcessor = RoleSelectionInput()
        updateLayout()
    }

    override fun render(delta: Float) {
        animationSeconds += delta
        updateLayout()
        viewport.apply()
        Gdx.gl.glClearColor(0.02f, 0.05f, 0.11f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        shapeRenderer.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        batch.begin()
        backgroundImage.draw(batch, viewport.worldWidth, viewport.worldHeight)
        batch.end()
        glassRenderer.captureBackbuffer()

        batch.begin()
        glassRenderer.draw(batch, hostButtonRect, pillRadius(hostButtonRect), HOST_BUTTON_GLASS_STYLE, animationSeconds)
        glassRenderer.draw(batch, guestButtonRect, pillRadius(guestButtonRect), GUEST_BUTTON_GLASS_STYLE, animationSeconds)
        drawTitleWordmark()
        drawCenteredText(buttonFont, "HOST", hostButtonRect, color(0xFFF8F0E7))
        drawCenteredText(buttonFont, "GUEST", guestButtonRect, color(0xFFF8F0E7))
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
        glassRenderer.dispose()
        if (this::titleFont.isInitialized) {
            titleFont.dispose()
        }
        if (this::buttonFont.isInitialized) {
            buttonFont.dispose()
        }
    }

    private fun updateLayout() {
        val worldWidth = viewport.worldWidth
        val worldHeight = viewport.worldHeight
        val titleWidth = (worldWidth * 0.62f).coerceIn(780f, 1080f)
        titleRect.set((worldWidth - titleWidth) * 0.5f, worldHeight - 158f, titleWidth, 124f)
        val buttonWidth = (worldWidth * 0.21f).coerceIn(312f, 352f)
        val buttonHeight = 118f
        val buttonGap = 44f
        val buttonY = 96f
        hostButtonRect.set(
            worldWidth * 0.5f - buttonGap * 0.5f - buttonWidth,
            buttonY,
            buttonWidth,
            buttonHeight,
        )
        guestButtonRect.set(
            worldWidth * 0.5f + buttonGap * 0.5f,
            buttonY,
            buttonWidth,
            buttonHeight,
        )
    }

    private fun pillRadius(rect: Rectangle): Float = minOf(rect.width, rect.height) * 0.5f

    private fun drawTitleWordmark() {
        val centerX = titleRect.x + titleRect.width * 0.5f
        titleLayout.setText(titleFont, "MELONMAN")
        val drawX = centerX - titleLayout.width * 0.5f
        val baselineY = titleRect.y + titleRect.height * 0.62f
        drawTitleLayer(drawX - 1f, baselineY + 4f, 0x4DFFF2D1)
        drawTitleLayer(drawX + 10f, baselineY - 12f, 0x8E4E1EFF)
        drawTitleLayer(drawX + 7f, baselineY - 8f, 0xCC884026)
        drawTitleLayer(drawX + 4f, baselineY - 4f, 0xFFF2A14EFF)
        drawTitleLayer(drawX, baselineY, 0xFFFFF7EA)
    }

    private fun drawTitleLayer(x: Float, y: Float, rgba: Long) {
        titleFont.color = color(rgba)
        titleFont.draw(batch, titleLayout, x, y)
    }

    private fun drawButton(rect: Rectangle, topColor: Long, bottomColor: Long, edgeColor: Long) {
    }

    private fun fillPanel(rect: Rectangle, topColor: Long, bottomColor: Long, edgeColor: Long) {
    }

    private fun drawCenteredText(font: BitmapFont, text: String, rect: Rectangle, color: Color) {
        font.color = color
        val layout = GlyphLayout(font, text)
        font.draw(
            batch,
            layout,
            rect.x + (rect.width - layout.width) / 2f,
            rect.y + (rect.height + layout.height) / 2f,
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

    private fun drawFrame(rect: Rectangle, rgba: Long, thickness: Float) {
        fillRect(rect.x, rect.y, rect.width, thickness, rgba)
        fillRect(rect.x, rect.y + rect.height - thickness, rect.width, thickness, rgba)
        fillRect(rect.x, rect.y + thickness, thickness, rect.height - thickness * 2f, rgba)
        fillRect(rect.x + rect.width - thickness, rect.y + thickness, thickness, rect.height - thickness * 2f, rgba)
    }

    private fun drawFrame(x: Float, y: Float, width: Float, height: Float, rgba: Long, thickness: Float) {
        fillRect(x, y, width, thickness, rgba)
        fillRect(x, y + height - thickness, width, thickness, rgba)
        fillRect(x, y + thickness, thickness, height - thickness * 2f, rgba)
        fillRect(x + width - thickness, y + thickness, thickness, height - thickness * 2f, rgba)
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

    private fun color(rgba: Long): Color {
        return Color(
            (((rgba shr 24) and 0xFF) / 255f).toFloat(),
            (((rgba shr 16) and 0xFF) / 255f).toFloat(),
            (((rgba shr 8) and 0xFF) / 255f).toFloat(),
            ((rgba and 0xFF) / 255f).toFloat(),
        )
    }

    private fun withAlpha(rgba: Long, alpha: Long): Long = (rgba and 0xFFFFFF00) or (alpha and 0xFF)

    private inner class RoleSelectionInput : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            viewport.unproject(touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f))
            return when {
                hostButtonRect.contains(touchPoint.x, touchPoint.y) -> {
                    onHostSelected()
                    true
                }

                guestButtonRect.contains(touchPoint.x, touchPoint.y) -> {
                    onGuestSelected()
                    true
                }

                else -> false
            }
        }
    }

    private companion object {
        val HOST_BUTTON_GLASS_STYLE = LiquidGlassStyle(
            bodyTint = 0xF8DFC18E,
            edgeTint = 0xFFFFE8B9FF,
            highlightTint = 0xFFFFFFFF,
            glowTint = 0xFFFFDA86FF,
            distortion = 0.018f,
            frost = 0.16f,
        )
        val GUEST_BUTTON_GLASS_STYLE = LiquidGlassStyle(
            bodyTint = 0xF4D0C58A,
            edgeTint = 0xFFF9DDD2FF,
            highlightTint = 0xFFFFFFFF,
            glowTint = 0xFFF6B7A2FF,
            distortion = 0.018f,
            frost = 0.17f,
        )
    }
}
