package com.hitster.core.model

/**
 * Regression coverage for DeckState, keeping the documented behavior of this module stable as gameplay and transport code evolve.
 */

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeckStateTest {
    @Test
    fun `draw top removes first card and shrinks deck`() {
        val deck = DeckState.fromEntries(
            listOf(
                sampleEntry("one", 1984),
                sampleEntry("two", 1990),
            ),
        )

        val draw = assertNotNull(deck.drawTop())

        assertEquals("one", draw.card.id)
        assertEquals(1, draw.nextDeck.size)
        assertEquals("two", draw.nextDeck.remainingCards.single().id)
    }

    @Test
    fun `drawing from empty deck returns null`() {
        val deck = DeckState.fromEntries(emptyList())

        assertNull(deck.drawTop())
    }

    private fun sampleEntry(id: String, year: Int): PlaylistEntry {
        return PlaylistEntry(
            id = id,
            title = "Track $id",
            artist = "Artist $id",
            releaseYear = year,
            playbackReference = PlaybackReference("spotify:track:$id"),
        )
    }
}
