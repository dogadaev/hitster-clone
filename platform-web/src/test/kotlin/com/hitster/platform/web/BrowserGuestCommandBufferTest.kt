package com.hitster.platform.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowserGuestCommandBufferTest {
    @Test
    fun `pending move commands coalesce while immediate commands keep order`() {
        val buffer = BrowserGuestCommandBuffer()

        buffer.enqueue(BrowserGuestCommandKind.IMMEDIATE, "draw")
        buffer.enqueue(BrowserGuestCommandKind.MOVE_PENDING, "move-1")
        buffer.enqueue(BrowserGuestCommandKind.MOVE_PENDING, "move-2")
        buffer.enqueue(BrowserGuestCommandKind.IMMEDIATE, "end")

        assertEquals(BrowserGuestBufferedCommand(BrowserGuestCommandKind.IMMEDIATE, "draw"), buffer.poll())
        assertEquals(BrowserGuestBufferedCommand(BrowserGuestCommandKind.MOVE_PENDING, "move-2"), buffer.poll())
        assertEquals(BrowserGuestBufferedCommand(BrowserGuestCommandKind.IMMEDIATE, "end"), buffer.poll())
        assertNull(buffer.poll())
    }

    @Test
    fun `doubt and main move queues coalesce independently`() {
        val buffer = BrowserGuestCommandBuffer()

        buffer.enqueue(BrowserGuestCommandKind.MOVE_PENDING, "main-1")
        buffer.enqueue(BrowserGuestCommandKind.MOVE_DOUBT, "doubt-1")
        buffer.enqueue(BrowserGuestCommandKind.MOVE_PENDING, "main-2")
        buffer.enqueue(BrowserGuestCommandKind.MOVE_DOUBT, "doubt-2")

        assertEquals(BrowserGuestBufferedCommand(BrowserGuestCommandKind.MOVE_PENDING, "main-2"), buffer.poll())
        assertEquals(BrowserGuestBufferedCommand(BrowserGuestCommandKind.MOVE_DOUBT, "doubt-2"), buffer.poll())
        assertNull(buffer.poll())
    }
}
