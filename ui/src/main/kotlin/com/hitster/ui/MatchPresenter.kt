package com.hitster.ui

import com.hitster.core.game.GameCommand
import com.hitster.core.game.GameEffect
import com.hitster.core.game.HostGameReducer
import com.hitster.core.game.ReducerResult
import com.hitster.core.model.GameState
import com.hitster.core.model.MatchStatus
import com.hitster.core.model.PlayerId
import com.hitster.networking.GameStateDto
import com.hitster.networking.GameStateMapper
import com.hitster.playback.api.PlaybackCommandResult
import com.hitster.playback.api.PlaybackController
import com.hitster.playback.api.PlaybackEventListener
import com.hitster.playback.api.PlaybackIssue
import com.hitster.playback.api.PlaybackSessionState

class MatchPresenter(
    private val reducer: HostGameReducer,
    private val playbackController: PlaybackController,
    private val hostId: PlayerId,
    val localPlayerId: PlayerId,
    initialState: GameState,
) {
    var state: GameState = initialState
        private set

    var lastError: String? = null
        private set

    var lastPlaybackIssue: PlaybackIssue? = null
        private set

    var playbackSessionState: PlaybackSessionState = playbackController.currentState()
        private set

    var lastPublishedSnapshot: GameStateDto = GameStateMapper.toDto(initialState)
        private set

    val localPlayer: com.hitster.core.model.PlayerState?
        get() = state.requirePlayer(localPlayerId)

    init {
        playbackController.setListener(
            object : PlaybackEventListener {
                override fun onSessionStateChanged(sessionState: PlaybackSessionState) {
                    playbackSessionState = sessionState
                }

                override fun onIssue(issue: PlaybackIssue?) {
                    lastPlaybackIssue = issue
                }
            },
        )
    }

    fun startMatch() {
        if (requiresHostPlaybackPairing()) {
            lastError = "Pair Spotify before starting."
            return
        }
        dispatch(GameCommand.StartGame(actorId = hostId))
    }

    fun prepareHostPlayback() {
        when (val playbackResult = playbackController.prepareSession()) {
            is PlaybackCommandResult.Success -> {
                lastError = null
                lastPlaybackIssue = null
            }

            is PlaybackCommandResult.Failure -> {
                lastPlaybackIssue = playbackResult.issue
            }
        }
    }

    fun drawCard() {
        drawCardAs(localPlayerId)
    }

    fun movePendingCard(requestedSlotIndex: Int) {
        movePendingCardAs(localPlayerId, requestedSlotIndex)
    }

    fun endTurn() {
        endTurnAs(localPlayerId)
    }

    internal fun drawCardAs(actorId: PlayerId) {
        dispatch(GameCommand.DrawCard(actorId = actorId))
    }

    internal fun movePendingCardAs(
        actorId: PlayerId,
        requestedSlotIndex: Int,
    ) {
        dispatch(
            GameCommand.MovePendingCard(
                actorId = actorId,
                requestedSlotIndex = requestedSlotIndex,
            ),
        )
    }

    internal fun endTurnAs(actorId: PlayerId) {
        dispatch(GameCommand.EndTurn(actorId = actorId))
    }

    fun requiresHostPlaybackPairing(): Boolean {
        if (localPlayerId != hostId || state.status != MatchStatus.LOBBY) {
            return false
        }
        return playbackSessionState != PlaybackSessionState.Ready &&
            playbackSessionState !is PlaybackSessionState.Playing
    }

    private fun dispatch(command: GameCommand) {
        when (val result = reducer.reduce(state, command)) {
            is ReducerResult.Accepted -> {
                lastError = null
                state = result.state
                applyEffects(result.effects)
            }

            is ReducerResult.Rejected -> {
                lastError = result.reason
            }
        }
    }

    private fun applyEffects(effects: List<GameEffect>) {
        effects.forEach { effect ->
            when (effect) {
                is GameEffect.PlayTrack -> {
                    when (val playbackResult = playbackController.playTrack(effect.reference)) {
                        is PlaybackCommandResult.Success -> lastPlaybackIssue = null
                        is PlaybackCommandResult.Failure -> lastPlaybackIssue = playbackResult.issue
                    }
                }

                GameEffect.PausePlayback -> {
                    when (val playbackResult = playbackController.pause()) {
                        is PlaybackCommandResult.Success -> lastPlaybackIssue = null
                        is PlaybackCommandResult.Failure -> lastPlaybackIssue = playbackResult.issue
                    }
                }

                is GameEffect.PublishSnapshot -> {
                    lastPublishedSnapshot = GameStateMapper.toDto(effect.state)
                }
            }
        }
    }
}
