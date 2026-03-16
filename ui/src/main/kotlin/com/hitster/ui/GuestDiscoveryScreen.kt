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
import com.hitster.networking.SessionAdvertisementDto

class GuestDiscoveryScreen(
    private val discoveryService: HostDiscoveryService,
    private val showBackButton: Boolean,
    private val onBack: () -> Unit,
    private val onHostSelected: (SessionAdvertisementDto) -> Unit,
) : ScreenAdapter() {
    private val camera = OrthographicCamera()
    private val viewport = ExtendViewport(1600f, 900f, camera)
    private val shapeRenderer = ShapeRenderer()
    private val batch = SpriteBatch()
    private val touchPoint = Vector3()
    private lateinit var titleFont: BitmapFont
    private lateinit var itemFont: BitmapFont
    private val titleLayout = GlyphLayout()
    private val backRect = Rectangle()
    private val hostRects = mutableListOf<Pair<Rectangle, SessionAdvertisementDto>>()
    @Volatile
    private var discoveredHosts: List<SessionAdvertisementDto> = emptyList()

    override fun show() {
        titleFont = createUiFont(70)
        itemFont = createUiFont(34)
        discoveryService.start { hosts ->
            discoveredHosts = hosts
        }
        Gdx.input.inputProcessor = DiscoveryInput()
        updateLayout()
    }

    override fun render(delta: Float) {
        updateLayout()
        viewport.apply()
        Gdx.gl.glClearColor(0.02f, 0.05f, 0.11f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        shapeRenderer.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.03f, 0.08f, 0.17f, 1f)
        shapeRenderer.rect(0f, 0f, viewport.worldWidth, viewport.worldHeight)
        if (showBackButton) {
            shapeRenderer.color = Color(0.16f, 0.22f, 0.42f, 1f)
            shapeRenderer.rect(backRect.x, backRect.y, backRect.width, backRect.height)
        }
        hostRects.forEachIndexed { index, (rect, _) ->
            shapeRenderer.color = if (index % 2 == 0) {
                Color(0.12f, 0.18f, 0.34f, 1f)
            } else {
                Color(0.09f, 0.15f, 0.29f, 1f)
            }
            shapeRenderer.rect(rect.x, rect.y, rect.width, rect.height)
        }
        shapeRenderer.end()

        batch.begin()
        titleLayout.setText(titleFont, "Available Hosts")
        titleFont.draw(batch, titleLayout, (viewport.worldWidth - titleLayout.width) / 2f, viewport.worldHeight - 70f)
        if (showBackButton) {
            drawText(itemFont, "BACK", backRect.x + 22f, backRect.y + backRect.height * 0.68f)
        }
        if (hostRects.isEmpty()) {
            drawText(itemFont, "Searching your local network for hosts...", 120f, viewport.worldHeight * 0.48f)
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
        if (this::titleFont.isInitialized) {
            titleFont.dispose()
        }
        if (this::itemFont.isInitialized) {
            itemFont.dispose()
        }
    }

    private fun updateLayout() {
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
