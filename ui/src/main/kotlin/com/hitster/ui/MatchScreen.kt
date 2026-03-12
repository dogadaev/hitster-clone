package com.hitster.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.Texture.TextureWrap
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.hitster.animations.AnimationCatalog
import com.hitster.core.model.MatchStatus
import com.hitster.core.model.PlayerState
import com.hitster.core.model.TurnPhase
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MatchScreen(
    private val presenter: MatchPresenter,
    private val animationCatalog: AnimationCatalog,
) : ScreenAdapter() {
    private val camera = OrthographicCamera()
    private val viewport = ExtendViewport(BASE_WORLD_WIDTH, BASE_WORLD_HEIGHT, camera)
    private val shapeRenderer = ShapeRenderer()
    private val batch = SpriteBatch()
    private lateinit var font: BitmapFont
    private lateinit var grainTexture: Texture
    private lateinit var glowTexture: Texture
    private lateinit var vignetteTexture: Texture
    private val textLayout = GlyphLayout()
    private val worldTouch = Vector2()

    private val headerRect = Rectangle()
    private val heroRect = Rectangle()
    private val actionButtonRect = Rectangle()
    private val deckPanelRect = Rectangle()
    private val deckRect = Rectangle()
    private val timelinePanelRect = Rectangle()
    private val timelineHeaderRect = Rectangle()
    private val timelineTrackRect = Rectangle()
    private val statusBannerRect = Rectangle()
    private val lobbyCardRect = Rectangle()
    private val startButtonRect = Rectangle()
    private val deckFrontCardRect = Rectangle()

    private var layoutWorldWidth = 0f
    private var layoutWorldHeight = 0f
    private var layoutStatus: MatchStatus? = null
    private var outerMargin = 28f
    private var panelGap = 22f
    private var panelPadding = 28f
    private var panelHeaderHeight = 84f
    private var cardHeight = 210f
    private var timelineCardsX = 0f
    private var timelineCardsWidth = 1f
    private var fontScaleMultiplier = 1.02f
    private var minimumTextScale = 0.88f
    private var shadowOffset = 1.2f
    private var timelineLayout = TimelineLayoutCalculator(trackX = 0f, trackWidth = 1f)
    private val timelineCardVisuals = mutableListOf<TimelineCardVisual>()
    private var pendingCardVisual: TimelineCardVisual? = null
    private var transientCardVisual: TimelineCardVisual? = null
    private val animatedCardLefts = mutableMapOf<String, Float>()
    private var animatedPendingCardLeft: Float? = null

    private var draggingDeckGhost = false
    private var draggingPendingCard = false
    private var pendingCardGrabOffsetX = 0f
    private var pendingCardDragDirectionX = 0f

    override fun show() {
        font = createFont()
        grainTexture = createGrainTexture()
        glowTexture = createGlowTexture()
        vignetteTexture = createVignetteTexture()
        Gdx.input.inputProcessor = MatchInputController()
        updateLayout()
    }

    override fun render(delta: Float) {
        updateLayout()
        updateTimelineVisuals(delta)
        viewport.apply()
        Gdx.gl.glClearColor(0.02f, 0.04f, 0.10f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        shapeRenderer.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        drawBackground()
        when (presenter.state.status) {
            MatchStatus.LOBBY -> drawLobby()
            MatchStatus.ACTIVE,
            MatchStatus.COMPLETE,
            -> drawMatch(includeOverlay = false)
        }
        shapeRenderer.end()

        batch.begin()
        drawAtmosphereTextures()
        when (presenter.state.status) {
            MatchStatus.LOBBY -> {
                drawLobbyTextures()
                drawLobbyText()
            }

            MatchStatus.ACTIVE,
            MatchStatus.COMPLETE,
            -> {
                drawMatchTextures()
                drawMatchText(includeOverlay = false)
            }
        }
        batch.end()

        if (hasOverlayTimelineVisuals()) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            drawTimelineCards(includeOverlay = true)
            shapeRenderer.end()

            batch.begin()
            drawTimelineCardText(includeOverlay = true)
            batch.end()
        }
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        updateLayout()
    }

    override fun dispose() {
        shapeRenderer.dispose()
        batch.dispose()
        if (this::font.isInitialized) {
            font.dispose()
        }
        if (this::grainTexture.isInitialized) {
            grainTexture.dispose()
        }
        if (this::glowTexture.isInitialized) {
            glowTexture.dispose()
        }
        if (this::vignetteTexture.isInitialized) {
            vignetteTexture.dispose()
        }
    }

    private fun updateLayout() {
        val worldWidth = viewport.worldWidth
        val worldHeight = viewport.worldHeight
        val status = presenter.state.status
        if (worldWidth == layoutWorldWidth && worldHeight == layoutWorldHeight && status == layoutStatus) {
            return
        }

        layoutWorldWidth = worldWidth
        layoutWorldHeight = worldHeight
        layoutStatus = status

        outerMargin = clamp(min(worldWidth, worldHeight) * 0.03f, 24f, 36f)
        panelGap = outerMargin * 0.76f
        panelPadding = clamp(worldHeight * 0.034f, 22f, 34f)
        panelHeaderHeight = clamp(worldHeight * 0.115f, 76f, 96f)

        val isLobby = status == MatchStatus.LOBBY
        val headerHeight = if (isLobby) clamp(worldHeight * 0.09f, 60f, 80f) else 0f
        headerRect.set(
            outerMargin,
            worldHeight - outerMargin - headerHeight,
            worldWidth - outerMargin * 2f,
            headerHeight,
        )

        val heroHeight = if (isLobby) {
            clamp(worldHeight * 0.125f, 94f, 122f)
        } else {
            clamp(worldHeight * 0.13f, 104f, 124f)
        }
        val heroY = if (isLobby) {
            headerRect.y - panelGap - heroHeight
        } else {
            worldHeight - clamp(outerMargin * 0.55f, 14f, 22f) - heroHeight
        }
        heroRect.set(
            outerMargin,
            heroY,
            worldWidth - outerMargin * 2f,
            heroHeight,
        )

        val deckWidth = clamp(worldWidth * 0.135f, 208f, 246f)
        val mainHeight = heroRect.y - outerMargin - panelGap
        deckPanelRect.set(outerMargin, outerMargin, deckWidth, mainHeight)
        timelinePanelRect.set(
            deckPanelRect.x + deckPanelRect.width + panelGap,
            outerMargin,
            worldWidth - outerMargin * 2f - deckWidth - panelGap,
            mainHeight,
        )

        timelineHeaderRect.set(
            timelinePanelRect.x,
            timelinePanelRect.y + timelinePanelRect.height - panelHeaderHeight,
            timelinePanelRect.width,
            panelHeaderHeight,
        )

        val trackHeight = timelinePanelRect.height - panelHeaderHeight - panelPadding * 2f
        timelineTrackRect.set(
            timelinePanelRect.x + panelPadding,
            timelinePanelRect.y + panelPadding,
            timelinePanelRect.width - panelPadding * 2f,
            trackHeight,
        )

        val deckCardWidth = clamp(deckPanelRect.width * 0.60f, 152f, 192f)
        val deckCardHeight = clamp(deckPanelRect.height * 0.44f, 214f, 272f)
        val deckContentHeight = deckPanelRect.height - panelHeaderHeight - panelPadding * 2f
        val stackOffset = deckStackOffset()
        deckRect.set(
            deckPanelRect.x + (deckPanelRect.width - deckCardWidth) / 2f,
            deckPanelRect.y + panelPadding + (deckContentHeight - deckCardHeight) / 2f,
            deckCardWidth,
            deckCardHeight,
        )
        deckFrontCardRect.set(
            deckRect.x + stackOffset,
            deckRect.y - stackOffset,
            deckRect.width,
            deckRect.height,
        )

        val actionHeight = clamp(heroRect.height * 0.62f, 64f, 80f)
        val actionWidth = clamp(worldWidth * 0.165f, 214f, 280f)
        actionButtonRect.set(
            heroRect.x + heroRect.width - panelPadding - actionWidth,
            heroRect.y + (heroRect.height - actionHeight) / 2f,
            actionWidth,
            actionHeight,
        )

        statusBannerRect.set(
            timelineTrackRect.x + timelineTrackRect.width * 0.27f,
            timelineTrackRect.y + timelineTrackRect.height * 0.17f,
            timelineTrackRect.width * 0.46f,
            clamp(timelineTrackRect.height * 0.16f, 68f, 90f),
        )

        val lobbyWidth = clamp(worldWidth * 0.50f, 860f, 1120f)
        val lobbyHeight = clamp(worldHeight * 0.44f, 400f, 540f)
        lobbyCardRect.set(
            (worldWidth - lobbyWidth) / 2f,
            outerMargin + worldHeight * 0.12f,
            lobbyWidth,
            lobbyHeight,
        )

        startButtonRect.set(
            (worldWidth - clamp(worldWidth * 0.24f, 420f, 540f)) / 2f,
            lobbyCardRect.y - panelGap - clamp(worldHeight * 0.11f, 98f, 118f),
            clamp(worldWidth * 0.24f, 420f, 540f),
            clamp(worldHeight * 0.11f, 98f, 118f),
        )

        val preferredCardWidth = clamp(timelineTrackRect.width * 0.18f, 156f, 220f)
        val minCardWidth = clamp(timelineTrackRect.width * 0.114f, 112f, 140f)
        timelineCardsX = timelineTrackRect.x + panelPadding * 0.28f
        timelineCardsWidth = timelineTrackRect.width - panelPadding * 0.56f
        timelineLayout = TimelineLayoutCalculator(
            trackX = timelineCardsX,
            trackWidth = timelineCardsWidth,
            preferredCardWidth = preferredCardWidth,
            minCardWidth = minCardWidth,
            preferredGap = clamp(timelineTrackRect.width * 0.025f, 20f, 32f),
            minGap = 14f,
        )

        cardHeight = clamp(timelineTrackRect.height * 0.80f, 224f, 292f)
        fontScaleMultiplier = clamp(worldHeight / 960f, 0.98f, 1.08f)
        minimumTextScale = 0.88f
        shadowOffset = clamp(worldHeight * 0.0011f, 1f, 1.6f)
    }

    private fun updateTimelineVisuals(delta: Float) {
        timelineCardVisuals.clear()
        pendingCardVisual = null
        transientCardVisual = null

        if (draggingDeckGhost) {
            val ghostWidth = clamp(timelineTrackRect.width * 0.14f, 128f, 178f)
            transientCardVisual = TimelineCardVisual(
                id = "deck-ghost",
                rect = Rectangle(
                    worldTouch.x - ghostWidth / 2f,
                    timelineCardBottom(cardHeight),
                    ghostWidth,
                    cardHeight,
                ),
                face = CardFace.Hidden,
                topColor = 0xFFD18AFF,
                bottomColor = 0xE8A650FF,
                edgeColor = 0xFFF3DCAD,
            )
        }

        val player = activePlayer()
        if (presenter.state.status == MatchStatus.LOBBY || player == null) {
            animatedCardLefts.clear()
            animatedPendingCardLeft = null
            return
        }

        val animationAlpha = clamp(delta * 12f, 0f, 1f)
        val cardBottom = timelineCardBottom(cardHeight)
        val visibleCardIds = mutableSetOf<String>()

        val pendingCard = player.pendingCard
        if (pendingCard == null) {
            val arrangement = timelineLayout.arrangement(player.timeline.cards.size)
            player.timeline.cards.forEachIndexed { index, card ->
                val animatedLeft = animatedLeft(card.id, arrangement.cardLefts[index], animationAlpha)
                visibleCardIds += card.id
                timelineCardVisuals += TimelineCardVisual(
                    id = card.id,
                rect = Rectangle(animatedLeft, cardBottom, arrangement.cardWidth, cardHeight),
                face = CardFace.Revealed,
                topColor = 0xF7E9D1FF,
                bottomColor = 0xDCC4A6FF,
                edgeColor = 0xFFF6E7CA,
                primaryText = card.title,
                secondaryText = card.artist,
                tertiaryText = card.releaseYear.toString(),
            )
        }
            animatedPendingCardLeft = null
            animatedCardLefts.keys.retainAll(visibleCardIds)
            return
        }

        val arrangement = timelineLayout.pendingArrangement(
            existingCardCount = player.timeline.cards.size,
            pendingSlotIndex = pendingCard.proposedSlotIndex,
        )
        val pendingLeftTarget = if (draggingPendingCard) {
            clamp(worldTouch.x - pendingCardGrabOffsetX, timelineCardsX, timelineCardsX + timelineCardsWidth - arrangement.cardWidth)
        } else {
            arrangement.pendingCardLeft
        }
        val pendingLeft = if (draggingPendingCard) {
            pendingLeftTarget
        } else {
            animatedPendingCardLeft?.let { current ->
                lerpToward(current, pendingLeftTarget, animationAlpha)
            } ?: pendingLeftTarget
        }
        player.timeline.cards.forEachIndexed { index, card ->
            val visualLeft = animatedLeft(card.id, arrangement.committedCardLefts[index], animationAlpha)
            animatedCardLefts[card.id] = visualLeft
            visibleCardIds += card.id
            timelineCardVisuals += TimelineCardVisual(
                id = card.id,
                rect = Rectangle(visualLeft, cardBottom, arrangement.cardWidth, cardHeight),
                face = CardFace.Revealed,
                topColor = 0xF7E9D1FF,
                bottomColor = 0xDCC4A6FF,
                edgeColor = 0xFFF6E7CA,
                primaryText = card.title,
                secondaryText = card.artist,
                tertiaryText = card.releaseYear.toString(),
            )
        }
        animatedPendingCardLeft = pendingLeft

        pendingCardVisual = TimelineCardVisual(
            id = pendingCard.entry.id,
            rect = Rectangle(pendingLeft, cardBottom, arrangement.cardWidth, cardHeight),
            face = CardFace.Hidden,
            topColor = 0xF5B348FF,
            bottomColor = 0xD48620FF,
            edgeColor = 0xFFF1D089,
            primaryText = "?",
            secondaryText = "LISTEN",
        )
        pendingCardVisual?.let(timelineCardVisuals::add)
        animatedCardLefts.keys.retainAll(visibleCardIds)
    }

    private fun timelineCardBottom(height: Float): Float {
        return timelineTrackRect.y + (timelineTrackRect.height - height) / 2f
    }

    private fun animatedLeft(cardId: String, target: Float, alpha: Float): Float {
        val current = animatedCardLefts[cardId]
        val next = current?.let { lerpToward(it, target, alpha) } ?: target
        animatedCardLefts[cardId] = next
        return next
    }

    private fun lerpToward(current: Float, target: Float, alpha: Float): Float {
        if (abs(target - current) < 0.5f) {
            return target
        }
        return current + (target - current) * alpha
    }

    // Generate a larger atlas so text stays sharp on high-density phones.
    private fun createFont(): BitmapFont {
        val fontFile = Gdx.files.internal(FONT_ASSET_PATH)
        if (!fontFile.exists()) {
            return BitmapFont().apply {
                setUseIntegerPositions(true)
                regions.forEach { region ->
                    region.texture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
                }
            }
        }

        val generator = FreeTypeFontGenerator(fontFile)
        return try {
            generator.generateFont(
                FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                    size = 56
                    minFilter = TextureFilter.Linear
                    magFilter = TextureFilter.Linear
                    kerning = true
                    hinting = FreeTypeFontGenerator.Hinting.Full
                },
            ).apply {
                setUseIntegerPositions(true)
                regions.forEach { region ->
                    region.texture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
                }
            }
        } finally {
            generator.dispose()
        }
    }

    private fun createGrainTexture(): Texture {
        val size = 96
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                val diagonal = (x + y) % 19 < 2
                val speck = ((x * 17 + y * 31) % 23) == 0
                val cross = x % 24 == 0 || y % 24 == 0
                val alpha = when {
                    diagonal -> 0.10f
                    speck -> 0.14f
                    cross -> 0.04f
                    else -> 0.015f
                }
                pixmap.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, alpha))
            }
        }
        return Texture(pixmap).also { texture ->
            pixmap.dispose()
            texture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
            texture.setWrap(TextureWrap.Repeat, TextureWrap.Repeat)
        }
    }

    private fun createGlowTexture(): Texture {
        val size = 192
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val center = (size - 1) / 2f
        val maxDistance = center * center * 2f
        for (x in 0 until size) {
            for (y in 0 until size) {
                val dx = x - center
                val dy = y - center
                val distance = (dx * dx + dy * dy) / maxDistance
                val alpha = clamp(1f - distance * 2.2f, 0f, 1f)
                pixmap.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, alpha * alpha))
            }
        }
        return Texture(pixmap).also { texture ->
            pixmap.dispose()
            texture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
        }
    }

    private fun createVignetteTexture(): Texture {
        val size = 192
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val center = (size - 1) / 2f
        val maxDistance = center * center * 2f
        for (x in 0 until size) {
            for (y in 0 until size) {
                val dx = x - center
                val dy = y - center
                val distance = (dx * dx + dy * dy) / maxDistance
                val alpha = clamp((distance - 0.12f) * 1.4f, 0f, 0.92f)
                pixmap.drawPixel(x, y, Color.rgba8888(0f, 0f, 0f, alpha))
            }
        }
        return Texture(pixmap).also { texture ->
            pixmap.dispose()
            texture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
        }
    }

    private fun drawBackground() {
        fillGradientRect(0f, 0f, layoutWorldWidth, layoutWorldHeight, 0x030814FF, 0x071327FF, 0x0C1E39FF, 0x08172FFF)
        fillGradientRect(0f, 0f, layoutWorldWidth, layoutWorldHeight * 0.28f, 0x06101CFF, 0x0A1530FF, 0x00000000, 0x00000000)
        fillGradientRect(0f, layoutWorldHeight * 0.68f, layoutWorldWidth, layoutWorldHeight * 0.32f, 0x0B1734D8, 0x081628D8, 0x172C56E4, 0x22396CE4)

        val crownHeight = clamp(layoutWorldHeight * 0.09f, 60f, 76f)
        fillGradientRect(
            outerMargin,
            layoutWorldHeight - outerMargin - crownHeight,
            layoutWorldWidth - outerMargin * 2f,
            crownHeight,
            0x202F58FF,
            0x1B2A4EFF,
            0x314A7EFF,
            0x2A3E6BFF,
        )
        fillRect(outerMargin + 8f, layoutWorldHeight - outerMargin - crownHeight + 6f, layoutWorldWidth - outerMargin * 2f - 16f, 2f, 0xFFFFFF18)
        fillRect(outerMargin + 8f, layoutWorldHeight - outerMargin - 10f, layoutWorldWidth - outerMargin * 2f - 16f, 2f, 0x0000003D)

        if (headerRect.height > 0f) {
            fillGradientRect(headerRect.x, headerRect.y, headerRect.width, headerRect.height, 0x243768FF, 0x1D315CFF, 0x4260A0FF, 0x334B82FF)
            drawFrame(headerRect, 0xB7C7EE3F, 2f)
        }
    }

    private fun drawAtmosphereTextures() {
        drawGlow(layoutWorldWidth * 0.52f, layoutWorldHeight * 0.50f, 760f, 540f, color(0x356AA136))
        drawGlow(-180f, -80f, 860f, 860f, color(0xE39B3826))
        drawRepeatedTexture(grainTexture, 0f, 0f, layoutWorldWidth, layoutWorldHeight, color(0xDCE6FF0D), layoutWorldWidth / 96f, layoutWorldHeight / 96f)
        drawTexture(vignetteTexture, 0f, 0f, layoutWorldWidth, layoutWorldHeight, color(0x000000C3))
    }

    private fun drawLobby() {
        fillPanel(lobbyCardRect, 0x13254BFF, 0x0D1B37FF, 0x4D67A5FF, 0x3E568DFF, 0xAFC2F044)
        fillButton(startButtonRect, 0xF6B447FF, 0xE4952BFF, 0xFFF0BF66)

        presenter.state.players.forEachIndexed { index, _ ->
            val rowY = lobbyCardRect.y + lobbyCardRect.height - 218f - index * 62f
            fillGradientRect(lobbyCardRect.x + 54f, rowY, 298f, 48f, 0x22345DFF, 0x1E2D4FFF, 0x324B80FF, 0x2C4374FF)
        }

        repeat(3) { index ->
            val offset = index * 24f
            drawCardSurface(
                left = lobbyCardRect.x + lobbyCardRect.width - 214f + offset,
                bottom = lobbyCardRect.y + 56f - offset,
                width = 142f,
                height = 200f,
                topColor = 0xF2D081FF,
                bottomColor = 0xD8A34BFF,
                edgeColor = 0xFFF5D4AA,
            )
        }
    }

    private fun drawLobbyTextures() {
        drawPanelTexture(lobbyCardRect, color(0xCFE0FF18))
        drawPanelTexture(startButtonRect, color(0xFFF5D41E))
    }

    private fun drawLobbyText() {
        drawTextBlock(
            text = "Hitster Clone",
            x = headerRect.x + 32f,
            y = headerRect.y,
            width = 430f,
            height = headerRect.height,
            scale = 1.48f,
            color = Color.WHITE,
            verticalAlign = VerticalTextAlign.Center,
        )
        drawTextBlock(
            text = "Local host session",
            x = headerRect.x + headerRect.width - 260f,
            y = headerRect.y,
            width = 228f,
            height = headerRect.height,
            scale = 0.98f,
            color = color(0xD8E4FDFF),
            align = Align.right,
            verticalAlign = VerticalTextAlign.Center,
        )

        drawTextBlock(
            text = "Ready to Start",
            x = lobbyCardRect.x + 54f,
            y = lobbyCardRect.y + lobbyCardRect.height - 96f,
            width = 360f,
            height = 54f,
            scale = 1.20f,
            color = Color.WHITE,
        )
        drawTextBlock(
            text = "${presenter.state.players.size} players connected",
            x = lobbyCardRect.x + 54f,
            y = lobbyCardRect.y + lobbyCardRect.height - 150f,
            width = 360f,
            height = 48f,
            scale = 0.94f,
            color = color(0xF3CF7BFF),
        )

        presenter.state.players.forEachIndexed { index, player ->
            drawTextBlock(
                text = player.displayName,
                x = lobbyCardRect.x + 70f,
                y = lobbyCardRect.y + lobbyCardRect.height - 218f - index * 62f,
                width = 260f,
                height = 48f,
                scale = 0.94f,
                color = Color.WHITE,
                verticalAlign = VerticalTextAlign.Center,
            )
        }

        drawTextBlock(
            text = "One phone. One timeline.",
            x = lobbyCardRect.x + 54f,
            y = lobbyCardRect.y + 54f,
            width = lobbyCardRect.width * 0.48f,
            height = 80f,
            scale = 0.96f,
            color = color(0xDDE6FFFF),
        )

        drawTextBlock(
            text = "Start Match",
            x = startButtonRect.x,
            y = startButtonRect.y,
            width = startButtonRect.width,
            height = startButtonRect.height,
            scale = 1.16f,
            color = color(0x1A1308FF),
            align = Align.center,
            verticalAlign = VerticalTextAlign.Center,
            shadowColor = color(0xFFF8E29F33),
        )
    }

    private fun drawMatch(includeOverlay: Boolean) {
        fillHero(heroRect)
        if (showActionButton()) {
            fillButton(actionButtonRect, 0xF6B447FF, 0xE6972CFF, 0xFFF2C56C)
        }

        fillPanel(deckPanelRect, 0x14264DFF, 0x0D1B37FF, 0x4C67A4FF, 0x3D568DFF, 0xAFC2F040)
        fillPanel(timelinePanelRect, 0x14264DFF, 0x0D1B37FF, 0x556EABFF, 0x41598FFF, 0xB4C7F144)
        fillTrack(timelineTrackRect)

        repeat(DECK_STACK_DEPTH) { index ->
            val offset = centeredDeckStackOffset(index)
            drawCardSurface(
                left = deckRect.x + offset,
                bottom = deckRect.y - offset,
                width = deckRect.width,
                height = deckRect.height,
                topColor = 0xE87853FF,
                bottomColor = 0xCC5D3EFF,
                edgeColor = 0xFFD3A28FFF,
            )
        }

        if (showStatusBanner()) {
            fillBanner(statusBannerRect)
        }

        drawTimelineCards(includeOverlay)
    }

    private fun drawMatchTextures() {
        drawPanelTexture(heroRect, color(0xCFE1FF10))
        drawPanelTexture(deckPanelRect, color(0xC9DBFF18))
        drawPanelTexture(timelinePanelRect, color(0xC9DBFF12))
        drawPanelTexture(timelineTrackRect, color(0xE1E8FF0E))
        drawRepeatedTexture(
            grainTexture,
            timelineTrackRect.x,
            timelineTrackRect.y,
            timelineTrackRect.width,
            timelineTrackRect.height,
            color(0x99B7EB12),
            timelineTrackRect.width / 92f,
            timelineTrackRect.height / 92f,
        )
        if (showActionButton()) {
            drawPanelTexture(actionButtonRect, color(0xFFF2D028))
        }
    }

    private fun drawMatchText(includeOverlay: Boolean) {
        val player = activePlayer()
        val turnLabelWidth = 170f
        val turnX = if (showActionButton()) {
            actionButtonRect.x - panelGap - turnLabelWidth
        } else {
            heroRect.x + heroRect.width - panelPadding - turnLabelWidth
        }

        drawTextBlock(
            text = player?.displayName ?: "Waiting",
            x = heroRect.x + panelPadding,
            y = heroRect.y,
            width = heroRect.width * 0.46f,
            height = heroRect.height,
            scale = 1.12f,
            color = Color.WHITE,
            verticalAlign = VerticalTextAlign.Center,
        )
        drawTextBlock(
            text = "Turn ${presenter.state.turn?.number ?: 0}",
            x = turnX,
            y = heroRect.y,
            width = turnLabelWidth,
            height = heroRect.height,
            scale = 0.92f,
            color = color(0xD9E4FDFF),
            align = Align.right,
            verticalAlign = VerticalTextAlign.Center,
        )
        if (showActionButton()) {
            drawTextBlock(
                text = "END TURN",
                x = actionButtonRect.x,
                y = actionButtonRect.y,
                width = actionButtonRect.width,
                height = actionButtonRect.height,
                scale = 1.06f,
                color = color(0x1A1308FF),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
                shadowColor = color(0xFFF7DA9638),
            )
        }

        drawTextBlock(
            text = "Deck",
            x = deckPanelRect.x,
            y = deckPanelRect.y + deckPanelRect.height - panelHeaderHeight,
            width = deckPanelRect.width,
            height = panelHeaderHeight,
            scale = 1.02f,
            color = Color.WHITE,
            insetX = panelPadding,
            verticalAlign = VerticalTextAlign.Center,
        )
        drawTextBlock(
            text = presenter.state.deck.size.toString(),
            x = deckFrontCardRect.x,
            y = deckFrontCardRect.y,
            width = deckFrontCardRect.width,
            height = deckFrontCardRect.height,
            scale = 1.34f,
            color = color(0xFFF5E8D0),
            align = Align.center,
            verticalAlign = VerticalTextAlign.Center,
            shadowColor = color(0x441A0C99),
        )

        drawTextBlock(
            text = "Timeline",
            x = timelinePanelRect.x,
            y = timelineHeaderRect.y,
            width = timelineHeaderRect.width * 0.45f,
            height = timelineHeaderRect.height,
            scale = 1.06f,
            color = Color.WHITE,
            insetX = panelPadding,
            verticalAlign = VerticalTextAlign.Center,
        )
        drawTextBlock(
            text = "Score ${player?.score ?: 0}",
            x = timelineHeaderRect.x + timelineHeaderRect.width - 220f,
            y = timelineHeaderRect.y,
            width = 184f,
            height = timelineHeaderRect.height,
            scale = 0.96f,
            color = color(0xF4CF79FF),
            align = Align.right,
            verticalAlign = VerticalTextAlign.Center,
        )

        if (player?.timeline?.cards.isNullOrEmpty() && player?.pendingCard == null && !showStatusBanner()) {
            drawTextBlock(
                text = "Drag from deck to timeline.",
                x = timelineTrackRect.x,
                y = timelineTrackRect.y,
                width = timelineTrackRect.width,
                height = timelineTrackRect.height,
                scale = 1.06f,
                color = color(0xE0E9FFFF),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
            )
        }

        if (showStatusBanner()) {
            drawTextBlock(
                text = statusBannerText(),
                x = statusBannerRect.x + 18f,
                y = statusBannerRect.y + 4f,
                width = statusBannerRect.width - 36f,
                height = statusBannerRect.height - 8f,
                scale = 0.96f,
                color = statusBannerColor(),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
                wrap = true,
            )
        }

        drawTimelineCardText(includeOverlay)
    }

    private fun fillHero(rect: Rectangle) {
        drawDropShadow(rect, 18f, 0x01050B40)
        fillGradientRect(rect.x, rect.y, rect.width, rect.height, 0x132145FF, 0x101C38FF, 0x1B2C59FF, 0x182955FF)
        drawFrame(rect, 0xA8C0F138, 2f)
        fillRect(rect.x + 2f, rect.y + 2f, rect.width - 4f, 1f, 0xFFFFFF12)
    }

    private fun fillTrack(rect: Rectangle) {
        drawDropShadow(rect, 18f, 0x01050B3B)
        fillGradientRect(rect.x, rect.y, rect.width, rect.height, 0x202D54FF, 0x1C2849FF, 0x283560FF, 0x24305AFF)
        drawFrame(rect, 0x9CB3E42A, 2f)
        fillRect(rect.x + 14f, rect.y + rect.height - 18f, rect.width - 28f, 2f, 0xFFFFFF14)
    }

    private fun fillBanner(rect: Rectangle) {
        drawDropShadow(rect, 12f, 0x02060C44)
        fillGradientRect(rect.x, rect.y, rect.width, rect.height, 0x24335DFF, 0x1F2C50FF, 0x33497BFF, 0x2B3E6BFF)
        drawFrame(rect, 0xBDD0F248, 2f)
    }

    private fun fillButton(rect: Rectangle, topColor: Long, bottomColor: Long, edgeColor: Long) {
        drawDropShadow(rect, 16f, 0x1409014B)
        fillGradientRect(rect.x, rect.y, rect.width, rect.height, bottomColor, bottomColor, topColor, topColor)
        fillRect(rect.x + 8f, rect.y + rect.height - 12f, rect.width - 16f, 3f, 0xFFFFFF1A)
        drawFrame(rect, edgeColor, 2f)
    }

    private fun fillPanel(rect: Rectangle, bodyTop: Long, bodyBottom: Long, headerTop: Long, headerBottom: Long, edgeColor: Long) {
        drawDropShadow(rect, 18f, 0x01050B48)
        fillGradientRect(rect.x, rect.y, rect.width, rect.height, bodyBottom, bodyBottom, bodyTop, bodyTop)
        fillGradientRect(rect.x, rect.y + rect.height - panelHeaderHeight, rect.width, panelHeaderHeight, headerBottom, headerBottom, headerTop, headerTop)
        fillRect(rect.x + 10f, rect.y + rect.height - panelHeaderHeight + 8f, rect.width - 20f, 2f, 0xFFFFFF16)
        drawFrame(rect, edgeColor, 2f)
    }

    private fun drawCardSurface(
        left: Float,
        bottom: Float,
        width: Float,
        height: Float,
        topColor: Long,
        bottomColor: Long,
        edgeColor: Long,
    ) {
        drawDropShadow(left, bottom, width, height, 12f, 0x01050B58)
        fillGradientRect(left, bottom, width, height, bottomColor, bottomColor, topColor, topColor)
        fillRect(left + 8f, bottom + height - 16f, width - 16f, 3f, 0xFFFFFF1F)
        fillRect(left + 8f, bottom + 12f, width - 16f, 2f, 0x0000001E)
        drawFrame(left, bottom, width, height, edgeColor, 2f)
    }

    private fun drawPanelTexture(rect: Rectangle, tint: Color) {
        drawRepeatedTexture(
            grainTexture,
            rect.x + 2f,
            rect.y + 2f,
            rect.width - 4f,
            rect.height - 4f,
            tint,
            max(1f, rect.width / 90f),
            max(1f, rect.height / 90f),
        )
    }

    private fun drawTimelineCards(includeOverlay: Boolean) {
        timelineCardVisuals.forEach { visual ->
            if (isOverlayVisual(visual) == includeOverlay) {
                drawCardVisual(visual)
            }
        }
        if (includeOverlay) {
            transientCardVisual?.let(::drawCardVisual)
        }
    }

    private fun drawTimelineCardText(includeOverlay: Boolean) {
        timelineCardVisuals.forEach { visual ->
            if (isOverlayVisual(visual) == includeOverlay) {
                drawCardText(visual)
            }
        }
        if (includeOverlay) {
            transientCardVisual?.let(::drawCardText)
        }
    }

    private fun hasOverlayTimelineVisuals(): Boolean {
        return draggingPendingCard && pendingCardVisual != null || transientCardVisual != null
    }

    private fun isOverlayVisual(visual: TimelineCardVisual): Boolean {
        return draggingPendingCard && pendingCardVisual?.id == visual.id
    }

    private fun drawCardVisual(visual: TimelineCardVisual) {
        drawCardSurface(
            left = visual.rect.x,
            bottom = visual.rect.y,
            width = visual.rect.width,
            height = visual.rect.height,
            topColor = visual.topColor,
            bottomColor = visual.bottomColor,
            edgeColor = visual.edgeColor,
        )
    }

    private fun drawCardText(visual: TimelineCardVisual) {
        when (visual.face) {
            CardFace.Revealed -> {
                visual.secondaryText?.let { artist ->
                    drawTextBlock(
                        text = artist,
                        x = visual.rect.x + 14f,
                        y = visual.rect.y + visual.rect.height * 0.72f,
                        width = visual.rect.width - 28f,
                        height = visual.rect.height * 0.11f,
                        scale = 0.46f,
                        color = color(0x393024FF),
                        align = Align.center,
                        verticalAlign = VerticalTextAlign.Center,
                        shadowColor = color(0xFFF7F0E028),
                        wrap = true,
                        enforceMinimumScale = false,
                    )
                }
                visual.tertiaryText?.let { year ->
                    drawTextBlock(
                        text = year,
                        x = visual.rect.x + 14f,
                        y = visual.rect.y + visual.rect.height * 0.42f,
                        width = visual.rect.width - 28f,
                        height = visual.rect.height * 0.14f,
                        scale = 0.76f,
                        color = color(0x17120CFF),
                        align = Align.center,
                        verticalAlign = VerticalTextAlign.Center,
                        shadowColor = color(0xFFF7F0E040),
                        enforceMinimumScale = false,
                    )
                }
                val title = visual.primaryText ?: return
                drawTextBlock(
                    text = title,
                    x = visual.rect.x + 14f,
                    y = visual.rect.y + visual.rect.height * 0.12f,
                    width = visual.rect.width - 28f,
                    height = visual.rect.height * 0.20f,
                    scale = 0.54f,
                    color = color(0x17120CFF),
                    align = Align.center,
                    verticalAlign = VerticalTextAlign.Bottom,
                    shadowColor = color(0xFFF7F0E040),
                    wrap = true,
                    insetY = 4f,
                    enforceMinimumScale = false,
                )
            }

            CardFace.Hidden -> {
                visual.primaryText?.let { hiddenLabel ->
                    drawTextBlock(
                        text = hiddenLabel,
                        x = visual.rect.x,
                        y = visual.rect.y + visual.rect.height * 0.16f,
                        width = visual.rect.width,
                        height = visual.rect.height * 0.56f,
                        scale = 1.54f,
                        color = color(0x1A1308FF),
                        align = Align.center,
                        verticalAlign = VerticalTextAlign.Center,
                        shadowColor = color(0xFFF9E4A84A),
                    )
                }
                visual.secondaryText?.let { secondaryLabel ->
                    drawTextBlock(
                        text = secondaryLabel,
                        x = visual.rect.x,
                        y = visual.rect.y + visual.rect.height * 0.04f,
                        width = visual.rect.width,
                        height = visual.rect.height * 0.22f,
                        scale = 0.80f,
                        color = color(0x1A1308FF),
                        align = Align.center,
                        verticalAlign = VerticalTextAlign.Center,
                        shadowColor = color(0xFFF9E4A84A),
                    )
                }
            }
        }
    }

    private fun showStatusBanner(): Boolean {
        return presenter.lastError != null || presenter.state.lastResolution != null
    }

    private fun statusBannerText(): String {
        presenter.lastError?.let { return it }
        presenter.state.lastResolution?.let { resolution ->
            return if (resolution.correct) {
                "Correct • ${resolution.releaseYear}"
            } else {
                "Incorrect • ${resolution.releaseYear}"
            }
        }
        return ""
    }

    private fun statusBannerColor(): Color {
        presenter.lastError?.let { return color(0xFFB7ACFF) }
        return if (presenter.state.lastResolution?.correct == true) {
            color(0xF4D283FF)
        } else {
            color(0xFFB7ACFF)
        }
    }

    private fun showActionButton(): Boolean = canEndTurn()

    private fun activePlayer(): PlayerState? = presenter.state.activePlayer

    private fun canDraw(): Boolean = presenter.state.turn?.phase == TurnPhase.WAITING_FOR_DRAW

    private fun canEndTurn(): Boolean = presenter.state.turn?.phase == TurnPhase.CARD_POSITIONED

    private fun requestedSlotIndexFor(x: Float): Int {
        val player = activePlayer() ?: return 0
        val pendingCard = player.pendingCard ?: return timelineLayout.nearestSlotIndex(player.timeline.cards.size, x)
        if (!draggingPendingCard) {
            return timelineLayout.nearestSlotIndex(player.timeline.cards.size, x)
        }

        val arrangement = timelineLayout.pendingArrangement(
            existingCardCount = player.timeline.cards.size,
            pendingSlotIndex = pendingCard.proposedSlotIndex,
        )
        val pendingLeft = clamp(
            x - pendingCardGrabOffsetX,
            timelineCardsX,
            timelineCardsX + timelineCardsWidth - arrangement.cardWidth,
        )
        val probeRatio = when {
            pendingCardDragDirectionX > 0f -> 0.74f
            pendingCardDragDirectionX < 0f -> 0.26f
            else -> 0.5f
        }
        val probeX = pendingLeft + arrangement.cardWidth * probeRatio
        return timelineLayout.nearestSlotIndex(player.timeline.cards.size, probeX)
    }

    private fun drawTextBlock(
        text: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        scale: Float,
        color: Color,
        align: Int = Align.left,
        verticalAlign: VerticalTextAlign = VerticalTextAlign.Center,
        wrap: Boolean = false,
        insetX: Float = 0f,
        insetY: Float = 0f,
        shadowColor: Color = color(0x02060CB8),
        enforceMinimumScale: Boolean = true,
    ) {
        val baseScale = if (enforceMinimumScale) max(scale, minimumTextScale) else scale
        val appliedScale = baseScale * fontScaleMultiplier
        val drawX = (x + insetX).roundToInt().toFloat()
        val availableWidth = max(1f, width - insetX * 2f).roundToInt().toFloat()
        val availableHeight = max(1f, height - insetY * 2f)

        font.data.setScale(appliedScale)
        textLayout.setText(font, text, color, availableWidth, align, wrap)
        val drawY = when (verticalAlign) {
            VerticalTextAlign.Top -> y + height - insetY
            VerticalTextAlign.Center -> y + insetY + (availableHeight + textLayout.height) / 2f
            VerticalTextAlign.Bottom -> y + insetY + textLayout.height
        }.roundToInt().toFloat()

        font.color = shadowColor
        font.draw(batch, textLayout, drawX + shadowOffset, drawY - shadowOffset)
        font.color = color
        font.draw(batch, textLayout, drawX, drawY)
    }

    private fun drawTexture(texture: Texture, x: Float, y: Float, width: Float, height: Float, tint: Color) {
        batch.color = tint
        batch.draw(texture, x, y, width, height)
        batch.setColor(Color.WHITE)
    }

    private fun drawRepeatedTexture(
        texture: Texture,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        tint: Color,
        repeatX: Float,
        repeatY: Float,
    ) {
        batch.color = tint
        batch.draw(texture, x, y, width, height, 0f, 0f, repeatX, repeatY)
        batch.setColor(Color.WHITE)
    }

    private fun drawGlow(x: Float, y: Float, width: Float, height: Float, tint: Color) {
        batch.flush()
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        batch.color = tint
        batch.draw(glowTexture, x, y, width, height)
        batch.flush()
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.setColor(Color.WHITE)
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
        drawFrame(rect.x, rect.y, rect.width, rect.height, rgba, thickness)
    }

    private fun drawFrame(x: Float, y: Float, width: Float, height: Float, rgba: Long, thickness: Float) {
        fillRect(x, y, width, thickness, rgba)
        fillRect(x, y + height - thickness, width, thickness, rgba)
        fillRect(x, y + thickness, thickness, height - thickness * 2f, rgba)
        fillRect(x + width - thickness, y + thickness, thickness, height - thickness * 2f, rgba)
    }

    private fun drawDropShadow(rect: Rectangle, spread: Float, rgba: Long) {
        drawDropShadow(rect.x, rect.y, rect.width, rect.height, spread, rgba)
    }

    private fun drawDropShadow(x: Float, y: Float, width: Float, height: Float, spread: Float, rgba: Long) {
        repeat(4) { layer ->
            val expansion = spread * (layer + 1) / 4f
            val alpha = when (layer) {
                0 -> 0x24L
                1 -> 0x18L
                2 -> 0x10L
                else -> 0x08L
            }
            val shadow = (rgba and 0xFFFFFF00) or alpha
            fillRect(x - expansion, y - expansion * 0.72f, width + expansion * 2f, height + expansion * 1.44f, shadow)
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

    private fun clamp(value: Float, minValue: Float, maxValue: Float): Float {
        return max(minValue, min(value, maxValue))
    }

    private fun centeredDeckStackOffset(index: Int): Float {
        return index * DECK_STACK_SPREAD - deckStackOffset()
    }

    private fun deckStackOffset(): Float {
        return (DECK_STACK_DEPTH - 1) * DECK_STACK_SPREAD / 2f
    }

    private inner class MatchInputController : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            updateLayout()
            val world = viewport.unproject(worldTouch.set(screenX.toFloat(), screenY.toFloat()))

            if (presenter.state.status == MatchStatus.LOBBY && startButtonRect.contains(world.x, world.y)) {
                presenter.startMatch()
                return true
            }

            if (presenter.state.status != MatchStatus.ACTIVE) {
                return false
            }

            if (canEndTurn() && actionButtonRect.contains(world.x, world.y)) {
                presenter.endTurn()
                return true
            }

            val player = activePlayer() ?: return false
            if (canDraw() && deckRect.contains(world.x, world.y)) {
                draggingDeckGhost = true
                worldTouch.set(world)
                return true
            }

            val currentPendingCard = pendingCardVisual
            if (currentPendingCard?.rect?.contains(world.x, world.y) == true) {
                draggingPendingCard = true
                worldTouch.set(world)
                pendingCardGrabOffsetX = world.x - currentPendingCard.rect.x
                pendingCardDragDirectionX = 0f
                return true
            }

            return false
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            updateLayout()
            if (!draggingDeckGhost && !draggingPendingCard) {
                return false
            }

            val previousX = worldTouch.x
            viewport.unproject(worldTouch.set(screenX.toFloat(), screenY.toFloat()))
            if (draggingPendingCard) {
                pendingCardDragDirectionX = worldTouch.x - previousX
                presenter.movePendingCard(requestedSlotIndexFor(worldTouch.x))
            }
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            updateLayout()
            if (!draggingDeckGhost && !draggingPendingCard) {
                return false
            }

            val world = viewport.unproject(worldTouch.set(screenX.toFloat(), screenY.toFloat()))

            if (draggingDeckGhost) {
                presenter.drawCard()
                presenter.movePendingCard(requestedSlotIndexFor(world.x))
            } else if (draggingPendingCard) {
                presenter.movePendingCard(requestedSlotIndexFor(world.x))
            }

            draggingDeckGhost = false
            draggingPendingCard = false
            pendingCardGrabOffsetX = 0f
            pendingCardDragDirectionX = 0f
            return true
        }
    }

    private data class TimelineCardVisual(
        val id: String,
        val rect: Rectangle,
        val face: CardFace,
        val topColor: Long,
        val bottomColor: Long,
        val edgeColor: Long,
        val primaryText: String? = null,
        val secondaryText: String? = null,
        val tertiaryText: String? = null,
    )

    private enum class CardFace {
        Revealed,
        Hidden,
    }

    private enum class VerticalTextAlign {
        Top,
        Center,
        Bottom,
    }

    private companion object {
        const val BASE_WORLD_WIDTH = 1600f
        const val BASE_WORLD_HEIGHT = 900f
        const val FONT_ASSET_PATH = "fonts/droid-sans-bold.ttf"
        const val DECK_STACK_DEPTH = 3
        const val DECK_STACK_SPREAD = 14f
    }
}
