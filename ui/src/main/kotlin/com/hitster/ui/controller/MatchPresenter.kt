package com.hitster.ui

/**
 * Orchestrates reducer state, playback side effects, snapshot publication, and host-side command handling for a local player.
 */

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

    /** Starts the authoritative match once the lobby has enough players and host playback is ready. */
    override fun startMatch() {
        if (!canStartLobbyMatch()) {
            lastError = "At least one guest must join before starting."
            return
        }
        if (requiresHostPlaybackPairing()) {
            lastError = "Pair Spotify before starting."
            return
        }
        dispatch(GameCommand.StartGame(actorId = hostId))
    }

    /** Attempts to prepare host playback from the lobby and surfaces any platform issue to the UI. */
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

    /** Draws a card as the local actor. */
    override fun drawCard() {
        drawCardAs(localPlayerId)
    }

    /** Requests a redraw as the local actor. */
    override fun redrawCard() {
        redrawCardAs(localPlayerId)
    }

    /** Toggles the local actor's doubt state. */
    override fun toggleDoubt() {
        toggleDoubtAs(localPlayerId)
    }

    /** Moves the local actor's pending card to a requested slot. */
    override fun movePendingCard(requestedSlotIndex: Int) {
        movePendingCardAs(localPlayerId, requestedSlotIndex)
    }

    /** Moves the local actor's doubt placement card to a requested slot. */
    override fun moveDoubtCard(requestedSlotIndex: Int) {
        moveDoubtCardAs(localPlayerId, requestedSlotIndex)
    }

    /** Applies a host coin adjustment on behalf of the local actor. */
    override fun adjustPlayerCoins(playerId: PlayerId, delta: Int) {
        adjustPlayerCoinsAs(localPlayerId, playerId, delta)
    }

    /** Completes the current local actor step, which may be a normal turn or a doubt placement. */
    override fun endTurn() {
        endTurnAs(localPlayerId)
    }

    /** Host-side helper used by automation and tests to draw for an arbitrary actor. */
    internal fun drawCardAs(actorId: PlayerId) {
        dispatch(GameCommand.DrawCard(actorId = actorId))
    }

    /** Host-side helper used by automation and tests to redraw for an arbitrary actor. */
    internal fun redrawCardAs(actorId: PlayerId) {
        dispatch(GameCommand.RedrawCard(actorId = actorId))
    }

    /** Host-side helper used by automation and tests to move an arbitrary actor's pending card. */
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

    /** Host-side helper used by automation and tests to arm or clear doubt for an arbitrary actor. */
    internal fun toggleDoubtAs(actorId: PlayerId) {
        dispatch(GameCommand.ToggleDoubt(actorId = actorId))
    }

    /** Host-side helper used by automation and tests to move an arbitrary actor's doubt card. */
    internal fun moveDoubtCardAs(
        actorId: PlayerId,
        requestedSlotIndex: Int,
    ) {
        dispatch(
            GameCommand.MoveDoubtCard(
                actorId = actorId,
                requestedSlotIndex = requestedSlotIndex,
            ),
        )
    }

    /** Host-side helper used by automation and tests to modify any player's coin count. */
    internal fun adjustPlayerCoinsAs(
        actorId: PlayerId,
        playerId: PlayerId,
        delta: Int,
    ) {
        dispatch(
            GameCommand.AdjustPlayerCoins(
                actorId = actorId,
                playerId = playerId,
                delta = delta,
            ),
        )
    }

    /** Host-side helper used by automation and tests to end a step for an arbitrary actor. */
    internal fun endTurnAs(actorId: PlayerId) {
        dispatch(GameCommand.EndTurn(actorId = actorId))
    }

    /** Returns `true` only when the host lobby must still block match start on playback readiness. */
    override fun requiresHostPlaybackPairing(): Boolean {
        if (!isLocalHost || state.status != MatchStatus.LOBBY) {
            return false
        }
        return playbackSessionState != PlaybackSessionState.Ready &&
            playbackSessionState !is PlaybackSessionState.Playing
    }

    /** Returns `true` when the local host is in the lobby and at least one guest has joined. */
    override fun canStartLobbyMatch(): Boolean {
        return isLocalHost &&
            state.status == MatchStatus.LOBBY &&
            state.players.size > 1
    }

    /** Converts transport DTO commands back into reducer commands on the authoritative host. */
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

            is ClientCommandDto.RedrawCard -> {
                dispatch(GameCommand.RedrawCard(actorId = PlayerId(command.actorId)))
            }

            is ClientCommandDto.ToggleDoubt -> {
                dispatch(GameCommand.ToggleDoubt(actorId = PlayerId(command.actorId)))
            }

            is ClientCommandDto.MovePendingCard -> {
                dispatch(
                    GameCommand.MovePendingCard(
                        actorId = PlayerId(command.actorId),
                        requestedSlotIndex = command.requestedSlotIndex,
                    ),
                )
            }

            is ClientCommandDto.MoveDoubtCard -> {
                dispatch(
                    GameCommand.MoveDoubtCard(
                        actorId = PlayerId(command.actorId),
                        requestedSlotIndex = command.requestedSlotIndex,
                    ),
                )
            }

            is ClientCommandDto.AdjustPlayerCoins -> {
                dispatch(
                    GameCommand.AdjustPlayerCoins(
                        actorId = PlayerId(command.actorId),
                        playerId = PlayerId(command.playerId),
                        delta = command.delta,
                    ),
                )
            }

            is ClientCommandDto.EndTurn -> {
                dispatch(GameCommand.EndTurn(actorId = PlayerId(command.actorId)))
            }
        }
    }

    /** Removes a lobby guest when their transport disconnects before the match has started. */
    fun handleRemoteDisconnect(actorId: String) {
        val playerId = PlayerId(actorId)
        val shouldRemove = synchronized(stateLock) {
            state.status == MatchStatus.LOBBY &&
                playerId != hostId &&
                state.players.any { it.id == playerId }
        }
        if (!shouldRemove) {
            return
        }
        dispatch(GameCommand.LeaveSession(playerId))
    }

    /** Runs one authoritative reducer step and applies any resulting playback or snapshot side effects. */
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

    /** Executes reducer-emitted side effects after the new state has already been committed. */
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

/** Resolves the actor id carried by any reducer command so rejection callbacks can target the right client. */
private fun GameCommand.actorId(): PlayerId {
    return when (this) {
        is GameCommand.JoinSession -> playerId
        is GameCommand.LeaveSession -> playerId
        is GameCommand.StartGame -> actorId
        is GameCommand.DrawCard -> actorId
        is GameCommand.RedrawCard -> actorId
        is GameCommand.ToggleDoubt -> actorId
        is GameCommand.MovePendingCard -> actorId
        is GameCommand.MoveDoubtCard -> actorId
        is GameCommand.AdjustPlayerCoins -> actorId
        is GameCommand.EndTurn -> actorId
    }
}
