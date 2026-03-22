package com.hitster.core.game

/**
 * Pure timeline placement calculations used to snap hidden cards to valid slots and validate chronological guesses.
 */

import com.hitster.core.model.PlaylistEntry

data class PlacementValidation(
    val slotIndex: Int,
    val isValid: Boolean,
    val lowerBoundYear: Int?,
    val upperBoundYear: Int?,
)

class TimelinePlacementValidator {
    /**
     * Validates a requested insertion slot against the neighboring revealed years around that slot.
     *
     * The returned slot is always normalized into the valid insertion range, even when the request was out of bounds.
     */
    fun validate(
        timeline: List<PlaylistEntry>,
        entry: PlaylistEntry,
        requestedSlotIndex: Int,
    ): PlacementValidation {
        val slotIndex = requestedSlotIndex.coerceIn(0, timeline.size)
        val lowerBoundYear = timeline.getOrNull(slotIndex - 1)?.releaseYear
        val upperBoundYear = timeline.getOrNull(slotIndex)?.releaseYear
        val isAfterLowerBound = lowerBoundYear?.let { entry.releaseYear >= it } ?: true
        val isBeforeUpperBound = upperBoundYear?.let { entry.releaseYear <= it } ?: true

        return PlacementValidation(
            slotIndex = slotIndex,
            isValid = isAfterLowerBound && isBeforeUpperBound,
            lowerBoundYear = lowerBoundYear,
            upperBoundYear = upperBoundYear,
        )
    }

    /** Clamps an arbitrary UI-requested slot into the legal insertion range for the current timeline size. */
    fun snapSlot(slotCount: Int, requestedSlotIndex: Int): Int {
        return requestedSlotIndex.coerceIn(0, slotCount)
    }
}
