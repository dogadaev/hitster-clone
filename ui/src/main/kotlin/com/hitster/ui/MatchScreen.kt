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
import com.hitster.core.model.DoubtPhase
import com.hitster.core.model.MatchStatus
import com.hitster.core.model.PlayerState
import com.hitster.core.model.PlaylistEntry
import com.hitster.core.model.TurnPhase
import com.hitster.playback.api.PlaybackSessionState
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class MatchScreen(
    private val presenter: MatchController,
    private val animationCatalog: AnimationCatalog,
) : ScreenAdapter() {
    private val camera = OrthographicCamera()
    private val viewport = ExtendViewport(BASE_WORLD_WIDTH, BASE_WORLD_HEIGHT, camera)
    private val shapeRenderer = ShapeRenderer()
    private val batch = SpriteBatch()
    private lateinit var font: BitmapFont
    private lateinit var flatTexture: Texture
    private lateinit var grainTexture: Texture
    private lateinit var glowTexture: Texture
    private lateinit var vignetteTexture: Texture
    private val textLayout = GlyphLayout()
    private val worldTouch = Vector2()

    private val headerRect = Rectangle()
    private val heroRect = Rectangle()
    private val actionButtonRect = Rectangle()
    private val doubtButtonRect = Rectangle()
    private val hostCoinsButtonRect = Rectangle()
    private val deckPanelRect = Rectangle()
    private val deckRect = Rectangle()
    private val timelinePanelRect = Rectangle()
    private val timelineHeaderRect = Rectangle()
    private val timelineTrackRect = Rectangle()
    private val coinPanelRect = Rectangle()
    private val coinPanelCloseRect = Rectangle()
    private val doubtPopupRect = Rectangle()
    private val doubtPopupHeaderRect = Rectangle()
    private val doubtPopupTrackRect = Rectangle()
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
    private val doubtTimelineCardVisuals = mutableListOf<TimelineCardVisual>()
    private var pendingCardVisual: TimelineCardVisual? = null
    private var doubtPendingCardVisual: TimelineCardVisual? = null
    private var transientCardVisual: TimelineCardVisual? = null
    private val animatedCardLefts = mutableMapOf<String, Float>()
    private val animatedDoubtCardLefts = mutableMapOf<String, Float>()
    private var animatedPendingCardLeft: Float? = null
    private var animatedDoubtPendingCardLeft: Float? = null
    private val confettiParticles = mutableListOf<ConfettiParticle>()
    private var celebratedResolutionCardId: String? = null
    private var inactiveTurnFilterAlpha = 0f
    private var overlayAnimationSeconds = 0f
    private var coinPanelOpen = false

    private var draggingDeckGhost = false
    private var draggingPendingCard = false
    private var draggingDoubtCard = false
    private var pendingCardGrabOffsetX = 0f
    private var doubtPendingCardGrabOffsetX = 0f
    private var pendingCardDragDirectionX = 0f

    override fun show() {
        font = createFont()
        flatTexture = createFlatTexture()
        grainTexture = createGrainTexture()
        glowTexture = createGlowTexture()
        vignetteTexture = createVignetteTexture()
        Gdx.input.inputProcessor = MatchInputController()
        updateLayout()
    }

    override fun render(delta: Float) {
        overlayAnimationSeconds += delta
        updateLayout()
        updateTimelineVisuals(delta)
        updateCelebration(delta)
        updateInactiveTurnFilter(delta)
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

        if (confettiParticles.isNotEmpty()) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            drawConfetti()
            shapeRenderer.end()
        }

        if (inactiveTurnFilterAlpha > 0.01f) {
            batch.begin()
            drawInactiveTurnFilter(inactiveTurnFilterAlpha)
            batch.end()
        }

        if (presenter.state.status != MatchStatus.LOBBY) {
            if (showDoubtPlacementPopup() || coinPanelOpen) {
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
                drawModalShapes()
                shapeRenderer.end()

                batch.begin()
                drawModalTextures()
                drawModalText()
                batch.end()
            }

            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            drawFloatingControlsShapes()
            shapeRenderer.end()

            batch.begin()
            drawFloatingControlsTextures()
            drawFloatingControlsText()
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
        if (this::flatTexture.isInitialized) {
            flatTexture.dispose()
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

        val trackInsetX = panelPadding * 0.48f
        val trackInsetBottom = panelPadding * 0.44f
        val trackInsetTop = panelPadding * 0.34f
        val trackHeight = timelinePanelRect.height - panelHeaderHeight - trackInsetBottom - trackInsetTop
        timelineTrackRect.set(
            timelinePanelRect.x + trackInsetX,
            timelinePanelRect.y + trackInsetBottom,
            timelinePanelRect.width - trackInsetX * 2f,
            trackHeight,
        )

        val deckCardWidth = clamp(deckPanelRect.width * 0.78f, 164f, 208f)
        val deckCardHeight = clamp(deckPanelRect.height * 0.46f, 222f, 296f)
        val deckContentHeight = deckPanelRect.height - panelPadding * 2f
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

        val actionSize = clamp(deckPanelRect.width * 0.84f, 176f, 214f)
        actionButtonRect.set(
            deckPanelRect.x + (deckPanelRect.width - actionSize) / 2f,
            deckPanelRect.y + (deckPanelRect.height - actionSize) / 2f,
            actionSize,
            actionSize,
        )
        val doubtButtonWidth = clamp(deckPanelRect.width * 0.92f, 184f, 236f)
        val doubtButtonHeight = clamp(deckPanelRect.height * 0.16f, 92f, 118f)
        doubtButtonRect.set(
            deckPanelRect.x + (deckPanelRect.width - doubtButtonWidth) / 2f,
            deckPanelRect.y + (deckPanelRect.height - doubtButtonHeight) / 2f,
            doubtButtonWidth,
            doubtButtonHeight,
        )
        val coinsButtonWidth = clamp(deckPanelRect.width * 0.82f, 124f, 176f)
        val coinsButtonHeight = clamp(deckPanelRect.height * 0.09f, 56f, 68f)
        hostCoinsButtonRect.set(
            deckPanelRect.x,
            deckPanelRect.y,
            coinsButtonWidth,
            coinsButtonHeight,
        )

        val coinPanelWidth = clamp(worldWidth * 0.48f, 620f, 860f)
        val coinPanelHeight = clamp(worldHeight * 0.60f, 420f, 620f)
        coinPanelRect.set(
            (worldWidth - coinPanelWidth) / 2f,
            (worldHeight - coinPanelHeight) / 2f,
            coinPanelWidth,
            coinPanelHeight,
        )
        coinPanelCloseRect.set(
            coinPanelRect.x + coinPanelRect.width - 68f,
            coinPanelRect.y + coinPanelRect.height - 68f,
            48f,
            48f,
        )

        val doubtPopupWidth = clamp(timelinePanelRect.width * 0.84f, 680f, 1080f)
        val doubtPopupHeight = clamp(timelinePanelRect.height * 0.76f, 400f, 620f)
        doubtPopupRect.set(
            timelinePanelRect.x + (timelinePanelRect.width - doubtPopupWidth) / 2f,
            timelinePanelRect.y + (timelinePanelRect.height - doubtPopupHeight) / 2f,
            doubtPopupWidth,
            doubtPopupHeight,
        )
        doubtPopupHeaderRect.set(
            doubtPopupRect.x,
            doubtPopupRect.y + doubtPopupRect.height - panelHeaderHeight,
            doubtPopupRect.width,
            panelHeaderHeight,
        )
        val doubtTrackInsetX = panelPadding * 0.82f
        val doubtTrackInsetBottom = panelPadding * 0.82f
        val doubtTrackInsetTop = panelPadding * 0.56f
        doubtPopupTrackRect.set(
            doubtPopupRect.x + doubtTrackInsetX,
            doubtPopupRect.y + doubtTrackInsetBottom,
            doubtPopupRect.width - doubtTrackInsetX * 2f,
            doubtPopupRect.height - panelHeaderHeight - doubtTrackInsetBottom - doubtTrackInsetTop,
        )

        val lobbyButtonWidth = clamp(worldWidth * 0.23f, 360f, 500f)
        val lobbyButtonHeight = clamp(worldHeight * 0.11f, 94f, 118f)
        startButtonRect.set(
            (worldWidth - lobbyButtonWidth) / 2f,
            outerMargin + clamp(worldHeight * 0.04f, 26f, 40f),
            lobbyButtonWidth,
            lobbyButtonHeight,
        )

        val lobbyStageBottom = startButtonRect.y + startButtonRect.height + clamp(worldHeight * 0.05f, 30f, 48f)
        val lobbyStageTop = headerRect.y - panelGap
        lobbyCardRect.set(
            outerMargin,
            lobbyStageBottom,
            worldWidth - outerMargin * 2f,
            max(1f, lobbyStageTop - lobbyStageBottom),
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
        doubtTimelineCardVisuals.clear()
        pendingCardVisual = null
        doubtPendingCardVisual = null
        transientCardVisual = null

        if (showDoubtPlacementPopup()) {
            coinPanelOpen = false
        }

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

        val player = localPlayer()
        if (presenter.state.status == MatchStatus.LOBBY || player == null) {
            animatedCardLefts.clear()
            animatedDoubtCardLefts.clear()
            animatedPendingCardLeft = null
            animatedDoubtPendingCardLeft = null
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
                val palette = DecadeCardPalettes.forYear(card.releaseYear)
                visibleCardIds += card.id
                timelineCardVisuals += TimelineCardVisual(
                    id = card.id,
                    rect = Rectangle(animatedLeft, cardBottom, arrangement.cardWidth, cardHeight),
                    face = CardFace.Revealed,
                    topColor = palette.topColor,
                    bottomColor = palette.bottomColor,
                    edgeColor = palette.edgeColor,
                    primaryText = card.title,
                    secondaryText = card.artist,
                    tertiaryText = card.releaseYear.toString(),
                )
            }
            animatedPendingCardLeft = null
            animatedCardLefts.keys.retainAll(visibleCardIds)
            updateDoubtTimelineVisuals(delta)
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
            val palette = DecadeCardPalettes.forYear(card.releaseYear)
            animatedCardLefts[card.id] = visualLeft
            visibleCardIds += card.id
            timelineCardVisuals += TimelineCardVisual(
                id = card.id,
                rect = Rectangle(visualLeft, cardBottom, arrangement.cardWidth, cardHeight),
                face = CardFace.Revealed,
                topColor = palette.topColor,
                bottomColor = palette.bottomColor,
                edgeColor = palette.edgeColor,
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
        updateDoubtTimelineVisuals(delta)
    }

    private fun updateDoubtTimelineVisuals(delta: Float) {
        if (!showDoubtPlacementPopup()) {
            animatedDoubtCardLefts.clear()
            animatedDoubtPendingCardLeft = null
            doubtTimelineCardVisuals.clear()
            doubtPendingCardVisual = null
            return
        }

        val doubt = presenter.state.doubt ?: return
        val targetPlayer = presenter.state.requirePlayer(doubt.targetPlayerId) ?: return
        val pendingCard = targetPlayer.pendingCard ?: return
        val animationAlpha = clamp(delta * 12f, 0f, 1f)
        val popupTrackX = doubtPopupTrackRect.x + panelPadding * 0.18f
        val popupTrackWidth = doubtPopupTrackRect.width - panelPadding * 0.36f
        val cardWidthPreferred = clamp(doubtPopupTrackRect.width * 0.18f, 148f, 208f)
        val cardWidthMin = clamp(doubtPopupTrackRect.width * 0.118f, 108f, 136f)
        val popupLayout = TimelineLayoutCalculator(
            trackX = popupTrackX,
            trackWidth = popupTrackWidth,
            preferredCardWidth = cardWidthPreferred,
            minCardWidth = cardWidthMin,
            preferredGap = clamp(doubtPopupTrackRect.width * 0.024f, 18f, 30f),
            minGap = 12f,
        )
        val arrangement = popupLayout.pendingArrangement(
            existingCardCount = targetPlayer.timeline.cards.size,
            pendingSlotIndex = doubt.proposedSlotIndex ?: pendingCard.proposedSlotIndex,
        )
        val popupCardHeight = clamp(doubtPopupTrackRect.height * 0.76f, 210f, 276f)
        val popupCardBottom = doubtPopupTrackRect.y + (doubtPopupTrackRect.height - popupCardHeight) / 2f
        val pendingLeftTarget = if (draggingDoubtCard) {
            clamp(
                worldTouch.x - doubtPendingCardGrabOffsetX,
                popupTrackX,
                popupTrackX + popupTrackWidth - arrangement.cardWidth,
            )
        } else {
            arrangement.pendingCardLeft
        }
        val pendingLeft = if (draggingDoubtCard) {
            pendingLeftTarget
        } else {
            animatedDoubtPendingCardLeft?.let { current ->
                lerpToward(current, pendingLeftTarget, animationAlpha)
            } ?: pendingLeftTarget
        }
        val visibleCardIds = mutableSetOf<String>()

        targetPlayer.timeline.cards.forEachIndexed { index, card ->
            val visualLeft = animatedDoubtLeft(card.id, arrangement.committedCardLefts[index], animationAlpha)
            val palette = DecadeCardPalettes.forYear(card.releaseYear)
            visibleCardIds += card.id
            doubtTimelineCardVisuals += TimelineCardVisual(
                id = card.id,
                rect = Rectangle(visualLeft, popupCardBottom, arrangement.cardWidth, popupCardHeight),
                face = CardFace.Revealed,
                topColor = palette.topColor,
                bottomColor = palette.bottomColor,
                edgeColor = palette.edgeColor,
                primaryText = card.title,
                secondaryText = card.artist,
                tertiaryText = card.releaseYear.toString(),
            )
        }

        animatedDoubtPendingCardLeft = pendingLeft
        doubtPendingCardVisual = TimelineCardVisual(
            id = pendingCard.entry.id,
            rect = Rectangle(pendingLeft, popupCardBottom, arrangement.cardWidth, popupCardHeight),
            face = CardFace.Hidden,
            topColor = 0x7ED9FFFF,
            bottomColor = 0x2D8FCAFF,
            edgeColor = 0xDBF5FFFF,
            primaryText = "?",
            secondaryText = "DOUBT",
        )
        doubtPendingCardVisual?.let(doubtTimelineCardVisuals::add)
        animatedDoubtCardLefts.keys.retainAll(visibleCardIds)
    }

    private fun animatedDoubtLeft(cardId: String, target: Float, alpha: Float): Float {
        val current = animatedDoubtCardLefts[cardId]
        val next = current?.let { lerpToward(it, target, alpha) } ?: target
        animatedDoubtCardLefts[cardId] = next
        return next
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

    private fun updateCelebration(delta: Float) {
        val resolution = localResolution()
        if (resolution?.correct == true && resolution.cardId != celebratedResolutionCardId) {
            spawnConfetti()
            celebratedResolutionCardId = resolution.cardId
        }

        val iterator = confettiParticles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            particle.ageSeconds += delta
            if (particle.ageSeconds >= particle.lifeSeconds) {
                iterator.remove()
                continue
            }
            particle.x += particle.velocityX * delta
            particle.y += particle.velocityY * delta
            particle.velocityY += CONFETTI_GRAVITY * delta
            particle.rotation += particle.angularVelocity * delta
        }
    }

    private fun updateInactiveTurnFilter(delta: Float) {
        val targetAlpha = inactiveTurnFilterTargetAlpha()
        val fadeRate = if (targetAlpha > inactiveTurnFilterAlpha) 1.35f else 2.8f
        inactiveTurnFilterAlpha = when {
            abs(targetAlpha - inactiveTurnFilterAlpha) < 0.02f -> targetAlpha
            targetAlpha > inactiveTurnFilterAlpha -> min(1f, inactiveTurnFilterAlpha + delta * fadeRate)
            else -> max(0f, inactiveTurnFilterAlpha - delta * fadeRate)
        }
    }

    private fun inactiveTurnFilterTargetAlpha(): Float {
        if (!shouldShowInactiveTurnFilter()) {
            return 0f
        }
        if (confettiParticles.isEmpty()) {
            return 1f
        }

        val blendProgress = confettiWindDownProgress()
        return clamp(blendProgress * 0.88f, 0f, 1f)
    }

    private fun confettiWindDownProgress(): Float {
        if (confettiParticles.isEmpty()) {
            return 1f
        }
        val slowestParticleProgress = confettiParticles.minOf { particle ->
            particle.ageSeconds / particle.lifeSeconds
        }
        return ((slowestParticleProgress - CONFETTI_FILTER_BLEND_START_PROGRESS) /
            (1f - CONFETTI_FILTER_BLEND_START_PROGRESS)).coerceIn(0f, 1f)
    }

    private fun spawnConfetti() {
        confettiParticles.clear()
        val random = Random(presenter.state.revision)
        repeat(CONFETTI_COUNT) { index ->
            confettiParticles += ConfettiParticle(
                x = -outerMargin + random.nextFloat() * (layoutWorldWidth + outerMargin * 2f),
                y = layoutWorldHeight * (0.40f + random.nextFloat() * 0.60f),
                width = 10f + random.nextFloat() * 12f,
                height = 6f + random.nextFloat() * 7f,
                velocityX = -260f + random.nextFloat() * 520f,
                velocityY = 40f + random.nextFloat() * 180f,
                rotation = random.nextFloat() * 360f,
                angularVelocity = -240f + random.nextFloat() * 480f,
                lifeSeconds = 2.30f + random.nextFloat() * 0.90f,
                colorRgba = CONFETTI_COLORS[index % CONFETTI_COLORS.size],
            )
        }
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

    private fun createFlatTexture(): Texture {
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.WHITE)
        pixmap.fill()
        return Texture(pixmap).also { texture ->
            pixmap.dispose()
            texture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
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
        if (showLobbyPrimaryButton()) {
            val isPairing = presenter.playbackSessionState == PlaybackSessionState.Connecting
            if (isPairing) {
                fillButton(startButtonRect, 0xA4B5DBFF, 0x7D90BBFF, 0xE9F0FF55)
            } else {
                fillButton(startButtonRect, 0xF6B447FF, 0xE4952BFF, 0xFFF0BF66)
            }
        }

        val cardWidth = clamp(lobbyCardRect.width * 0.11f, 142f, 176f)
        val cardHeight = clamp(lobbyCardRect.height * 0.42f, 194f, 238f)
        val cardCenterX = lobbyCardRect.x + lobbyCardRect.width / 2f
        val cardBottom = lobbyCardRect.y + lobbyCardRect.height * 0.42f

        repeat(3) { index ->
            val depth = abs(index - 1)
            val offset = (index - 1) * (cardWidth * 0.33f)
            drawCardSurface(
                left = cardCenterX - cardWidth / 2f + offset,
                bottom = cardBottom - depth * 16f,
                width = cardWidth,
                height = cardHeight,
                topColor = if (index == 1) 0xF2D081FF else 0xDFB768FF,
                bottomColor = if (index == 1) 0xD8A34BFF else 0xC18B43FF,
                edgeColor = 0xFFF5D4AA,
            )
        }

        lobbyPlayerBadgeRects().forEach { rect ->
            drawDropShadow(rect, 12f, 0x01050B38)
            fillGradientRect(rect.x, rect.y, rect.width, rect.height, 0x17284AFF, 0x132342FF, 0x243D6FFF, 0x1B3158FF)
            drawFrame(rect, 0xAFC3F032, 2f)
        }
    }

    private fun drawLobbyTextures() {
        if (showLobbyPrimaryButton()) {
            drawPanelTexture(startButtonRect, color(0xFFF5D41E))
        }
        lobbyPlayerBadgeRects().forEach { rect ->
            drawPanelTexture(rect, color(0xC7DAFF10))
        }
    }

    private fun drawLobbyText() {
        drawTextBlock(
            text = "Hitster Clone",
            x = headerRect.x,
            y = headerRect.y,
            width = headerRect.width,
            height = headerRect.height,
            scale = 1.40f,
            color = Color.WHITE,
            align = Align.center,
            verticalAlign = VerticalTextAlign.Center,
        )

        presenter.state.players.zip(lobbyPlayerBadgeRects()).forEach { (player, rect) ->
            drawTextBlock(
                text = player.displayName,
                x = rect.x,
                y = rect.y,
                width = rect.width,
                height = rect.height,
                scale = 0.90f,
                color = Color.WHITE,
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
            )
        }
        drawTextBlock(
            text = "${presenter.state.players.size} PLAYERS",
            x = lobbyCardRect.x,
            y = lobbyCardRect.y + lobbyCardRect.height * 0.18f,
            width = lobbyCardRect.width,
            height = 42f,
            scale = 0.74f,
            color = color(0xF3CF7BFF),
            align = Align.center,
            verticalAlign = VerticalTextAlign.Center,
        )

        if (showLobbyPrimaryButton()) {
            drawTextBlock(
                text = lobbyPrimaryActionText(),
                x = startButtonRect.x,
                y = startButtonRect.y,
                width = startButtonRect.width,
                height = startButtonRect.height,
                scale = 1.16f,
                color = if (presenter.playbackSessionState == PlaybackSessionState.Connecting) {
                    color(0x0F1A2EFF)
                } else {
                    color(0x1A1308FF)
                },
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
                shadowColor = color(0xFFF8E29F33),
            )
        } else {
            drawTextBlock(
                text = lobbyWaitingText(),
                x = startButtonRect.x,
                y = startButtonRect.y,
                width = startButtonRect.width,
                height = startButtonRect.height,
                scale = 0.88f,
                color = color(0xD9E4FDFF),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
                shadowColor = color(0x02060C8A),
            )
        }
    }

    private fun lobbyPlayerBadgeRects(): List<Rectangle> {
        val playerCount = presenter.state.players.size
        if (playerCount == 0) {
            return emptyList()
        }

        val badgeWidth = clamp(lobbyCardRect.width * 0.16f, 220f, 310f)
        val badgeHeight = clamp(lobbyCardRect.height * 0.12f, 56f, 70f)
        val columnGap = clamp(panelGap * 1.05f, 18f, 30f)
        val rowGap = clamp(panelGap * 0.76f, 16f, 22f)
        val columns = max(1, min(3, playerCount))
        val rows = (playerCount + columns - 1) / columns
        val baseY = lobbyCardRect.y + clamp(lobbyCardRect.height * 0.08f, 18f, 30f)
        val rects = ArrayList<Rectangle>(playerCount)

        repeat(rows) { row ->
            val firstIndex = row * columns
            val rowSize = min(columns, playerCount - firstIndex)
            val rowWidth = rowSize * badgeWidth + (rowSize - 1) * columnGap
            val startX = lobbyCardRect.x + (lobbyCardRect.width - rowWidth) / 2f
            val y = baseY + (rows - 1 - row) * (badgeHeight + rowGap)
            repeat(rowSize) { column ->
                rects += Rectangle(
                    startX + column * (badgeWidth + columnGap),
                    y,
                    badgeWidth,
                    badgeHeight,
                )
            }
        }

        return rects
    }

    private fun drawMatch(includeOverlay: Boolean) {
        fillHero(heroRect)
        fillPanel(timelinePanelRect, 0x14264DFF, 0x0D1B37FF, 0x556EABFF, 0x41598FFF, 0xB4C7F144)
        if (!showActionButton() && !showDoubtToggleButton()) {
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
        }

        drawTimelineCards(includeOverlay)
        drawTargetDoubtArrow()
    }

    private fun drawMatchTextures() {
        drawPanelTexture(heroRect, color(0xCFE1FF10))
        drawPanelTexture(timelinePanelRect, color(0xC9DBFF12))
        drawRepeatedTexture(
            grainTexture,
            timelinePanelRect.x + 2f,
            timelinePanelRect.y + 2f,
            timelinePanelRect.width - 4f,
            timelinePanelRect.height - panelHeaderHeight - 4f,
            color(0x7FA9DD0C),
            timelinePanelRect.width / 116f,
            max(1f, (timelinePanelRect.height - panelHeaderHeight) / 116f),
        )
    }

    private fun drawActionButtonGlow(rect: Rectangle, enabled: Boolean) {
        val expansion = if (enabled) 56f else 42f
        val outerTint = if (enabled) {
            color(0xF4BA4FFF)
        } else {
            color(0xD5B16DCC)
        }
        val innerTint = if (enabled) {
            color(0xFFF2B4FF)
        } else {
            color(0xFFF2CFB8)
        }
        drawGlow(
            rect.x - expansion * 0.5f,
            rect.y - expansion * 0.52f,
            rect.width + expansion,
            rect.height + expansion,
            colorWithAlpha(outerTint.toRgba(), if (enabled) 0.16f else 0.08f),
        )
        drawGlow(
            rect.x + rect.width * 0.04f,
            rect.y + rect.height * 0.06f,
            rect.width * 0.92f,
            rect.height * 0.92f,
            colorWithAlpha(innerTint.toRgba(), if (enabled) 0.10f else 0.05f),
        )
    }

    private fun drawMatchText(includeOverlay: Boolean) {
        val player = localPlayer()
        val toolbarStatus = toolbarStatusText()
        val turnLabelWidth = if (toolbarStatus == null) 170f else 142f
        val turnX = heroRect.x + heroRect.width - panelPadding - turnLabelWidth
        val playerWidth = if (toolbarStatus == null) {
            heroRect.width * 0.46f
        } else {
            clamp(heroRect.width * 0.25f, 230f, 360f)
        }

        drawTextBlock(
            text = activeTurnToolbarLabel(),
            x = heroRect.x + panelPadding,
            y = heroRect.y,
            width = playerWidth,
            height = heroRect.height,
            scale = if (toolbarStatus == null) 1.08f else 0.96f,
            color = activeTurnToolbarColor(),
            verticalAlign = VerticalTextAlign.Center,
            shadowColor = activeTurnToolbarShadowColor(),
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
        toolbarStatus?.let { text ->
            val messageX = heroRect.x + panelPadding + playerWidth + panelGap
            val messageRight = turnX - panelGap
            val messageWidth = max(1f, messageRight - messageX)
            val fittedStatus = fitSingleLineText(
                text = text,
                color = toolbarStatusColor(),
                maxWidth = max(1f, messageWidth - 12f),
                preferredScale = 0.82f,
                minimumScale = 0.62f,
            )
            drawTextBlock(
                text = fittedStatus.text,
                x = messageX,
                y = heroRect.y,
                width = messageWidth,
                height = heroRect.height,
                scale = fittedStatus.scale,
                color = toolbarStatusColor(),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
                shadowColor = color(0x02060C8A),
                enforceMinimumScale = false,
            )
        }

        if (!showActionButton() && !showDoubtToggleButton()) {
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
        }

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
            text = "Score ${player?.score ?: 0}  Coins ${player?.coins ?: 0}",
            x = timelineHeaderRect.x + timelineHeaderRect.width - 332f,
            y = timelineHeaderRect.y,
            width = 296f,
            height = timelineHeaderRect.height,
            scale = 0.86f,
            color = color(0xF4CF79FF),
            align = Align.right,
            verticalAlign = VerticalTextAlign.Center,
        )

        if (player?.timeline?.cards.isNullOrEmpty() && player?.pendingCard == null && toolbarStatus == null) {
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

        drawTimelineCardText(includeOverlay)
    }

    private fun drawModalShapes() {
        fillRect(0f, 0f, layoutWorldWidth, layoutWorldHeight, 0x03060CB2)
        if (showDoubtPlacementPopup()) {
            drawDoubtPopupShapes()
        }
        if (coinPanelOpen) {
            drawCoinPanelShapes()
        }
    }

    private fun drawModalTextures() {
        if (showDoubtPlacementPopup()) {
            drawDoubtPopupTextures()
        }
        if (coinPanelOpen) {
            drawCoinPanelTextures()
        }
    }

    private fun drawModalText() {
        if (showDoubtPlacementPopup()) {
            drawDoubtPopupText()
        }
        if (coinPanelOpen) {
            drawCoinPanelText()
        }
    }

    private fun drawFloatingControlsShapes() {
        if (showCoinsShortcutButton()) {
            fillButton(hostCoinsButtonRect, 0xF4C55BFF, 0xDA8E2CFF, 0xFFF3C07D)
        }
        when {
            showActionButton() -> {
                val enabled = isActionButtonEnabled()
                fillCircularActionButton(actionButtonRect, enabled)
                drawFastForwardGlyph(actionButtonRect, enabled)
            }

            showDoubtToggleButton() -> {
                val isActive = isDoubtToggleActive()
                fillButton(
                    doubtButtonRect,
                    if (isActive) 0x6FD8FFFF else 0xF6C96BFF,
                    if (isActive) 0x2C8ECAFF else 0xD78B25FF,
                    if (isActive) 0xD9F6FFFF else 0xFFF2C286,
                )
            }
        }
    }

    private fun drawFloatingControlsTextures() {
        if (showActionButton()) {
            drawActionButtonGlow(actionButtonRect, isActionButtonEnabled())
        }
        if (showDoubtToggleButton()) {
            drawPanelTexture(
                doubtButtonRect,
                if (isDoubtToggleActive()) color(0xD4F6FF16) else color(0xFFF3D712),
            )
        }
        if (showCoinsShortcutButton()) {
            drawPanelTexture(hostCoinsButtonRect, color(0xFFE7B313))
        }
    }

    private fun drawFloatingControlsText() {
        if (showDoubtToggleButton()) {
            drawTextBlock(
                text = if (isDoubtToggleActive()) "DOUBTING" else "DOUBT",
                x = doubtButtonRect.x,
                y = doubtButtonRect.y + doubtButtonRect.height * 0.14f,
                width = doubtButtonRect.width,
                height = doubtButtonRect.height * 0.56f,
                scale = 0.92f,
                color = if (isDoubtToggleActive()) color(0x041C2FFF) else color(0x1A1308FF),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
                shadowColor = color(0xFFF6E29A2C),
            )
            drawTextBlock(
                text = "COINS ${localPlayer()?.coins ?: 0}",
                x = doubtButtonRect.x,
                y = doubtButtonRect.y + doubtButtonRect.height * 0.02f,
                width = doubtButtonRect.width,
                height = doubtButtonRect.height * 0.24f,
                scale = 0.56f,
                color = if (isDoubtToggleActive()) color(0x03314BFF) else color(0x33200BFF),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
                shadowColor = color(0xFFF4E3B022),
            )
        }
        if (showCoinsShortcutButton()) {
            drawTextBlock(
                text = "COINS",
                x = hostCoinsButtonRect.x,
                y = hostCoinsButtonRect.y,
                width = hostCoinsButtonRect.width,
                height = hostCoinsButtonRect.height,
                scale = 0.64f,
                color = color(0x1A1308FF),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
                shadowColor = color(0xFFF8E29F33),
            )
        }
    }

    private fun drawDoubtPopupShapes() {
        fillPanel(doubtPopupRect, 0x113255FF, 0x0B2139FF, 0x4AA5D8FF, 0x3687BBFF, 0xC9F5FF48)
        fillTrack(doubtPopupTrackRect)
        doubtTimelineCardVisuals.forEach(::drawCardVisual)
        doubtArrowXForPopup()?.let { arrowX ->
            drawDoubtArrow(arrowX, doubtPopupTrackRect.y + doubtPopupTrackRect.height + 8f)
        }
    }

    private fun drawDoubtPopupTextures() {
        drawPanelTexture(doubtPopupRect, color(0xC9EAFF12))
        drawRepeatedTexture(
            grainTexture,
            doubtPopupTrackRect.x + 2f,
            doubtPopupTrackRect.y + 2f,
            doubtPopupTrackRect.width - 4f,
            doubtPopupTrackRect.height - 4f,
            color(0x8FE3FF0D),
            doubtPopupTrackRect.width / 112f,
            max(1f, doubtPopupTrackRect.height / 112f),
        )
    }

    private fun drawDoubtPopupText() {
        val doubt = presenter.state.doubt ?: return
        val targetPlayer = presenter.state.requirePlayer(doubt.targetPlayerId) ?: return
        drawTextBlock(
            text = "${targetPlayer.displayName.uppercase()} TIMELINE",
            x = doubtPopupHeaderRect.x,
            y = doubtPopupHeaderRect.y,
            width = doubtPopupHeaderRect.width * 0.62f,
            height = doubtPopupHeaderRect.height,
            scale = 0.92f,
            color = color(0xDFF7FFFF),
            insetX = panelPadding,
            verticalAlign = VerticalTextAlign.Center,
        )
        drawTextBlock(
            text = "PLACE YOUR DOUBT",
            x = doubtPopupHeaderRect.x + doubtPopupHeaderRect.width - 300f,
            y = doubtPopupHeaderRect.y,
            width = 260f,
            height = doubtPopupHeaderRect.height,
            scale = 0.72f,
            color = color(0x7ED9FFFF),
            align = Align.right,
            verticalAlign = VerticalTextAlign.Center,
        )
        doubtTimelineCardVisuals.forEach(::drawCardText)
    }

    private fun drawCoinPanelShapes() {
        fillPanel(coinPanelRect, 0x132145FF, 0x0C1A33FF, 0xF0BE61FF, 0xD58E2DFF, 0xFFE7B44D)
        fillButton(coinPanelCloseRect, 0xE38A7AFF, 0xB84A35FF, 0xFFDAB2A5)
        coinPanelRows().forEach { row ->
            drawDropShadow(row.rowRect, 10f, 0x01050B38)
            fillGradientRect(
                row.rowRect.x,
                row.rowRect.y,
                row.rowRect.width,
                row.rowRect.height,
                0x13284BFF,
                0x102241FF,
                0x203862FF,
                0x183154FF,
            )
            drawFrame(row.rowRect, 0xAFC4F02A, 2f)
            fillButton(row.minusRect, 0xF0B25CFF, 0xC97C1DFF, 0xFFE6BF82)
            fillButton(row.plusRect, 0x7CD4E8FF, 0x2E8AB7FF, 0xDDF8FFFF)
        }
    }

    private fun drawCoinPanelTextures() {
        drawPanelTexture(coinPanelRect, color(0xC7DAFF10))
        coinPanelRows().forEach { row ->
            drawPanelTexture(row.rowRect, color(0xC7DAFF0C))
        }
    }

    private fun drawCoinPanelText() {
        drawTextBlock(
            text = "MANAGE COINS",
            x = coinPanelRect.x,
            y = coinPanelRect.y + coinPanelRect.height - panelHeaderHeight,
            width = coinPanelRect.width * 0.72f,
            height = panelHeaderHeight,
            scale = 0.98f,
            color = Color.WHITE,
            insetX = panelPadding,
            verticalAlign = VerticalTextAlign.Center,
        )
        drawTextBlock(
            text = "X",
            x = coinPanelCloseRect.x,
            y = coinPanelCloseRect.y,
            width = coinPanelCloseRect.width,
            height = coinPanelCloseRect.height,
            scale = 0.88f,
            color = color(0xFFF9EBE4),
            align = Align.center,
            verticalAlign = VerticalTextAlign.Center,
        )
        coinPanelRows().forEach { row ->
            val player = presenter.state.requirePlayer(row.playerId) ?: return@forEach
            drawTextBlock(
                text = player.displayName,
                x = row.rowRect.x + 22f,
                y = row.rowRect.y,
                width = row.rowRect.width * 0.42f,
                height = row.rowRect.height,
                scale = 0.74f,
                color = Color.WHITE,
                verticalAlign = VerticalTextAlign.Center,
            )
            drawTextBlock(
                text = player.coins.toString(),
                x = row.rowRect.x + row.rowRect.width * 0.48f,
                y = row.rowRect.y,
                width = row.rowRect.width * 0.12f,
                height = row.rowRect.height,
                scale = 0.86f,
                color = color(0xF4CF79FF),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
            )
            drawTextBlock(
                text = "-",
                x = row.minusRect.x,
                y = row.minusRect.y,
                width = row.minusRect.width,
                height = row.minusRect.height,
                scale = 0.86f,
                color = color(0x1A1308FF),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
            )
            drawTextBlock(
                text = "+",
                x = row.plusRect.x,
                y = row.plusRect.y,
                width = row.plusRect.width,
                height = row.plusRect.height,
                scale = 0.86f,
                color = color(0x031E2FFF),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
            )
        }
    }

    private fun drawConfetti() {
        confettiParticles.forEach { particle ->
            val fade = 1f - particle.ageSeconds / particle.lifeSeconds
            shapeRenderer.color = colorWithAlpha(particle.colorRgba, fade)
            shapeRenderer.rect(
                particle.x,
                particle.y,
                particle.width / 2f,
                particle.height / 2f,
                particle.width,
                particle.height,
                1f,
                1f,
                particle.rotation,
            )
        }
    }

    private fun drawInactiveTurnFilter(alpha: Float) {
        val time = overlayAnimationSeconds
        val slowDriftX = sin(time * 0.17f) * 0.12f
        val slowDriftY = cos(time * 0.13f) * 0.10f
        val grainDriftX = time * 0.021f
        val grainDriftY = time * 0.015f
        val accentGlowWidth = layoutWorldWidth * (0.54f + 0.05f * sin(time * 0.41f))
        val accentGlowHeight = layoutWorldHeight * (0.62f + 0.04f * cos(time * 0.37f))
        val accentGlowX = layoutWorldWidth * (0.18f + 0.06f * sin(time * 0.23f))
        val accentGlowY = layoutWorldHeight * (0.14f + 0.03f * cos(time * 0.19f))
        val sweepGlowWidth = layoutWorldWidth * 0.46f
        val sweepGlowHeight = layoutWorldHeight * 0.72f
        val sweepGlowX = layoutWorldWidth * (0.52f + 0.08f * cos(time * 0.28f))
        val sweepGlowY = layoutWorldHeight * (0.08f + 0.04f * sin(time * 0.22f))

        drawTexture(flatTexture, 0f, 0f, layoutWorldWidth, layoutWorldHeight, colorWithAlpha(0xCFD7E2FF, alpha * 0.34f))
        drawTexture(flatTexture, 0f, 0f, layoutWorldWidth, layoutWorldHeight, colorWithAlpha(0x7C8EADFF, alpha * 0.27f))
        drawTexture(flatTexture, 0f, 0f, layoutWorldWidth, layoutWorldHeight, colorWithAlpha(0x2A3344FF, alpha * 0.12f))
        drawGlow(
            accentGlowX,
            accentGlowY,
            accentGlowWidth,
            accentGlowHeight,
            colorWithAlpha(0xDCE8FFF0, alpha * 0.12f),
        )
        drawGlow(
            sweepGlowX,
            sweepGlowY,
            sweepGlowWidth,
            sweepGlowHeight,
            colorWithAlpha(0x91A9CFFF, alpha * 0.08f),
        )
        drawRepeatedTexture(
            grainTexture,
            0f,
            0f,
            layoutWorldWidth,
            layoutWorldHeight,
            colorWithAlpha(0xDDE6F1FF, alpha * 0.10f),
            layoutWorldWidth / 88f,
            layoutWorldHeight / 88f,
            grainDriftX,
            grainDriftY,
        )
        drawRepeatedTexture(
            grainTexture,
            0f,
            0f,
            layoutWorldWidth,
            layoutWorldHeight,
            colorWithAlpha(0x94A6BEFF, alpha * 0.06f),
            layoutWorldWidth / 58f,
            layoutWorldHeight / 58f,
            slowDriftX,
            slowDriftY,
        )
        drawTexture(vignetteTexture, 0f, 0f, layoutWorldWidth, layoutWorldHeight, colorWithAlpha(0x000000FF, alpha * 0.26f))
        drawTexture(vignetteTexture, 0f, 0f, layoutWorldWidth, layoutWorldHeight, colorWithAlpha(0x24334DFF, alpha * 0.13f))
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

    private fun fillButton(rect: Rectangle, topColor: Long, bottomColor: Long, edgeColor: Long) {
        drawDropShadow(rect, 16f, 0x1409014B)
        fillGradientRect(rect.x, rect.y, rect.width, rect.height, bottomColor, bottomColor, topColor, topColor)
        fillRect(rect.x + 8f, rect.y + rect.height - 12f, rect.width - 16f, 3f, 0xFFFFFF1A)
        drawFrame(rect, edgeColor, 2f)
    }

    private fun fillCircularActionButton(rect: Rectangle, enabled: Boolean) {
        val radius = min(rect.width, rect.height) / 2f
        val centerX = rect.x + rect.width / 2f
        val centerY = rect.y + rect.height / 2f
        val outerShadow = if (enabled) 0x170801FFL else 0x1C0E03FFL
        val outerRing = if (enabled) 0x4A2404FFL else 0x573915FFL
        val rimColor = if (enabled) 0xFFF3C86EFFL else 0xE1C28CFFL
        val innerRim = if (enabled) 0xFFF9E2A5FFL else 0xF0DDB3FFL
        val bodyLower = if (enabled) 0xD78418FFL else 0xBA8E43FFL
        val bodyUpper = if (enabled) 0xF7C759FFL else 0xD8B36AFFL
        val coreColor = if (enabled) 0xE8A93CFFL else 0xC59A58FFL
        val highlightColor = if (enabled) 0xFFF5C6FFL else 0xFFF0D0FFL
        val lowerShade = if (enabled) 0x8F5310FFL else 0x7E6130FFL

        repeat(5) { layer ->
            val expansion = 5f + layer * 4.6f
            val alpha = 0.13f - layer * 0.02f
            shapeRenderer.color = colorWithAlpha(outerShadow, alpha)
            shapeRenderer.circle(centerX, centerY - radius * 0.16f + layer * 1.4f, radius + expansion, 72)
        }

        shapeRenderer.color = color(outerRing)
        shapeRenderer.circle(centerX, centerY - radius * 0.02f, radius * 1.05f, 72)

        shapeRenderer.color = color(rimColor)
        shapeRenderer.circle(centerX, centerY, radius, 72)

        shapeRenderer.color = color(innerRim)
        shapeRenderer.circle(centerX, centerY + radius * 0.02f, radius * 0.93f, 72)

        shapeRenderer.color = color(bodyLower)
        shapeRenderer.circle(centerX, centerY - radius * 0.03f, radius * 0.86f, 72)

        shapeRenderer.color = color(bodyUpper)
        shapeRenderer.circle(centerX, centerY + radius * 0.17f, radius * 0.72f, 72)

        shapeRenderer.color = color(coreColor)
        shapeRenderer.circle(centerX, centerY - radius * 0.11f, radius * 0.56f, 72)

        shapeRenderer.color = colorWithAlpha(highlightColor, if (enabled) 0.52f else 0.28f)
        shapeRenderer.circle(centerX - radius * 0.08f, centerY + radius * 0.34f, radius * 0.38f, 54)

        shapeRenderer.color = colorWithAlpha(highlightColor, if (enabled) 0.22f else 0.12f)
        shapeRenderer.circle(centerX + radius * 0.18f, centerY + radius * 0.12f, radius * 0.18f, 42)

        shapeRenderer.color = colorWithAlpha(lowerShade, if (enabled) 0.22f else 0.16f)
        shapeRenderer.circle(centerX, centerY - radius * 0.30f, radius * 0.52f, 72)
    }

    private fun drawFastForwardGlyph(rect: Rectangle, enabled: Boolean) {
        val shadowColor = if (enabled) color(0x703406A8) else color(0x5D3E1D82)
        val baseGlyphColor = if (enabled) color(0x8B4708FF) else color(0x7A5930FF)
        val glyphColor = if (enabled) color(0xFFF2D0FF) else color(0xF3E2BCFF)
        val triangleWidth = rect.width * 0.21f
        val triangleHeight = rect.height * 0.28f
        val gap = triangleWidth * 0.12f
        val centerY = rect.y + rect.height * 0.51f
        val startX = rect.x + (rect.width - (triangleWidth * 2f + gap)) / 2f

        shapeRenderer.color = shadowColor
        drawRightTriangle(startX + 4f, centerY - triangleHeight / 2f - 5f, triangleWidth, triangleHeight)
        drawRightTriangle(startX + triangleWidth + gap + 4f, centerY - triangleHeight / 2f - 5f, triangleWidth, triangleHeight)

        shapeRenderer.color = baseGlyphColor
        drawRightTriangle(startX + 1.5f, centerY - triangleHeight / 2f - 1.5f, triangleWidth, triangleHeight)
        drawRightTriangle(startX + triangleWidth + gap + 1.5f, centerY - triangleHeight / 2f - 1.5f, triangleWidth, triangleHeight)

        shapeRenderer.color = glyphColor
        drawRightTriangle(startX, centerY - triangleHeight / 2f, triangleWidth, triangleHeight)
        drawRightTriangle(startX + triangleWidth + gap, centerY - triangleHeight / 2f, triangleWidth, triangleHeight)
    }

    private fun drawRightTriangle(left: Float, bottom: Float, width: Float, height: Float) {
        shapeRenderer.triangle(
            left,
            bottom,
            left,
            bottom + height,
            left + width,
            bottom + height / 2f,
        )
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

    private fun toolbarStatusText(): String? {
        presenter.lastError?.let { return it }
        localResolution()?.let { resolution ->
            if (resolution.correct) {
                return null
            }
            return resolvedTrackLabel(resolution.cardId)
        }
        presenter.state.doubt?.let { doubt ->
            val doubterName = presenter.state.requirePlayer(doubt.doubterId)?.displayName?.uppercase() ?: "PLAYER"
            return when {
                showDoubtPlacementPopup() -> "PLACE YOUR DOUBT"
                presenter.localPlayerId == doubt.targetPlayerId && doubt.phase == DoubtPhase.ARMED -> "$doubterName DOUBTS"
                presenter.localPlayerId == doubt.targetPlayerId -> "$doubterName IS DOUBTING"
                presenter.localPlayerId != doubt.doubterId && doubt.phase == DoubtPhase.ARMED -> "$doubterName ARMED A DOUBT"
                else -> null
            }
        }
        return null
    }

    private fun toolbarStatusColor(): Color {
        presenter.lastError?.let { return color(0xFFB7ACFF) }
        return if (localResolution()?.correct == true) {
            color(0xF4D283FF)
        } else {
            color(0xFFB7ACFF)
        }
    }

    private fun resolvedTrackLabel(cardId: String): String {
        val entry = resolvedTrackEntry(cardId) ?: return "Unknown Track"
        return "${entry.artist} - ${entry.title} (${entry.releaseYear})"
    }

    private fun resolvedTrackEntry(cardId: String): PlaylistEntry? {
        presenter.state.discardPile.asReversed().firstOrNull { it.id == cardId }?.let { return it }
        presenter.state.players.asSequence()
            .flatMap { it.timeline.cards.asSequence() }
            .firstOrNull { it.id == cardId }
            ?.let { return it }
        return null
    }

    private fun activeTurnToolbarLabel(): String {
        if (showDoubtPlacementPopup()) {
            return "YOUR DOUBT"
        }
        val turnPlayer = presenter.state.turn
            ?.activePlayerId
            ?.let { playerId -> presenter.state.requirePlayer(playerId) }
            ?: return "Waiting"
        return if (turnPlayer.id == presenter.localPlayerId) {
            "YOUR TURN"
        } else {
            "${turnPlayer.displayName.uppercase()} TURN"
        }
    }

    private fun activeTurnToolbarColor(): Color {
        return if (isLocalPlayersTurn()) {
            color(0xF4CF79FF)
        } else {
            Color.WHITE
        }
    }

    private fun activeTurnToolbarShadowColor(): Color {
        return if (isLocalPlayersTurn()) {
            color(0x533106A6)
        } else {
            color(0x02060CB8)
        }
    }

    private fun shouldShowInactiveTurnFilter(): Boolean {
        return presenter.state.status == MatchStatus.ACTIVE &&
            !isLocalPlayersTurn() &&
            !showDoubtPlacementPopup()
    }

    private fun showActionButton(): Boolean = canEndTurn()

    private fun showHostCoinsButton(): Boolean = presenter.isLocalHost && presenter.state.status == MatchStatus.ACTIVE

    private fun showCoinsShortcutButton(): Boolean =
        showHostCoinsButton() &&
            !coinPanelOpen &&
            !showDoubtPlacementPopup()

    private fun showDoubtToggleButton(): Boolean {
        if (presenter.state.status != MatchStatus.ACTIVE || showDoubtPlacementPopup()) {
            return false
        }
        val localPlayer = localPlayer() ?: return false
        val turn = presenter.state.turn ?: return false
        val doubt = presenter.state.doubt
        if (isLocalPlayersTurn()) {
            return false
        }
        if (localPlayer.coins <= 0 && !(doubt?.doubterId == presenter.localPlayerId && doubt.phase == DoubtPhase.ARMED)) {
            return false
        }
        return when {
            doubt == null -> turn.phase == TurnPhase.AWAITING_PLACEMENT || turn.phase == TurnPhase.CARD_POSITIONED
            doubt.doubterId == presenter.localPlayerId && doubt.phase == DoubtPhase.ARMED -> true
            else -> false
        }
    }

    private fun isDoubtToggleActive(): Boolean {
        val doubt = presenter.state.doubt ?: return false
        return doubt.doubterId == presenter.localPlayerId && doubt.phase == DoubtPhase.ARMED
    }

    private fun showDoubtPlacementPopup(): Boolean {
        val doubt = presenter.state.doubt ?: return false
        val phase = presenter.state.turn?.phase ?: return false
        return doubt.doubterId == presenter.localPlayerId &&
            (phase == TurnPhase.AWAITING_DOUBT_PLACEMENT || phase == TurnPhase.DOUBT_POSITIONED)
    }

    private fun isActionButtonEnabled(): Boolean = canEndTurn()

    private fun showLobbyPairingGate(): Boolean = presenter.requiresHostPlaybackPairing()

    private fun showLobbyPrimaryButton(): Boolean = presenter.isLocalHost &&
        (showLobbyPairingGate() || presenter.canStartLobbyMatch())

    private fun lobbyPrimaryActionText(): String {
        return if (showLobbyPairingGate()) {
            if (presenter.playbackSessionState == PlaybackSessionState.Connecting) {
                "PAIRING..."
            } else {
                "PAIR SPOTIFY"
            }
        } else {
            "START"
        }
    }

    private fun lobbyWaitingText(): String {
        return if (presenter.isLocalHost) {
            "WAITING FOR PLAYERS"
        } else {
            "WAITING FOR HOST"
        }
    }

    private fun localPlayer(): PlayerState? = presenter.localPlayer

    private fun localResolution() = presenter.state.lastResolution?.takeIf { it.playerId == presenter.localPlayerId }

    private fun isLocalPlayersTurn(): Boolean = presenter.state.turn?.activePlayerId == presenter.localPlayerId

    private fun canDraw(): Boolean = isLocalPlayersTurn() && presenter.state.turn?.phase == TurnPhase.WAITING_FOR_DRAW

    private fun canEndTurn(): Boolean {
        val phase = presenter.state.turn?.phase ?: return false
        return when {
            showDoubtPlacementPopup() -> phase == TurnPhase.AWAITING_DOUBT_PLACEMENT || phase == TurnPhase.DOUBT_POSITIONED
            isLocalPlayersTurn() -> phase == TurnPhase.CARD_POSITIONED
            else -> false
        }
    }

    private fun canMoveMainPendingCard(): Boolean {
        val phase = presenter.state.turn?.phase ?: return false
        return isLocalPlayersTurn() && (phase == TurnPhase.AWAITING_PLACEMENT || phase == TurnPhase.CARD_POSITIONED)
    }

    private fun canMoveDoubtCard(): Boolean {
        val phase = presenter.state.turn?.phase ?: return false
        return showDoubtPlacementPopup() &&
            (phase == TurnPhase.AWAITING_DOUBT_PLACEMENT || phase == TurnPhase.DOUBT_POSITIONED)
    }

    private fun drawDropSlotIndexFor(x: Float): Int {
        val player = localPlayer() ?: return 0
        return timelineLayout.nearestSlotIndex(player.timeline.cards.size, x)
    }

    private fun requestedSlotIndexFor(x: Float): Int {
        val player = localPlayer() ?: return 0
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

    private fun requestedDoubtSlotIndexFor(x: Float): Int {
        val doubt = presenter.state.doubt ?: return 0
        val targetPlayer = presenter.state.requirePlayer(doubt.targetPlayerId) ?: return 0
        val pendingCard = targetPlayer.pendingCard ?: return 0
        val popupTrackX = doubtPopupTrackRect.x + panelPadding * 0.18f
        val popupTrackWidth = doubtPopupTrackRect.width - panelPadding * 0.36f
        val popupLayout = TimelineLayoutCalculator(
            trackX = popupTrackX,
            trackWidth = popupTrackWidth,
            preferredCardWidth = clamp(doubtPopupTrackRect.width * 0.18f, 148f, 208f),
            minCardWidth = clamp(doubtPopupTrackRect.width * 0.118f, 108f, 136f),
            preferredGap = clamp(doubtPopupTrackRect.width * 0.024f, 18f, 30f),
            minGap = 12f,
        )
        if (!draggingDoubtCard) {
            return popupLayout.nearestSlotIndex(targetPlayer.timeline.cards.size, x)
        }

        val arrangement = popupLayout.pendingArrangement(
            existingCardCount = targetPlayer.timeline.cards.size,
            pendingSlotIndex = doubt.proposedSlotIndex ?: pendingCard.proposedSlotIndex,
        )
        val pendingLeft = clamp(
            x - doubtPendingCardGrabOffsetX,
            popupTrackX,
            popupTrackX + popupTrackWidth - arrangement.cardWidth,
        )
        val probeX = pendingLeft + arrangement.cardWidth * 0.5f
        return popupLayout.nearestSlotIndex(targetPlayer.timeline.cards.size, probeX)
    }

    private fun drawTargetDoubtArrow() {
        val arrowX = doubtArrowXForMainTimeline() ?: return
        drawDoubtArrow(arrowX, timelineTrackRect.y + timelineTrackRect.height + 8f)
    }

    private fun doubtArrowXForMainTimeline(): Float? {
        val doubt = presenter.state.doubt ?: return null
        val phase = presenter.state.turn?.phase ?: return null
        if (phase != TurnPhase.AWAITING_DOUBT_PLACEMENT && phase != TurnPhase.DOUBT_POSITIONED) {
            return null
        }
        if (doubt.targetPlayerId != presenter.localPlayerId) {
            return null
        }
        val targetPlayer = localPlayer() ?: return null
        val pendingCard = targetPlayer.pendingCard ?: return null
        val arrangement = timelineLayout.pendingArrangement(
            existingCardCount = targetPlayer.timeline.cards.size,
            pendingSlotIndex = doubt.proposedSlotIndex ?: pendingCard.proposedSlotIndex,
        )
        return arrangement.pendingCardLeft + arrangement.cardWidth / 2f
    }

    private fun doubtArrowXForPopup(): Float? {
        val doubt = presenter.state.doubt ?: return null
        val targetPlayer = presenter.state.requirePlayer(doubt.targetPlayerId) ?: return null
        val pendingCard = targetPlayer.pendingCard ?: return null
        val popupLayout = TimelineLayoutCalculator(
            trackX = doubtPopupTrackRect.x + panelPadding * 0.18f,
            trackWidth = doubtPopupTrackRect.width - panelPadding * 0.36f,
            preferredCardWidth = clamp(doubtPopupTrackRect.width * 0.18f, 148f, 208f),
            minCardWidth = clamp(doubtPopupTrackRect.width * 0.118f, 108f, 136f),
            preferredGap = clamp(doubtPopupTrackRect.width * 0.024f, 18f, 30f),
            minGap = 12f,
        )
        val arrangement = popupLayout.pendingArrangement(
            existingCardCount = targetPlayer.timeline.cards.size,
            pendingSlotIndex = doubt.proposedSlotIndex ?: pendingCard.proposedSlotIndex,
        )
        return arrangement.pendingCardLeft + arrangement.cardWidth / 2f
    }

    private fun drawDoubtArrow(centerX: Float, bottomY: Float) {
        shapeRenderer.color = colorWithAlpha(0x3A2007FF, 0.32f)
        shapeRenderer.rect(centerX - 3f, bottomY - 4f, 6f, 26f)
        shapeRenderer.triangle(
            centerX,
            bottomY + 34f,
            centerX - 18f,
            bottomY + 14f,
            centerX + 18f,
            bottomY + 14f,
        )
        shapeRenderer.color = color(0xFFF0BF56FF)
        shapeRenderer.rect(centerX - 2f, bottomY, 4f, 24f)
        shapeRenderer.triangle(
            centerX,
            bottomY + 30f,
            centerX - 14f,
            bottomY + 12f,
            centerX + 14f,
            bottomY + 12f,
        )
    }

    private fun coinPanelRows(): List<CoinPanelRowLayout> {
        if (!coinPanelOpen) {
            return emptyList()
        }
        val players = presenter.state.players
        if (players.isEmpty()) {
            return emptyList()
        }
        val rowHeight = clamp((coinPanelRect.height - panelHeaderHeight - 72f) / players.size, 64f, 82f)
        val rowGap = 14f
        val startY = coinPanelRect.y + coinPanelRect.height - panelHeaderHeight - rowHeight - 22f
        return players.mapIndexed { index, player ->
            val y = startY - index * (rowHeight + rowGap)
            val rowRect = Rectangle(
                coinPanelRect.x + panelPadding,
                y,
                coinPanelRect.width - panelPadding * 2f,
                rowHeight,
            )
            val buttonSize = rowHeight - 18f
            val plusRect = Rectangle(
                rowRect.x + rowRect.width - buttonSize - 16f,
                rowRect.y + (rowRect.height - buttonSize) / 2f,
                buttonSize,
                buttonSize,
            )
            val minusRect = Rectangle(
                plusRect.x - buttonSize - 14f,
                plusRect.y,
                buttonSize,
                buttonSize,
            )
            CoinPanelRowLayout(
                playerId = player.id,
                rowRect = rowRect,
                minusRect = minusRect,
                plusRect = plusRect,
            )
        }
    }

    private fun fitSingleLineText(
        text: String,
        color: Color,
        maxWidth: Float,
        preferredScale: Float,
        minimumScale: Float,
    ): FittedTextLine {
        val width = max(1f, maxWidth)
        var scale = preferredScale
        while (scale >= minimumScale) {
            font.data.setScale(scale * fontScaleMultiplier)
            textLayout.setText(font, text, color, 0f, Align.left, false)
            if (textLayout.width <= width) {
                return FittedTextLine(text = text, scale = scale)
            }
            scale -= 0.04f
        }

        val ellipsis = "..."
        font.data.setScale(minimumScale * fontScaleMultiplier)
        for (endExclusive in text.length downTo 1) {
            val candidate = text.take(endExclusive).trimEnd() + ellipsis
            textLayout.setText(font, candidate, color, 0f, Align.left, false)
            if (textLayout.width <= width) {
                return FittedTextLine(text = candidate, scale = minimumScale)
            }
        }
        return FittedTextLine(text = ellipsis, scale = minimumScale)
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
        drawRepeatedTexture(texture, x, y, width, height, tint, repeatX, repeatY, 0f, 0f)
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
        offsetX: Float,
        offsetY: Float,
    ) {
        batch.color = tint
        batch.draw(texture, x, y, width, height, offsetX, offsetY, offsetX + repeatX, offsetY + repeatY)
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

    private fun colorWithAlpha(rgba: Long, alphaMultiplier: Float): Color {
        return color(rgba).also { it.a *= clamp(alphaMultiplier, 0f, 1f) }
    }

    private fun Color.toRgba(): Long {
        val red = (r * 255f).roundToInt().coerceIn(0, 255).toLong()
        val green = (g * 255f).roundToInt().coerceIn(0, 255).toLong()
        val blue = (b * 255f).roundToInt().coerceIn(0, 255).toLong()
        val alpha = (a * 255f).roundToInt().coerceIn(0, 255).toLong()
        return (red shl 24) or (green shl 16) or (blue shl 8) or alpha
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

    private fun actionButtonContains(x: Float, y: Float): Boolean {
        val radius = min(actionButtonRect.width, actionButtonRect.height) / 2f
        val centerX = actionButtonRect.x + actionButtonRect.width / 2f
        val centerY = actionButtonRect.y + actionButtonRect.height / 2f
        val dx = x - centerX
        val dy = y - centerY
        return dx * dx + dy * dy <= radius * radius
    }

    private inner class MatchInputController : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            updateLayout()
            val world = viewport.unproject(worldTouch.set(screenX.toFloat(), screenY.toFloat()))

            if (coinPanelOpen) {
                if (coinPanelCloseRect.contains(world.x, world.y)) {
                    coinPanelOpen = false
                    return true
                }
                coinPanelRows().firstOrNull { row ->
                    row.minusRect.contains(world.x, world.y) || row.plusRect.contains(world.x, world.y)
                }?.let { row ->
                    if (row.minusRect.contains(world.x, world.y)) {
                        presenter.adjustPlayerCoins(row.playerId, -1)
                    } else if (row.plusRect.contains(world.x, world.y)) {
                        presenter.adjustPlayerCoins(row.playerId, 1)
                    }
                    return true
                }
                if (!coinPanelRect.contains(world.x, world.y)) {
                    coinPanelOpen = false
                }
                return true
            }

            if (presenter.state.status == MatchStatus.LOBBY && showLobbyPrimaryButton() && startButtonRect.contains(world.x, world.y)) {
                if (showLobbyPairingGate()) {
                    if (presenter.playbackSessionState != PlaybackSessionState.Connecting) {
                        presenter.prepareHostPlayback()
                    }
                } else {
                    presenter.startMatch()
                }
                return true
            }

            if (showDoubtPlacementPopup()) {
                if (canEndTurn() && actionButtonContains(world.x, world.y)) {
                    presenter.endTurn()
                    return true
                }
                val popupPendingCard = doubtPendingCardVisual
                if (canMoveDoubtCard() && popupPendingCard?.rect?.contains(world.x, world.y) == true) {
                    draggingDoubtCard = true
                    worldTouch.set(world)
                    doubtPendingCardGrabOffsetX = world.x - popupPendingCard.rect.x
                    return true
                }
                return doubtPopupRect.contains(world.x, world.y)
            }

            if (presenter.state.status != MatchStatus.ACTIVE) {
                return false
            }

            if (showHostCoinsButton() && hostCoinsButtonRect.contains(world.x, world.y)) {
                coinPanelOpen = true
                return true
            }

            if (canEndTurn() && actionButtonContains(world.x, world.y)) {
                presenter.endTurn()
                return true
            }

            if (showDoubtToggleButton() && doubtButtonRect.contains(world.x, world.y)) {
                presenter.toggleDoubt()
                return true
            }

            val player = localPlayer() ?: return false
            if (canDraw() && deckRect.contains(world.x, world.y)) {
                draggingDeckGhost = true
                worldTouch.set(world)
                return true
            }

            val currentPendingCard = pendingCardVisual
            if (canMoveMainPendingCard() && currentPendingCard?.rect?.contains(world.x, world.y) == true) {
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
            if (!draggingDeckGhost && !draggingPendingCard && !draggingDoubtCard) {
                return false
            }

            val previousX = worldTouch.x
            viewport.unproject(worldTouch.set(screenX.toFloat(), screenY.toFloat()))
            if (draggingDoubtCard) {
                presenter.moveDoubtCard(requestedDoubtSlotIndexFor(worldTouch.x))
            } else if (draggingPendingCard) {
                pendingCardDragDirectionX = worldTouch.x - previousX
                presenter.movePendingCard(requestedSlotIndexFor(worldTouch.x))
            }
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            updateLayout()
            if (!draggingDeckGhost && !draggingPendingCard && !draggingDoubtCard) {
                return false
            }

            val world = viewport.unproject(worldTouch.set(screenX.toFloat(), screenY.toFloat()))

            if (draggingDeckGhost) {
                val drawDropSlotIndex = drawDropSlotIndexFor(world.x)
                presenter.drawCard()
                presenter.movePendingCard(drawDropSlotIndex)
            } else if (draggingDoubtCard) {
                presenter.moveDoubtCard(requestedDoubtSlotIndexFor(world.x))
            } else if (draggingPendingCard) {
                presenter.movePendingCard(requestedSlotIndexFor(world.x))
            }

            draggingDeckGhost = false
            draggingPendingCard = false
            draggingDoubtCard = false
            pendingCardGrabOffsetX = 0f
            doubtPendingCardGrabOffsetX = 0f
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

    private data class FittedTextLine(
        val text: String,
        val scale: Float,
    )

    private data class ConfettiParticle(
        var x: Float,
        var y: Float,
        val width: Float,
        val height: Float,
        val velocityX: Float,
        var velocityY: Float,
        var rotation: Float,
        val angularVelocity: Float,
        val lifeSeconds: Float,
        var ageSeconds: Float = 0f,
        val colorRgba: Long,
    )

    private data class CoinPanelRowLayout(
        val playerId: com.hitster.core.model.PlayerId,
        val rowRect: Rectangle,
        val minusRect: Rectangle,
        val plusRect: Rectangle,
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
        const val CONFETTI_COUNT = 110
        const val CONFETTI_GRAVITY = -520f
        const val CONFETTI_FILTER_BLEND_START_PROGRESS = 0.72f
        val CONFETTI_COLORS = longArrayOf(
            0xFF7280FF,
            0xFFD86161FF,
            0xFFE7B64CFF,
            0xFF5EC4A6FF,
            0xFF72A6FFFF,
            0xFFE98DD4FF,
        )
    }
}
