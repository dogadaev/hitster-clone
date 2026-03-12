package com.hitster.core.model

enum class MatchStatus {
    LOBBY,
    ACTIVE,
    COMPLETE,
}

enum class TurnPhase {
    WAITING_FOR_DRAW,
    AWAITING_PLACEMENT,
    CARD_POSITIONED,
    COMPLETE,
}

data class PendingCard(
    val entry: PlaylistEntry,
    val proposedSlotIndex: Int,
)

data class PlayerTimeline(
    val cards: List<PlaylistEntry> = emptyList(),
) {
    fun validSlotRange(): IntRange = 0..cards.size

    fun insertAt(slotIndex: Int, entry: PlaylistEntry): PlayerTimeline {
        val normalizedIndex = slotIndex.coerceIn(validSlotRange())
        val before = cards.take(normalizedIndex)
        val after = cards.drop(normalizedIndex)
        return copy(cards = before + entry + after)
    }
}

data class PlayerState(
    val id: PlayerId,
    val displayName: String,
    val connected: Boolean = true,
    val score: Int = 0,
    val timeline: PlayerTimeline = PlayerTimeline(),
    val pendingCard: PendingCard? = null,
)

data class TurnState(
    val number: Int,
    val activePlayerId: PlayerId,
    val phase: TurnPhase,
)

data class TurnResolution(
    val playerId: PlayerId,
    val cardId: String,
    val attemptedSlotIndex: Int,
    val correct: Boolean,
    val releaseYear: Int,
    val message: String,
)

data class GameState(
    val sessionId: SessionId,
    val hostId: PlayerId,
    val revision: Long = 0,
    val status: MatchStatus = MatchStatus.LOBBY,
    val players: List<PlayerState>,
    val activePlayerIndex: Int = 0,
    val deck: DeckState,
    val discardPile: List<PlaylistEntry> = emptyList(),
    val turn: TurnState? = null,
    val lastResolution: TurnResolution? = null,
) {
    val activePlayer: PlayerState?
        get() = players.getOrNull(activePlayerIndex)

    fun requirePlayer(playerId: PlayerId): PlayerState? = players.firstOrNull { it.id == playerId }
}

