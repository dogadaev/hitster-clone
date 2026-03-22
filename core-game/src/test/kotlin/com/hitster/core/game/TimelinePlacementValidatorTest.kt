package com.hitster.core.game

/**
 * Regression coverage for TimelinePlacementValidator, keeping the documented behavior of this module stable as gameplay and transport code evolve.
 */

import com.hitster.core.model.PlaybackReference
import com.hitster.core.model.PlaylistEntry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimelinePlacementValidatorTest {
    private val validator = TimelinePlacementValidator()

    @Test
    fun `validates card between adjacent release years`() {
        val timeline = listOf(
            entry("a", 1984),
            entry("b", 1995),
        )

        val result = validator.validate(
            timeline = timeline,
            entry = entry("c", 1990),
            requestedSlotIndex = 1,
        )

        assertTrue(result.isValid)
    }

    @Test
    fun `rejects card when it violates chronological order`() {
        val timeline = listOf(
            entry("a", 1984),
            entry("b", 1995),
        )

        val result = validator.validate(
            timeline = timeline,
            entry = entry("c", 2004),
            requestedSlotIndex = 1,
        )

        assertFalse(result.isValid)
    }

    private fun entry(id: String, year: Int): PlaylistEntry {
        return PlaylistEntry(
            id = id,
            title = id,
            artist = "Artist",
            releaseYear = year,
            playbackReference = PlaybackReference("spotify:track:$id"),
        )
    }
}
