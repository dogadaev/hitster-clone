package com.hitster.core.model

/**
 * Immutable deck operations used by the host to draw, shuffle, and track the remaining playlist entries.
 */

import kotlin.random.Random

data class DeckState(
    val remainingCards: List<PlaylistEntry>,
) {
    val size: Int = remainingCards.size

    /** Removes and returns the current top card, or `null` when the deck is exhausted. */
    fun drawTop(): DeckDraw? {
        val card = remainingCards.firstOrNull() ?: return null
        return DeckDraw(
            card = card,
            nextDeck = copy(remainingCards = remainingCards.drop(1)),
        )
    }

    /** Returns a new deck order using the supplied deterministic seed. */
    fun shuffled(seed: Long): DeckState {
        return copy(remainingCards = remainingCards.shuffled(Random(seed)))
    }

    companion object {
        /** Copies raw playlist entries into an immutable deck state. */
        fun fromEntries(entries: List<PlaylistEntry>): DeckState = DeckState(entries.toList())
    }
}

data class DeckDraw(
    val card: PlaylistEntry,
    val nextDeck: DeckState,
)
