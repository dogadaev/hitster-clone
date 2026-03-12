package com.hitster.core.game

import com.hitster.core.model.GameState
import com.hitster.core.model.MatchStatus
import com.hitster.core.model.DeckState
import com.hitster.core.model.PendingCard
import com.hitster.core.model.PlayerId
import com.hitster.core.model.PlayerState
import com.hitster.core.model.TurnPhase
import com.hitster.core.model.TurnResolution
import com.hitster.core.model.TurnState

class HostGameReducer(
    private val placementValidator: TimelinePlacementValidator = TimelinePlacementValidator(),
) {
    fun reduce(
        state: GameState,
        command: GameCommand,
    ): ReducerResult {
        return when (command) {
            is GameCommand.JoinSession -> joinSession(state, command)
            is GameCommand.StartGame -> startGame(state, command.actorId)
            is GameCommand.DrawCard -> drawCard(state, command.actorId)
            is GameCommand.MovePendingCard -> movePendingCard(state, command.actorId, command.requestedSlotIndex)
            is GameCommand.EndTurn -> endTurn(state, command.actorId)
        }
    }

    private fun joinSession(
        state: GameState,
        command: GameCommand.JoinSession,
    ): ReducerResult {
        if (state.status != MatchStatus.LOBBY) {
            return reject(state, "Players can only join while the match is still in the lobby.")
        }
        if (state.players.any { it.id == command.playerId }) {
            return reject(state, "Player '${command.playerId.value}' is already in the session.")
        }

        val nextState = state.copy(
            revision = state.revision + 1,
            players = state.players + PlayerState(
                id = command.playerId,
                displayName = command.displayName,
            ),
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
            lastResolution = null,
        )

        return accept(
            nextState,
            GameEffect.PlayTrack(draw.card.playbackReference),
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

    private fun endTurn(
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

        return accept(
            nextState,
            GameEffect.PausePlayback,
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

    private fun dealOpeningTimelines(
        players: List<PlayerState>,
        deck: DeckState,
    ): OpeningDeal? {
        var remainingDeck = deck
        val seededPlayers = players.map { player ->
            val draw = remainingDeck.drawTop() ?: return null
            remainingDeck = draw.nextDeck
            player.copy(
                timeline = player.timeline.insertAt(0, draw.card),
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
