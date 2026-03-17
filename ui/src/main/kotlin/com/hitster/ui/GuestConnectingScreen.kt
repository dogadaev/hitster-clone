package com.hitster.ui

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

class GuestConnectingScreen(
    private val controller: MatchController,
    private val hostDisplayName: String,
    private val onConnected: () -> Unit,
    private val onCancel: () -> Unit,
) : ScreenAdapter() {
    private val camera = OrthographicCamera()
    private val viewport = ExtendViewport(1600f, 900f, camera)
    private val shapeRenderer = ShapeRenderer()
    private val batch = SpriteBatch()
    private val touchPoint = Vector3()
    private val backdrop = AtmosphericBackdrop()
    private lateinit var titleFont: BitmapFont
    private lateinit var bodyFont: BitmapFont
    private lateinit var detailFont: BitmapFont
    private val titleLayout = GlyphLayout()
    private val detailLayout = GlyphLayout()
    private val titleRect = Rectangle()
    private val buttonRect = Rectangle()
    private var transitionDispatched = false
    private var animationSeconds = 0f

    override fun show() {
        titleFont = createUiFont(72)
        bodyFont = createUiFont(36)
        detailFont = createUiFont(26)
        backdrop.load()
        Gdx.input.inputProcessor = ConnectingInput()
        updateLayout()
    }

    override fun render(delta: Float) {
        animationSeconds += delta
        if (!transitionDispatched && controller.localPlayer != null) {
            transitionDispatched = true
            onConnected()
            return
        }

        updateLayout()
        viewport.apply()
        Gdx.gl.glClearColor(0.02f, 0.05f, 0.11f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        shapeRenderer.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        backdrop.drawShapes(shapeRenderer, viewport.worldWidth, viewport.worldHeight, 34f)
        fillPanel(titleRect, 0x223868FF, 0x15284BFF, 0x9EC3FF2A)
        if (controller.lastError != null) {
            fillPanel(buttonRect, 0x2F4E87FF, 0x1B3158FF, 0xC7D8FF42)
        }
        shapeRenderer.end()

        batch.begin()
        backdrop.drawTextures(batch, viewport.worldWidth, viewport.worldHeight, animationSeconds, 1.02f)
        backdrop.drawPanelTexture(batch, titleRect, Color(0.78f, 0.86f, 1f, 0.08f), animationSeconds)
        if (controller.lastError != null) {
            backdrop.drawPanelTexture(batch, buttonRect, Color(0.84f, 0.92f, 1f, 0.08f), animationSeconds)
        }
        titleLayout.setText(titleFont, "Joining Host")
        titleFont.draw(batch, titleLayout, (viewport.worldWidth - titleLayout.width) / 2f, titleRect.y + (titleRect.height + titleLayout.height) / 2f)
        drawText(bodyFont, hostDisplayName, 0f, viewport.worldHeight * 0.55f, viewport.worldWidth, true)

        val message = controller.lastError ?: "Connecting to the host..."
        drawText(bodyFont, message, 0f, viewport.worldHeight * 0.44f, viewport.worldWidth, true)
        controller.connectionStatus?.let { status ->
            drawWrappedText(
                detailFont,
                status,
                180f,
                viewport.worldHeight * 0.34f,
                viewport.worldWidth - 360f,
            )
        }

        if (controller.lastError != null) {
            drawText(bodyFont, "BACK TO HOSTS", buttonRect.x, buttonRect.y + buttonRect.height * 0.66f, buttonRect.width, true)
        }
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
        if (this::titleFont.isInitialized) {
            titleFont.dispose()
        }
        if (this::bodyFont.isInitialized) {
            bodyFont.dispose()
        }
        if (this::detailFont.isInitialized) {
            detailFont.dispose()
        }
    }

    private fun updateLayout() {
        titleRect.set(42f, viewport.worldHeight - 132f, viewport.worldWidth - 84f, 88f)
        buttonRect.set(
            (viewport.worldWidth - 360f) / 2f,
            viewport.worldHeight * 0.22f,
            360f,
            96f,
        )
    }

    private fun drawText(
        font: BitmapFont,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        centered: Boolean,
    ) {
        font.color = Color.WHITE
        if (centered) {
            val layout = GlyphLayout(font, text)
            font.draw(batch, layout, x + (width - layout.width) / 2f, y)
        } else {
            font.draw(batch, text, x, y)
        }
    }

    private fun drawWrappedText(
        font: BitmapFont,
        text: String,
        x: Float,
        y: Float,
        width: Float,
    ) {
        font.color = Color(0.76f, 0.82f, 0.95f, 1f)
        detailLayout.setText(font, text, font.color, width, 1, true)
        font.draw(batch, detailLayout, x, y)
    }

    private fun fillPanel(rect: Rectangle, topColor: Long, bottomColor: Long, edgeColor: Long) {
        drawDropShadow(rect, 18f, 0x01050B30)
        fillGradientRect(rect.x, rect.y, rect.width, rect.height, bottomColor, bottomColor, topColor, topColor)
        fillRect(rect.x + 10f, rect.y + rect.height - 12f, rect.width - 20f, 2f, 0xFFFFFF12)
        drawFrame(rect, edgeColor, 2f)
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

    private inner class ConnectingInput : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (controller.lastError == null) {
                return false
            }
            viewport.unproject(touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f))
            if (buttonRect.contains(touchPoint.x, touchPoint.y)) {
                onCancel()
                return true
            }
            return false
        }
    }
}
