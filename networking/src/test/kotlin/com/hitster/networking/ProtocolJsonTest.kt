package com.hitster.networking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertNull

class ProtocolJsonTest {
    @Test
    fun `encodeClientCommandPayload emits browser safe join payloads`() {
        val payload = encodeClientCommandPayload(
            ClientCommandDto.JoinSession(
                actorId = "guest-1",
                displayName = "Guest",
            ),
        )

        assertTrue(payload.contains("\"type\":\"join_session\""))
        assertTrue(payload.contains("\"actorId\":\"guest-1\""))
        assertTrue(payload.contains("\"displayName\":\"Guest\""))
    }

    @Test
    fun `encodeClientCommandPayload emits doubt command payloads`() {
        val payload = encodeClientCommandPayload(
            ClientCommandDto.ToggleDoubt(actorId = "guest-1"),
        )

        assertTrue(payload.contains("\"type\":\"toggle_doubt\""))
        assertTrue(payload.contains("\"actorId\":\"guest-1\""))
    }

    @Test
    fun `encodeClientCommandPayload emits redraw command payloads`() {
        val payload = encodeClientCommandPayload(
            ClientCommandDto.RedrawCard(actorId = "guest-1"),
        )

        assertTrue(payload.contains("\"type\":\"redraw_card\""))
        assertTrue(payload.contains("\"actorId\":\"guest-1\""))
    }

    @Test
    fun `decodeHostEventPayload parses snapshot payloads`() {
        val payload = """
            {
              "type": "snapshot",
              "state": {
                "sessionId": "session-1",
                "hostId": "host",
                "revision": 3,
                "status": "LOBBY",
                "activePlayerIndex": 0,
                "deckRemaining": 12,
                "discardPile": [],
                "players": [
                  {
                    "id": "host",
                    "displayName": "Host",
                    "connected": true,
                    "score": 0,
                    "coins": 2,
                    "timeline": [],
                    "pendingCard": null
                  },
                  {
                    "id": "guest-1",
                    "displayName": "Guest",
                    "connected": true,
                    "score": 0,
                    "coins": 1,
                    "timeline": [],
                    "pendingCard": null
                  }
                ],
                "turn": null,
                "doubt": null,
                "lastResolution": null
              }
            }
        """.trimIndent()

        val event = decodeHostEventPayload(payload)
        val snapshot = assertIs<HostEventDto.SnapshotPublished>(event)

        assertEquals("session-1", snapshot.state.sessionId)
        assertEquals("guest-1", snapshot.state.players.last().id)
        assertEquals(1, snapshot.state.players.last().coins)
    }

    @Test
    fun `decodeHostEventPayload parses command rejection payloads`() {
        val payload = """
            {
              "type": "command_rejected",
              "actorId": "guest-1",
              "reason": "Nope",
              "revision": 4
            }
        """.trimIndent()

        val event = decodeHostEventPayload(payload)
        val rejected = assertIs<HostEventDto.CommandRejected>(event)

        assertEquals("guest-1", rejected.actorId)
        assertEquals("Nope", rejected.reason)
        assertEquals(4L, rejected.revision)
    }

    @Test
    fun `decodeHostEventPayload returns null for unknown payloads`() {
        val payload = """{"type":"unknown"}"""

        val event = decodeHostEventPayload(payload)

        assertNull(event)
    }
}
