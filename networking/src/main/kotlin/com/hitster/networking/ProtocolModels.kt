package com.hitster.networking

/**
 * Transport DTOs and mapping helpers shared by Android hosts, Android guests, and browser guests.
 */

import com.hitster.core.model.GameState
import com.hitster.core.model.MatchStatus
import com.hitster.core.model.DeckState
import com.hitster.core.model.DoubtPhase
import com.hitster.core.model.DoubtState
import com.hitster.core.model.PendingCard
import com.hitster.core.model.PlaybackReference
import com.hitster.core.model.PlayerId
import com.hitster.core.model.PlayerState
import com.hitster.core.model.PlayerTimeline
import com.hitster.core.model.PlaylistEntry
import com.hitster.core.model.SessionId
import com.hitster.core.model.TurnPhase
import com.hitster.core.model.TurnResolution
import com.hitster.core.model.TurnState
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
    val hostAddress: String,
    val serverPort: Int,
    val playerCount: Int,
    val guestJoinUrl: String? = null,
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
    @SerialName("update_player_name")
    data class UpdatePlayerName(
        override val actorId: String,
        val displayName: String,
    ) : ClientCommandDto()

    @Serializable
    @SerialName("reorder_lobby_players")
    data class ReorderLobbyPlayers(
        override val actorId: String,
        val playerId: String,
        val targetIndex: Int,
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
    @SerialName("redraw_card")
    data class RedrawCard(
        override val actorId: String,
    ) : ClientCommandDto()

    @Serializable
    @SerialName("toggle_playback")
    data class TogglePlayback(
        override val actorId: String,
    ) : ClientCommandDto()

    @Serializable
    @SerialName("toggle_doubt")
    data class ToggleDoubt(
        override val actorId: String,
    ) : ClientCommandDto()

    @Serializable
    @SerialName("move_pending_card")
    data class MovePendingCard(
        override val actorId: String,
        val requestedSlotIndex: Int,
    ) : ClientCommandDto()

    @Serializable
    @SerialName("move_doubt_card")
    data class MoveDoubtCard(
        override val actorId: String,
        val requestedSlotIndex: Int,
    ) : ClientCommandDto()

    @Serializable
    @SerialName("adjust_player_coins")
    data class AdjustPlayerCoins(
        override val actorId: String,
        val playerId: String,
        val delta: Int,
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
        val actorId: String,
        val reason: String,
        val revision: Long,
    ) : HostEventDto()

    @Serializable
    @SerialName("playback_state_changed")
    data class PlaybackStateChanged(
        val state: PlaybackStateDto,
    ) : HostEventDto()
}

@Serializable
data class PlaybackStateDto(
    val status: PlaybackStatusDto,
    val spotifyUri: String? = null,
)

@Serializable
enum class PlaybackStatusDto {
    DISCONNECTED,
    CONNECTING,
    READY,
    PLAYING,
    PAUSED,
}

@Serializable
data class GameStateDto(
    val sessionId: String,
    val hostId: String,
    val revision: Long,
    val hostTimeEpochMillis: Long = 0L,
    val status: MatchStatusDto,
    val activePlayerIndex: Int,
    val deckRemaining: Int,
    val discardPile: List<TimelineCardDto>,
    val players: List<PlayerStateDto>,
    val turn: TurnStateDto?,
    val doubt: DoubtStateDto?,
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
    val coins: Int,
    val timeline: List<TimelineCardDto>,
    val pendingCard: PendingCardDto?,
)

@Serializable
data class DoubtStateDto(
    val doubterId: String,
    val targetPlayerId: String,
    val phase: String,
    val proposedSlotIndex: Int? = null,
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
    val doubtWindowEndsAtEpochMillis: Long? = null,
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
    /** Starts the authoritative host transport and binds it to the supplied session advertisement and command sink. */
    fun startHosting(
        advertisement: SessionAdvertisementDto,
        hostListener: HostCommandListener,
    )

    /** Connects a client listener to an existing advertised session. */
    fun joinSession(
        sessionId: String,
        clientListener: ClientEventListener,
    )

    /** Sends one client-originated command across the active transport. */
    fun sendCommand(command: ClientCommandDto)

    /** Broadcasts one host event to every connected guest. */
    fun broadcast(event: HostEventDto)

    /** Stops any active host or guest transport resources. */
    fun stop()
}

fun interface HostCommandListener {
    /** Delivers a guest command to the authoritative host logic. */
    fun onCommand(command: ClientCommandDto)
}

fun interface ClientEventListener {
    /** Delivers a host-originated event to one guest client. */
    fun onEvent(event: HostEventDto)
}

