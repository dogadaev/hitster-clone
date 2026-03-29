package com.hitster.ui.screen

/**
 * Main libGDX renderer and input handler for the landscape gameplay, lobby, and animated match presentation.
 */

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
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.hitster.animations.AnimationCatalog
import com.hitster.core.model.DoubtPhase
import com.hitster.core.model.MatchStatus
import com.hitster.core.model.PlayerId
import com.hitster.core.model.PlayerState
import com.hitster.core.model.PlaylistEntry
import com.hitster.core.model.TurnPhase
import com.hitster.playback.api.PlaybackSessionState
import com.hitster.ui.controller.MatchController
import com.hitster.ui.controller.UiBootstrapper
import com.hitster.ui.layout.TimelineLayoutCalculator
import com.hitster.ui.render.LiquidGlassStyle
import com.hitster.ui.render.LiquidGlassSurfaceRenderer
import com.hitster.ui.render.UiShadowRenderer
import com.hitster.ui.render.VerticalCropAnchor
import com.hitster.ui.render.WidthFittedBackgroundImage
import com.hitster.ui.theme.DecadeCardPalettes
import com.hitster.ui.theme.createUiFont
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
    private val requestDisplayNameInput: (String, (String?) -> Unit) -> Unit,
    private val onLocalDisplayNameEdited: (String) -> Unit = {},
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
    private val glassRenderer = LiquidGlassSurfaceRenderer()
    private val lobbyBackgroundImage = WidthFittedBackgroundImage("lobby-background.png", VerticalCropAnchor.TOP)
    private val matchBackgroundImage = WidthFittedBackgroundImage("match-background.png", VerticalCropAnchor.CENTER)
    private val textLayout = GlyphLayout()
    private val worldTouch = Vector2()

    private val headerRect = Rectangle()
    private val heroRect = Rectangle()
    private val playbackButtonRect = Rectangle()
    private val actionButtonRect = Rectangle()
    private val redrawButtonRect = Rectangle()
    private val doubtButtonRect = Rectangle()
    private val hostCoinsButtonRect = Rectangle()
    private val timelinePanelRect = Rectangle()
    private val timelineHeaderRect = Rectangle()
    private val timelineTrackRect = Rectangle()
    private val coinPanelRect = Rectangle()
    private val coinPanelCloseRect = Rectangle()
    private val doubtPopupRect = Rectangle()
    private val doubtPopupHeaderRect = Rectangle()
    private val doubtPopupTrackRect = Rectangle()
    private val lobbyCardRect = Rectangle()
    private val lobbyMainRect = Rectangle()
    private val lobbyJoinPanelRect = Rectangle()
    private val lobbyQrRect = Rectangle()
    private val lobbyJoinUrlRect = Rectangle()
    private val startButtonRect = Rectangle()

    private var layoutWorldWidth = 0f
    private var layoutWorldHeight = 0f
    private var layoutStatus: MatchStatus? = null
    private var layoutGuestJoinUrl: String? = null
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
    private val animatedCardLefts = mutableMapOf<String, Float>()
    private val animatedDoubtCardLefts = mutableMapOf<String, Float>()
    private var animatedPendingCardLeft: Float? = null
    private var animatedDoubtPendingCardLeft: Float? = null
    private val confettiParticles = mutableListOf<ConfettiParticle>()
    private var celebratedResolutionCardId: String? = null
    private var inactiveTurnFilterAlpha = 0f
    private var overlayAnimationSeconds = 0f
    private var coinPanelOpen = false
    private var draggingLobbyPlayerId: PlayerId? = null
    private var lobbyReorderTargetIndex: Int? = null
    private val lobbyDragPosition = Vector2()
    private val lobbyDragOffset = Vector2()
    private val animatedLobbyBadgeRects = mutableMapOf<PlayerId, Rectangle>()

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
        glassRenderer.load()
        lobbyBackgroundImage.load()
        matchBackgroundImage.load()
        Gdx.input.inputProcessor = MatchInputController()
        updateLayout()
    }

    override fun render(delta: Float) {
        overlayAnimationSeconds += delta
        updateLayout()
        updateLobbyBadgeAnimations(delta)
        updateTimelineVisuals(delta)
        updateCelebration(delta)
        updateInactiveTurnFilter(delta)
        viewport.apply()
        Gdx.gl.glClearColor(0.02f, 0.04f, 0.10f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        shapeRenderer.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        if (presenter.state.status == MatchStatus.LOBBY) {
            batch.begin()
            lobbyBackgroundImage.draw(batch, layoutWorldWidth, layoutWorldHeight)
            batch.end()
        } else {
            batch.begin()
            matchBackgroundImage.draw(batch, layoutWorldWidth, layoutWorldHeight, color(0xFFF8F2F2))
            batch.end()
        }

        beginFilledShapes()
        drawBackground()
        when (presenter.state.status) {
            MatchStatus.LOBBY -> drawLobby()
            MatchStatus.ACTIVE,
            MatchStatus.COMPLETE,
            -> drawMatch(includeOverlay = false)
        }
        endFilledShapes()

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
            beginFilledShapes()
            drawTimelineCards(includeOverlay = true)
            endFilledShapes()

            batch.begin()
            drawTimelineCardText(includeOverlay = true)
            batch.end()
        }

        if (confettiParticles.isNotEmpty()) {
            beginFilledShapes()
            drawConfetti()
            endFilledShapes()
        }

        if (inactiveTurnFilterAlpha > 0.01f) {
            batch.begin()
            drawInactiveTurnFilter(inactiveTurnFilterAlpha)
            batch.end()
        }

        if (presenter.state.status != MatchStatus.LOBBY) {
            if (coinPanelOpen) {
                beginFilledShapes()
                drawModalShapes()
                endFilledShapes()

                batch.begin()
                drawModalTextures()
                drawModalText()
                batch.end()
            }

            beginFilledShapes()
            drawFloatingControlsShapes()
            endFilledShapes()

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
        glassRenderer.dispose()
        lobbyBackgroundImage.dispose()
        matchBackgroundImage.dispose()
    }

    private fun updateLayout() {
        val worldWidth = viewport.worldWidth
        val worldHeight = viewport.worldHeight
        val status = presenter.state.status
        val guestJoinUrl = presenter.guestJoinUrl
        if (
            worldWidth == layoutWorldWidth &&
            worldHeight == layoutWorldHeight &&
            status == layoutStatus &&
            guestJoinUrl == layoutGuestJoinUrl
        ) {
            return
        }

        layoutWorldWidth = worldWidth
        layoutWorldHeight = worldHeight
        layoutStatus = status
        layoutGuestJoinUrl = guestJoinUrl

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
        val actionButtonWidth = clamp(heroRect.width * 0.14f, 176f, 216f)
        val actionButtonHeight = clamp(heroRect.height * 0.56f, 50f, 62f)
        actionButtonRect.set(
            heroRect.x + heroRect.width - panelPadding - actionButtonWidth,
            heroRect.y + (heroRect.height - actionButtonHeight) / 2f,
            actionButtonWidth,
            actionButtonHeight,
        )
        val playbackButtonWidth = clamp(heroRect.width * 0.11f, 138f, 166f)
        val playbackButtonHeight = clamp(heroRect.height * 0.56f, 50f, 62f)
        playbackButtonRect.set(
            actionButtonRect.x - panelGap - playbackButtonWidth,
            heroRect.y + (heroRect.height - playbackButtonHeight) / 2f,
            playbackButtonWidth,
            playbackButtonHeight,
        )

        val mainHeight = heroRect.y - outerMargin - panelGap
        timelinePanelRect.set(
            outerMargin,
            outerMargin,
            worldWidth - outerMargin * 2f,
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
        val headerButtonWidth = clamp(timelineHeaderRect.width * 0.16f, 152f, 206f)
        val headerButtonHeight = clamp(timelineHeaderRect.height * 0.62f, 50f, 62f)
        redrawButtonRect.set(
            timelineHeaderRect.x + panelPadding,
            timelineHeaderRect.y + (timelineHeaderRect.height - headerButtonHeight) / 2f,
            headerButtonWidth,
            headerButtonHeight,
        )

        val coinsButtonWidth = clamp(timelinePanelRect.width * 0.11f, 124f, 176f)
        val coinsButtonHeight = clamp(timelinePanelRect.height * 0.09f, 56f, 68f)
        val overlayControlLeft = timelinePanelRect.x + panelPadding
        val overlayControlBottom = timelinePanelRect.y + panelPadding
        hostCoinsButtonRect.set(
            overlayControlLeft,
            overlayControlBottom,
            coinsButtonWidth,
            coinsButtonHeight,
        )
        val doubtButtonWidth = clamp(timelinePanelRect.width * 0.20f, 192f, 254f)
        val doubtButtonHeight = clamp(timelinePanelRect.height * 0.16f, 92f, 118f)
        val doubtButtonBottom = if (showCoinsShortcutButton()) {
            hostCoinsButtonRect.y + hostCoinsButtonRect.height + panelGap * 0.52f
        } else {
            overlayControlBottom
        }
        doubtButtonRect.set(
            overlayControlLeft,
            doubtButtonBottom,
            doubtButtonWidth,
            doubtButtonHeight,
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

        val doubtPopupWidth = clamp(timelinePanelRect.width * 0.97f, 840f, 1320f)
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
        val doubtTrackInsetX = panelPadding * 0.30f
        val doubtTrackInsetBottom = panelPadding * 0.82f
        val doubtTrackInsetTop = panelPadding * 0.42f
        doubtPopupTrackRect.set(
            doubtPopupRect.x + doubtTrackInsetX,
            doubtPopupRect.y + doubtTrackInsetBottom,
            doubtPopupRect.width - doubtTrackInsetX * 2f,
            doubtPopupRect.height - panelHeaderHeight - doubtTrackInsetBottom - doubtTrackInsetTop,
        )

        val lobbyButtonWidth = clamp(worldWidth * 0.27f, 420f, 580f)
        val lobbyButtonHeight = clamp(worldHeight * 0.14f, 116f, 144f)
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
        if (showLobbyJoinPanel()) {
            val joinPanelWidth = clamp(lobbyCardRect.width * 0.235f, 256f, 330f)
            val joinPanelHeight = clamp(lobbyCardRect.height * 0.80f, 332f, 432f)
            lobbyJoinPanelRect.set(
                lobbyCardRect.x + lobbyCardRect.width - joinPanelWidth,
                lobbyCardRect.y + (lobbyCardRect.height - joinPanelHeight) / 2f,
                joinPanelWidth,
                joinPanelHeight,
            )
            lobbyMainRect.set(
                lobbyCardRect.x,
                lobbyCardRect.y,
                max(1f, lobbyJoinPanelRect.x - lobbyCardRect.x - panelGap),
                lobbyCardRect.height,
            )

            val qrInset = clamp(panelPadding * 0.80f, 18f, 28f)
            val qrSize = min(
                lobbyJoinPanelRect.width - qrInset * 2f,
                lobbyJoinPanelRect.height - panelHeaderHeight - qrInset * 2f - 64f,
            )
            lobbyQrRect.set(
                lobbyJoinPanelRect.x + (lobbyJoinPanelRect.width - qrSize) / 2f,
                lobbyJoinPanelRect.y + panelPadding + 48f,
                qrSize,
                qrSize,
            )
            lobbyJoinUrlRect.set(
                lobbyJoinPanelRect.x + qrInset,
                lobbyJoinPanelRect.y + qrInset,
                lobbyJoinPanelRect.width - qrInset * 2f,
                max(1f, lobbyQrRect.y - lobbyJoinPanelRect.y - qrInset - 12f),
            )
        } else {
            lobbyMainRect.set(lobbyCardRect)
            lobbyJoinPanelRect.set(0f, 0f, 0f, 0f)
            lobbyQrRect.set(0f, 0f, 0f, 0f)
            lobbyJoinUrlRect.set(0f, 0f, 0f, 0f)
        }

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

        cardHeight = clamp(timelineTrackRect.height * 0.88f, 244f, 322f)
        fontScaleMultiplier = clamp(worldHeight / 960f, 0.98f, 1.08f)
        minimumTextScale = 0.88f
        shadowOffset = clamp(worldHeight * 0.0011f, 1f, 1.6f)
    }

    private fun updateTimelineVisuals(delta: Float) {
        timelineCardVisuals.clear()
        doubtTimelineCardVisuals.clear()
        pendingCardVisual = null
        doubtPendingCardVisual = null

        if (isLocalDoubtPlacementPhase()) {
            coinPanelOpen = false
        }

        val player = displayedTimelinePlayer()
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
            return
        }

        val localDoubtPlacement = isLocalDoubtPlacementPhase()
        val arrangement = timelineLayout.pendingArrangement(
            existingCardCount = player.timeline.cards.size,
            pendingSlotIndex = if (localDoubtPlacement) {
                presenter.state.doubt?.proposedSlotIndex ?: pendingCard.proposedSlotIndex
            } else {
                pendingCard.proposedSlotIndex
            },
        )
        val pendingLeftTarget = when {
            localDoubtPlacement && draggingDoubtCard -> {
                clamp(worldTouch.x - doubtPendingCardGrabOffsetX, timelineCardsX, timelineCardsX + timelineCardsWidth - arrangement.cardWidth)
            }

            draggingPendingCard -> {
                clamp(worldTouch.x - pendingCardGrabOffsetX, timelineCardsX, timelineCardsX + timelineCardsWidth - arrangement.cardWidth)
            }

            else -> arrangement.pendingCardLeft
        }
        val pendingLeft = if (draggingPendingCard || (localDoubtPlacement && draggingDoubtCard)) {
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

        val pendingTopColor = if (localDoubtPlacement) 0x7ED9FFFF else 0xF5B348FF
        val pendingBottomColor = if (localDoubtPlacement) 0x2D8FCAFF else 0xD48620FF
        val pendingEdgeColor = if (localDoubtPlacement) 0xDBF5FFFF else 0xFFF1D089
        pendingCardVisual = TimelineCardVisual(
            id = pendingCard.entry.id,
            rect = Rectangle(pendingLeft, cardBottom, arrangement.cardWidth, cardHeight),
            face = CardFace.Hidden,
            topColor = pendingTopColor,
            bottomColor = pendingBottomColor,
            edgeColor = pendingEdgeColor,
            primaryText = "?",
            secondaryText = if (localDoubtPlacement) "DOUBT" else "LISTEN",
        )
        pendingCardVisual?.let(timelineCardVisuals::add)
        animatedCardLefts.keys.retainAll(visibleCardIds)
    }

    private fun updateDoubtTimelineVisuals(delta: Float) {
        animatedDoubtCardLefts.clear()
        animatedDoubtPendingCardLeft = null
        doubtTimelineCardVisuals.clear()
        doubtPendingCardVisual = null
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
        return createUiFont(size = 56)
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
        if (presenter.state.status == MatchStatus.LOBBY) {
            return
        }
        fillGradientRect(0f, 0f, layoutWorldWidth, layoutWorldHeight, 0x04020312, 0x04020312, 0x14060524, 0x14060524)
        fillGradientRect(0f, 0f, layoutWorldWidth, layoutWorldHeight, 0x00000000, 0x00000000, 0x2A0E0714, 0x2A0E0714)
    }

    /**
     * ShapeRenderer does not guarantee alpha blending for filled passes, and this screen depends on
     * semi-transparent washes so the art-directed backgrounds remain visible behind the gameplay UI.
     */
    private fun beginFilledShapes() {
        UiShadowRenderer.beginFilled(shapeRenderer)
    }

    private fun endFilledShapes() {
        UiShadowRenderer.endFilled(shapeRenderer)
    }

    private fun drawAtmosphereTextures() {
        if (presenter.state.status == MatchStatus.LOBBY) {
            drawTexture(flatTexture, 0f, 0f, layoutWorldWidth, layoutWorldHeight, color(0xF6B25D06))
            drawTexture(vignetteTexture, 0f, 0f, layoutWorldWidth, layoutWorldHeight, color(0x00000042))
            drawTexture(vignetteTexture, 0f, 0f, layoutWorldWidth, layoutWorldHeight, color(0x4E140B18))
            return
        }
        val time = overlayAnimationSeconds
        val leftBloomX = -layoutWorldWidth * 0.16f + sin(time * 0.11f) * 118f
        val leftBloomY = layoutWorldHeight * 0.18f + cos(time * 0.13f) * 36f
        val centerBloomX = layoutWorldWidth * 0.20f + cos(time * 0.08f) * 142f
        val centerBloomY = layoutWorldHeight * 0.44f + sin(time * 0.10f) * 42f
        val rightBloomX = layoutWorldWidth * 0.58f + sin(time * 0.07f) * 152f
        val rightBloomY = layoutWorldHeight * 0.50f + cos(time * 0.09f) * 34f
        val topBloomX = layoutWorldWidth * 0.32f + cos(time * 0.05f) * 110f
        val topBloomY = layoutWorldHeight * 0.66f + sin(time * 0.06f) * 30f
        val emberX = layoutWorldWidth * 0.72f + cos(time * 0.06f) * 84f
        val emberY = -layoutWorldHeight * 0.08f + sin(time * 0.08f) * 44f

        drawGlow(leftBloomX, leftBloomY, layoutWorldWidth * 0.52f, layoutWorldHeight * 0.44f, color(0xD05A2718))
        drawGlow(centerBloomX, centerBloomY, layoutWorldWidth * 0.62f, layoutWorldHeight * 0.52f, color(0x78322414))
        drawGlow(rightBloomX, rightBloomY, layoutWorldWidth * 0.52f, layoutWorldHeight * 0.40f, color(0xF79A4018))
        drawGlow(topBloomX, topBloomY, layoutWorldWidth * 0.66f, layoutWorldHeight * 0.26f, color(0xFFD78D10))
        drawGlow(emberX, emberY, layoutWorldWidth * 0.40f, layoutWorldWidth * 0.40f, color(0xFFB05C0C))
        drawTexture(flatTexture, 0f, 0f, layoutWorldWidth, layoutWorldHeight, color(0xFFF0D706))
        drawTexture(vignetteTexture, 0f, 0f, layoutWorldWidth, layoutWorldHeight, color(0x00000054))
        drawTexture(vignetteTexture, 0f, 0f, layoutWorldWidth, layoutWorldHeight, color(0x34120D16))
    }

    private fun drawLobby() {
        if (showLobbyPrimaryButton()) {
            val isPairing = presenter.playbackSessionState == PlaybackSessionState.Connecting
            if (isPairing) {
                fillButton(startButtonRect, 0xC5886FFF, 0x934634FF, 0xFFDDBFA8)
            } else {
                fillButton(startButtonRect, 0xF5C067FF, 0xD37D29FF, 0xFFF0BF66)
            }
        }

        if (showLobbyJoinPanel()) {
            fillPanel(lobbyJoinPanelRect, 0x3B201FFF, 0x1B1117FF, 0x6E311FFF, 0x52241DFF, 0xFFD4A461)
            drawDropShadow(lobbyQrRect, 12f, 0x01050B38)
            fillGradientRect(
                lobbyQrRect.x - 10f,
                lobbyQrRect.y - 10f,
                lobbyQrRect.width + 20f,
                lobbyQrRect.height + 20f,
                0xE9EEF7FF,
                0xF1F5FCFF,
                0xFFFFFFFF,
                0xF6FAFFFF,
            )
            drawFrame(
                lobbyQrRect.x - 10f,
                lobbyQrRect.y - 10f,
                lobbyQrRect.width + 20f,
                lobbyQrRect.height + 20f,
                0xFFF2C85A,
                2f,
            )
        }

        lobbyBadgeVisuals().forEach(::drawLobbyBadgeShape)
        lobbyDraggedBadgeVisual()?.let(::drawLobbyBadgeShape)
    }

    private fun drawLobbyTextures() {
        if (showLobbyPrimaryButton()) {
            glassRenderer.draw(
                batch,
                startButtonRect,
                min(startButtonRect.height * 0.44f, 42f),
                START_BUTTON_GLASS_STYLE,
                overlayAnimationSeconds,
            )
            drawPanelTexture(startButtonRect, color(0xFFF3D21D))
            val time = overlayAnimationSeconds
            drawGlow(
                startButtonRect.x - startButtonRect.width * 0.14f + sin(time * 0.20f) * 14f,
                startButtonRect.y - startButtonRect.height * 0.32f,
                startButtonRect.width * 1.28f,
                startButtonRect.height * 1.58f,
                color(0xF9C06D24),
            )
        }
        if (showLobbyJoinPanel()) {
            val time = overlayAnimationSeconds
            glassRenderer.draw(
                batch,
                lobbyJoinPanelRect,
                40f,
                LOBBY_PANEL_GLASS_STYLE,
                overlayAnimationSeconds,
            )
            drawPanelTexture(lobbyJoinPanelRect, color(0xFFD8B80F))
            drawGlow(
                lobbyJoinPanelRect.x - lobbyJoinPanelRect.width * 0.14f + cos(time * 0.13f) * 10f,
                lobbyJoinPanelRect.y + lobbyJoinPanelRect.height * 0.18f + sin(time * 0.11f) * 10f,
                lobbyJoinPanelRect.width * 1.18f,
                lobbyJoinPanelRect.height * 0.58f,
                color(0xFFBA7814),
            )
            drawRepeatedTexture(
                grainTexture,
                lobbyJoinPanelRect.x + 2f,
                lobbyJoinPanelRect.y + 2f,
                lobbyJoinPanelRect.width - 4f,
                lobbyJoinPanelRect.height - 4f,
                color(0xFFF2DB0B),
                max(1f, lobbyJoinPanelRect.width / 100f),
                max(1f, lobbyJoinPanelRect.height / 100f),
                time * 0.004f,
                time * 0.003f,
            )
            presenter.guestJoinQrTexture?.let { qrTexture ->
                drawTexture(qrTexture, lobbyQrRect.x, lobbyQrRect.y, lobbyQrRect.width, lobbyQrRect.height, Color.WHITE)
            }
        }
        lobbyBadgeVisuals().forEach(::drawLobbyBadgeTexture)
        lobbyDraggedBadgeVisual()?.let(::drawLobbyBadgeTexture)
    }

    private fun drawLobbyText() {
        val badges = lobbyBadgeVisuals()
        val badgeColumn = lobbyBadgeColumnRect()
        drawTextBlock(
            text = "${presenter.state.players.size} PLAYERS",
            x = badgeColumn.x,
            y = badgeColumn.y + badgeColumn.height - 54f,
            width = badgeColumn.width,
            height = 42f,
            scale = 0.74f,
            color = color(0xFFDFA15B),
            align = Align.left,
            verticalAlign = VerticalTextAlign.Center,
        )
        badges.forEach(::drawLobbyBadgeText)
        lobbyDraggedBadgeVisual()?.let(::drawLobbyBadgeText)
        if (showLobbyJoinPanel()) {
            drawTextBlock(
                text = "SCAN TO JOIN",
                x = lobbyJoinPanelRect.x,
                y = lobbyJoinPanelRect.y + lobbyJoinPanelRect.height - panelHeaderHeight,
                width = lobbyJoinPanelRect.width,
                height = panelHeaderHeight,
                scale = 0.72f,
                color = color(0xFFDFA15B),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
            )
            drawTextBlock(
                text = displayLobbyJoinUrl(),
                x = lobbyJoinUrlRect.x,
                y = lobbyJoinUrlRect.y,
                width = lobbyJoinUrlRect.width,
                height = lobbyJoinUrlRect.height,
                scale = 0.52f,
                color = color(0xFFF0E4D7),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
                wrap = true,
                enforceMinimumScale = false,
            )
        }

        if (showLobbyPrimaryButton()) {
            drawTextBlock(
                text = lobbyPrimaryActionText(),
                x = startButtonRect.x,
                y = startButtonRect.y,
                width = startButtonRect.width,
                height = startButtonRect.height,
                scale = 1.00f,
                color = color(0xFFF7EDE2),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
                shadowColor = color(0x4A160FA4),
            )
        } else {
            drawTextBlock(
                text = lobbyWaitingText(),
                x = startButtonRect.x,
                y = startButtonRect.y,
                width = startButtonRect.width,
                height = startButtonRect.height,
                scale = 0.88f,
                color = color(0xFFF0E4D7),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
                shadowColor = color(0x35160F8A),
            )
        }
    }

    private fun drawLobbyBadgeShape(visual: LobbyBadgeVisual) {
        visual.editRect?.let(::drawLobbyEditIcon)
    }

    private fun drawLobbyBadgeTexture(visual: LobbyBadgeVisual) {
        glassRenderer.draw(
            batch,
            visual.rect,
            min(visual.rect.height * 0.48f, 34f),
            if (visual.isDragged) DRAGGED_BADGE_GLASS_STYLE else BADGE_GLASS_STYLE,
            overlayAnimationSeconds,
            pressed = if (visual.isDragged) 0.30f else 0f,
        )
        drawPanelTexture(visual.rect, color(if (visual.isDragged) 0xFFE4C518 else 0xFFD8B50F))
    }

    private fun drawLobbyBadgeText(visual: LobbyBadgeVisual) {
        val leftInset = 22f
        val rightInset = 22f + (visual.editRect?.width ?: 0f) + if (visual.editRect != null) 14f else 0f
        drawTextBlock(
            text = visual.player.displayName,
            x = visual.rect.x + leftInset,
            y = visual.rect.y,
            width = visual.rect.width - leftInset - rightInset,
            height = visual.rect.height,
            scale = visual.textScale,
            color = color(0xFFF2E6D7),
            align = Align.left,
            verticalAlign = VerticalTextAlign.Center,
            shadowColor = color(0x35150EA0),
            enforceMinimumScale = false,
        )
    }

    private fun drawLobbyEditIcon(rect: Rectangle) {
        val startX = rect.x + rect.width * 0.18f
        val startY = rect.y + rect.height * 0.20f
        val endX = rect.x + rect.width * 0.82f
        val endY = rect.y + rect.height * 0.80f
        shapeRenderer.color = color(0x3B1E067A)
        shapeRenderer.rectLine(startX + 1.6f, startY - 1.6f, endX + 1.6f, endY - 1.6f, 5.6f)
        shapeRenderer.color = color(0xFFEAB7FF)
        shapeRenderer.rectLine(startX, startY, endX, endY, 5.0f)
        shapeRenderer.color = color(0x7E460FFF)
        shapeRenderer.triangle(
            endX - 2.4f,
            endY + 2.4f,
            endX + 6.2f,
            endY - 6.2f,
            endX + 1.2f,
            endY - 1.2f,
        )
        shapeRenderer.color = color(0xFFF5DE9AFF)
        shapeRenderer.rectLine(startX - 1.2f, startY - 1.2f, startX + 8f, startY + 8f, 2.6f)
    }

    private fun lobbyBadgeVisuals(): List<LobbyBadgeVisual> {
        val orderedPlayers = lobbyDisplayedPlayers(includeDraggedPlayer = false)
        if (orderedPlayers.isEmpty()) {
            return emptyList()
        }
        return buildLobbyBadgeVisuals(orderedPlayers)
    }

    private fun lobbyDraggedBadgeVisual(): LobbyBadgeVisual? {
        val draggedPlayerId = draggingLobbyPlayerId ?: return null
        val player = presenter.state.requirePlayer(draggedPlayerId) ?: return null
        val baseVisual = buildLobbyBadgeVisuals(listOf(player)).firstOrNull() ?: return null
        val columnRect = lobbyBadgeColumnRect()
        val rect = Rectangle(
            clamp(
                lobbyDragPosition.x - lobbyDragOffset.x,
                columnRect.x,
                columnRect.x + columnRect.width - baseVisual.rect.width,
            ),
            clamp(
                lobbyDragPosition.y - lobbyDragOffset.y,
                columnRect.y,
                columnRect.y + columnRect.height - baseVisual.rect.height,
            ),
            baseVisual.rect.width,
            baseVisual.rect.height,
        )
        val editRect = if (player.id == presenter.localPlayerId) {
            Rectangle(
                rect.x + rect.width - 38f,
                rect.y + (rect.height - 24f) / 2f,
                24f,
                24f,
            )
        } else {
            null
        }
        return baseVisual.copy(rect = rect, editRect = editRect, isDragged = true)
    }

    private fun buildLobbyBadgeVisuals(players: List<PlayerState>): List<LobbyBadgeVisual> {
        val targetVisuals = buildLobbyBadgeTargetVisuals(players)
        return targetVisuals.map { target ->
            val animatedRect = animatedLobbyBadgeRects[target.player.id]
            if (animatedRect == null) {
                target
            } else {
                val rect = Rectangle(animatedRect)
                val editRect = target.editRect?.let {
                    Rectangle(
                        rect.x + rect.width - 46f,
                        rect.y + (rect.height - 32f) / 2f,
                        32f,
                        32f,
                    )
                }
                target.copy(rect = rect, editRect = editRect)
            }
        }
    }

    private fun buildLobbyBadgeTargetVisuals(players: List<PlayerState>): List<LobbyBadgeVisual> {
        if (players.isEmpty()) {
            return emptyList()
        }
        val columnRect = lobbyBadgeColumnRect()
        val rowGap = clamp(panelGap * 0.64f, 12f, 18f)
        val topReserve = 86f
        val availableHeight = max(1f, columnRect.height - topReserve)
        val badgeHeight = clamp(
            (availableHeight - rowGap * max(0, players.size - 1)) / players.size,
            54f,
            76f,
        )
        val maxBadgeWidth = max(220f, columnRect.width)

        data class LobbyBadgeSpec(
            val player: PlayerState,
            val width: Float,
            val textScale: Float,
        )

        val specs = players.map { player ->
            val textScale = when {
                player.displayName.length >= 22 -> 0.66f
                player.displayName.length >= 18 -> 0.72f
                else -> 0.80f
            }
            val editReserve = if (player.id == presenter.localPlayerId) 52f else 0f
            val measuredWidth = measureTextWidth(player.displayName, textScale)
            val width = clamp(measuredWidth + 58f + editReserve, 170f, maxBadgeWidth)
            LobbyBadgeSpec(player = player, width = width, textScale = textScale)
        }

        val visuals = ArrayList<LobbyBadgeVisual>(players.size)
        val availableTop = columnRect.y + columnRect.height - topReserve
        var currentY = availableTop - badgeHeight
        specs.forEach { spec ->
            val rect = Rectangle(columnRect.x, currentY, spec.width, badgeHeight)
            val editRect = if (spec.player.id == presenter.localPlayerId) {
                Rectangle(
                    rect.x + rect.width - 46f,
                    rect.y + (rect.height - 32f) / 2f,
                    32f,
                    32f,
                )
            } else {
                null
            }
            visuals += LobbyBadgeVisual(
                player = spec.player,
                rect = rect,
                editRect = editRect,
                textScale = spec.textScale,
                isDragged = false,
            )
            currentY -= badgeHeight + rowGap
        }
        return visuals
    }

    private fun updateLobbyBadgeAnimations(delta: Float) {
        if (presenter.state.status != MatchStatus.LOBBY) {
            animatedLobbyBadgeRects.clear()
            return
        }
        val targets = buildLobbyBadgeTargetVisuals(lobbyDisplayedPlayers(includeDraggedPlayer = false))
        val targetIds = targets.map { it.player.id }.toSet()
        animatedLobbyBadgeRects.keys.retainAll(targetIds)
        val smoothing = clamp(delta * 14f, 0f, 1f)
        targets.forEach { target ->
            val current = animatedLobbyBadgeRects[target.player.id]
            if (current == null) {
                animatedLobbyBadgeRects[target.player.id] = Rectangle(target.rect)
            } else {
                current.x += (target.rect.x - current.x) * smoothing
                current.y += (target.rect.y - current.y) * smoothing
                current.width += (target.rect.width - current.width) * smoothing
                current.height += (target.rect.height - current.height) * smoothing
            }
        }
    }

    private fun lobbyDisplayedPlayers(includeDraggedPlayer: Boolean): List<PlayerState> {
        val draggedPlayerId = draggingLobbyPlayerId
        val targetIndex = lobbyReorderTargetIndex
        val previewPlayers = if (presenter.isLocalHost && draggedPlayerId != null && targetIndex != null) {
            presenter.state.players.moveLobbyPlayer(draggedPlayerId, targetIndex)
        } else {
            presenter.state.players
        }
        return if (includeDraggedPlayer) previewPlayers else previewPlayers.filterNot { it.id == draggedPlayerId }
    }

    private fun requestLobbyDisplayNameEdit() {
        val currentName = presenter.localPlayer?.displayName ?: return
        requestDisplayNameInput(currentName) { submittedName ->
            val sanitized = submittedName?.let(UiBootstrapper::sanitizeDisplayName).orEmpty()
            if (sanitized.isBlank() || sanitized == currentName) {
                return@requestDisplayNameInput
            }
            presenter.updateLocalDisplayName(sanitized)
            onLocalDisplayNameEdited(sanitized)
        }
    }

    private fun measureTextWidth(text: String, scale: Float): Float {
        font.data.setScale(scale * fontScaleMultiplier)
        textLayout.setText(font, text)
        return textLayout.width
    }

    private fun lobbyReorderIndexFor(x: Float, y: Float): Int {
        val draggedPlayerId = draggingLobbyPlayerId ?: return 0
        val currentPlayers = presenter.state.players
        if (currentPlayers.size <= 1) {
            return 0
        }
        val visuals = buildLobbyBadgeVisuals(currentPlayers)
            .filterNot { it.player.id == draggedPlayerId }
        if (visuals.isEmpty()) {
            return 0
        }
        val nearestVisual = visuals.minByOrNull { visual ->
            val centerY = visual.rect.y + visual.rect.height / 2f
            val dy = centerY - y
            dy * dy
        } ?: return 0
        val nearestIndex = currentPlayers.indexOfFirst { it.id == nearestVisual.player.id }.coerceAtLeast(0)
        val centerY = nearestVisual.rect.y + nearestVisual.rect.height / 2f
        return if (y < centerY) {
            min(nearestIndex + 1, currentPlayers.lastIndex)
        } else {
            nearestIndex
        }
    }

    private fun lobbyBadgeColumnRect(): Rectangle {
        val insetX = clamp(panelPadding * 0.9f, 22f, 34f)
        val insetY = clamp(panelPadding * 0.8f, 20f, 30f)
        val columnWidth = clamp(
            lobbyMainRect.width * if (showLobbyJoinPanel()) 0.44f else 0.36f,
            260f,
            440f,
        )
        return Rectangle(
            lobbyMainRect.x + insetX,
            lobbyMainRect.y + insetY,
            min(columnWidth, lobbyMainRect.width - insetX * 2f),
            max(1f, lobbyMainRect.height - insetY * 2f),
        )
    }

    private fun drawMatch(includeOverlay: Boolean) {
        fillHero(heroRect)
        if (showPlaybackToggleButton()) {
            if (isPlaybackPaused()) {
                fillButton(playbackButtonRect, 0xF5CB77FF, 0xD98B35FF, 0xFFF1C58D)
            } else {
                fillButton(playbackButtonRect, 0xCE7D63FF, 0xA3472CFF, 0xFFD8B39A)
            }
        }
        if (showActionButton()) {
            fillButton(actionButtonRect, 0xF5CB77FF, 0xD98B35FF, 0xFFF1C58D)
        }
        if (headerDrawButtonLabel() != null) {
            fillButton(redrawButtonRect, 0xF4C870FF, 0xD47F20FF, 0xFFF0C38D)
        }
        fillPanel(timelinePanelRect, 0x40221BFF, 0x1C0F13FF, 0x7E3A23FF, 0x5B221DFF, 0xFFD5A55C)
        if (showActionWell()) {
            fillActionWell()
        }

        drawTimelineCards(includeOverlay)
        drawTargetDoubtArrow()
    }

    private fun drawMatchTextures() {
        glassRenderer.draw(batch, heroRect, min(heroRect.height * 0.46f, 38f), HERO_GLASS_STYLE, overlayAnimationSeconds)
        drawPanelTexture(heroRect, color(0xFFE3BE12))
        if (showPlaybackToggleButton()) {
            glassRenderer.draw(
                batch,
                playbackButtonRect,
                min(playbackButtonRect.height * 0.44f, 40f),
                if (isPlaybackPaused()) PRIMARY_BUTTON_GLASS_STYLE else SECONDARY_BUTTON_GLASS_STYLE,
                overlayAnimationSeconds,
                pressed = if (isPlaybackPaused()) 0f else 0.18f,
            )
            drawPanelTexture(
                playbackButtonRect,
                if (isPlaybackPaused()) color(0xFFF0CE18) else color(0xFFD6A21A),
            )
        }
        if (showActionButton()) {
            glassRenderer.draw(
                batch,
                actionButtonRect,
                min(actionButtonRect.height * 0.44f, 40f),
                PRIMARY_BUTTON_GLASS_STYLE,
                overlayAnimationSeconds,
            )
            drawPanelTexture(actionButtonRect, color(0xFFF0CE18))
        }
        if (headerDrawButtonLabel() != null) {
            glassRenderer.draw(
                batch,
                redrawButtonRect,
                min(redrawButtonRect.height * 0.44f, 40f),
                PRIMARY_BUTTON_GLASS_STYLE,
                overlayAnimationSeconds,
            )
            drawPanelTexture(redrawButtonRect, color(0xFFF2D017))
        }
        glassRenderer.draw(batch, timelinePanelRect, 40f, TIMELINE_GLASS_STYLE, overlayAnimationSeconds)
        drawPanelTexture(timelinePanelRect, color(0xFFD8B80D))
        if (showActionWell()) {
            drawActionWellTexture()
        }
        drawRepeatedTexture(
            grainTexture,
            timelinePanelRect.x + 2f,
            timelinePanelRect.y + 2f,
            timelinePanelRect.width - 4f,
            timelinePanelRect.height - panelHeaderHeight - 4f,
            color(0xFFE4C30A),
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
        val player = displayedTimelinePlayer()
        val toolbarStatus = toolbarStatusText()
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
        toolbarStatus?.let { text ->
            val messageX = heroRect.x + panelPadding + playerWidth + panelGap
            val messageRight = if (showPlaybackToggleButton()) {
                playbackButtonRect.x - panelGap
            } else if (showActionButton()) {
                actionButtonRect.x - panelGap
            } else {
                heroRect.x + heroRect.width - panelPadding
            }
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
                shadowColor = color(0x35160F92),
                enforceMinimumScale = false,
            )
        }
        if (showPlaybackToggleButton()) {
            drawTextBlock(
                text = playbackToggleLabel(),
                x = playbackButtonRect.x,
                y = playbackButtonRect.y,
                width = playbackButtonRect.width,
                height = playbackButtonRect.height,
                scale = 0.64f,
                color = color(0xFFF8EEE2),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
                shadowColor = color(0x45160FA2),
            )
        }
        if (showActionButton()) {
            drawTextBlock(
                text = actionButtonLabel(),
                x = actionButtonRect.x,
                y = actionButtonRect.y,
                width = actionButtonRect.width,
                height = actionButtonRect.height,
                scale = 0.62f,
                color = color(0xFFF8EEE2),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
                shadowColor = color(0x45160FA2),
            )
        }

        headerDrawButtonLabel()?.let { label ->
            drawTextBlock(
                text = label,
                x = redrawButtonRect.x,
                y = redrawButtonRect.y,
                width = redrawButtonRect.width,
                height = redrawButtonRect.height,
                scale = 0.60f,
                color = color(0xFFF8EEE2),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
                shadowColor = color(0x45160FA2),
            )
        }
        drawTextBlock(
            text = "Score ${player?.score ?: 0}  Coins ${player?.coins ?: 0}",
            x = timelineHeaderRect.x + timelineHeaderRect.width - 332f,
            y = timelineHeaderRect.y,
            width = 296f,
            height = timelineHeaderRect.height,
            scale = 0.86f,
            color = color(0xFFD88C4EFF),
            align = Align.right,
            verticalAlign = VerticalTextAlign.Center,
        )

        if (player?.timeline?.cards.isNullOrEmpty() && player?.pendingCard == null && toolbarStatus == null) {
            drawTextBlock(
                text = if (canDraw()) "Tap DRAW to start." else "Waiting for draw.",
                x = timelineTrackRect.x,
                y = timelineTrackRect.y,
                width = timelineTrackRect.width,
                height = timelineTrackRect.height,
                scale = 1.06f,
                color = color(0xFFF1E7DB),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
            )
        }

        drawTimelineCardText(includeOverlay)
    }

    private fun drawModalShapes() {
        fillRect(0f, 0f, layoutWorldWidth, layoutWorldHeight, 0x03060CB2)
        if (coinPanelOpen) {
            drawCoinPanelShapes()
        }
    }

    private fun drawModalTextures() {
        if (coinPanelOpen) {
            drawCoinPanelTextures()
        }
    }

    private fun drawModalText() {
        if (coinPanelOpen) {
            drawCoinPanelText()
        }
    }

    private fun drawFloatingControlsShapes() {
        if (showCoinsShortcutButton()) {
            fillButton(hostCoinsButtonRect, 0xF2C468FF, 0xCE7E24FF, 0xFFF0C48D)
        }
        if (showDoubtToggleButton()) {
            fillDoubtToggleButton(isDoubtToggleActive())
        }
    }

    private fun drawFloatingControlsTextures() {
        if (showDoubtToggleButton()) {
            drawDoubtButtonGlow(isDoubtToggleActive())
            glassRenderer.draw(
                batch,
                doubtButtonRect,
                min(doubtButtonRect.height * 0.44f, 38f),
                if (isDoubtToggleActive()) ACTIVE_DOUBT_GLASS_STYLE else IDLE_DOUBT_GLASS_STYLE,
                overlayAnimationSeconds,
                pressed = if (isDoubtToggleActive()) 0.28f else 0f,
            )
            drawPanelTexture(
                doubtButtonRect,
                if (isDoubtToggleActive()) color(0xFFD2A61A) else color(0xFFECC47014),
            )
        }
        if (showCoinsShortcutButton()) {
            glassRenderer.draw(
                batch,
                hostCoinsButtonRect,
                min(hostCoinsButtonRect.height * 0.44f, 32f),
                PRIMARY_BUTTON_GLASS_STYLE,
                overlayAnimationSeconds,
            )
            drawPanelTexture(hostCoinsButtonRect, color(0xFFE7B513))
        }
    }

    private fun drawFloatingControlsText() {
        if (showDoubtToggleButton()) {
            val isActive = isDoubtToggleActive()
            val resolvedLabelColor = if (isActive) color(0xFFF9F0E5) else color(0xFFF7ECDD)
            val countdown = if (presenter.state.turn?.phase == TurnPhase.AWAITING_DOUBT_WINDOW) {
                doubtWindowCountdownSecondsRemaining()
            } else {
                null
            }
            val fittedLabel = fitSingleLineText(
                text = when {
                    isActive -> "DOUBTING"
                    countdown != null -> "DOUBT $countdown"
                    else -> "DOUBT"
                },
                color = resolvedLabelColor,
                maxWidth = doubtButtonRect.width - 28f,
                preferredScale = 0.92f,
                minimumScale = 0.70f,
            )
            drawTextBlock(
                text = fittedLabel.text,
                x = doubtButtonRect.x + 8f,
                y = doubtButtonRect.y + doubtButtonRect.height * 0.08f,
                width = doubtButtonRect.width - 16f,
                height = doubtButtonRect.height * 0.84f,
                scale = fittedLabel.scale,
                color = resolvedLabelColor,
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
                shadowColor = color(0x47160EA4),
                enforceMinimumScale = false,
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
                color = color(0xFFF8EEE2),
                align = Align.center,
                verticalAlign = VerticalTextAlign.Center,
                shadowColor = color(0x45160FA2),
            )
        }
    }

    private fun drawDoubtPopupShapes() {
        fillPanel(doubtPopupRect, 0x113255FF, 0x0B2139FF, 0x4AA5D8FF, 0x3687BBFF, 0xC9F5FF48)
        fillTrack(doubtPopupTrackRect)
        drawDoubtPopupCards(includeOverlay = false)
    }

    private fun drawDoubtPopupTextures() {
        glassRenderer.draw(batch, doubtPopupRect, 40f, DOUBT_POPUP_GLASS_STYLE, overlayAnimationSeconds)
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
        drawDoubtPopupCardText(includeOverlay = false)
    }

    private fun drawCoinPanelShapes() {
        fillPanel(coinPanelRect, 0x3E1F1DFF, 0x1E1116FF, 0xA55D35FF, 0x6D2E24FF, 0xFFD29F66)
        fillButton(coinPanelCloseRect, 0xE08E79FF, 0xB54C38FF, 0xFFDAB2A5)
        coinPanelRows().forEach { row ->
            fillButton(row.minusRect, 0xF0B25CFF, 0xC97C1DFF, 0xFFE6BF82)
            fillButton(row.plusRect, 0xE69C76FF, 0xB85636FF, 0xFFDAB9A6)
        }
    }

    private fun drawCoinPanelTextures() {
        glassRenderer.draw(batch, coinPanelRect, 40f, COIN_PANEL_GLASS_STYLE, overlayAnimationSeconds)
        glassRenderer.draw(
            batch,
            coinPanelCloseRect,
            min(coinPanelCloseRect.height * 0.44f, 32f),
            CLOSE_BUTTON_GLASS_STYLE,
            overlayAnimationSeconds,
            pressed = 0.1f,
        )
        drawPanelTexture(coinPanelRect, color(0xFFD7B70E))
        coinPanelRows().forEach { row ->
            glassRenderer.draw(
                batch,
                row.rowRect,
                min(row.rowRect.height * 0.36f, 28f),
                COIN_ROW_GLASS_STYLE,
                overlayAnimationSeconds,
            )
            glassRenderer.draw(
                batch,
                row.minusRect,
                min(row.minusRect.height * 0.44f, 26f),
                PRIMARY_BUTTON_GLASS_STYLE,
                overlayAnimationSeconds,
            )
            glassRenderer.draw(
                batch,
                row.plusRect,
                min(row.plusRect.height * 0.44f, 26f),
                SECONDARY_BUTTON_GLASS_STYLE,
                overlayAnimationSeconds,
            )
            drawPanelTexture(row.rowRect, color(0xFFD6B30A))
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
            color = color(0xFFF2E6D7),
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
                color = color(0xFFF0E4D7),
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
                color = color(0x2A1209FF),
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

        drawTexture(flatTexture, 0f, 0f, layoutWorldWidth, layoutWorldHeight, colorWithAlpha(0xE7D8C8FF, alpha * 0.26f))
        drawTexture(flatTexture, 0f, 0f, layoutWorldWidth, layoutWorldHeight, colorWithAlpha(0x8F6558FF, alpha * 0.22f))
        drawTexture(flatTexture, 0f, 0f, layoutWorldWidth, layoutWorldHeight, colorWithAlpha(0x2D1B1FFF, alpha * 0.14f))
        drawGlow(
            accentGlowX,
            accentGlowY,
            accentGlowWidth,
            accentGlowHeight,
            colorWithAlpha(0xF5D8BEF0, alpha * 0.10f),
        )
        drawGlow(
            sweepGlowX,
            sweepGlowY,
            sweepGlowWidth,
            sweepGlowHeight,
            colorWithAlpha(0xC98A64FF, alpha * 0.07f),
        )
        drawRepeatedTexture(
            grainTexture,
            0f,
            0f,
            layoutWorldWidth,
            layoutWorldHeight,
            colorWithAlpha(0xF4E3D0FF, alpha * 0.08f),
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
            colorWithAlpha(0xB68873FF, alpha * 0.05f),
            layoutWorldWidth / 58f,
            layoutWorldHeight / 58f,
            slowDriftX,
            slowDriftY,
        )
        drawTexture(vignetteTexture, 0f, 0f, layoutWorldWidth, layoutWorldHeight, colorWithAlpha(0x000000FF, alpha * 0.24f))
        drawTexture(vignetteTexture, 0f, 0f, layoutWorldWidth, layoutWorldHeight, colorWithAlpha(0x5A251DFF, alpha * 0.11f))
    }

    private fun fillHero(rect: Rectangle) {
    }

    private fun fillTrack(rect: Rectangle) {
    }

    private fun fillButton(rect: Rectangle, topColor: Long, bottomColor: Long, edgeColor: Long) {
    }

    private fun fillDoubtToggleButton(isActive: Boolean) {
    }

    private fun drawDoubtButtonGlow(isActive: Boolean) {
        val glowTint = if (isActive) 0xFFBD86FF else 0xFFE89DFF
        val expansion = if (isActive) 42f else 36f
        drawGlow(
            doubtButtonRect.x - expansion * 0.42f,
            doubtButtonRect.y - expansion * 0.36f,
            doubtButtonRect.width + expansion * 0.84f,
            doubtButtonRect.height + expansion * 0.72f,
            colorWithAlpha(glowTint, if (isActive) 0.16f else 0.12f),
        )
    }

    private fun fillCircularActionButton(rect: Rectangle, enabled: Boolean) {
        val radius = min(rect.width, rect.height) / 2f
        val centerX = rect.x + rect.width / 2f
        val centerY = rect.y + rect.height / 2f
        val outerShadow = if (enabled) 0x1D090A8CL else 0x180A086AL
        val bodyLower = if (enabled) 0xC66C32B4 else 0xA9854A90L
        val bodyUpper = if (enabled) 0xF2C06A64 else 0xD9B06C56L
        val coreColor = if (enabled) 0xF7B45C80 else 0xD5B37A6CL
        val highlightColor = if (enabled) 0xFFF8F0FF else 0xFFF5E8FFL
        val lowerShade = if (enabled) 0x3A140A30L else 0x28190D28L

        repeat(5) { layer ->
            val expansion = 5f + layer * 4.6f
            val alpha = 0.13f - layer * 0.02f
            shapeRenderer.color = colorWithAlpha(outerShadow, alpha)
            shapeRenderer.circle(centerX, centerY - radius * 0.16f + layer * 1.4f, radius + expansion, 72)
        }

        shapeRenderer.color = color(bodyLower)
        shapeRenderer.circle(centerX, centerY - radius * 0.02f, radius * 0.96f, 72)

        shapeRenderer.color = color(bodyUpper)
        shapeRenderer.circle(centerX, centerY + radius * 0.16f, radius * 0.76f, 72)

        shapeRenderer.color = color(coreColor)
        shapeRenderer.circle(centerX, centerY - radius * 0.12f, radius * 0.62f, 72)

        shapeRenderer.color = colorWithAlpha(highlightColor, if (enabled) 0.26f else 0.18f)
        shapeRenderer.circle(centerX - radius * 0.06f, centerY + radius * 0.30f, radius * 0.46f, 54)

        shapeRenderer.color = colorWithAlpha(highlightColor, if (enabled) 0.12f else 0.08f)
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

    private fun fillPanel(rect: Rectangle, bodyTop: Long, bodyBottom: Long, headerTop: Long, headerBottom: Long, _edgeColor: Long) {
    }

    private fun fillActionWell() {
    }

    private fun drawActionWellTexture() {
        val rect = actionWellRect()
        glassRenderer.draw(batch, rect, min(rect.height * 0.48f, 38f), ACTION_WELL_GLASS_STYLE, overlayAnimationSeconds)
        drawPanelTexture(rect, color(0xFFDAB10A))
    }

    private fun showActionWell(): Boolean = showDoubtToggleButton() || showCoinsShortcutButton()

    private fun actionWellRect(): Rectangle {
        val margin = clamp(panelPadding * 0.55f, 12f, 18f)
        val includeCoins = showCoinsShortcutButton()
        val includeDoubt = showDoubtToggleButton()
        val left = when {
            includeCoins && includeDoubt -> min(hostCoinsButtonRect.x, doubtButtonRect.x)
            includeCoins -> hostCoinsButtonRect.x
            else -> doubtButtonRect.x
        }
        val bottom = when {
            includeCoins && includeDoubt -> min(hostCoinsButtonRect.y, doubtButtonRect.y)
            includeCoins -> hostCoinsButtonRect.y
            else -> doubtButtonRect.y
        }
        val right = when {
            includeCoins && includeDoubt -> max(
                hostCoinsButtonRect.x + hostCoinsButtonRect.width,
                doubtButtonRect.x + doubtButtonRect.width,
            )
            includeCoins -> hostCoinsButtonRect.x + hostCoinsButtonRect.width
            else -> doubtButtonRect.x + doubtButtonRect.width
        }
        val top = when {
            includeCoins && includeDoubt -> max(
                hostCoinsButtonRect.y + hostCoinsButtonRect.height,
                doubtButtonRect.y + doubtButtonRect.height,
            )
            includeCoins -> hostCoinsButtonRect.y + hostCoinsButtonRect.height
            else -> doubtButtonRect.y + doubtButtonRect.height
        }
        return Rectangle(
            left - margin,
            bottom - margin,
            right - left + margin * 2f,
            top - bottom + margin * 2f,
        )
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
        drawGlow(
            rect.x - rect.width * 0.10f,
            rect.y - rect.height * 0.08f,
            rect.width * 0.78f,
            rect.height * 0.64f,
            colorWithAlpha(tint.toRgba(), 0.06f),
        )
        drawGlow(
            rect.x + rect.width * 0.18f,
            rect.y + rect.height * 0.48f,
            rect.width * 0.42f,
            rect.height * 0.18f,
            colorWithAlpha(0xFFF8F0E6FF, 0.08f),
        )
    }

    private fun drawTimelineCards(includeOverlay: Boolean) {
        timelineCardVisuals.forEach { visual ->
            if (isOverlayVisual(visual) == includeOverlay) {
                drawCardVisual(visual)
            }
        }
    }

    private fun drawTimelineCardText(includeOverlay: Boolean) {
        val layerVisuals = timelineCardVisuals.filter { isOverlayVisual(it) == includeOverlay }
        layerVisuals.forEachIndexed { index, visual ->
            drawClippedCardText(visual, layerVisuals, index)
        }
    }

    private fun drawDoubtPopupCards(includeOverlay: Boolean) {
        doubtTimelineCardVisuals.forEach { visual ->
            if (isDoubtOverlayVisual(visual) == includeOverlay) {
                drawCardVisual(visual)
            }
        }
    }

    private fun drawDoubtPopupCardText(includeOverlay: Boolean) {
        val layerVisuals = doubtTimelineCardVisuals.filter { isDoubtOverlayVisual(it) == includeOverlay }
        layerVisuals.forEachIndexed { index, visual ->
            drawClippedCardText(visual, layerVisuals, index)
        }
    }

    private fun drawClippedCardText(
        visual: TimelineCardVisual,
        layeredVisuals: List<TimelineCardVisual>,
        index: Int,
    ) {
        val clipBounds = visibleTextClipBounds(visual, layeredVisuals, index)
        if (clipBounds.width <= 6f || clipBounds.height <= 6f) {
            return
        }
        withBatchClip(clipBounds) {
            drawCardText(visual)
        }
    }

    private fun visibleTextClipBounds(
        visual: TimelineCardVisual,
        layeredVisuals: List<TimelineCardVisual>,
        index: Int,
    ): Rectangle {
        var visibleLeft = visual.rect.x
        var visibleRight = visual.rect.x + visual.rect.width
        val top = visual.rect.y + visual.rect.height
        for (laterIndex in index + 1 until layeredVisuals.size) {
            val laterRect = layeredVisuals[laterIndex].rect
            val overlapsVertically = laterRect.y < top && laterRect.y + laterRect.height > visual.rect.y
            if (!overlapsVertically) {
                continue
            }
            if (laterRect.x <= visibleLeft && laterRect.x + laterRect.width > visibleLeft) {
                visibleLeft = min(visibleRight, laterRect.x + laterRect.width)
            } else if (laterRect.x in (visibleLeft + 0.5f)..<visibleRight) {
                visibleRight = laterRect.x
            }
        }
        val insetX = 3f
        val insetY = 2f
        return Rectangle(
            visibleLeft + insetX,
            visual.rect.y + insetY,
            max(0f, visibleRight - visibleLeft - insetX * 2f),
            max(0f, visual.rect.height - insetY * 2f),
        )
    }

    private fun withBatchClip(clipBounds: Rectangle, block: () -> Unit) {
        batch.flush()
        val scissors = Rectangle()
        viewport.calculateScissors(batch.transformMatrix, clipBounds, scissors)
        if (ScissorStack.pushScissors(scissors)) {
            try {
                block()
                batch.flush()
            } finally {
                ScissorStack.popScissors()
            }
        } else {
            block()
        }
    }

    private fun hasOverlayTimelineVisuals(): Boolean {
        return (draggingPendingCard || draggingDoubtCard) && pendingCardVisual != null
    }

    private fun hasOverlayDoubtTimelineVisuals(): Boolean {
        return draggingDoubtCard && doubtPendingCardVisual != null
    }

    private fun isOverlayVisual(visual: TimelineCardVisual): Boolean {
        return (draggingPendingCard || draggingDoubtCard) && pendingCardVisual?.id == visual.id
    }

    private fun isDoubtOverlayVisual(visual: TimelineCardVisual): Boolean {
        return draggingDoubtCard && doubtPendingCardVisual?.id == visual.id
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
                        y = visual.rect.y + visual.rect.height * 0.73f,
                        width = visual.rect.width - 28f,
                        height = visual.rect.height * 0.14f,
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
                        y = visual.rect.y + visual.rect.height * 0.43f,
                        width = visual.rect.width - 28f,
                        height = visual.rect.height * 0.16f,
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
                    y = visual.rect.y + visual.rect.height * 0.10f,
                    width = visual.rect.width - 28f,
                    height = visual.rect.height * 0.26f,
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
        doubtWindowStatusText()?.let { return it }
        presenter.state.doubt?.let { doubt ->
            val doubterName = presenter.state.requirePlayer(doubt.doubterId)?.displayName?.uppercase() ?: "PLAYER"
            return when {
                isLocalDoubtPlacementPhase() -> "PLACE YOUR DOUBT"
                presenter.localPlayerId == doubt.targetPlayerId && doubt.phase == DoubtPhase.ARMED -> "$doubterName DOUBTS"
                presenter.localPlayerId == doubt.targetPlayerId -> "$doubterName IS DOUBTING"
                presenter.localPlayerId != doubt.doubterId && doubt.phase == DoubtPhase.ARMED -> "$doubterName ARMED A DOUBT"
                else -> null
            }
        }
        return null
    }

    private fun toolbarStatusColor(): Color {
        presenter.lastError?.let { return color(0xFFCBAB8D) }
        return if (localResolution()?.correct == true) {
            color(0xFFD88B4D)
        } else {
            color(0xFFCBAB8D)
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
        if (isLocalDoubtPlacementPhase()) {
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
            color(0xFFD88A45)
        } else {
            color(0xFFF2E6D7)
        }
    }

    private fun activeTurnToolbarShadowColor(): Color {
        return if (isLocalPlayersTurn()) {
            color(0x533106A6)
        } else {
            color(0x35150EA8)
        }
    }

    private fun shouldShowInactiveTurnFilter(): Boolean {
        return presenter.state.status == MatchStatus.ACTIVE &&
            !isLocalPlayersTurn() &&
            !isLocalDoubtPlacementPhase()
    }

    private fun showActionButton(): Boolean = canEndTurn()

    private fun showPlaybackToggleButton(): Boolean =
        presenter.state.status == MatchStatus.ACTIVE &&
            isLocalPlayersTurn() &&
            (presenter.playbackSessionState is PlaybackSessionState.Playing ||
                presenter.playbackSessionState is PlaybackSessionState.Paused)

    private fun isPlaybackPaused(): Boolean = presenter.playbackSessionState is PlaybackSessionState.Paused

    private fun playbackToggleLabel(): String = if (isPlaybackPaused()) "RESUME" else "PAUSE"

    private fun showRedrawButton(): Boolean = canRedraw() && !isLocalDoubtPlacementPhase()

    private fun showHostCoinsButton(): Boolean = presenter.isLocalHost && presenter.state.status == MatchStatus.ACTIVE

    private fun showCoinsShortcutButton(): Boolean =
        showHostCoinsButton() &&
            !coinPanelOpen &&
            !isLocalDoubtPlacementPhase()

    private fun showDoubtToggleButton(): Boolean {
        if (presenter.state.status != MatchStatus.ACTIVE || isLocalDoubtPlacementPhase()) {
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
            doubt == null -> {
                turn.phase == TurnPhase.AWAITING_PLACEMENT ||
                    turn.phase == TurnPhase.CARD_POSITIONED ||
                    turn.phase == TurnPhase.AWAITING_DOUBT_WINDOW
            }

            doubt.doubterId == presenter.localPlayerId && doubt.phase == DoubtPhase.ARMED -> true
            else -> false
        }
    }

    private fun isDoubtToggleActive(): Boolean {
        val doubt = presenter.state.doubt ?: return false
        return doubt.doubterId == presenter.localPlayerId && doubt.phase == DoubtPhase.ARMED
    }

    private fun isLocalDoubtPlacementPhase(): Boolean {
        val doubt = presenter.state.doubt ?: return false
        val phase = presenter.state.turn?.phase ?: return false
        return doubt.doubterId == presenter.localPlayerId &&
            (phase == TurnPhase.AWAITING_DOUBT_PLACEMENT || phase == TurnPhase.DOUBT_POSITIONED)
    }

    private fun isActionButtonEnabled(): Boolean = canEndTurn()

    private fun showLobbyPairingGate(): Boolean = presenter.requiresHostPlaybackPairing()

    private fun showLobbyPrimaryButton(): Boolean = presenter.isLocalHost &&
        (showLobbyPairingGate() || presenter.canStartLobbyMatch())

    private fun showLobbyJoinPanel(): Boolean =
        presenter.isLocalHost &&
            presenter.state.status == MatchStatus.LOBBY &&
            !presenter.guestJoinUrl.isNullOrBlank()

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

    private fun displayLobbyJoinUrl(): String {
        val joinUrl = presenter.guestJoinUrl ?: return ""
        return joinUrl.removePrefix("http://").removePrefix("https://")
    }

    private fun localPlayer(): PlayerState? = presenter.localPlayer

    private fun displayedTimelinePlayer(): PlayerState? {
        val activePlayerId = presenter.state.turn?.activePlayerId
        return activePlayerId?.let { presenter.state.requirePlayer(it) } ?: presenter.localPlayer
    }

    private fun localResolution() = presenter.state.lastResolution?.takeIf { it.playerId == presenter.localPlayerId }

    private fun isLocalPlayersTurn(): Boolean = presenter.state.turn?.activePlayerId == presenter.localPlayerId

    private fun doubtWindowStatusText(): String? {
        val turn = presenter.state.turn ?: return null
        if (turn.phase != TurnPhase.AWAITING_DOUBT_WINDOW) {
            return null
        }
        val seconds = doubtWindowCountdownSecondsRemaining()
        return "DOUBT WINDOW ${seconds ?: 0}"
    }

    private fun doubtWindowCountdownSecondsRemaining(): Int? {
        val deadline = presenter.state.turn?.doubtWindowEndsAtEpochMillis ?: return null
        val remainingMillis = max(0L, deadline - presenter.currentSharedTimeMillis)
        return countdownSecondsRemaining(remainingMillis)
    }

    private fun headerDrawButtonLabel(): String? = when {
        canDraw() -> "DRAW"
        showRedrawButton() -> "REDRAW"
        else -> null
    }

    private fun actionButtonLabel(): String = if (isLocalDoubtPlacementPhase()) "LOCK DOUBT" else "END TURN"

    private fun canDraw(): Boolean = isLocalPlayersTurn() && presenter.state.turn?.phase == TurnPhase.WAITING_FOR_DRAW

    private fun canRedraw(): Boolean {
        val phase = presenter.state.turn?.phase ?: return false
        val player = localPlayer() ?: return false
        if (!isLocalPlayersTurn() || player.pendingCard == null) {
            return false
        }
        return phase == TurnPhase.AWAITING_PLACEMENT || phase == TurnPhase.CARD_POSITIONED
    }

    private fun canEndTurn(): Boolean {
        val phase = presenter.state.turn?.phase ?: return false
        return when {
            isLocalDoubtPlacementPhase() -> phase == TurnPhase.AWAITING_DOUBT_PLACEMENT || phase == TurnPhase.DOUBT_POSITIONED
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
        return isLocalDoubtPlacementPhase() &&
            (phase == TurnPhase.AWAITING_DOUBT_PLACEMENT || phase == TurnPhase.DOUBT_POSITIONED)
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
        if (!draggingDoubtCard) {
            return timelineLayout.nearestSlotIndex(targetPlayer.timeline.cards.size, x)
        }

        val arrangement = timelineLayout.pendingArrangement(
            existingCardCount = targetPlayer.timeline.cards.size,
            pendingSlotIndex = doubt.proposedSlotIndex ?: pendingCard.proposedSlotIndex,
        )
        val pendingLeft = clamp(
            x - doubtPendingCardGrabOffsetX,
            timelineCardsX,
            timelineCardsX + timelineCardsWidth - arrangement.cardWidth,
        )
        val probeX = pendingLeft + arrangement.cardWidth * 0.5f
        return timelineLayout.nearestSlotIndex(targetPlayer.timeline.cards.size, probeX)
    }

    private fun drawTargetDoubtArrow() {
        val arrowX = doubtArrowXForMainTimeline() ?: return
        drawDoubtArrow(arrowX, timelineCardBottom(cardHeight) + cardHeight + 2f)
    }

    private fun doubtArrowXForMainTimeline(): Float? {
        val doubt = presenter.state.doubt ?: return null
        val phase = presenter.state.turn?.phase ?: return null
        if (phase != TurnPhase.AWAITING_DOUBT_PLACEMENT && phase != TurnPhase.DOUBT_POSITIONED) {
            return null
        }
        if (isLocalDoubtPlacementPhase()) {
            return null
        }
        val targetPlayer = displayedTimelinePlayer() ?: return null
        if (doubt.targetPlayerId != targetPlayer.id) {
            return null
        }
        val pendingCard = targetPlayer.pendingCard ?: return null
        val pendingVisual = pendingCardVisual ?: return null
        val committedVisuals = timelineCardVisuals
            .asSequence()
            .filter { it.id != pendingCard.entry.id }
            .sortedBy { it.rect.x }
            .toList()
        val visibleRects = buildList {
            for (index in 0..committedVisuals.size) {
                if (index == pendingCard.proposedSlotIndex) {
                    add(pendingVisual.rect)
                }
                if (index < committedVisuals.size) {
                    add(committedVisuals[index].rect)
                }
            }
        }
        if (visibleRects.isEmpty()) {
            return null
        }

        val doubtSlot = doubt.proposedSlotIndex ?: pendingCard.proposedSlotIndex
        if (doubtSlot == pendingCard.proposedSlotIndex) {
            return pendingVisual.rect.x + pendingVisual.rect.width / 2f
        }
        val boundaryIndex = (doubtSlot + if (doubtSlot > pendingCard.proposedSlotIndex) 1 else 0)
            .coerceIn(0, visibleRects.size)
        val edgeOffset = max(18f, pendingVisual.rect.width * 0.16f)
        return when (boundaryIndex) {
            0 -> visibleRects.first().x - edgeOffset
            visibleRects.size -> visibleRects.last().x + visibleRects.last().width + edgeOffset
            else -> {
                val leftRect = visibleRects[boundaryIndex - 1]
                val rightRect = visibleRects[boundaryIndex]
                (leftRect.x + leftRect.width + rightRect.x) / 2f
            }
        }
    }

    private fun drawDoubtArrow(centerX: Float, tipY: Float) {
        shapeRenderer.color = colorWithAlpha(0x3A2007FF, 0.24f)
        shapeRenderer.triangle(
            centerX,
            tipY - 3f,
            centerX - 22f,
            tipY + 29f,
            centerX + 22f,
            tipY + 29f,
        )
        shapeRenderer.color = color(0xFFF5CC63FF)
        shapeRenderer.triangle(
            centerX,
            tipY,
            centerX - 18f,
            tipY + 24f,
            centerX + 18f,
            tipY + 24f,
        )
        shapeRenderer.color = colorWithAlpha(0xFFFCE0A8FF, 0.42f)
        shapeRenderer.triangle(
            centerX,
            tipY + 6f,
            centerX - 10f,
            tipY + 21f,
            centerX + 10f,
            tipY + 21f,
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

    private fun withAlpha(rgba: Long, alpha: Int): Long = (rgba and 0xFFFFFF00) or (alpha.toLong() and 0xFF)

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

    private fun actionButtonContains(x: Float, y: Float): Boolean = actionButtonRect.contains(x, y)

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

            if (presenter.state.status == MatchStatus.LOBBY) {
                lobbyBadgeVisuals().firstOrNull { it.editRect?.contains(world.x, world.y) == true }?.let {
                    requestLobbyDisplayNameEdit()
                    return true
                }

                if (presenter.isLocalHost) {
                    lobbyBadgeVisuals().firstOrNull { it.rect.contains(world.x, world.y) }?.let { visual ->
                        draggingLobbyPlayerId = visual.player.id
                        lobbyReorderTargetIndex = presenter.state.players.indexOfFirst { it.id == visual.player.id }.coerceAtLeast(0)
                        lobbyDragPosition.set(world.x, world.y)
                        lobbyDragOffset.set(world.x - visual.rect.x, world.y - visual.rect.y)
                        return true
                    }
                }

                if (showLobbyPrimaryButton() && startButtonRect.contains(world.x, world.y)) {
                    if (showLobbyPairingGate()) {
                        if (presenter.playbackSessionState != PlaybackSessionState.Connecting) {
                            presenter.prepareHostPlayback()
                        }
                    } else {
                        presenter.startMatch()
                    }
                    return true
                }
            }

            if (presenter.state.status != MatchStatus.ACTIVE) {
                return false
            }

            if (showCoinsShortcutButton() && hostCoinsButtonRect.contains(world.x, world.y)) {
                coinPanelOpen = true
                return true
            }

            if (headerDrawButtonLabel() != null && redrawButtonRect.contains(world.x, world.y)) {
                if (canDraw()) {
                    presenter.drawCard()
                } else {
                    presenter.redrawCard()
                }
                return true
            }

            if (showPlaybackToggleButton() && playbackButtonRect.contains(world.x, world.y)) {
                presenter.togglePlayback()
                return true
            }

            if (canEndTurn() && actionButtonContains(world.x, world.y)) {
                presenter.endTurn()
                return true
            }

            val currentPendingCard = pendingCardVisual
            if (canMoveDoubtCard() && currentPendingCard?.rect?.contains(world.x, world.y) == true) {
                draggingDoubtCard = true
                worldTouch.set(world)
                doubtPendingCardGrabOffsetX = world.x - currentPendingCard.rect.x
                return true
            }

            if (showDoubtToggleButton() && doubtButtonRect.contains(world.x, world.y)) {
                presenter.toggleDoubt()
                return true
            }

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
            if (draggingLobbyPlayerId != null) {
                viewport.unproject(worldTouch.set(screenX.toFloat(), screenY.toFloat()))
                lobbyDragPosition.set(worldTouch.x, worldTouch.y)
                lobbyReorderTargetIndex = lobbyReorderIndexFor(worldTouch.x, worldTouch.y)
                return true
            }

            if (!draggingPendingCard && !draggingDoubtCard) {
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
            if (draggingLobbyPlayerId != null) {
                val draggedPlayerId = draggingLobbyPlayerId
                val targetIndex = lobbyReorderTargetIndex
                draggingLobbyPlayerId = null
                lobbyReorderTargetIndex = null
                lobbyDragOffset.setZero()
                lobbyDragPosition.setZero()
                if (draggedPlayerId != null && targetIndex != null) {
                    presenter.reorderLobbyPlayer(draggedPlayerId, targetIndex)
                }
                return true
            }

            if (!draggingPendingCard && !draggingDoubtCard) {
                return false
            }

            val world = viewport.unproject(worldTouch.set(screenX.toFloat(), screenY.toFloat()))

            if (draggingDoubtCard) {
                presenter.moveDoubtCard(requestedDoubtSlotIndexFor(world.x))
            } else if (draggingPendingCard) {
                presenter.movePendingCard(requestedSlotIndexFor(world.x))
            }

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

    private data class LobbyBadgeVisual(
        val player: PlayerState,
        val rect: Rectangle,
        val editRect: Rectangle?,
        val textScale: Float,
        val isDragged: Boolean,
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
        const val CONFETTI_COUNT = 110
        const val CONFETTI_GRAVITY = -520f
        const val CONFETTI_FILTER_BLEND_START_PROGRESS = 0.72f
        val HERO_GLASS_STYLE = LiquidGlassStyle(
            bodyTint = 0x6E3E3A96,
            edgeTint = 0xFFF1DDC9FF,
            highlightTint = 0xFFFFFFFF,
            glowTint = 0xFFF1B76BFF,
            distortion = 0.010f,
            frost = 0.15f,
        )
        val TIMELINE_GLASS_STYLE = LiquidGlassStyle(
            bodyTint = 0x5E37358A,
            edgeTint = 0xFFF0DCC5FF,
            highlightTint = 0xFFFFFFFF,
            glowTint = 0xFFE7A15FFF,
            distortion = 0.012f,
            frost = 0.18f,
        )
        val LOBBY_PANEL_GLASS_STYLE = LiquidGlassStyle(
            bodyTint = 0x5C37348E,
            edgeTint = 0xFFF5E3CDFF,
            highlightTint = 0xFFFFFFFF,
            glowTint = 0xFFF1AF72FF,
            distortion = 0.012f,
            frost = 0.18f,
        )
        val BADGE_GLASS_STYLE = LiquidGlassStyle(
            bodyTint = 0x6A433FA6,
            edgeTint = 0xFFF7EBDDFF,
            highlightTint = 0xFFFFFFFF,
            glowTint = 0xFFF3B98CFF,
            distortion = 0.013f,
            frost = 0.20f,
        )
        val DRAGGED_BADGE_GLASS_STYLE = LiquidGlassStyle(
            bodyTint = 0x8B554FBA,
            edgeTint = 0xFFFFF2E4FF,
            highlightTint = 0xFFFFFFFF,
            glowTint = 0xFFFFC39DFF,
            distortion = 0.016f,
            frost = 0.22f,
        )
        val PRIMARY_BUTTON_GLASS_STYLE = LiquidGlassStyle(
            bodyTint = 0x8E5647BC,
            edgeTint = 0xFFFFE7B5FF,
            highlightTint = 0xFFFFFFFF,
            glowTint = 0xFFFFC767FF,
            distortion = 0.014f,
            frost = 0.19f,
        )
        val SECONDARY_BUTTON_GLASS_STYLE = LiquidGlassStyle(
            bodyTint = 0x78445AB8,
            edgeTint = 0xFFF9DDD2FF,
            highlightTint = 0xFFFFFFFF,
            glowTint = 0xFFF0A88DFF,
            distortion = 0.014f,
            frost = 0.19f,
        )
        val START_BUTTON_GLASS_STYLE = LiquidGlassStyle(
            bodyTint = 0x905749C0,
            edgeTint = 0xFFFFE8B9FF,
            highlightTint = 0xFFFFFFFF,
            glowTint = 0xFFFFCD78FF,
            distortion = 0.015f,
            frost = 0.20f,
        )
        val IDLE_DOUBT_GLASS_STYLE = LiquidGlassStyle(
            bodyTint = 0x8F5744B8,
            edgeTint = 0xFFFFE5B1FF,
            highlightTint = 0xFFFFFFFF,
            glowTint = 0xFFFFC979FF,
            distortion = 0.014f,
            frost = 0.19f,
        )
        val ACTIVE_DOUBT_GLASS_STYLE = LiquidGlassStyle(
            bodyTint = 0x92554BC2,
            edgeTint = 0xFFFFF1D5FF,
            highlightTint = 0xFFFFFFFF,
            glowTint = 0xFFFFC7A0FF,
            distortion = 0.016f,
            frost = 0.22f,
        )
        val ACTION_WELL_GLASS_STYLE = LiquidGlassStyle(
            bodyTint = 0x52302E7C,
            edgeTint = 0xFFEFD9C2FF,
            highlightTint = 0xFFFFFFFF,
            glowTint = 0xFFE9A66CFF,
            distortion = 0.011f,
            frost = 0.16f,
        )
        val COIN_PANEL_GLASS_STYLE = LiquidGlassStyle(
            bodyTint = 0x5D36368E,
            edgeTint = 0xFFF6E4D0FF,
            highlightTint = 0xFFFFFFFF,
            glowTint = 0xFFF0AE71FF,
            distortion = 0.012f,
            frost = 0.18f,
        )
        val COIN_ROW_GLASS_STYLE = LiquidGlassStyle(
            bodyTint = 0x56323384,
            edgeTint = 0xFFF2DFC8FF,
            highlightTint = 0xFFFFFFFF,
            glowTint = 0xFFE6A069FF,
            distortion = 0.012f,
            frost = 0.18f,
        )
        val CLOSE_BUTTON_GLASS_STYLE = LiquidGlassStyle(
            bodyTint = 0xC26A5A92,
            edgeTint = 0xFFF8DED3FF,
            highlightTint = 0xFFFFFFFF,
            glowTint = 0xFFF2A88EFF,
            distortion = 0.014f,
            frost = 0.19f,
        )
        val DOUBT_POPUP_GLASS_STYLE = LiquidGlassStyle(
            bodyTint = 0x4F6B8D7C,
            edgeTint = 0xFFE5F2FFFF,
            highlightTint = 0xFFFFFFFF,
            glowTint = 0xFF97CFFFFF,
            distortion = 0.016f,
            frost = 0.24f,
        )
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

internal fun countdownSecondsRemaining(remainingMillis: Long): Int {
    val clampedRemainingMillis = max(0L, remainingMillis)
    return ((clampedRemainingMillis + 999L) / 1_000L).toInt()
}

private fun List<PlayerState>.moveLobbyPlayer(
    playerId: PlayerId,
    targetIndex: Int,
): List<PlayerState> {
    val currentIndex = indexOfFirst { it.id == playerId }
    if (currentIndex < 0) {
        return this
    }
    val reordered = toMutableList()
    val player = reordered.removeAt(currentIndex)
    reordered.add(targetIndex.coerceIn(0, reordered.size), player)
    return reordered.toList()
}
