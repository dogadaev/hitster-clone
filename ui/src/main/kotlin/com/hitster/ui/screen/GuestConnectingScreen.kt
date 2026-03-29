package com.hitster.ui.screen

/**
 * Guest entry screen that discovers a local host, opens the guest transport automatically, and waits for the authoritative join snapshot.
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
import com.hitster.networking.SessionAdvertisementDto
import com.hitster.ui.controller.HostDiscoveryService
import com.hitster.ui.controller.MatchController
import com.hitster.ui.render.AtmosphericBackdrop
import com.hitster.ui.render.VerticalCropAnchor
import com.hitster.ui.render.WidthFittedBackgroundImage
import com.hitster.ui.theme.createUiFont

class GuestConnectingScreen(
    private val discoveryService: HostDiscoveryService,
    private val createController: (SessionAdvertisementDto) -> MatchController,
    private val showBackButton: Boolean,
    private val onConnected: (MatchController) -> Unit,
    private val onCancel: () -> Unit,
) : ScreenAdapter() {
    private val camera = OrthographicCamera()
    private val viewport = ExtendViewport(1600f, 900f, camera)
    private val shapeRenderer = ShapeRenderer()
    private val batch = SpriteBatch()
    private val touchPoint = Vector3()
    private val backdrop = AtmosphericBackdrop()
    private val backgroundImage = WidthFittedBackgroundImage("welcome-background.png", VerticalCropAnchor.CENTER)
    private lateinit var titleFont: BitmapFont
    private lateinit var bodyFont: BitmapFont
    private lateinit var detailFont: BitmapFont
    private val titleLayout = GlyphLayout()
    private val detailLayout = GlyphLayout()
    private val backLayout = GlyphLayout()
    private val titleRect = Rectangle()
    private val buttonRect = Rectangle()
    private var transitionDispatched = false
    private var animationSeconds = 0f
    @Volatile
    private var controller: MatchController? = null
    @Volatile
    private var discoveredHostDisplayName: String? = null
    private var autoSelectedSessionId: String? = null

    override fun show() {
        titleFont = createUiFont(72)
        bodyFont = createUiFont(36)
        detailFont = createUiFont(26)
        backdrop.load()
        backgroundImage.load()
        discoveryService.start { hosts ->
            if (controller != null) {
                return@start
            }
            val selectedHost = hosts.firstOrNull() ?: return@start
            if (autoSelectedSessionId == selectedHost.sessionId) {
                return@start
            }
            autoSelectedSessionId = selectedHost.sessionId
            discoveredHostDisplayName = selectedHost.hostDisplayName
            controller = createController(selectedHost)
            discoveryService.stop()
        }
        Gdx.input.inputProcessor = ConnectingInput()
        updateLayout()
    }

    override fun render(delta: Float) {
        animationSeconds += delta
        val activeController = controller
        if (!transitionDispatched && activeController?.localPlayer != null) {
            transitionDispatched = true
            onConnected(activeController)
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

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        fillPanel(titleRect, 0x522620FF, 0x29151AFF, 0xFFD5A55C)
        if (showBackButton || activeController?.lastError != null) {
            fillPanel(buttonRect, 0xC26A5AFF, 0x853228FF, 0xF3C1AF)
        }
        shapeRenderer.end()

        batch.begin()
        backdrop.drawPanelTexture(batch, titleRect, Color(1f, 0.87f, 0.68f, 0.10f), animationSeconds)
        if (showBackButton || activeController?.lastError != null) {
            backdrop.drawPanelTexture(batch, buttonRect, Color(1f, 0.82f, 0.72f, 0.10f), animationSeconds)
        }
        titleLayout.setText(titleFont, "Joining Host")
        titleFont.color = color(0xFFF4E6D7)
        titleFont.draw(batch, titleLayout, (viewport.worldWidth - titleLayout.width) / 2f, titleRect.y + (titleRect.height + titleLayout.height) / 2f)
        val hostDisplayName = discoveredHostDisplayName
        drawText(
            bodyFont,
            hostDisplayName ?: "Searching local network...",
            0f,
            viewport.worldHeight * 0.55f,
            viewport.worldWidth,
            true,
        )

        val message = when {
            activeController?.lastError != null -> activeController.lastError!!
            activeController != null -> "Connecting to the host..."
            else -> "Looking for an available host..."
        }
        drawText(bodyFont, message, 0f, viewport.worldHeight * 0.44f, viewport.worldWidth, true)
        val detailStatus = activeController?.connectionStatus ?: "Waiting for a host on the same local network."
        detailStatus.let { status ->
            drawWrappedText(
                detailFont,
                status,
                180f,
                viewport.worldHeight * 0.34f,
                viewport.worldWidth - 360f,
            )
        }

        if (showBackButton || activeController?.lastError != null) {
            val buttonLabel = if (showBackButton) "BACK" else "RETRY"
            drawText(bodyFont, buttonLabel, buttonRect.x, buttonRect.y + buttonRect.height * 0.66f, buttonRect.width, true)
        }
        batch.end()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        updateLayout()
    }

    override fun hide() {
        discoveryService.stop()
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
        font.color = color(0xFFF2E6D7)
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
        font.color = color(0xFFE4CDBA)
        detailLayout.setText(font, text, font.color, width, 1, true)
        font.draw(batch, detailLayout, x, y)
    }

    private fun fillPanel(rect: Rectangle, topColor: Long, bottomColor: Long, edgeColor: Long) {
        drawDropShadow(rect, 18f, 0x09050634)
        fillGradientRect(rect.x, rect.y, rect.width, rect.height, withAlpha(bottomColor, 0xA2), withAlpha(bottomColor, 0xA2), withAlpha(topColor, 0xB0), withAlpha(topColor, 0xB0))
        drawFrame(rect, edgeColor, 1.6f)
        drawFrame(rect.x + 4f, rect.y + 4f, rect.width - 8f, rect.height - 8f, 0xFFF0D3A2, 0.9f)
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

    private inner class ConnectingInput : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (!showBackButton && controller?.lastError == null) {
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
