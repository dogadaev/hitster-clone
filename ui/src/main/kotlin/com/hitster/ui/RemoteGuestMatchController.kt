package com.hitster.ui

import com.hitster.core.model.DeckState
import com.hitster.core.model.GameState
import com.hitster.core.model.MatchStatus
import com.hitster.core.model.PlayerId
import com.hitster.core.model.PlayerState
import com.hitster.core.model.SessionId
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

    override val lastPlaybackIssue: PlaybackIssue? = null

    override val playbackSessionState: PlaybackSessionState = PlaybackSessionState.Ready

    override val isLocalHost: Boolean = false

    override val localPlayer: PlayerState?
        get() = state.requirePlayer(localPlayerId)

    fun attachClient(client: GuestSessionClient) {
        this.client = client
    }

    fun connect() {
        client.connect()
    }

    override fun startMatch() = Unit

    override fun prepareHostPlayback() = Unit

    override fun drawCard() {
        client.sendCommand(ClientCommandDto.DrawCard(actorId = localPlayerId.value))
    }

    override fun movePendingCard(requestedSlotIndex: Int) {
        client.sendCommand(
            ClientCommandDto.MovePendingCard(
                actorId = localPlayerId.value,
                requestedSlotIndex = requestedSlotIndex,
            ),
        )
    }

    override fun endTurn() {
        client.sendCommand(ClientCommandDto.EndTurn(actorId = localPlayerId.value))
    }

    override fun requiresHostPlaybackPairing(): Boolean = false

    override fun canStartLobbyMatch(): Boolean = false

    override fun dispose() {
        if (this::client.isInitialized) {
            client.close()
        }
    }

    internal fun handleEvent(event: HostEventDto) {
        when (event) {
            is HostEventDto.CommandRejected -> {
                if (event.actorId == localPlayerId.value) {
                    lastError = event.reason
                }
            }

            is HostEventDto.SnapshotPublished -> {
                lastError = null
                state = GameStateMapper.fromDto(event.state)
            }
        }
    }

    internal fun handleDisconnect(reason: String) {
        lastError = reason
    }
}
