package com.hitster.core.game

/**
 * Applies the full host-authoritative match rule set, including turn flow, doubts, redraws, scoring, and snapshot publication.
 */

import com.hitster.core.model.GameState
import com.hitster.core.model.MatchStatus
import com.hitster.core.model.DeckState
import com.hitster.core.model.DoubtPhase
import com.hitster.core.model.DoubtState
import com.hitster.core.model.PendingCard
import com.hitster.core.model.PlayerId
import com.hitster.core.model.PlayerState
import com.hitster.core.model.TurnPhase
import com.hitster.core.model.TurnResolution
import com.hitster.core.model.TurnState

class HostGameReducer(
    private val placementValidator: TimelinePlacementValidator = TimelinePlacementValidator(),
) {
    /**
     * Applies one player or host command to the authoritative match state.
     *
     * Every accepted transition returns a new immutable state plus the side effects that the platform boundary must execute.
     */
    fun reduce(
        state: GameState,
        command: GameCommand,
    ): ReducerResult {
        return when (command) {
            is GameCommand.JoinSession -> joinSession(state, command)
            is GameCommand.UpdatePlayerName -> updatePlayerName(state, command.actorId, command.displayName)
            is GameCommand.ReorderLobbyPlayers -> reorderLobbyPlayers(state, command.actorId, command.playerId, command.targetIndex)
            is GameCommand.LeaveSession -> leaveSession(state, command.playerId)
            is GameCommand.StartGame -> startGame(state, command.actorId)
            is GameCommand.DrawCard -> drawCard(state, command.actorId)
            is GameCommand.RedrawCard -> redrawCard(state, command.actorId)
            is GameCommand.ToggleDoubt -> toggleDoubt(state, command.actorId)
            is GameCommand.MovePendingCard -> movePendingCard(state, command.actorId, command.requestedSlotIndex)
            is GameCommand.MoveDoubtCard -> moveDoubtCard(state, command.actorId, command.requestedSlotIndex)
            is GameCommand.AdjustPlayerCoins -> adjustPlayerCoins(state, command.actorId, command.playerId, command.delta)
            is GameCommand.EndTurn -> endTurn(state, command.actorId)
        }
    }

    private fun joinSession(
        state: GameState,
        command: GameCommand.JoinSession,
    ): ReducerResult {
        val sanitizedDisplayName = sanitizeDisplayName(command.displayName)
        if (sanitizedDisplayName.isBlank()) {
            return reject(state, "Display name cannot be blank.")
        }
        val existingPlayer = state.players.firstOrNull { it.id == command.playerId }
        if (existingPlayer != null) {
            val reattachedPlayer = existingPlayer.copy(
                displayName = sanitizedDisplayName,
                connected = true,
            )
            val nextState = state.copy(
                revision = state.revision + 1,
                players = state.players.replacePlayer(reattachedPlayer),
            )
            return accept(nextState)
        }
        if (state.status != MatchStatus.LOBBY) {
            return reject(state, "Players can only join while the match is still in the lobby.")
        }

        val nextState = state.copy(
            revision = state.revision + 1,
            players = state.players + PlayerState(
                id = command.playerId,
                displayName = sanitizedDisplayName,
            ),
        )

        return accept(nextState)
    }

    private fun updatePlayerName(
        state: GameState,
        actorId: PlayerId,
        displayName: String,
    ): ReducerResult {
        if (state.status != MatchStatus.LOBBY) {
            return reject(state, "Player names can only be changed in the lobby.")
        }
        val player = state.requirePlayer(actorId) ?: return reject(state, "Unknown player.")
        val sanitizedDisplayName = sanitizeDisplayName(displayName)
        if (sanitizedDisplayName.isBlank()) {
            return reject(state, "Display name cannot be blank.")
        }
        if (player.displayName == sanitizedDisplayName) {
            return accept(state)
        }

        return accept(
            state.copy(
                revision = state.revision + 1,
                players = state.players.replacePlayer(player.copy(displayName = sanitizedDisplayName)),
            ),
        )
    }

    private fun reorderLobbyPlayers(
        state: GameState,
        actorId: PlayerId,
        playerId: PlayerId,
        targetIndex: Int,
    ): ReducerResult {
        if (actorId != state.hostId) {
            return reject(state, "Only the host can reorder players.")
        }
        if (state.status != MatchStatus.LOBBY) {
            return reject(state, "Players can only be reordered in the lobby.")
        }
        if (state.players.none { it.id == playerId }) {
            return reject(state, "Unknown player.")
        }
        if (state.players.size <= 1) {
            return reject(state, "Not enough players to reorder.")
        }

        val normalizedTargetIndex = targetIndex.coerceIn(0, state.players.lastIndex)
        val currentIndex = state.players.indexOfFirst { it.id == playerId }
        if (currentIndex == normalizedTargetIndex) {
            return accept(state)
        }

        return accept(
            state.copy(
                revision = state.revision + 1,
                players = state.players.movePlayer(playerId, normalizedTargetIndex),
                activePlayerIndex = 0,
            ),
        )
    }

    private fun leaveSession(
        state: GameState,
        playerId: PlayerId,
    ): ReducerResult {
        if (state.status != MatchStatus.LOBBY) {
            return reject(state, "Players can only leave while the match is still in the lobby.")
        }
        if (playerId == state.hostId) {
            return reject(state, "The host cannot leave the session.")
        }
        if (state.players.none { it.id == playerId }) {
            return reject(state, "Player '${playerId.value}' is not in the session.")
        }

        val nextState = state.copy(
            revision = state.revision + 1,
            players = state.players.filterNot { it.id == playerId },
            activePlayerIndex = 0,
        )

        return accept(nextState)
    }

    private fun startGame(
        state: GameState,
        actorId: PlayerId,
    ): ReducerResult {
        if (actorId != state.hostId) {
            return reject(state, "Only the host can start the match.")
        }
        if (state.status != MatchStatus.LOBBY) {
            return reject(state, "Match has already started.")
        }
        if (state.players.size < 2) {
            return reject(state, "At least two players are required to start a match.")
        }
        if (state.deck.size <= state.players.size) {
            return reject(state, "At least one revealed starting card per player and one draw card are required to start.")
        }

        val openingDeal = dealOpeningTimelines(state.players, state.deck)
            ?: return reject(state, "The deck could not provide starting cards for every player.")

        val activePlayerId = openingDeal.players.first().id
        val nextState = state.copy(
            revision = state.revision + 1,
            status = MatchStatus.ACTIVE,
            activePlayerIndex = 0,
            players = openingDeal.players,
            deck = openingDeal.deck,
            turn = TurnState(
                number = 1,
                activePlayerId = activePlayerId,
                phase = TurnPhase.WAITING_FOR_DRAW,
            ),
            lastResolution = null,
        )

        return accept(nextState)
    }

    private fun drawCard(
        state: GameState,
        actorId: PlayerId,
    ): ReducerResult {
        val turn = state.turn ?: return reject(state, "Match has not started yet.")
        if (state.status != MatchStatus.ACTIVE) {
            return reject(state, "Match is not active.")
        }
        if (turn.activePlayerId != actorId) {
            return reject(state, "Only the active player can draw a card.")
        }
        if (turn.phase != TurnPhase.WAITING_FOR_DRAW) {
            return reject(state, "Card draw is not allowed at this point in the turn.")
        }

        val draw = state.deck.drawTop() ?: return reject(state, "The deck is empty.")
        val activePlayer = state.requirePlayer(actorId) ?: return reject(state, "Unknown player.")
        val defaultSlot = activePlayer.timeline.cards.size
        val updatedPlayer = activePlayer.copy(
            pendingCard = PendingCard(
                entry = draw.card,
                proposedSlotIndex = defaultSlot,
            ),
        )

        val nextState = state.copy(
            revision = state.revision + 1,
            deck = draw.nextDeck,
            players = state.players.replacePlayer(updatedPlayer),
            turn = turn.copy(phase = TurnPhase.AWAITING_PLACEMENT),
            doubt = null,
            lastResolution = null,
        )

        return accept(
            nextState,
            GameEffect.PlayTrack(draw.card.playbackReference),
        )
    }

    private fun redrawCard(
        state: GameState,
        actorId: PlayerId,
    ): ReducerResult {
        val turn = state.turn ?: return reject(state, "Match has not started yet.")
        if (state.status != MatchStatus.ACTIVE) {
            return reject(state, "Match is not active.")
        }
        if (turn.activePlayerId != actorId) {
            return reject(state, "Only the active player can redraw a card.")
        }
        if (turn.phase != TurnPhase.AWAITING_PLACEMENT && turn.phase != TurnPhase.CARD_POSITIONED) {
            return reject(state, "A redraw is only allowed while placing the current card.")
        }

        val activePlayer = state.requirePlayer(actorId) ?: return reject(state, "Unknown player.")
        val pendingCard = activePlayer.pendingCard ?: return reject(state, "There is no pending card to redraw.")
        val draw = state.deck.drawTop() ?: return reject(state, "The deck is empty.")
        val replacementSlot = pendingCard.proposedSlotIndex.coerceIn(activePlayer.timeline.validSlotRange())
        val replacementCard = PendingCard(
            entry = draw.card,
            proposedSlotIndex = replacementSlot,
        )
        val updatedPlayer = activePlayer.copy(pendingCard = replacementCard)
        val nextDiscardPile = state.discardPile + pendingCard.entry
        val nextPhase = if (turn.phase == TurnPhase.CARD_POSITIONED) {
            TurnPhase.CARD_POSITIONED
        } else {
            TurnPhase.AWAITING_PLACEMENT
        }
        val nextState = state.copy(
            revision = state.revision + 1,
            deck = draw.nextDeck,
            discardPile = nextDiscardPile,
            players = state.players.replacePlayer(updatedPlayer),
            turn = turn.copy(phase = nextPhase),
            doubt = null,
            lastResolution = null,
        )

        return accept(
            nextState,
            GameEffect.PlayTrack(draw.card.playbackReference),
        )
    }

    private fun toggleDoubt(
        state: GameState,
        actorId: PlayerId,
    ): ReducerResult {
        val turn = state.turn ?: return reject(state, "Match has not started yet.")
        if (state.status != MatchStatus.ACTIVE) {
            return reject(state, "Match is not active.")
        }
        if (actorId == turn.activePlayerId) {
            return reject(state, "The active player cannot doubt their own card.")
        }
        if (turn.phase != TurnPhase.AWAITING_PLACEMENT && turn.phase != TurnPhase.CARD_POSITIONED) {
            return reject(state, "A doubt can only be armed after the active player draws a card.")
        }

        val doubter = state.requirePlayer(actorId) ?: return reject(state, "Unknown player.")
        if (doubter.coins <= 0) {
            return reject(state, "A coin is required to arm a doubt.")
        }

        val nextDoubt = when (val currentDoubt = state.doubt) {
            null -> DoubtState(
                doubterId = actorId,
                targetPlayerId = turn.activePlayerId,
                phase = DoubtPhase.ARMED,
            )

            else -> {
                if (currentDoubt.doubterId != actorId || currentDoubt.phase != DoubtPhase.ARMED) {
                    return reject(state, "Another player already armed a doubt.")
                }
                null
            }
        }

        return accept(
            state.copy(
                revision = state.revision + 1,
                doubt = nextDoubt,
            ),
        )
    }

    private fun movePendingCard(
        state: GameState,
        actorId: PlayerId,
        requestedSlotIndex: Int,
    ): ReducerResult {
        val turn = state.turn ?: return reject(state, "Match has not started yet.")
        if (state.status != MatchStatus.ACTIVE) {
            return reject(state, "Match is not active.")
        }
        if (turn.activePlayerId != actorId) {
            return reject(state, "Only the active player can move the pending card.")
        }

        val activePlayer = state.requirePlayer(actorId) ?: return reject(state, "Unknown player.")
        val pendingCard = activePlayer.pendingCard ?: return reject(state, "There is no pending card to place.")
        val snappedSlot = placementValidator.snapSlot(activePlayer.timeline.cards.size, requestedSlotIndex)
        val updatedPlayer = activePlayer.copy(
            pendingCard = pendingCard.copy(proposedSlotIndex = snappedSlot),
        )

        val nextState = state.copy(
            revision = state.revision + 1,
            players = state.players.replacePlayer(updatedPlayer),
            turn = turn.copy(phase = TurnPhase.CARD_POSITIONED),
        )

        return accept(nextState)
    }

    private fun moveDoubtCard(
        state: GameState,
        actorId: PlayerId,
        requestedSlotIndex: Int,
    ): ReducerResult {
        val turn = state.turn ?: return reject(state, "Match has not started yet.")
        if (state.status != MatchStatus.ACTIVE) {
            return reject(state, "Match is not active.")
        }
        if (turn.phase != TurnPhase.AWAITING_DOUBT_PLACEMENT && turn.phase != TurnPhase.DOUBT_POSITIONED) {
            return reject(state, "Doubt placement is not active.")
        }

        val doubt = state.doubt ?: return reject(state, "There is no active doubt.")
        if (doubt.doubterId != actorId) {
            return reject(state, "Only the doubting player can move the doubt card.")
        }

        val targetPlayer = state.requirePlayer(doubt.targetPlayerId) ?: return reject(state, "Unknown target player.")
        val pendingCard = targetPlayer.pendingCard ?: return reject(state, "The target player has no card to doubt.")
        val snappedSlot = placementValidator.snapSlot(targetPlayer.timeline.cards.size, requestedSlotIndex)
        val nextState = state.copy(
            revision = state.revision + 1,
            doubt = doubt.copy(
                phase = DoubtPhase.POSITIONED,
                proposedSlotIndex = snappedSlot,
            ),
            turn = turn.copy(phase = TurnPhase.DOUBT_POSITIONED),
            lastResolution = null,
        )

        return accept(nextState)
    }

    private fun adjustPlayerCoins(
        state: GameState,
        actorId: PlayerId,
        playerId: PlayerId,
        delta: Int,
    ): ReducerResult {
        if (actorId != state.hostId) {
            return reject(state, "Only the host can adjust coins.")
        }
        if (delta == 0) {
            return reject(state, "Coin adjustment cannot be zero.")
        }

        val player = state.requirePlayer(playerId) ?: return reject(state, "Unknown player.")
        val nextCoins = maxOf(0, player.coins + delta)
        if (nextCoins == player.coins) {
            return reject(state, "Coin count is already at zero.")
        }

        return accept(
            state.copy(
                revision = state.revision + 1,
                players = state.players.replacePlayer(player.copy(coins = nextCoins)),
            ),
        )
    }

    private fun endTurn(
        state: GameState,
        actorId: PlayerId,
    ): ReducerResult {
        return when (state.turn?.phase) {
            TurnPhase.AWAITING_DOUBT_PLACEMENT,
            TurnPhase.DOUBT_POSITIONED,
            -> resolveDoubt(state, actorId)

            else -> resolveTurn(state, actorId)
        }
    }

    private fun resolveTurn(
        state: GameState,
        actorId: PlayerId,
    ): ReducerResult {
        val turn = state.turn ?: return reject(state, "Match has not started yet.")
        if (state.status != MatchStatus.ACTIVE) {
            return reject(state, "Match is not active.")
        }
        if (turn.activePlayerId != actorId) {
            return reject(state, "Only the active player can end the turn.")
        }

        val activePlayer = state.requirePlayer(actorId) ?: return reject(state, "Unknown player.")
        val pendingCard = activePlayer.pendingCard ?: return reject(state, "There is no pending card to resolve.")
        val armedDoubt = state.doubt
        if (armedDoubt?.phase == DoubtPhase.ARMED) {
            val nextState = state.copy(
                revision = state.revision + 1,
                turn = turn.copy(phase = TurnPhase.AWAITING_DOUBT_PLACEMENT),
                doubt = armedDoubt.copy(
                    targetPlayerId = actorId,
                    phase = DoubtPhase.POSITIONING,
                    proposedSlotIndex = pendingCard.proposedSlotIndex,
                ),
                lastResolution = null,
            )

            return accept(nextState)
        }
        val validation = placementValidator.validate(
            timeline = activePlayer.timeline.cards,
            entry = pendingCard.entry,
            requestedSlotIndex = pendingCard.proposedSlotIndex,
        )

        val resolvedPlayer = if (validation.isValid) {
            activePlayer.copy(
                score = activePlayer.score + 1,
                timeline = activePlayer.timeline.insertAt(validation.slotIndex, pendingCard.entry),
                pendingCard = null,
            )
        } else {
            activePlayer.copy(pendingCard = null)
        }
        val reachedWinningTimeline = validation.isValid && resolvedPlayer.timeline.cards.size >= WINNING_TIMELINE_SIZE

        val nextDiscardPile = if (validation.isValid) {
            state.discardPile
        } else {
            state.discardPile + pendingCard.entry
        }

        val resolution = TurnResolution(
            playerId = actorId,
            cardId = pendingCard.entry.id,
            attemptedSlotIndex = validation.slotIndex,
            correct = validation.isValid,
            releaseYear = pendingCard.entry.releaseYear,
            message = when {
                reachedWinningTimeline -> "Correct placement. Timeline complete."
                validation.isValid -> "Correct placement."
                else -> "Incorrect placement. Card was discarded."
            },
        )

        val deckExhausted = state.deck.size == 0
        val matchComplete = reachedWinningTimeline || deckExhausted
        val nextActivePlayerIndex = if (matchComplete) {
            state.activePlayerIndex
        } else {
            (state.activePlayerIndex + 1) % state.players.size
        }
        val nextTurn = if (matchComplete) {
            turn.copy(phase = TurnPhase.COMPLETE)
        } else {
            TurnState(
                number = turn.number + 1,
                activePlayerId = state.players[nextActivePlayerIndex].id,
                phase = TurnPhase.WAITING_FOR_DRAW,
            )
        }

        val nextState = state.copy(
            revision = state.revision + 1,
            status = if (matchComplete) MatchStatus.COMPLETE else MatchStatus.ACTIVE,
            activePlayerIndex = nextActivePlayerIndex,
            players = state.players.replacePlayer(resolvedPlayer),
            discardPile = nextDiscardPile,
            turn = nextTurn,
            lastResolution = resolution,
        )

        return accept(nextState)
    }

    private fun resolveDoubt(
        state: GameState,
        actorId: PlayerId,
    ): ReducerResult {
        val turn = state.turn ?: return reject(state, "Match has not started yet.")
        if (state.status != MatchStatus.ACTIVE) {
            return reject(state, "Match is not active.")
        }

        val doubt = state.doubt ?: return reject(state, "There is no active doubt.")
        if (doubt.doubterId != actorId) {
            return reject(state, "Only the doubting player can resolve the doubt.")
        }

        val targetPlayer = state.requirePlayer(doubt.targetPlayerId) ?: return reject(state, "Unknown target player.")
        val pendingCard = targetPlayer.pendingCard ?: return reject(state, "The target player has no pending card.")
        val doubtSlotIndex = doubt.proposedSlotIndex ?: return reject(state, "The doubt card has not been positioned yet.")
        val doubter = state.requirePlayer(doubt.doubterId) ?: return reject(state, "Unknown doubting player.")

        val targetValidation = placementValidator.validate(
            timeline = targetPlayer.timeline.cards,
            entry = pendingCard.entry,
            requestedSlotIndex = pendingCard.proposedSlotIndex,
        )
        val doubtValidation = placementValidator.validate(
            timeline = targetPlayer.timeline.cards,
            entry = pendingCard.entry,
            requestedSlotIndex = doubtSlotIndex,
        )
        val doubtSpentPlayer = doubter.copy(coins = maxOf(0, doubter.coins - 1))
        val stealSucceeded = !targetValidation.isValid && doubtValidation.isValid
        val doubterInsertionSlot = correctTimelineInsertionSlot(doubtSpentPlayer.timeline.cards, pendingCard.entry)

        val nextPlayers: List<PlayerState>
        val nextDiscardPile: List<com.hitster.core.model.PlaylistEntry>
        val resolution: TurnResolution
        val reachedWinningTimeline: Boolean

        if (stealSucceeded) {
            val resolvedTarget = targetPlayer.copy(pendingCard = null)
            val resolvedDoubter = doubtSpentPlayer.copy(
                score = doubtSpentPlayer.score + 1,
                timeline = doubtSpentPlayer.timeline.insertAt(doubterInsertionSlot, pendingCard.entry),
            )
            nextPlayers = state.players
                .replacePlayer(resolvedTarget)
                .replacePlayer(resolvedDoubter)
            nextDiscardPile = state.discardPile
            reachedWinningTimeline = resolvedDoubter.timeline.cards.size >= WINNING_TIMELINE_SIZE
            resolution = TurnResolution(
                playerId = doubt.doubterId,
                cardId = pendingCard.entry.id,
                attemptedSlotIndex = doubterInsertionSlot,
                correct = true,
                releaseYear = pendingCard.entry.releaseYear,
                message = when {
                    reachedWinningTimeline -> "Doubt successful. Timeline complete."
                    else -> "Doubt successful. Card stolen."
                },
            )
        } else if (targetValidation.isValid) {
            val resolvedTarget = targetPlayer.copy(
                score = targetPlayer.score + 1,
                timeline = targetPlayer.timeline.insertAt(targetValidation.slotIndex, pendingCard.entry),
                pendingCard = null,
            )
            nextPlayers = state.players
                .replacePlayer(resolvedTarget)
                .replacePlayer(doubtSpentPlayer)
            nextDiscardPile = state.discardPile
            reachedWinningTimeline = resolvedTarget.timeline.cards.size >= WINNING_TIMELINE_SIZE
            resolution = TurnResolution(
                playerId = targetPlayer.id,
                cardId = pendingCard.entry.id,
                attemptedSlotIndex = targetValidation.slotIndex,
                correct = true,
                releaseYear = pendingCard.entry.releaseYear,
                message = when {
                    reachedWinningTimeline -> "Correct placement. Timeline complete."
                    else -> "Correct placement."
                },
            )
        } else {
            val resolvedTarget = targetPlayer.copy(pendingCard = null)
            nextPlayers = state.players
                .replacePlayer(resolvedTarget)
                .replacePlayer(doubtSpentPlayer)
            nextDiscardPile = state.discardPile + pendingCard.entry
            reachedWinningTimeline = false
            resolution = TurnResolution(
                playerId = targetPlayer.id,
                cardId = pendingCard.entry.id,
                attemptedSlotIndex = targetValidation.slotIndex,
                correct = false,
                releaseYear = pendingCard.entry.releaseYear,
                message = "Incorrect placement. Card was discarded.",
            )
        }

        val deckExhausted = state.deck.size == 0
        val matchComplete = reachedWinningTimeline || deckExhausted
        val nextActivePlayerIndex = if (matchComplete) {
            state.activePlayerIndex
        } else {
            (state.activePlayerIndex + 1) % state.players.size
        }
        val nextTurn = if (matchComplete) {
            turn.copy(phase = TurnPhase.COMPLETE)
        } else {
            TurnState(
                number = turn.number + 1,
                activePlayerId = state.players[nextActivePlayerIndex].id,
                phase = TurnPhase.WAITING_FOR_DRAW,
            )
        }

        return accept(
            state.copy(
                revision = state.revision + 1,
                status = if (matchComplete) MatchStatus.COMPLETE else MatchStatus.ACTIVE,
                activePlayerIndex = nextActivePlayerIndex,
                players = nextPlayers,
                discardPile = nextDiscardPile,
                turn = nextTurn,
                doubt = null,
                lastResolution = resolution,
            ),
        )
    }

    private fun accept(
        state: GameState,
        vararg effects: GameEffect,
    ): ReducerResult {
        return ReducerResult.Accepted(
            state = state,
            effects = effects.toList() + GameEffect.PublishSnapshot(state),
        )
    }

    private fun reject(
        state: GameState,
        reason: String,
    ): ReducerResult = ReducerResult.Rejected(state, reason)

    private fun correctTimelineInsertionSlot(
        timeline: List<com.hitster.core.model.PlaylistEntry>,
        entry: com.hitster.core.model.PlaylistEntry,
    ): Int {
        return timeline.indexOfFirst { existing -> existing.releaseYear > entry.releaseYear }
            .takeIf { it >= 0 }
            ?: timeline.size
    }

    private fun dealOpeningTimelines(
        players: List<PlayerState>,
        deck: DeckState,
    ): OpeningDeal? {
        var remainingDeck = deck
        val seededPlayers = players.map { player ->
            val draw = remainingDeck.drawTop() ?: return null
            remainingDeck = draw.nextDeck
            player.copy(
                score = 1,
                timeline = player.timeline.insertAt(0, draw.card),
                coins = 0,
                pendingCard = null,
            )
        }

        return OpeningDeal(
            players = seededPlayers,
            deck = remainingDeck,
        )
    }

    private data class OpeningDeal(
        val players: List<PlayerState>,
        val deck: DeckState,
    )

    private companion object {
        const val WINNING_TIMELINE_SIZE = 10
    }
}

private fun List<PlayerState>.replacePlayer(updatedPlayer: PlayerState): List<PlayerState> {
    return map { existing ->
        if (existing.id == updatedPlayer.id) updatedPlayer else existing
    }
}

private fun List<PlayerState>.movePlayer(
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

private fun sanitizeDisplayName(raw: String): String {
    return raw
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .take(MAX_DISPLAY_NAME_LENGTH)
}

private const val MAX_DISPLAY_NAME_LENGTH = 24
