package com.hitster.core.game

import com.hitster.core.model.GameState
import com.hitster.core.model.PlaybackReference
import com.hitster.core.model.PlayerId

sealed interface GameCommand {
    data class JoinSession(
        val playerId: PlayerId,
        val displayName: String,
    ) : GameCommand

    data class LeaveSession(
        val playerId: PlayerId,
    ) : GameCommand

    data class StartGame(
        val actorId: PlayerId,
    ) : GameCommand

    data class DrawCard(
        val actorId: PlayerId,
    ) : GameCommand

    data class MovePendingCard(
        val actorId: PlayerId,
        val requestedSlotIndex: Int,
    ) : GameCommand

    data class EndTurn(
        val actorId: PlayerId,
    ) : GameCommand
}

sealed interface GameEffect {
    data class PlayTrack(
        val reference: PlaybackReference,
    ) : GameEffect

    data object PausePlayback : GameEffect

    data class PublishSnapshot(
        val state: GameState,
    ) : GameEffect
}

sealed interface ReducerResult {
    data class Accepted(
        val state: GameState,
        val effects: List<GameEffect>,
    ) : ReducerResult

    data class Rejected(
        val state: GameState,
        val reason: String,
    ) : ReducerResult
}
