package com.hitster.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimelineLayoutCalculatorTest {
    private val calculator = TimelineLayoutCalculator(
        trackX = 100f,
        trackWidth = 1000f,
        preferredCardWidth = 150f,
        minCardWidth = 92f,
        preferredGap = 28f,
        minGap = 12f,
    )

    @Test
    fun `centers the committed timeline group`() {
        val arrangement = calculator.arrangement(cardCount = 3)
        val groupCenter = arrangement.groupStartX + arrangement.groupWidth / 2f

        assertEquals(600f, groupCenter)
    }

    @Test
    fun `supports insertion at the far left and far right`() {
        val slotCenters = calculator.insertionSlotCenters(existingCardCount = 2)
        val committed = calculator.arrangement(cardCount = 2)

        assertEquals(3, slotCenters.size)
        assertTrue(slotCenters.first() < committed.cardLefts.first() + committed.cardWidth / 2f)
        assertTrue(slotCenters.last() > committed.cardLefts.last() + committed.cardWidth / 2f)
    }

    @Test
    fun `inserts a pending card between existing cards and keeps the group centered`() {
        val arrangement = calculator.pendingArrangement(existingCardCount = 3, pendingSlotIndex = 1)
        val groupCenter = arrangement.groupStartX + arrangement.groupWidth / 2f

        assertEquals(600f, groupCenter)
        assertTrue(arrangement.committedCardLefts[0] < arrangement.pendingCardLeft)
        assertTrue(arrangement.pendingCardLeft < arrangement.committedCardLefts[1])
    }

    @Test
    fun `recalculates widths to fit larger timelines inside the track`() {
        val arrangement = calculator.arrangement(cardCount = 8)

        assertTrue(arrangement.groupWidth <= 1000f)
        assertTrue(arrangement.cardWidth >= 92f)
    }

    @Test
    fun `finds the nearest valid insertion slot`() {
        val slotCenters = calculator.insertionSlotCenters(existingCardCount = 3)

        assertEquals(0, calculator.nearestSlotIndex(existingCardCount = 3, x = slotCenters.first() - 30f))
        assertEquals(3, calculator.nearestSlotIndex(existingCardCount = 3, x = slotCenters.last() + 30f))
        assertEquals(2, calculator.nearestSlotIndex(existingCardCount = 3, x = slotCenters[2] + 4f))
    }
}
