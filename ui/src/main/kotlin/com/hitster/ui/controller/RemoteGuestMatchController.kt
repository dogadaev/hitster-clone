package com.hitster.ui.controller

/**
 * Guest-side controller that applies authoritative snapshots from the host while providing optimistic local drag feedback.
 */

import com.hitster.core.model.DeckState
import com.hitster.core.model.GameState
import com.hitster.core.model.MatchStatus
import com.hitster.core.model.PlayerId
import com.hitster.core.model.PlayerState
import com.hitster.core.model.DoubtPhase
import com.hitster.core.model.SessionId
import com.hitster.core.model.TurnPhase
import com.hitster.networking.ClientCommandDto
import com.hitster.networking.GameStateMapper
import com.hitster.networking.HostEventDto
import com.hitster.networking.SessionAdvertisementDto
import com.hitster.playback.api.PlaybackIssue
import com.hitster.playback.api.PlaybackSessionState

class RemoteGuestMatchController(
    private val advertisement: SessionAdvertisementDto,
    override val localPlayerId: PlayerId,
) : MatchController {
    private lateinit var client: GuestSessionClient

    override var state: GameState = GameState(
        sessionId = SessionId(advertisement.sessionId),
        hostId = PlayerId(advertisement.hostPlayerId),
        status = MatchStatus.LOBBY,
        players = emptyList(),
        deck = DeckState(emptyList()),
    )
        private set

    override var lastError: String? = null
        private set

    override var connectionStatus: String? = "Preparing guest connection..."
        private set

    override val lastPlaybackIssue: PlaybackIssue? = null

    override val playbackSessionState: PlaybackSessionState = PlaybackSessionState.Ready

    override val isLocalHost: Boolean = false

    override val localPlayer: PlayerState?
        get() = state.requirePlayer(localPlayerId)

    /** Attaches the concrete transport client before the guest join flow begins. */
    fun attachClient(client: GuestSessionClient) {
        this.client = client
        connectionStatus = "Guest client attached."
    }

    /** Starts the guest join flow against the discovered host advertisement. */
    fun connect() {
        connectionStatus = "Opening guest connection..."
        client.connect()
    }

    override fun startMatch() = Unit

    override fun prepareHostPlayback() = Unit

    override fun drawCard() {
        client.sendCommand(ClientCommandDto.DrawCard(actorId = localPlayerId.value))
    }

    override fun redrawCard() {
        client.sendCommand(ClientCommandDto.RedrawCard(actorId = localPlayerId.value))
    }

    override fun toggleDoubt() {
        client.sendCommand(ClientCommandDto.ToggleDoubt(actorId = localPlayerId.value))
    }

    override fun movePendingCard(requestedSlotIndex: Int) {
        applyOptimisticPendingMove(requestedSlotIndex)
        client.sendCommand(
            ClientCommandDto.MovePendingCard(
                actorId = localPlayerId.value,
                requestedSlotIndex = requestedSlotIndex,
            ),
        )
    }

    override fun moveDoubtCard(requestedSlotIndex: Int) {
        applyOptimisticDoubtMove(requestedSlotIndex)
        client.sendCommand(
            ClientCommandDto.MoveDoubtCard(
                actorId = localPlayerId.value,
                requestedSlotIndex = requestedSlotIndex,
            ),
        )
    }

    override fun adjustPlayerCoins(playerId: PlayerId, delta: Int) {
        client.sendCommand(
            ClientCommandDto.AdjustPlayerCoins(
                actorId = localPlayerId.value,
                playerId = playerId.value,
                delta = delta,
            ),
        )
    }

    override fun endTurn() {
        client.sendCommand(ClientCommandDto.EndTurn(actorId = localPlayerId.value))
    }

    override fun requiresHostPlaybackPairing(): Boolean = false

    override fun canStartLobbyMatch(): Boolean = false

    /** Closes the guest transport when the shared app disposes the controller. */
    override fun dispose() {
        if (this::client.isInitialized) {
            client.close()
        }
    }

    /** Applies authoritative host events and clears optimistic state when real snapshots arrive. */
    internal fun handleEvent(event: HostEventDto) {
        when (event) {
            is HostEventDto.CommandRejected -> {
                if (event.actorId == localPlayerId.value) {
                    lastError = event.reason
                }
                connectionStatus = "Host rejected the latest guest command."
            }

            is HostEventDto.SnapshotPublished -> {
                lastError = null
                state = GameStateMapper.fromDto(event.state)
                connectionStatus = when {
                    state.requirePlayer(localPlayerId) != null ->
                        "Host snapshot confirmed ${localPlayerId.value}."

                    else ->
                        "Snapshot received, waiting for ${localPlayerId.value}. Players: ${
                            state.players.joinToString(", ") { it.id.value }
                        }"
                }
            }
        }
    }

    /** Surfaces guest disconnect reasons to the waiting or reconnecting UI. */
    internal fun handleDisconnect(reason: String) {
        lastError = reason
        connectionStatus = reason
    }

    /** Updates transient connection text shown while the guest transport is connecting or polling. */
    internal fun updateConnectionStatus(status: String) {
        connectionStatus = status
    }

    /** Mirrors the latest pending-card drag locally until the next authoritative snapshot arrives. */
    private fun applyOptimisticPendingMove(requestedSlotIndex: Int) {
        val turn = state.turn ?: return
        val player = localPlayer ?: return
        val pendingCard = player.pendingCard ?: return
        val snappedSlot = requestedSlotIndex.coerceIn(0, player.timeline.cards.size)
        state = state.copy(
            players = state.players.map { candidate ->
                if (candidate.id == localPlayerId) {
                    candidate.copy(pendingCard = pendingCard.copy(proposedSlotIndex = snappedSlot))
                } else {
                    candidate
                }
            },
            turn = if (turn.activePlayerId == localPlayerId) {
                turn.copy(phase = TurnPhase.CARD_POSITIONED)
            } else {
                turn
            },
        )
    }

    /** Mirrors the latest doubt-placement drag locally until the next authoritative snapshot arrives. */
    private fun applyOptimisticDoubtMove(requestedSlotIndex: Int) {
        val turn = state.turn ?: return
        val doubt = state.doubt ?: return
        if (doubt.doubterId != localPlayerId) {
            return
        }
        val targetPlayer = state.requirePlayer(doubt.targetPlayerId) ?: return
        val snappedSlot = requestedSlotIndex.coerceIn(0, targetPlayer.timeline.cards.size)
        state = state.copy(
            doubt = doubt.copy(
                phase = DoubtPhase.POSITIONED,
                proposedSlotIndex = snappedSlot,
            ),
            turn = turn.copy(phase = TurnPhase.DOUBT_POSITIONED),
            lastResolution = null,
        )
    }
}
