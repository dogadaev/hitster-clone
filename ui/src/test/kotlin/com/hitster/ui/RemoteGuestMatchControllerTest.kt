package com.hitster.ui

import com.hitster.core.model.DeckState
import com.hitster.core.model.DoubtPhase
import com.hitster.core.model.DoubtState
import com.hitster.core.model.GameState
import com.hitster.core.model.MatchStatus
import com.hitster.core.model.PendingCard
import com.hitster.core.model.PlaybackReference
import com.hitster.core.model.PlayerId
import com.hitster.core.model.PlayerState
import com.hitster.core.model.PlayerTimeline
import com.hitster.core.model.PlaylistEntry
import com.hitster.core.model.SessionId
import com.hitster.core.model.TurnPhase
import com.hitster.core.model.TurnState
import com.hitster.networking.ClientCommandDto
import com.hitster.networking.GameStateMapper
import com.hitster.networking.HostEventDto
import com.hitster.networking.SessionAdvertisementDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RemoteGuestMatchControllerTest {
    @Test
    fun `move pending card updates local guest state immediately`() {
        val localPlayerId = PlayerId("guest")
        val client = RecordingGuestSessionClient()
        val controller = RemoteGuestMatchController(advertisement = advertisement(), localPlayerId = localPlayerId)
        controller.attachClient(client)
        controller.handleEvent(HostEventDto.SnapshotPublished(GameStateMapper.toDto(activeGuestState(localPlayerId))))

        controller.movePendingCard(0)

        assertEquals(0, controller.localPlayer?.pendingCard?.proposedSlotIndex)
        assertEquals(TurnPhase.CARD_POSITIONED, controller.state.turn?.phase)
        val command = assertIs<ClientCommandDto.MovePendingCard>(client.commands.single())
        assertEquals(0, command.requestedSlotIndex)
    }

    @Test
    fun `move doubt card updates local doubt placement immediately`() {
        val localPlayerId = PlayerId("guest")
        val client = RecordingGuestSessionClient()
        val controller = RemoteGuestMatchController(advertisement = advertisement(), localPlayerId = localPlayerId)
        controller.attachClient(client)
        controller.handleEvent(HostEventDto.SnapshotPublished(GameStateMapper.toDto(activeDoubtState(localPlayerId))))

        controller.moveDoubtCard(1)

        assertEquals(1, controller.state.doubt?.proposedSlotIndex)
        assertEquals(DoubtPhase.POSITIONED, controller.state.doubt?.phase)
        assertEquals(TurnPhase.DOUBT_POSITIONED, controller.state.turn?.phase)
        val command = assertIs<ClientCommandDto.MoveDoubtCard>(client.commands.single())
        assertEquals(1, command.requestedSlotIndex)
    }

    private fun advertisement(): SessionAdvertisementDto {
        return SessionAdvertisementDto(
            sessionId = "session-1",
            hostPlayerId = "host",
            hostDisplayName = "Host",
            hostAddress = "127.0.0.1",
            serverPort = 28761,
            playerCount = 2,
        )
    }

    private fun activeGuestState(localPlayerId: PlayerId): GameState {
        val pendingEntry = sampleEntry("pending", 2003)
        return GameState(
            sessionId = SessionId("session-1"),
            hostId = PlayerId("host"),
            status = MatchStatus.ACTIVE,
            players = listOf(
                PlayerState(
                    id = PlayerId("host"),
                    displayName = "Host",
                    timeline = PlayerTimeline(
                        listOf(sampleEntry("h1", 1990), sampleEntry("h2", 2008)),
                    ),
                ),
                PlayerState(
                    id = localPlayerId,
                    displayName = "Guest",
                    timeline = PlayerTimeline(
                        listOf(sampleEntry("g1", 1986), sampleEntry("g2", 2015)),
                    ),
                    pendingCard = PendingCard(
                        entry = pendingEntry,
                        proposedSlotIndex = 2,
                    ),
                ),
            ),
            activePlayerIndex = 1,
            deck = DeckState(emptyList()),
            turn = TurnState(
                number = 4,
                activePlayerId = localPlayerId,
                phase = TurnPhase.AWAITING_PLACEMENT,
            ),
        )
    }

    private fun activeDoubtState(localPlayerId: PlayerId): GameState {
        val hostId = PlayerId("host")
        val pendingEntry = sampleEntry("pending", 2001)
        return GameState(
            sessionId = SessionId("session-1"),
            hostId = hostId,
            status = MatchStatus.ACTIVE,
            players = listOf(
                PlayerState(
                    id = hostId,
                    displayName = "Host",
                    timeline = PlayerTimeline(
                        listOf(sampleEntry("h1", 1992), sampleEntry("h2", 2010)),
                    ),
                    pendingCard = PendingCard(
                        entry = pendingEntry,
                        proposedSlotIndex = 2,
                    ),
                ),
                PlayerState(
                    id = localPlayerId,
                    displayName = "Guest",
                    timeline = PlayerTimeline(
                        listOf(sampleEntry("g1", 1980), sampleEntry("g2", 2020)),
                    ),
                    coins = 1,
                ),
            ),
            activePlayerIndex = 0,
            deck = DeckState(emptyList()),
            turn = TurnState(
                number = 6,
                activePlayerId = hostId,
                phase = TurnPhase.AWAITING_DOUBT_PLACEMENT,
            ),
            doubt = DoubtState(
                doubterId = localPlayerId,
                targetPlayerId = hostId,
                phase = DoubtPhase.POSITIONING,
                proposedSlotIndex = null,
            ),
        )
    }

    private fun sampleEntry(id: String, year: Int): PlaylistEntry {
        return PlaylistEntry(
            id = id,
            title = "Track $id",
            artist = "Artist $id",
            releaseYear = year,
            playbackReference = PlaybackReference("spotify:track:$id"),
        )
    }

    private class RecordingGuestSessionClient : GuestSessionClient {
        val commands = mutableListOf<ClientCommandDto>()

        override fun connect() = Unit

        override fun sendCommand(command: ClientCommandDto) {
            commands += command
        }

        override fun close() = Unit
    }
}
