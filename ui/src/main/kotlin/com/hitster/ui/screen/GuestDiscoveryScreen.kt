package com.hitster.ui.screen

/**
 * Browser and Android guest discovery UI for finding an Android host on the local network.
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
import com.hitster.ui.render.AtmosphericBackdrop
import com.hitster.ui.render.VerticalCropAnchor
import com.hitster.ui.render.WidthFittedBackgroundImage
import com.hitster.ui.theme.createUiFont

class GuestDiscoveryScreen(
    private val discoveryService: HostDiscoveryService,
    private val showBackButton: Boolean,
    private val autoJoinSingleHost: Boolean,
    private val onBack: () -> Unit,
    private val onHostSelected: (SessionAdvertisementDto) -> Unit,
) : ScreenAdapter() {
    private val camera = OrthographicCamera()
    private val viewport = ExtendViewport(1600f, 900f, camera)
    private val shapeRenderer = ShapeRenderer()
    private val batch = SpriteBatch()
    private val touchPoint = Vector3()
    private val backdrop = AtmosphericBackdrop()
    private val backgroundImage = WidthFittedBackgroundImage("welcome-background.png", VerticalCropAnchor.CENTER)
    private lateinit var titleFont: BitmapFont
    private lateinit var itemFont: BitmapFont
    private val titleLayout = GlyphLayout()
    private val backRect = Rectangle()
    private val titleRect = Rectangle()
    private val hostRects = mutableListOf<Pair<Rectangle, SessionAdvertisementDto>>()
    @Volatile
    private var discoveredHosts: List<SessionAdvertisementDto> = emptyList()
    private var autoJoinSessionId: String? = null
    private var animationSeconds = 0f

    override fun show() {
        titleFont = createUiFont(70)
        itemFont = createUiFont(34)
        backdrop.load()
        backgroundImage.load()
        discoveryService.start { hosts ->
            discoveredHosts = hosts
        }
        Gdx.input.inputProcessor = DiscoveryInput()
        updateLayout()
    }

    override fun render(delta: Float) {
        animationSeconds += delta
        updateLayout()
        val autoJoinHost = if (autoJoinSingleHost) discoveredHosts.singleOrNull() else null
        if (autoJoinHost != null && autoJoinSessionId != autoJoinHost.sessionId) {
            autoJoinSessionId = autoJoinHost.sessionId
            onHostSelected(autoJoinHost)
            return
        }
        viewport.apply()
        Gdx.gl.glClearColor(0.02f, 0.05f, 0.11f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        shapeRenderer.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        batch.begin()
        backgroundImage.draw(batch, viewport.worldWidth, viewport.worldHeight)
        batch.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        fillPanel(titleRect, 0x223868FF, 0x15284BFF, 0x9EC3FF2A)
        if (showBackButton) {
            fillPanel(backRect, 0x294A86FF, 0x18345DFF, 0xC7D8FF4A)
        }
        hostRects.forEachIndexed { index, (rect, _) ->
            if (index % 2 == 0) {
                fillPanel(rect, 0x203968FF, 0x12233FFF, 0xB6CAE832)
            } else {
                fillPanel(rect, 0x1A3159FF, 0x10203AFF, 0xA8BEE028)
            }
        }
        shapeRenderer.end()

        batch.begin()
        backdrop.drawPanelTexture(batch, titleRect, Color(0.78f, 0.86f, 1f, 0.08f), animationSeconds)
        if (showBackButton) {
            backdrop.drawPanelTexture(batch, backRect, Color(0.84f, 0.92f, 1f, 0.08f), animationSeconds)
        }
        hostRects.forEach { (rect, _) ->
            backdrop.drawPanelTexture(batch, rect, Color(0.83f, 0.90f, 1f, 0.06f), animationSeconds)
        }
        titleLayout.setText(titleFont, "Available Hosts")
        titleFont.draw(batch, titleLayout, (viewport.worldWidth - titleLayout.width) / 2f, titleRect.y + (titleRect.height + titleLayout.height) / 2f)
        if (showBackButton) {
            drawText(itemFont, "BACK", backRect.x + 22f, backRect.y + backRect.height * 0.68f)
        }
        if (hostRects.isEmpty()) {
            drawCenteredText(itemFont, "Searching your local network for hosts...", 0f, viewport.worldHeight * 0.48f, viewport.worldWidth)
        } else {
            hostRects.forEach { (rect, host) ->
                drawText(itemFont, host.hostDisplayName, rect.x + 28f, rect.y + rect.height * 0.70f)
                drawText(
                    itemFont,
                    "${host.playerCount} players  |  ${host.hostAddress}:${host.serverPort}",
                    rect.x + 28f,
                    rect.y + rect.height * 0.34f,
                )
            }
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
        if (this::itemFont.isInitialized) {
            itemFont.dispose()
        }
    }

    private fun updateLayout() {
        titleRect.set(42f, viewport.worldHeight - 132f, viewport.worldWidth - 84f, 88f)
        backRect.set(42f, viewport.worldHeight - 128f, 182f, 74f)
        val rowHeight = 124f
        val rowGap = 18f
        val listTop = viewport.worldHeight - 220f
        val listWidth = viewport.worldWidth - 160f
        hostRects.clear()
        discoveredHosts.forEachIndexed { index, host ->
            hostRects += Rectangle(
                80f,
                listTop - (index + 1) * rowHeight - index * rowGap,
                listWidth,
                rowHeight,
            ) to host
        }
    }

    private fun drawText(font: BitmapFont, text: String, x: Float, y: Float) {
        font.color = Color.WHITE
        font.draw(batch, text, x, y)
    }

    private fun drawCenteredText(font: BitmapFont, text: String, x: Float, y: Float, width: Float) {
        font.color = Color.WHITE
        val layout = GlyphLayout(font, text)
        font.draw(batch, layout, x + (width - layout.width) / 2f, y)
    }

    private fun fillPanel(rect: Rectangle, topColor: Long, bottomColor: Long, _edgeColor: Long) {
        drawDropShadow(rect, 18f, 0x01050B30)
        fillGradientRect(rect.x, rect.y, rect.width, rect.height, 0x0D16288A, 0x0C152689, 0x182B50A8, 0x132347A1)
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

    private inner class DiscoveryInput : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            viewport.unproject(touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f))
            if (showBackButton && backRect.contains(touchPoint.x, touchPoint.y)) {
                onBack()
                return true
            }

            hostRects.firstOrNull { it.first.contains(touchPoint.x, touchPoint.y) }?.second?.let {
                onHostSelected(it)
                return true
            }

            return false
        }
    }
}