object GameStateMapper {
    /** Converts the full authoritative shared state into a transport-safe DTO graph. */
    fun toDto(
        state: GameState,
        hostTimeEpochMillis: Long = System.currentTimeMillis(),
    ): GameStateDto {
        return GameStateDto(
            sessionId = state.sessionId.value,
            hostId = state.hostId.value,
            revision = state.revision,
            hostTimeEpochMillis = hostTimeEpochMillis,
            status = state.status.toDto(),
            activePlayerIndex = state.activePlayerIndex,
            deckRemaining = state.deck.size,
            discardPile = state.discardPile.map(::toTimelineCardDto),
            players = state.players.map(::toDto),
            turn = state.turn?.let {
                TurnStateDto(
                    number = it.number,
                    activePlayerId = it.activePlayerId.value,
                    phase = it.phase.name,
                    doubtWindowEndsAtEpochMillis = it.doubtWindowEndsAtEpochMillis,
                )
            },
            doubt = state.doubt?.let {
                DoubtStateDto(
                    doubterId = it.doubterId.value,
                    targetPlayerId = it.targetPlayerId.value,
                    phase = it.phase.name,
                    proposedSlotIndex = it.proposedSlotIndex,
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
            coins = player.coins,
            timeline = player.timeline.cards.map(::toTimelineCardDto),
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

    /**
     * Rebuilds a client-side view of the authoritative state from transport DTOs.
     *
     * Deck contents are reconstructed as placeholders because guests only need the remaining count, not hidden card identities.
     */
    fun fromDto(state: GameStateDto): GameState {
        val players = state.players.map(::toPlayerState)
        val discardPile = state.discardPile.map(::toPlaylistEntry)
        return GameState(
            sessionId = SessionId(state.sessionId),
            hostId = PlayerId(state.hostId),
            revision = state.revision,
            status = state.status.toModel(),
            players = players,
            activePlayerIndex = state.activePlayerIndex,
            deck = DeckState(
                remainingCards = List(state.deckRemaining) { index ->
                    placeholderEntry(
                        id = "deck-placeholder-$index",
                        title = "",
                        artist = "",
                        releaseYear = 0,
                        spotifyUri = "",
                    )
                },
            ),
            discardPile = discardPile,
            turn = state.turn?.let {
                TurnState(
                    number = it.number,
                    activePlayerId = PlayerId(it.activePlayerId),
                    phase = TurnPhase.valueOf(it.phase),
                    doubtWindowEndsAtEpochMillis = it.doubtWindowEndsAtEpochMillis,
                )
            },
            doubt = state.doubt?.let {
                DoubtState(
                    doubterId = PlayerId(it.doubterId),
                    targetPlayerId = PlayerId(it.targetPlayerId),
                    phase = DoubtPhase.valueOf(it.phase),
                    proposedSlotIndex = it.proposedSlotIndex,
                )
            },
            lastResolution = state.lastResolution?.let {
                TurnResolution(
                    playerId = PlayerId(it.playerId),
                    cardId = it.cardId,
                    attemptedSlotIndex = it.attemptedSlotIndex,
                    correct = it.correct,
                    releaseYear = it.releaseYear,
                    message = it.message,
                )
            },
        )
    }

    private fun toPlayerState(player: PlayerStateDto): PlayerState {
        return PlayerState(
            id = PlayerId(player.id),
            displayName = player.displayName,
            connected = player.connected,
            score = player.score,
            coins = player.coins,
            timeline = PlayerTimeline(player.timeline.map(::toPlaylistEntry)),
            pendingCard = player.pendingCard?.let {
                PendingCard(
                    entry = placeholderEntry(
                        id = it.id,
                        title = "",
                        artist = "",
                        releaseYear = 0,
                        spotifyUri = "",
                    ),
                    proposedSlotIndex = it.proposedSlotIndex,
                )
            },
        )
    }

    private fun toTimelineCardDto(entry: PlaylistEntry): TimelineCardDto {
        return TimelineCardDto(
            id = entry.id,
            title = entry.title,
            artist = entry.artist,
            releaseYear = entry.releaseYear,
            spotifyUri = entry.playbackReference.spotifyUri,
            coverImageUrl = entry.coverImageUrl,
        )
    }

    private fun toPlaylistEntry(card: TimelineCardDto): PlaylistEntry {
        return PlaylistEntry(
            id = card.id,
            title = card.title,
            artist = card.artist,
            releaseYear = card.releaseYear,
            playbackReference = PlaybackReference(card.spotifyUri),
            coverImageUrl = card.coverImageUrl,
        )
    }

    private fun placeholderEntry(
        id: String,
        title: String,
        artist: String,
        releaseYear: Int,
        spotifyUri: String,
    ): PlaylistEntry {
        return PlaylistEntry(
            id = id,
            title = title,
            artist = artist,
            releaseYear = releaseYear,
            playbackReference = PlaybackReference(spotifyUri),
        )
    }

    private fun MatchStatusDto.toModel(): MatchStatus {
        return when (this) {
            MatchStatusDto.LOBBY -> MatchStatus.LOBBY
            MatchStatusDto.ACTIVE -> MatchStatus.ACTIVE
            MatchStatusDto.COMPLETE -> MatchStatus.COMPLETE
        }
    }
}
