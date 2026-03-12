package com.hitster.networking

import com.hitster.core.model.GameState
import com.hitster.core.model.MatchStatus
import com.hitster.core.model.PendingCard
import com.hitster.core.model.PlayerState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val CURRENT_PROTOCOL_VERSION: Int = 1

@Serializable
enum class TransportModeDto {
    LOCAL_NETWORK,
    REMOTE_SERVER,
    BLUETOOTH,
}

@Serializable
data class SessionAdvertisementDto(
    val protocolVersion: Int = CURRENT_PROTOCOL_VERSION,
    val sessionId: String,
    val hostPlayerId: String,
    val hostDisplayName: String,
    val playerCount: Int,
    val transportMode: TransportModeDto = TransportModeDto.LOCAL_NETWORK,
)

@Serializable
sealed class ClientCommandDto {
    abstract val actorId: String

    @Serializable
    @SerialName("join_session")
    data class JoinSession(
        override val actorId: String,
        val displayName: String,
    ) : ClientCommandDto()

    @Serializable
    @SerialName("start_game")
    data class StartGame(
        override val actorId: String,
    ) : ClientCommandDto()

    @Serializable
    @SerialName("draw_card")
    data class DrawCard(
        override val actorId: String,
    ) : ClientCommandDto()

    @Serializable
    @SerialName("move_pending_card")
    data class MovePendingCard(
        override val actorId: String,
        val requestedSlotIndex: Int,
    ) : ClientCommandDto()

    @Serializable
    @SerialName("end_turn")
    data class EndTurn(
        override val actorId: String,
    ) : ClientCommandDto()
}

@Serializable
sealed class HostEventDto {
    @Serializable
    @SerialName("snapshot")
    data class SnapshotPublished(
        val state: GameStateDto,
    ) : HostEventDto()

    @Serializable
    @SerialName("command_rejected")
    data class CommandRejected(
        val reason: String,
        val revision: Long,
    ) : HostEventDto()
}

@Serializable
data class GameStateDto(
    val sessionId: String,
    val hostId: String,
    val revision: Long,
    val status: MatchStatusDto,
    val activePlayerIndex: Int,
    val deckRemaining: Int,
    val discardPileCardIds: List<String>,
    val players: List<PlayerStateDto>,
    val turn: TurnStateDto?,
    val lastResolution: TurnResolutionDto?,
)

@Serializable
enum class MatchStatusDto {
    LOBBY,
    ACTIVE,
    COMPLETE,
}

@Serializable
data class PlayerStateDto(
    val id: String,
    val displayName: String,
    val connected: Boolean,
    val score: Int,
    val timeline: List<TimelineCardDto>,
    val pendingCard: PendingCardDto?,
)

@Serializable
data class TimelineCardDto(
    val id: String,
    val title: String,
    val artist: String,
    val releaseYear: Int,
    val spotifyUri: String,
    val coverImageUrl: String? = null,
)

@Serializable
data class PendingCardDto(
    val id: String,
    val proposedSlotIndex: Int,
)

@Serializable
data class TurnStateDto(
    val number: Int,
    val activePlayerId: String,
    val phase: String,
)

@Serializable
data class TurnResolutionDto(
    val playerId: String,
    val cardId: String,
    val attemptedSlotIndex: Int,
    val correct: Boolean,
    val releaseYear: Int,
    val message: String,
)

interface SessionTransport {
    fun startHosting(
        advertisement: SessionAdvertisementDto,
        hostListener: HostCommandListener,
    )

    fun joinSession(
        sessionId: String,
        clientListener: ClientEventListener,
    )

    fun sendCommand(command: ClientCommandDto)

    fun broadcast(event: HostEventDto)

    fun stop()
}

fun interface HostCommandListener {
    fun onCommand(command: ClientCommandDto)
}

fun interface ClientEventListener {
    fun onEvent(event: HostEventDto)
}

object GameStateMapper {
    fun toDto(state: GameState): GameStateDto {
        return GameStateDto(
            sessionId = state.sessionId.value,
            hostId = state.hostId.value,
            revision = state.revision,
            status = state.status.toDto(),
            activePlayerIndex = state.activePlayerIndex,
            deckRemaining = state.deck.size,
            discardPileCardIds = state.discardPile.map { it.id },
            players = state.players.map(::toDto),
            turn = state.turn?.let {
                TurnStateDto(
                    number = it.number,
                    activePlayerId = it.activePlayerId.value,
                    phase = it.phase.name,
                )
            },
            lastResolution = state.lastResolution?.let {
                TurnResolutionDto(
                    playerId = it.playerId.value,
                    cardId = it.cardId,
                    attemptedSlotIndex = it.attemptedSlotIndex,
                    correct = it.correct,
                    releaseYear = it.releaseYear,
                    message = it.message,
                )
            },
        )
    }

    private fun toDto(player: PlayerState): PlayerStateDto {
        return PlayerStateDto(
            id = player.id.value,
            displayName = player.displayName,
            connected = player.connected,
            score = player.score,
            timeline = player.timeline.cards.map {
                TimelineCardDto(
                    id = it.id,
                    title = it.title,
                    artist = it.artist,
                    releaseYear = it.releaseYear,
                    spotifyUri = it.playbackReference.spotifyUri,
                    coverImageUrl = it.coverImageUrl,
                )
            },
            pendingCard = player.pendingCard?.toDto(),
        )
    }

    private fun PendingCard.toDto(): PendingCardDto {
        return PendingCardDto(
            id = entry.id,
            proposedSlotIndex = proposedSlotIndex,
        )
    }

    private fun MatchStatus.toDto(): MatchStatusDto {
        return when (this) {
            MatchStatus.LOBBY -> MatchStatusDto.LOBBY
            MatchStatus.ACTIVE -> MatchStatusDto.ACTIVE
            MatchStatus.COMPLETE -> MatchStatusDto.COMPLETE
        }
    }
}

