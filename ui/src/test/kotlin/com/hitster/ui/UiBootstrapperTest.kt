package com.hitster.ui

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiBootstrapperTest {
    @Test
    fun `random id suffix uses fixed width browser safe characters`() {
        val suffix = UiBootstrapper.randomIdSuffix(Random(1234))

        assertEquals(8, suffix.length)
        assertTrue(suffix.all { it in 'a'..'z' || it in '0'..'9' })
    }
}
