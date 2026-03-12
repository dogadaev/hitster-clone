package com.hitster.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.BitmapFont
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

    private var layoutWorldWidth = 0f
    private var layoutWorldHeight = 0f
    private var outerMargin = 28f
    private var panelGap = 22f
    private var panelPadding = 28f
    private var panelHeaderHeight = 84f
    private var cardHeight = 210f
    private var fontScaleMultiplier = 1.02f
    private var minimumTextScale = 0.88f
    private var shadowOffset = 1.2f
    private var timelineLayout = TimelineLayoutCalculator(trackX = 0f, trackWidth = 1f)

    private var draggingDeckGhost = false
    private var draggingPendingCard = false

    override fun show() {
        font = createFont()
        Gdx.input.inputProcessor = MatchInputController()
        updateLayout()
    }

    override fun render(delta: Float) {
        updateLayout()
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
            -> drawMatch()
        }
        shapeRenderer.end()

        batch.begin()
        when (presenter.state.status) {
            MatchStatus.LOBBY -> drawLobbyText()
            MatchStatus.ACTIVE,
            MatchStatus.COMPLETE,
            -> drawMatchText()
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
        if (this::font.isInitialized) {
            font.dispose()
        }
    }

    private fun updateLayout() {
        val worldWidth = viewport.worldWidth
        val worldHeight = viewport.worldHeight
        if (worldWidth == layoutWorldWidth && worldHeight == layoutWorldHeight) {
            return
        }

        layoutWorldWidth = worldWidth
        layoutWorldHeight = worldHeight

        outerMargin = clamp(min(worldWidth, worldHeight) * 0.03f, 24f, 36f)
        panelGap = outerMargin * 0.76f
        panelPadding = clamp(worldHeight * 0.034f, 22f, 34f)
        panelHeaderHeight = clamp(worldHeight * 0.115f, 76f, 96f)

        val isLobby = presenter.state.status == MatchStatus.LOBBY
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
        deckRect.set(
            deckPanelRect.x + (deckPanelRect.width - deckCardWidth) / 2f,
            deckPanelRect.y + deckPanelRect.height * 0.26f,
            deckCardWidth,
            deckCardHeight,
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

        val preferredCardWidth = clamp(timelineTrackRect.width * 0.14f, 126f, 178f)
        val minCardWidth = clamp(timelineTrackRect.width * 0.10f, 98f, 126f)
        timelineLayout = TimelineLayoutCalculator(
            trackX = timelineTrackRect.x + panelPadding * 0.28f,
            trackWidth = timelineTrackRect.width - panelPadding * 0.56f,
            preferredCardWidth = preferredCardWidth,
            minCardWidth = minCardWidth,
            preferredGap = clamp(timelineTrackRect.width * 0.025f, 20f, 32f),
            minGap = 14f,
        )

        cardHeight = clamp(timelineTrackRect.height * 0.62f, 176f, 236f)
        fontScaleMultiplier = clamp(worldHeight / 960f, 0.98f, 1.08f)
        minimumTextScale = 0.88f
        shadowOffset = clamp(worldHeight * 0.0011f, 1f, 1.6f)
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

    private fun drawBackground() {
        fillRect(0f, 0f, layoutWorldWidth, layoutWorldHeight, 0x04101EFF)
        fillRect(0f, layoutWorldHeight * 0.70f, layoutWorldWidth, layoutWorldHeight * 0.30f, 0x0B1736FF)
        fillRect(0f, 0f, layoutWorldWidth, layoutWorldHeight * 0.18f, 0x09152FFF)
        fillRect(layoutWorldWidth * 0.66f, 0f, layoutWorldWidth * 0.34f, layoutWorldHeight * 0.42f, 0x081327AA)
        if (headerRect.height > 0f) {
            fillRect(headerRect.x, headerRect.y, headerRect.width, headerRect.height, 0x2B3868FF)
        }
    }

    private fun drawLobby() {
        fillPanel(lobbyCardRect, 0x101C41FF, 0x3C4F86FF)
        fillRect(startButtonRect.x, startButtonRect.y, startButtonRect.width, startButtonRect.height, 0xF0A339FF)

        presenter.state.players.forEachIndexed { index, _ ->
            fillRect(
                lobbyCardRect.x + 54f,
                lobbyCardRect.y + lobbyCardRect.height - 194f - index * 58f,
                290f,
                44f,
                0x22315DFF,
            )
        }

        repeat(3) { index ->
            val offset = index * 24f
            fillRect(
                lobbyCardRect.x + lobbyCardRect.width - 208f + offset,
                lobbyCardRect.y + 54f - offset,
                138f,
                196f,
                0xEBC171FF,
            )
        }
    }

    private fun drawLobbyText() {
        drawText("Hitster Clone", headerRect.x + 42f, headerRect.y + headerRect.height * 0.67f, 360f, 1.42f, Color.WHITE)
        drawText(
            "Local host session",
            headerRect.x + headerRect.width - 220f,
            headerRect.y + headerRect.height * 0.66f,
            180f,
            0.94f,
            color(0xCFD8F8FF),
            Align.right,
        )

        drawText("Ready to Start", lobbyCardRect.x + 54f, lobbyCardRect.y + lobbyCardRect.height - 54f, 300f, 1.20f, Color.WHITE)
        drawText(
            "${presenter.state.players.size} players connected",
            lobbyCardRect.x + 54f,
            lobbyCardRect.y + lobbyCardRect.height - 108f,
            320f,
            0.94f,
            color(0xF3C76EFF),
        )

        presenter.state.players.forEachIndexed { index, player ->
            drawText(
                text = player.displayName,
                x = lobbyCardRect.x + 74f,
                y = lobbyCardRect.y + lobbyCardRect.height - 160f - index * 58f,
                width = 244f,
                scale = 0.94f,
                color = Color.WHITE,
            )
        }

        drawText(
            text = "One phone. One timeline.",
            x = lobbyCardRect.x + 54f,
            y = lobbyCardRect.y + 118f,
            width = lobbyCardRect.width * 0.48f,
            scale = 0.96f,
            color = color(0xD8E0FDFF),
        )

        drawText(
            "Start Match",
            startButtonRect.x,
            startButtonRect.y + startButtonRect.height * 0.62f,
            startButtonRect.width,
            1.14f,
            color(0x1A1308FF),
            Align.center,
        )
    }

    private fun drawMatch() {
        fillRect(heroRect.x, heroRect.y, heroRect.width, heroRect.height, 0x101C41FF)
        if (showActionButton()) {
            fillRect(
                actionButtonRect.x,
                actionButtonRect.y,
                actionButtonRect.width,
                actionButtonRect.height,
                0xF0A339FF,
            )
        }

        fillPanel(deckPanelRect, 0x101C41FF, 0x334779FF)
        fillPanel(timelinePanelRect, 0x101C41FF, 0x3C4F86FF)
        fillRect(timelineTrackRect.x, timelineTrackRect.y, timelineTrackRect.width, timelineTrackRect.height, 0x1D294CFF)

        repeat(3) { index ->
            val offset = index * 14f
            fillRect(deckRect.x + offset, deckRect.y - offset, deckRect.width, deckRect.height, 0xE56E4CFF)
        }

        if (showStatusBanner()) {
            fillRect(statusBannerRect.x, statusBannerRect.y, statusBannerRect.width, statusBannerRect.height, 0x22325EFF)
        }

        drawTimelineRail()
        drawTransientCard()
    }

    private fun drawMatchText() {
        val player = activePlayer()
        val turnLabelWidth = 148f
        val turnX = if (showActionButton()) {
            actionButtonRect.x - panelGap - turnLabelWidth
        } else {
            heroRect.x + heroRect.width - panelPadding - turnLabelWidth
        }

        drawText(
            player?.displayName ?: "Waiting",
            heroRect.x + panelPadding,
            heroRect.y + heroRect.height * 0.64f,
            heroRect.width * 0.46f,
            1.08f,
            Color.WHITE,
        )
        drawText(
            "Turn ${presenter.state.turn?.number ?: 0}",
            turnX,
            heroRect.y + heroRect.height * 0.64f,
            turnLabelWidth,
            0.90f,
            color(0xCFD8F8FF),
            Align.right,
        )
        if (showActionButton()) {
            drawText(
                "END TURN",
                actionButtonRect.x,
                actionButtonRect.y + actionButtonRect.height * 0.63f,
                actionButtonRect.width,
                1.02f,
                color(0x1A1308FF),
                Align.center,
            )
        }

        drawText(
            "Deck",
            deckPanelRect.x + panelPadding,
            deckPanelRect.y + deckPanelRect.height - panelHeaderHeight * 0.54f,
            120f,
            1.02f,
            Color.WHITE,
        )
        drawText(
            "${presenter.state.deck.size} left",
            deckRect.x,
            deckRect.y + deckRect.height + 58f,
            deckRect.width,
            1.20f,
            Color.WHITE,
            Align.center,
        )

        val deckHint = deckHint()
        if (deckHint.isNotBlank()) {
            drawText(
                deckHint,
                deckPanelRect.x + panelPadding,
                deckPanelRect.y + panelPadding + 34f,
                deckPanelRect.width - panelPadding * 2f,
                0.92f,
                color(0xD8E0FDFF),
            )
        }

        drawText(
            "Timeline",
            timelinePanelRect.x + panelPadding,
            timelineHeaderRect.y + timelineHeaderRect.height * 0.60f,
            220f,
            1.06f,
            Color.WHITE,
        )
        drawText(
            "Score ${player?.score ?: 0}",
            timelineHeaderRect.x + timelineHeaderRect.width - 190f,
            timelineHeaderRect.y + timelineHeaderRect.height * 0.60f,
            150f,
            0.94f,
            color(0xF3C76EFF),
            Align.right,
        )

        if (player?.timeline?.cards.isNullOrEmpty() && player?.pendingCard == null && !showStatusBanner()) {
            drawText(
                text = "Drag from deck to timeline.",
                x = timelineTrackRect.x,
                y = timelineTrackRect.y + timelineTrackRect.height * 0.56f,
                width = timelineTrackRect.width,
                scale = 1.04f,
                color = color(0xD8E0FDFF),
                align = Align.center,
            )
        }

        if (showStatusBanner()) {
            drawText(
                text = statusBannerText(),
                x = statusBannerRect.x + 18f,
                y = statusBannerRect.y + statusBannerRect.height * 0.62f,
                width = statusBannerRect.width - 36f,
                scale = 0.96f,
                color = statusBannerColor(),
                align = Align.center,
                wrap = true,
            )
        }

        drawTimelineText(player)
    }

    private fun fillPanel(rect: Rectangle, fillColor: Long, headerColor: Long) {
        fillRect(rect.x, rect.y, rect.width, rect.height, fillColor)
        fillRect(rect.x, rect.y + rect.height - panelHeaderHeight, rect.width, panelHeaderHeight, headerColor)
    }

    private fun drawTimelineRail() {
        val player = activePlayer()
        val slotCenters = timelineLayout.insertionSlotCenters(player?.timeline?.cards?.size ?: 0)
        slotCenters.forEach { center ->
            fillRect(center - 2f, timelineTrackRect.y + 20f, 4f, timelineTrackRect.height - 40f, 0x6078ABFF)
        }

        if (player == null) {
            return
        }

        val pendingCard = player.pendingCard
        if (pendingCard == null) {
            val arrangement = timelineLayout.arrangement(player.timeline.cards.size)
            player.timeline.cards.forEachIndexed { index, _ ->
                fillRect(arrangement.cardLefts[index], timelineTrackRect.y + 24f, arrangement.cardWidth, cardHeight, 0xF0E1C9FF)
            }
            return
        }

        val arrangement = timelineLayout.pendingArrangement(
            existingCardCount = player.timeline.cards.size,
            pendingSlotIndex = pendingCard.proposedSlotIndex,
        )
        player.timeline.cards.forEachIndexed { index, _ ->
            fillRect(
                arrangement.committedCardLefts[index],
                timelineTrackRect.y + 30f,
                arrangement.cardWidth,
                cardHeight - 10f,
                0xF0E1C9FF,
            )
        }
        fillRect(arrangement.pendingCardLeft, timelineTrackRect.y + 14f, arrangement.cardWidth, cardHeight, 0xF0A339FF)
    }

    private fun drawTimelineText(player: PlayerState?) {
        if (player == null) {
            return
        }

        val pendingCard = player.pendingCard
        if (pendingCard == null) {
            val arrangement = timelineLayout.arrangement(player.timeline.cards.size)
            player.timeline.cards.forEachIndexed { index, card ->
                drawResolvedCard(arrangement.cardLefts[index], arrangement.cardWidth, card.releaseYear.toString())
            }
            return
        }

        val arrangement = timelineLayout.pendingArrangement(
            existingCardCount = player.timeline.cards.size,
            pendingSlotIndex = pendingCard.proposedSlotIndex,
        )
        player.timeline.cards.forEachIndexed { index, card ->
            drawResolvedCard(arrangement.committedCardLefts[index], arrangement.cardWidth, card.releaseYear.toString())
        }
        drawHiddenCard(arrangement.pendingCardLeft, arrangement.cardWidth)
    }

    private fun drawResolvedCard(left: Float, width: Float, year: String) {
        drawText(year, left, timelineTrackRect.y + cardHeight * 0.61f, width, 1.10f, color(0x15120CFF), Align.center)
    }

    private fun drawHiddenCard(left: Float, width: Float) {
        drawText("?", left, timelineTrackRect.y + cardHeight * 0.67f, width, 1.50f, color(0x1A1308FF), Align.center)
        drawText("LISTEN", left, timelineTrackRect.y + 60f, width, 0.78f, color(0x1A1308FF), Align.center)
    }

    private fun drawTransientCard() {
        if (!draggingDeckGhost && !draggingPendingCard) {
            return
        }

        val ghostWidth = clamp(timelineTrackRect.width * 0.14f, 128f, 178f)
        fillRect(worldTouch.x - ghostWidth / 2f, worldTouch.y - cardHeight / 2f, ghostWidth, cardHeight, 0xFFD18AFF)
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
        presenter.lastError?.let { return color(0xFFB3A2FF) }
        return if (presenter.state.lastResolution?.correct == true) {
            color(0xF3C76EFF)
        } else {
            color(0xFFB3A2FF)
        }
    }

    private fun phaseSummary(): String {
        if (presenter.state.status == MatchStatus.COMPLETE) {
            return "Final reveal"
        }
        return when {
            presenter.state.lastResolution != null && activePlayer()?.pendingCard == null -> "Placement checked"
            canEndTurn() -> "Ready to reveal"
            activePlayer()?.pendingCard != null -> "Move the hidden card"
            canDraw() -> "Drag from the deck"
            else -> "Waiting"
        }
    }

    private fun deckHint(): String {
        return if (canDraw()) {
            "Drag to draw."
        } else {
            ""
        }
    }

    private fun showActionButton(): Boolean = canEndTurn()

    private fun activePlayer(): PlayerState? = presenter.state.activePlayer

    private fun canDraw(): Boolean = presenter.state.turn?.phase == TurnPhase.WAITING_FOR_DRAW

    private fun canEndTurn(): Boolean = presenter.state.turn?.phase == TurnPhase.CARD_POSITIONED

    private fun requestedSlotIndexFor(x: Float): Int {
        val player = activePlayer() ?: return 0
        return timelineLayout.nearestSlotIndex(player.timeline.cards.size, x)
    }

    private fun drawText(
        text: String,
        x: Float,
        y: Float,
        width: Float,
        scale: Float,
        color: Color,
        align: Int = Align.left,
        wrap: Boolean = false,
    ) {
        val appliedScale = max(scale, minimumTextScale) * fontScaleMultiplier
        val drawX = x.roundToInt().toFloat()
        val drawY = y.roundToInt().toFloat()
        val drawWidth = width.roundToInt().toFloat()

        font.data.setScale(appliedScale)
        font.color = color(0x02060CBF)
        font.draw(batch, text, drawX + shadowOffset, drawY - shadowOffset, drawWidth, align, wrap)
        font.color = color
        font.draw(batch, text, drawX, drawY, drawWidth, align, wrap)
    }

    private fun fillRect(x: Float, y: Float, width: Float, height: Float, rgba: Long) {
        shapeRenderer.color = color(rgba)
        shapeRenderer.rect(x, y, width, height)
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
                return true
            }

            val pendingRect = player.pendingCard?.let { pending ->
                val arrangement = timelineLayout.pendingArrangement(
                    existingCardCount = player.timeline.cards.size,
                    pendingSlotIndex = pending.proposedSlotIndex,
                )
                Rectangle(arrangement.pendingCardLeft, timelineTrackRect.y + 14f, arrangement.cardWidth, cardHeight)
            }
            if (pendingRect?.contains(world.x, world.y) == true) {
                draggingPendingCard = true
                return true
            }

            return false
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            updateLayout()
            if (!draggingDeckGhost && !draggingPendingCard) {
                return false
            }

            viewport.unproject(worldTouch.set(screenX.toFloat(), screenY.toFloat()))
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
            return true
        }
    }

    private companion object {
        const val BASE_WORLD_WIDTH = 1600f
        const val BASE_WORLD_HEIGHT = 900f
        const val FONT_ASSET_PATH = "fonts/droid-sans-bold.ttf"
    }
}
