package com.hitster.ui.theme

/**
 * Regression coverage for UiFontSupport, keeping the documented behavior of this module stable as gameplay and transport code evolve.
 */

import kotlin.test.Test
import kotlin.test.assertTrue

class UiFontSupportTest {
    @Test
    fun `ui font character set includes cyrillic glyphs`() {
        assertTrue(UI_FONT_CHARACTERS.contains('А'))
        assertTrue(UI_FONT_CHARACTERS.contains('Я'))
        assertTrue(UI_FONT_CHARACTERS.contains('а'))
        assertTrue(UI_FONT_CHARACTERS.contains('я'))
        assertTrue(UI_FONT_CHARACTERS.contains('Ё'))
        assertTrue(UI_FONT_CHARACTERS.contains('ё'))
        assertTrue(UI_FONT_CHARACTERS.contains('№'))
    }
}
