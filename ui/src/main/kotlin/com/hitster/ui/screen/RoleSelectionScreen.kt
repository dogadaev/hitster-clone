package com.hitster.ui

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
    private lateinit var titleFont: BitmapFont
    private lateinit var buttonFont: BitmapFont
    private val titleLayout = GlyphLayout()
    private val titleRect = Rectangle()
    private val hostButtonRect = Rectangle()
    private val guestButtonRect = Rectangle()
    private var animationSeconds = 0f

    override fun show() {
        titleFont = createUiFont(82)
        buttonFont = createUiFont(54)
        backdrop.load()
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

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        backdrop.drawShapes(shapeRenderer, viewport.worldWidth, viewport.worldHeight, 34f)
        fillPanel(titleRect, 0x223868FF, 0x15284BFF, 0x9EC3FF2A)
        drawButton(hostButtonRect, 0xF6BF55FF, 0xD98B1FFF, 0xFFF3C68C)
        drawButton(guestButtonRect, 0x8FC9FFFF, 0x347EB5FF, 0xE4F6FFFF)
        shapeRenderer.end()

        batch.begin()
        backdrop.drawTextures(batch, viewport.worldWidth, viewport.worldHeight, animationSeconds, 1.08f)
        backdrop.drawPanelTexture(batch, titleRect, Color(0.78f, 0.86f, 1f, 0.08f), animationSeconds)
        backdrop.drawPanelTexture(batch, hostButtonRect, Color(1f, 0.94f, 0.78f, 0.12f), animationSeconds)
        backdrop.drawPanelTexture(batch, guestButtonRect, Color(0.85f, 0.94f, 1f, 0.10f), animationSeconds)
        titleLayout.setText(titleFont, "Choose Your Role")
        titleFont.draw(
            batch,
            titleLayout,
            (viewport.worldWidth - titleLayout.width) / 2f,
            titleRect.y + (titleRect.height + titleLayout.height) / 2f,
        )
        drawCenteredText(buttonFont, "HOST", hostButtonRect, Color(0.12f, 0.08f, 0.03f, 1f))
        drawCenteredText(buttonFont, "GUEST", guestButtonRect, Color.WHITE)
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
        if (this::buttonFont.isInitialized) {
            buttonFont.dispose()
        }
    }

    private fun updateLayout() {
        val worldWidth = viewport.worldWidth
        val worldHeight = viewport.worldHeight
        titleRect.set(44f, worldHeight - 132f, worldWidth - 88f, 90f)
        val buttonWidth = worldWidth * 0.34f
        val buttonHeight = 118f
        hostButtonRect.set(
            (worldWidth - buttonWidth) / 2f,
            worldHeight * 0.46f,
            buttonWidth,
            buttonHeight,
        )
        guestButtonRect.set(
            (worldWidth - buttonWidth) / 2f,
            worldHeight * 0.26f,
            buttonWidth,
            buttonHeight,
        )
    }

    private fun drawButton(rect: Rectangle, topColor: Long, bottomColor: Long, edgeColor: Long) {
        drawDropShadow(rect, 16f, 0x01050B46)
        fillGradientRect(rect.x, rect.y, rect.width, rect.height, bottomColor, bottomColor, topColor, topColor)
        fillRect(rect.x + 10f, rect.y + rect.height - 12f, rect.width - 20f, 3f, 0xFFFFFF16)
        drawFrame(rect, edgeColor, 2f)
    }

    private fun fillPanel(rect: Rectangle, topColor: Long, bottomColor: Long, _edgeColor: Long) {
        drawDropShadow(rect, 18f, 0x01050B32)
        fillGradientRect(rect.x, rect.y, rect.width, rect.height, 0x0D16288A, 0x0C152689, 0x182B50A8, 0x132347A1)
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
}
