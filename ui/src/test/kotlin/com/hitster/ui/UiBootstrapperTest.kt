package com.hitster.ui

import com.hitster.core.game.GameSessionFactory
import com.hitster.core.model.PlaybackReference
import com.hitster.core.model.PlayerId
import com.hitster.core.model.PlayerState
import com.hitster.core.model.PlaylistEntry
import com.hitster.core.model.SessionId
import com.hitster.networking.GameStateMapper
import com.hitster.networking.HostEventDto
import com.hitster.networking.SessionAdvertisementDto
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UiBootstrapperTest {
    @Test
    fun `random id suffix uses fixed width browser safe characters`() {
        val suffix = UiBootstrapper.randomIdSuffix(Random(1234))

        assertEquals(8, suffix.length)
        assertTrue(suffix.all { it in 'a'..'z' || it in '0'..'9' })
    }

    @Test
    fun `remote guest controller connects only after controller assignment`() {
        val advertisement = SessionAdvertisementDto(
            sessionId = "session-1",
            hostPlayerId = "host",
            hostDisplayName = "Host",
            hostAddress = "127.0.0.1",
            serverPort = 28761,
            playerCount = 1,
        )
        val expectedGuestId = PlayerId("guest-fixed")
        val expectedGuest = PlayerState(
            id = expectedGuestId,
            displayName = "Safari Guest",
        )
        val joinedState = GameSessionFactory.createLobby(
            sessionId = SessionId(advertisement.sessionId),
            hostId = PlayerId(advertisement.hostPlayerId),
            hostName = advertisement.hostDisplayName,
            deckEntries = listOf(
                PlaylistEntry(
                    id = "track-1",
                    title = "Take On Me",
                    artist = "a-ha",
                    releaseYear = 1985,
                    playbackReference = PlaybackReference("spotify:track:2WfaOiMkCvy7F5fcp2zZ8L"),
                ),
            ),
        ).copy(players = listOf(PlayerState(PlayerId(advertisement.hostPlayerId), advertisement.hostDisplayName), expectedGuest))
        val joinedSnapshot = HostEventDto.SnapshotPublished(GameStateMapper.toDto(joinedState))
        var connectCalled = false

        val controller = UiBootstrapper.createRemoteGuestController(
            advertisement = advertisement,
            displayName = expectedGuest.displayName,
            clientFactory = { _, actorId, _, onEvent, _, _ ->
                assertEquals(expectedGuestId, actorId)
                object : GuestSessionClient {
                    override fun connect() {
                        connectCalled = true
                        onEvent(joinedSnapshot)
                    }

                    override fun sendCommand(command: com.hitster.networking.ClientCommandDto) = Unit

                    override fun close() = Unit
                }
            },
            playerIdFactory = { expectedGuestId },
        )

        assertTrue(connectCalled)
        assertNotNull(controller.localPlayer)
        assertEquals(expectedGuestId, controller.localPlayer?.id)
    }
}
