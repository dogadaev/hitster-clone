package com.hitster.core.model

/**
 * Immutable deck operations used by the host to draw, shuffle, and track the remaining playlist entries.
 */

import kotlin.random.Random

data class DeckState(
    val remainingCards: List<PlaylistEntry>,
) {
    val size: Int = remainingCards.size

    fun drawTop(): DeckDraw? {
        val card = remainingCards.firstOrNull() ?: return null
        return DeckDraw(
            card = card,
            nextDeck = copy(remainingCards = remainingCards.drop(1)),
        )
    }

    fun shuffled(seed: Long): DeckState {
        return copy(remainingCards = remainingCards.shuffled(Random(seed)))
    }

    companion object {
        fun fromEntries(entries: List<PlaylistEntry>): DeckState = DeckState(entries.toList())
    }
}

data class DeckDraw(
    val card: PlaylistEntry,
    val nextDeck: DeckState,
)
