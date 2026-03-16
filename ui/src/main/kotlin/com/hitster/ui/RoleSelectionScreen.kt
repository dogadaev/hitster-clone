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

class RoleSelectionScreen(
    private val onHostSelected: () -> Unit,
    private val onGuestSelected: () -> Unit,
) : ScreenAdapter() {
    private val camera = OrthographicCamera()
    private val viewport = ExtendViewport(1600f, 900f, camera)
    private val shapeRenderer = ShapeRenderer()
    private val batch = SpriteBatch()
    private val touchPoint = Vector3()
    private lateinit var titleFont: BitmapFont
    private lateinit var buttonFont: BitmapFont
    private val titleLayout = GlyphLayout()
    private val hostButtonRect = Rectangle()
    private val guestButtonRect = Rectangle()

    override fun show() {
        titleFont = createUiFont(82)
        buttonFont = createUiFont(54)
        Gdx.input.inputProcessor = RoleSelectionInput()
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
        shapeRenderer.color = Color(0.13f, 0.20f, 0.39f, 1f)
        shapeRenderer.rect(40f, viewport.worldHeight - 132f, viewport.worldWidth - 80f, 92f)
        drawButton(hostButtonRect, Color(0.95f, 0.69f, 0.27f, 1f), Color(0.88f, 0.56f, 0.15f, 1f))
        drawButton(guestButtonRect, Color(0.67f, 0.78f, 0.95f, 1f), Color(0.38f, 0.49f, 0.74f, 1f))
        shapeRenderer.end()

        batch.begin()
        titleLayout.setText(titleFont, "Choose Your Role")
        titleFont.draw(
            batch,
            titleLayout,
            (viewport.worldWidth - titleLayout.width) / 2f,
            viewport.worldHeight - 64f,
        )
        drawCenteredText(buttonFont, "HOST", hostButtonRect, Color(0.10f, 0.08f, 0.04f, 1f))
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

    private fun drawButton(rect: Rectangle, topColor: Color, bottomColor: Color) {
        shapeRenderer.color = bottomColor
        shapeRenderer.rect(rect.x, rect.y, rect.width, rect.height)
        shapeRenderer.color = topColor
        shapeRenderer.rect(rect.x + 8f, rect.y + 8f, rect.width - 16f, rect.height - 16f)
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
