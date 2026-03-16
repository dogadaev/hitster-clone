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
import com.hitster.networking.ClientCommandDto
import com.hitster.playback.api.PlaybackCommandResult
import com.hitster.playback.api.PlaybackController
import com.hitster.playback.api.PlaybackEventListener
import com.hitster.playback.api.PlaybackIssue
import com.hitster.playback.api.PlaybackSessionState

class MatchPresenter(
    private val reducer: HostGameReducer,
    private val playbackController: PlaybackController,
    private val hostId: PlayerId,
    override val localPlayerId: PlayerId,
    initialState: GameState,
) : MatchController {
    @Volatile
    override var state: GameState = initialState
        private set

    override val isLocalHost: Boolean = localPlayerId == hostId

    @Volatile
    override var lastError: String? = null
        private set

    @Volatile
    override var lastPlaybackIssue: PlaybackIssue? = null
        private set

    @Volatile
    override var playbackSessionState: PlaybackSessionState = playbackController.currentState()
        private set

    @Volatile
    var lastPublishedSnapshot: GameStateDto = GameStateMapper.toDto(initialState)
        private set

    private val stateLock = Any()

    var snapshotListener: ((GameStateDto) -> Unit)? = null
    var rejectionListener: ((String, String, Long) -> Unit)? = null

    override val localPlayer: com.hitster.core.model.PlayerState?
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

    override fun startMatch() {
        if (requiresHostPlaybackPairing()) {
            lastError = "Pair Spotify before starting."
            return
        }
        dispatch(GameCommand.StartGame(actorId = hostId))
    }

    override fun prepareHostPlayback() {
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

    override fun drawCard() {
        drawCardAs(localPlayerId)
    }

    override fun movePendingCard(requestedSlotIndex: Int) {
        movePendingCardAs(localPlayerId, requestedSlotIndex)
    }

    override fun endTurn() {
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

    override fun requiresHostPlaybackPairing(): Boolean {
        if (!isLocalHost || state.status != MatchStatus.LOBBY) {
            return false
        }
        return playbackSessionState != PlaybackSessionState.Ready &&
            playbackSessionState !is PlaybackSessionState.Playing
    }

    fun handleRemoteCommand(command: ClientCommandDto) {
        when (command) {
            is ClientCommandDto.JoinSession -> {
                dispatch(
                    GameCommand.JoinSession(
                        playerId = PlayerId(command.actorId),
                        displayName = command.displayName,
                    ),
                )
            }

            is ClientCommandDto.StartGame -> {
                dispatch(GameCommand.StartGame(actorId = PlayerId(command.actorId)))
            }

            is ClientCommandDto.DrawCard -> {
                dispatch(GameCommand.DrawCard(actorId = PlayerId(command.actorId)))
            }

            is ClientCommandDto.MovePendingCard -> {
                dispatch(
                    GameCommand.MovePendingCard(
                        actorId = PlayerId(command.actorId),
                        requestedSlotIndex = command.requestedSlotIndex,
                    ),
                )
            }

            is ClientCommandDto.EndTurn -> {
                dispatch(GameCommand.EndTurn(actorId = PlayerId(command.actorId)))
            }
        }
    }

    private fun dispatch(command: GameCommand) {
        synchronized(stateLock) {
            when (val result = reducer.reduce(state, command)) {
                is ReducerResult.Accepted -> {
                    lastError = null
                    state = result.state
                    applyEffects(result.effects)
                }

                is ReducerResult.Rejected -> {
                    lastError = result.reason
                    rejectionListener?.invoke(command.actorId().value, result.reason, result.state.revision)
                }
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
                    snapshotListener?.invoke(lastPublishedSnapshot)
                }
            }
        }
    }
}

private fun GameCommand.actorId(): PlayerId {
    return when (this) {
        is GameCommand.JoinSession -> playerId
        is GameCommand.StartGame -> actorId
        is GameCommand.DrawCard -> actorId
        is GameCommand.MovePendingCard -> actorId
        is GameCommand.EndTurn -> actorId
    }
}
