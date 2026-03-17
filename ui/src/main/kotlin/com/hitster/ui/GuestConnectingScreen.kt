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
    private lateinit var titleFont: BitmapFont
    private lateinit var bodyFont: BitmapFont
    private lateinit var detailFont: BitmapFont
    private val titleLayout = GlyphLayout()
    private val detailLayout = GlyphLayout()
    private val buttonRect = Rectangle()
    private var transitionDispatched = false

    override fun show() {
        titleFont = createUiFont(72)
        bodyFont = createUiFont(36)
        detailFont = createUiFont(26)
        Gdx.input.inputProcessor = ConnectingInput()
        updateLayout()
    }

    override fun render(delta: Float) {
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
        shapeRenderer.color = Color(0.03f, 0.08f, 0.17f, 1f)
        shapeRenderer.rect(0f, 0f, viewport.worldWidth, viewport.worldHeight)
        if (controller.lastError != null) {
            shapeRenderer.color = Color(0.12f, 0.18f, 0.34f, 1f)
            shapeRenderer.rect(buttonRect.x, buttonRect.y, buttonRect.width, buttonRect.height)
        }
        shapeRenderer.end()

        batch.begin()
        titleLayout.setText(titleFont, "Joining Host")
        titleFont.draw(batch, titleLayout, (viewport.worldWidth - titleLayout.width) / 2f, viewport.worldHeight - 110f)
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
