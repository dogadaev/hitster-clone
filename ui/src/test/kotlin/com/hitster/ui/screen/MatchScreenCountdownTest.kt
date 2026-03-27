package com.hitster.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals

class MatchScreenCountdownTest {
    @Test
    fun `countdown shows zero after the three second window has elapsed`() {
        assertEquals(3, countdownSecondsRemaining(3_000L))
        assertEquals(2, countdownSecondsRemaining(2_000L))
        assertEquals(1, countdownSecondsRemaining(1_000L))
        assertEquals(1, countdownSecondsRemaining(1L))
        assertEquals(0, countdownSecondsRemaining(0L))
        assertEquals(0, countdownSecondsRemaining(-250L))
    }
}
