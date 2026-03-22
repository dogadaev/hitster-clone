package com.hitster.ui

/**
 * Regression coverage for UiBootstrapper, keeping the documented behavior of this module stable as gameplay and transport code evolve.
 */

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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UiBootstrapperTest {
    @Test
    fun `display name sanitization trims collapses whitespace and caps length`() {
        val sanitized = UiBootstrapper.sanitizeDisplayName("   Иван   Петрович   Сидоров   ОченьДлиннаяФамилия   ")

        assertEquals("Иван Петрович Сидоров Оч", sanitized)
    }

    @Test
    fun `random id suffix uses fixed width browser safe characters`() {
        val suffix = UiBootstrapper.randomIdSuffix(Random(1234))

        assertEquals(8, suffix.length)
        assertTrue(suffix.all { it in 'a'..'z' || it in '0'..'9' })
    }

    @Test
    fun `shuffle seed comes from the provided random source`() {
        assertEquals(Random(1234).nextLong(), UiBootstrapper.nextShuffleSeed(Random(1234)))
    }

    @Test
    fun `hosted lobby deck order changes with different shuffle seeds`() {
        fun buildController(shuffleSeed: Long): HostedMatchController {
            return UiBootstrapper.createHostedMatchController(
                hostDisplayName = "Host",
                shuffleSeed = shuffleSeed,
                sessionTransportFactory = {
                    object : HostedSessionTransport {
                        override fun start() = Unit

                        override fun broadcast(event: HostEventDto) = Unit

                        override fun close() = Unit
                    }
                },
            )
        }

        val first = buildController(11L)
        val second = buildController(11L)
        val third = buildController(29L)
        try {
            val firstDeck = first.state.deck.remainingCards.map { it.id }
            val secondDeck = second.state.deck.remainingCards.map { it.id }
            val thirdDeck = third.state.deck.remainingCards.map { it.id }

            assertEquals(firstDeck, secondDeck)
            assertNotEquals(firstDeck, thirdDeck)
        } finally {
            first.dispose()
            second.dispose()
            third.dispose()
        }
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
