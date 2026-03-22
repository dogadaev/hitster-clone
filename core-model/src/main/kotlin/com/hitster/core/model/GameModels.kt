package com.hitster.core.model

/**
 * Shared immutable match-state models that are synchronized between the authoritative host and every client view.
 */

enum class MatchStatus {
    LOBBY,
    ACTIVE,
    COMPLETE,
}

enum class TurnPhase {
    WAITING_FOR_DRAW,
    AWAITING_PLACEMENT,
    CARD_POSITIONED,
    AWAITING_DOUBT_PLACEMENT,
    DOUBT_POSITIONED,
    COMPLETE,
}

enum class DoubtPhase {
    ARMED,
    POSITIONING,
    POSITIONED,
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
    val coins: Int = 0,
    val timeline: PlayerTimeline = PlayerTimeline(),
    val pendingCard: PendingCard? = null,
)

data class DoubtState(
    val doubterId: PlayerId,
    val targetPlayerId: PlayerId,
    val phase: DoubtPhase,
    val proposedSlotIndex: Int? = null,
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
    val doubt: DoubtState? = null,
    val lastResolution: TurnResolution? = null,
) {
    val activePlayer: PlayerState?
        get() = players.getOrNull(activePlayerIndex)

    fun requirePlayer(playerId: PlayerId): PlayerState? = players.firstOrNull { it.id == playerId }
}
