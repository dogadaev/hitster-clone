package com.hitster.ui

/**
 * Regression coverage for DecadeCardPalette, keeping the documented behavior of this module stable as gameplay and transport code evolve.
 */

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DecadeCardPaletteTest {
    @Test
    fun clampsOlderYearsIntoFiftiesPalette() {
        assertEquals(
            DecadeCardPalettes.forYear(1954),
            DecadeCardPalettes.forYear(1941),
        )
    }

    @Test
    fun mapsDifferentDecadesToDifferentPalettes() {
        assertNotEquals(
            DecadeCardPalettes.forYear(1978),
            DecadeCardPalettes.forYear(1984),
        )
        assertNotEquals(
            DecadeCardPalettes.forYear(1996),
            DecadeCardPalettes.forYear(2003),
        )
    }

    @Test
    fun clampsModernYearsIntoCurrentPalette() {
        assertEquals(
            DecadeCardPalettes.forYear(2020),
            DecadeCardPalettes.forYear(2026),
        )
    }
}
