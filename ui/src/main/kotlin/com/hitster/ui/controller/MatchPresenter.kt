package com.hitster.ui.controller

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
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

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
    private val timerExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "match-presenter-timers").apply { isDaemon = true }
    }
    private var doubtWindowFuture: ScheduledFuture<*>? = null

    var snapshotListener: ((GameStateDto) -> Unit)? = null
    var rejectionListener: ((String, String, Long) -> Unit)? = null
    var playbackStateListener: ((PlaybackSessionState) -> Unit)? = null

    override val localPlayer: com.hitster.core.model.PlayerState?
        get() = state.requirePlayer(localPlayerId)

    init {
        playbackController.setListener(
            object : PlaybackEventListener {
                override fun onSessionStateChanged(sessionState: PlaybackSessionState) {
                    playbackSessionState = sessionState
                    playbackStateListener?.invoke(sessionState)
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

    /** Updates the local player's lobby display name. */
    override fun updateLocalDisplayName(displayName: String) {
        dispatch(
            GameCommand.UpdatePlayerName(
                actorId = localPlayerId,
                displayName = displayName,
            ),
        )
    }

    /** Reorders the lobby roster on behalf of the local host. */
    override fun reorderLobbyPlayer(playerId: PlayerId, targetIndex: Int) {
        dispatch(
            GameCommand.ReorderLobbyPlayers(
                actorId = localPlayerId,
                playerId = playerId,
                targetIndex = targetIndex,
            ),
        )
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

    /** Toggles local playback for the active actor without changing authoritative match state. */
    override fun togglePlayback() {
        togglePlaybackAs(localPlayerId)
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

    /** Host-side helper used by transport handling and tests to pause or resume playback for an arbitrary actor. */
    internal fun togglePlaybackAs(actorId: PlayerId, remoteCommand: Boolean = false) {
        val action = synchronized(stateLock) {
            when {
                state.status != MatchStatus.ACTIVE -> PlaybackToggleAction.Reject("Playback controls are only available during an active match.")
                state.turn?.activePlayerId != actorId -> PlaybackToggleAction.Reject("Only the current player can control playback.")
                playbackSessionState is PlaybackSessionState.Playing -> PlaybackToggleAction.Pause
                playbackSessionState is PlaybackSessionState.Paused -> PlaybackToggleAction.Resume
                else -> PlaybackToggleAction.Reject("There is no active preview track to control.")
            }
        }

        when (action) {
            is PlaybackToggleAction.Reject -> {
                if (remoteCommand) {
                    rejectionListener?.invoke(actorId.value, action.reason, state.revision)
                } else {
                    lastError = action.reason
                }
            }

            PlaybackToggleAction.Pause -> {
                lastError = null
                when (val playbackResult = playbackController.pause()) {
                    is PlaybackCommandResult.Success -> lastPlaybackIssue = null
                    is PlaybackCommandResult.Failure -> {
                        lastPlaybackIssue = playbackResult.issue
                        if (remoteCommand) {
                            rejectionListener?.invoke(actorId.value, playbackResult.issue.message, state.revision)
                        } else {
                            lastError = playbackResult.issue.message
                        }
                    }
                }
            }

            PlaybackToggleAction.Resume -> {
                lastError = null
                when (val playbackResult = playbackController.resume()) {
                    is PlaybackCommandResult.Success -> lastPlaybackIssue = null
                    is PlaybackCommandResult.Failure -> {
                        lastPlaybackIssue = playbackResult.issue
                        if (remoteCommand) {
                            rejectionListener?.invoke(actorId.value, playbackResult.issue.message, state.revision)
                        } else {
                            lastError = playbackResult.issue.message
                        }
                    }
                }
            }
        }
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
        return playbackSessionState == PlaybackSessionState.Disconnected ||
            playbackSessionState == PlaybackSessionState.Connecting
    }

    /** Returns `true` when the local host is in the lobby and at least one guest has joined. */
    override fun canStartLobbyMatch(): Boolean {
        return isLocalHost &&
            state.status == MatchStatus.LOBBY &&
            state.players.size > 1
    }

    /** Releases playback listeners and background timers owned by the presenter. */
    override fun dispose() {
        synchronized(stateLock) {
            doubtWindowFuture?.cancel(false)
            doubtWindowFuture = null
        }
        timerExecutor.shutdownNow()
        playbackController.setListener(null)
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

            is ClientCommandDto.UpdatePlayerName -> {
                dispatch(
                    GameCommand.UpdatePlayerName(
                        actorId = PlayerId(command.actorId),
                        displayName = command.displayName,
                    ),
                )
            }

            is ClientCommandDto.ReorderLobbyPlayers -> {
                dispatch(
                    GameCommand.ReorderLobbyPlayers(
                        actorId = PlayerId(command.actorId),
                        playerId = PlayerId(command.playerId),
                        targetIndex = command.targetIndex,
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

            is ClientCommandDto.TogglePlayback -> {
                togglePlaybackAs(actorId = PlayerId(command.actorId), remoteCommand = true)
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
                    rescheduleDoubtWindowFinalizer()
                    applyEffects(result.effects)
                }

                is ReducerResult.Rejected -> {
                    if (command !is GameCommand.FinalizeDoubtWindow) {
                        lastError = result.reason
                        rejectionListener?.invoke(command.actorId().value, result.reason, result.state.revision)
                    }
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

    /** Keeps one host-side timer aligned with the active shared doubt window so it resolves without UI input. */
    private fun rescheduleDoubtWindowFinalizer() {
        doubtWindowFuture?.cancel(false)
        doubtWindowFuture = null
        if (!isLocalHost) {
            return
        }
        val turn = state.turn ?: return
        val deadline = turn.doubtWindowEndsAtEpochMillis ?: return
        if (turn.phase != com.hitster.core.model.TurnPhase.AWAITING_DOUBT_WINDOW) {
            return
        }
        val delayMillis = maxOf(0L, deadline - System.currentTimeMillis())
        val expectedTurnNumber = turn.number
        doubtWindowFuture = timerExecutor.schedule(
            {
                val shouldFinalize = synchronized(stateLock) {
                    val latestTurn = state.turn
                    state.status == MatchStatus.ACTIVE &&
                        latestTurn?.phase == com.hitster.core.model.TurnPhase.AWAITING_DOUBT_WINDOW &&
                        latestTurn.number == expectedTurnNumber &&
                        latestTurn.doubtWindowEndsAtEpochMillis == deadline &&
                        System.currentTimeMillis() >= deadline
                }
                if (shouldFinalize) {
                    dispatch(GameCommand.FinalizeDoubtWindow(actorId = hostId))
                }
            },
            delayMillis,
            TimeUnit.MILLISECONDS,
        )
    }
}

/** Resolves the actor id carried by any reducer command so rejection callbacks can target the right client. */
private fun GameCommand.actorId(): PlayerId {
    return when (this) {
        is GameCommand.JoinSession -> playerId
        is GameCommand.UpdatePlayerName -> actorId
        is GameCommand.ReorderLobbyPlayers -> actorId
        is GameCommand.LeaveSession -> playerId
        is GameCommand.StartGame -> actorId
        is GameCommand.DrawCard -> actorId
        is GameCommand.RedrawCard -> actorId
        is GameCommand.ToggleDoubt -> actorId
        is GameCommand.MovePendingCard -> actorId
        is GameCommand.MoveDoubtCard -> actorId
        is GameCommand.AdjustPlayerCoins -> actorId
        is GameCommand.EndTurn -> actorId
        is GameCommand.FinalizeDoubtWindow -> actorId
    }
}

private sealed interface PlaybackToggleAction {
    data object Pause : PlaybackToggleAction

    data object Resume : PlaybackToggleAction

    data class Reject(
        val reason: String,
    ) : PlaybackToggleAction
}
