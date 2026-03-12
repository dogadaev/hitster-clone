package com.hitster.core.game

import com.hitster.core.model.PlaylistEntry

data class PlacementValidation(
    val slotIndex: Int,
    val isValid: Boolean,
    val lowerBoundYear: Int?,
    val upperBoundYear: Int?,
)

class TimelinePlacementValidator {
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

    fun snapSlot(slotCount: Int, requestedSlotIndex: Int): Int {
        return requestedSlotIndex.coerceIn(0, slotCount)
    }
}

