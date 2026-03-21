package com.hitster.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
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
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport

class NameEntryScreen(
    initialName: String = "",
    private val showBackButton: Boolean,
    private val onBack: () -> Unit = {},
    private val onConfirmed: (String) -> Unit,
) : ScreenAdapter() {
    private val camera = OrthographicCamera()
    private val viewport = ExtendViewport(1600f, 900f, camera)
    private val shapeRenderer = ShapeRenderer()
    private val batch = SpriteBatch()
    private val touchPoint = Vector3()
    private val backdrop = AtmosphericBackdrop()
    private lateinit var titleFont: BitmapFont
    private lateinit var bodyFont: BitmapFont
    private lateinit var buttonFont: BitmapFont
    private val titleLayout = GlyphLayout()
    private val bodyLayout = GlyphLayout()
    private val titleRect = Rectangle()
    private val inputRect = Rectangle()
    private val continueButtonRect = Rectangle()
    private val backButtonRect = Rectangle()
    private var animationSeconds = 0f
    private var enteredName = UiBootstrapper.sanitizeDisplayName(initialName)

    override fun show() {
        titleFont = createUiFont(78)
        bodyFont = createUiFont(44)
        buttonFont = createUiFont(50)
        backdrop.load()
        Gdx.input.inputProcessor = NameEntryInput()
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
        backdrop.drawShapes(shapeRenderer, viewport.worldWidth, viewport.worldHeight, 32f)
        fillPanel(titleRect, 0x223868FF, 0x15284BFF, 0x9EC3FF2A)
        fillPanel(inputRect, 0x1E2D50FF, 0x0F1A31FF, 0x96BFFF2A)
        drawButton(
            rect = continueButtonRect,
            topColor = if (canContinue()) 0xF6BF55FF else 0x7B6A49FF,
            bottomColor = if (canContinue()) 0xD98B1FFF else 0x4A3F2CFF,
            edgeColor = if (canContinue()) 0xFFF3C68C else 0xCDBDA04A,
        )
        if (showBackButton) {
            drawButton(backButtonRect, 0x89BFEFFF, 0x2B679CFF, 0xE0F0FFFF)
        }
        shapeRenderer.end()

        batch.begin()
        backdrop.drawTextures(batch, viewport.worldWidth, viewport.worldHeight, animationSeconds, 1.08f)
        backdrop.drawPanelTexture(batch, titleRect, Color(0.78f, 0.86f, 1f, 0.08f), animationSeconds)
        backdrop.drawPanelTexture(batch, inputRect, Color(0.74f, 0.84f, 1f, 0.06f), animationSeconds)
        backdrop.drawPanelTexture(batch, continueButtonRect, Color(1f, 0.94f, 0.78f, if (canContinue()) 0.12f else 0.05f), animationSeconds)
        if (showBackButton) {
            backdrop.drawPanelTexture(batch, backButtonRect, Color(0.85f, 0.94f, 1f, 0.10f), animationSeconds)
        }

        titleLayout.setText(titleFont, "Enter Your Name")
        titleFont.draw(
            batch,
            titleLayout,
            (viewport.worldWidth - titleLayout.width) / 2f,
            titleRect.y + (titleRect.height + titleLayout.height) / 2f,
        )

        val bodyColor = if (enteredName.isBlank()) Color(0.76f, 0.82f, 0.95f, 0.92f) else Color.WHITE
        val bodyText = if (enteredName.isBlank()) "Tap here to type your name" else enteredName
        drawCenteredText(
            font = bodyFont,
            text = bodyText,
            rect = inputRect,
            color = bodyColor,
        )
        drawCenteredText(
            font = buttonFont,
            text = "CONTINUE",
            rect = continueButtonRect,
            color = if (canContinue()) Color(0.12f, 0.08f, 0.03f, 1f) else Color(0.22f, 0.18f, 0.14f, 0.92f),
        )
        if (showBackButton) {
            drawCenteredText(buttonFont, "BACK", backButtonRect, Color.WHITE)
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
        if (this::buttonFont.isInitialized) {
            buttonFont.dispose()
        }
    }

    private fun updateLayout() {
        val worldWidth = viewport.worldWidth
        val worldHeight = viewport.worldHeight
        titleRect.set(44f, worldHeight - 132f, worldWidth - 88f, 90f)
        inputRect.set(worldWidth * 0.22f, worldHeight * 0.46f, worldWidth * 0.56f, 116f)
        continueButtonRect.set(worldWidth * 0.34f, worldHeight * 0.23f, worldWidth * 0.32f, 112f)
        if (showBackButton) {
            backButtonRect.set(58f, 48f, 212f, 92f)
        }
    }

    private fun requestNameInput() {
        Gdx.input.getTextInput(
            object : Input.TextInputListener {
                override fun input(text: String?) {
                    enteredName = UiBootstrapper.sanitizeDisplayName(text.orEmpty())
                }

                override fun canceled() = Unit
            },
            "Your name",
            enteredName,
            "",
        )
    }

    private fun canContinue(): Boolean = enteredName.isNotBlank()

    private fun drawButton(rect: Rectangle, topColor: Long, bottomColor: Long, edgeColor: Long) {
        drawDropShadow(rect, 16f, 0x01050B46)
        fillGradientRect(rect.x, rect.y, rect.width, rect.height, bottomColor, bottomColor, topColor, topColor)
        fillRect(rect.x + 10f, rect.y + rect.height - 12f, rect.width - 20f, 3f, 0xFFFFFF16)
        drawFrame(rect, edgeColor, 2f)
    }

    private fun fillPanel(rect: Rectangle, topColor: Long, bottomColor: Long, edgeColor: Long) {
        drawDropShadow(rect, 18f, 0x01050B32)
        fillGradientRect(rect.x, rect.y, rect.width, rect.height, bottomColor, bottomColor, topColor, topColor)
        fillRect(rect.x + 8f, rect.y + rect.height - 10f, rect.width - 16f, 2f, 0xFFFFFF12)
        drawFrame(rect, edgeColor, 2f)
    }

    private fun drawCenteredText(font: BitmapFont, text: String, rect: Rectangle, color: Color) {
        font.color = color
        bodyLayout.setText(font, text, color, rect.width - 28f, Align.center, true)
        font.draw(
            batch,
            bodyLayout,
            rect.x + (rect.width - bodyLayout.width) / 2f,
            rect.y + (rect.height + bodyLayout.height) / 2f,
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

    private inner class NameEntryInput : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            viewport.unproject(touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f))
            return when {
                inputRect.contains(touchPoint.x, touchPoint.y) -> {
                    requestNameInput()
                    true
                }

                continueButtonRect.contains(touchPoint.x, touchPoint.y) && canContinue() -> {
                    onConfirmed(enteredName)
                    true
                }

                showBackButton && backButtonRect.contains(touchPoint.x, touchPoint.y) -> {
                    onBack()
                    true
                }

                else -> false
            }
        }
    }
}
