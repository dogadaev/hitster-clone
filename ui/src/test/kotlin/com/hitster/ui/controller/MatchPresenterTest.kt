package com.hitster.ui.controller

/**
 * Regression coverage for MatchPresenter, keeping the documented behavior of this module stable as gameplay and transport code evolve.
 */

import com.hitster.core.game.GameCommand
import com.hitster.core.game.GameSessionFactory
import com.hitster.core.game.HostGameReducer
import com.hitster.core.game.ReducerResult
import com.hitster.core.model.PlaybackReference
import com.hitster.core.model.PlayerId
import com.hitster.core.model.PlaylistEntry
import com.hitster.core.model.SessionId
import com.hitster.playback.api.PlaybackCommandResult
import com.hitster.playback.api.PlaybackController
import com.hitster.playback.api.PlaybackEventListener
import com.hitster.playback.api.PlaybackIssue
import com.hitster.playback.api.PlaybackIssueCode
import com.hitster.playback.api.PlaybackSessionState
import com.hitster.playback.api.NoOpPlaybackController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MatchPresenterTest {
    private val reducer = HostGameReducer()
    private val hostId = PlayerId("host")
    private val guestId = PlayerId("guest")

    @Test
    fun `turn actions always use the local player identity`() {
        val presenter = MatchPresenter(
            reducer = reducer,
            playbackController = NoOpPlaybackController(),
            hostId = hostId,
            localPlayerId = hostId,
            initialState = lobbyWithGuest(
                listOf(
                    entry("seed-host", 1980),
                    entry("seed-guest", 2000),
                    entry("draw-host", 1990),
                    entry("draw-guest", 2010),
                ),
            ),
        )

        presenter.startMatch()
        presenter.drawCard()
        presenter.movePendingCard(requestedSlotIndex = 1)
        presenter.endTurn()

        presenter.drawCard()

        assertEquals("Card draw is not allowed at this point in the turn.", presenter.lastError)
    }

    @Test
    fun `presenter receives asynchronous playback issues from the controller listener`() {
        val playbackController = FakePlaybackController()
        val presenter = MatchPresenter(
            reducer = reducer,
            playbackController = playbackController,
            hostId = hostId,
            localPlayerId = hostId,
            initialState = lobbyWithGuest(
                listOf(
                    entry("seed-host", 1980),
                    entry("seed-guest", 2000),
                    entry("draw-host", 1990),
                    entry("draw-guest", 2010),
                ),
            ),
        )
        val expectedIssue = PlaybackIssue(
            code = PlaybackIssueCode.NOT_AUTHENTICATED,
            message = "Spotify login expired.",
        )

        playbackController.dispatchIssue(expectedIssue)

        assertSame(expectedIssue, presenter.lastPlaybackIssue)
    }

    @Test
    fun `host must pair playback before starting the match`() {
        val playbackController = FakePlaybackController(
            initialState = PlaybackSessionState.Disconnected,
        )
        val presenter = MatchPresenter(
            reducer = reducer,
            playbackController = playbackController,
            hostId = hostId,
            localPlayerId = hostId,
            initialState = lobbyWithGuest(deckEntries()),
        )

        presenter.startMatch()

        assertEquals("Pair Spotify before starting.", presenter.lastError)
        assertEquals(com.hitster.core.model.MatchStatus.LOBBY, presenter.state.status)
    }

    @Test
    fun `guests are not blocked by the host pairing gate`() {
        val playbackController = FakePlaybackController(
            initialState = PlaybackSessionState.Disconnected,
        )
        val presenter = MatchPresenter(
            reducer = reducer,
            playbackController = playbackController,
            hostId = hostId,
            localPlayerId = guestId,
            initialState = lobbyWithGuest(deckEntries()),
        )

        assertFalse(presenter.requiresHostPlaybackPairing())
    }

    @Test
    fun `host cannot start a lobby without at least one guest`() {
        val presenter = MatchPresenter(
            reducer = reducer,
            playbackController = NoOpPlaybackController(),
            hostId = hostId,
            localPlayerId = hostId,
            initialState = GameSessionFactory.createLobby(
                sessionId = SessionId("session"),
                hostId = hostId,
                hostName = "Host",
                deckEntries = deckEntries(),
            ),
        )

        presenter.startMatch()

        assertEquals("At least one guest must join before starting.", presenter.lastError)
        assertFalse(presenter.canStartLobbyMatch())
        assertEquals(com.hitster.core.model.MatchStatus.LOBBY, presenter.state.status)
    }

    @Test
    fun `prepare host playback updates readiness once the controller connects`() {
        val playbackController = FakePlaybackController(
            initialState = PlaybackSessionState.Disconnected,
        )
        val presenter = MatchPresenter(
            reducer = reducer,
            playbackController = playbackController,
            hostId = hostId,
            localPlayerId = hostId,
            initialState = lobbyWithGuest(deckEntries()),
        )

        presenter.prepareHostPlayback()
        playbackController.dispatchSessionState(PlaybackSessionState.Connecting)
        playbackController.dispatchSessionState(PlaybackSessionState.Ready)

        assertEquals(1, playbackController.prepareCalls)
        assertEquals(PlaybackSessionState.Ready, presenter.playbackSessionState)
        assertTrue(!presenter.requiresHostPlaybackPairing())
    }

    @Test
    fun `toggle playback pauses when the current player is previewing a track`() {
        val playbackController = FakePlaybackController(
            initialState = PlaybackSessionState.Playing("spotify:track:draw-host"),
        )
        val presenter = MatchPresenter(
            reducer = reducer,
            playbackController = playbackController,
            hostId = hostId,
            localPlayerId = hostId,
            initialState = activeTurnState(activePlayerId = hostId),
        )

        presenter.togglePlayback()

        assertEquals(1, playbackController.pauseCalls)
        assertEquals(0, playbackController.resumeCalls)
    }

    @Test
    fun `toggle playback resumes when the current player has paused the preview`() {
        val playbackController = FakePlaybackController(
            initialState = PlaybackSessionState.Paused("spotify:track:draw-host"),
        )
        val presenter = MatchPresenter(
            reducer = reducer,
            playbackController = playbackController,
            hostId = hostId,
            localPlayerId = hostId,
            initialState = activeTurnState(activePlayerId = hostId),
        )

        presenter.togglePlayback()

        assertEquals(0, playbackController.pauseCalls)
        assertEquals(1, playbackController.resumeCalls)
    }

    @Test
    fun `toggle playback rejects non active players`() {
        val playbackController = FakePlaybackController(
            initialState = PlaybackSessionState.Playing("spotify:track:draw-host"),
        )
        val presenter = MatchPresenter(
            reducer = reducer,
            playbackController = playbackController,
            hostId = hostId,
            localPlayerId = guestId,
            initialState = activeTurnState(activePlayerId = hostId),
        )

        presenter.togglePlayback()

        assertEquals("Only the current player can control playback.", presenter.lastError)
        assertEquals(0, playbackController.pauseCalls)
        assertEquals(0, playbackController.resumeCalls)
    }

    @Test
    fun `remote joins publish snapshots even when handled off the render thread`() {
        val presenter = MatchPresenter(
            reducer = reducer,
            playbackController = NoOpPlaybackController(),
            hostId = hostId,
            localPlayerId = hostId,
            initialState = GameSessionFactory.createLobby(
                sessionId = SessionId("session"),
                hostId = hostId,
                hostName = "Host",
                deckEntries = deckEntries(),
            ),
        )
        val snapshotPublished = CountDownLatch(1)
        presenter.snapshotListener = { snapshot ->
            if (snapshot.players.any { it.id == "guest-background" }) {
                snapshotPublished.countDown()
            }
        }

        val commandThread = Thread {
            presenter.handleRemoteCommand(
                com.hitster.networking.ClientCommandDto.JoinSession(
                    actorId = "guest-background",
                    displayName = "Background Guest",
                ),
            )
        }

        commandThread.start()
        commandThread.join()

        assertTrue(snapshotPublished.await(1, TimeUnit.SECONDS))
        assertEquals(2, presenter.state.players.size)
        assertEquals("guest-background", presenter.state.players.last().id.value)
    }

    @Test
    fun `remote disconnect removes guest from the lobby and publishes a snapshot`() {
        val presenter = MatchPresenter(
            reducer = reducer,
            playbackController = NoOpPlaybackController(),
            hostId = hostId,
            localPlayerId = hostId,
            initialState = lobbyWithGuest(deckEntries()),
        )
        val snapshotPublished = CountDownLatch(1)
        presenter.snapshotListener = { snapshot ->
            if (snapshot.players.none { it.id == guestId.value }) {
                snapshotPublished.countDown()
            }
        }

        val disconnectThread = Thread {
            presenter.handleRemoteDisconnect(guestId.value)
        }

        disconnectThread.start()
        disconnectThread.join()

        assertTrue(snapshotPublished.await(1, TimeUnit.SECONDS))
        assertEquals(listOf(hostId), presenter.state.players.map { it.id })
        assertTrue(!presenter.canStartLobbyMatch())
    }

    private fun lobbyWithGuest(entries: List<PlaylistEntry>) =
        acceptedState(
            reducer.reduce(
                GameSessionFactory.createLobby(
                    sessionId = SessionId("session"),
                    hostId = hostId,
                    hostName = "Host",
                    deckEntries = entries,
                ),
                GameCommand.JoinSession(
                    playerId = guestId,
                    displayName = "Guest",
                ),
            ),
        )

    private fun activeTurnState(activePlayerId: PlayerId): com.hitster.core.model.GameState {
        val activeEntry = entry("draw-host", 1990)
        val waitingEntry = entry("draw-guest", 2010)
        return com.hitster.core.model.GameState(
            sessionId = SessionId("session"),
            hostId = hostId,
            status = com.hitster.core.model.MatchStatus.ACTIVE,
            players = listOf(
                com.hitster.core.model.PlayerState(
                    id = hostId,
                    displayName = "Host",
                    timeline = com.hitster.core.model.PlayerTimeline(listOf(entry("seed-host", 1980))),
                    pendingCard = if (activePlayerId == hostId) {
                        com.hitster.core.model.PendingCard(activeEntry, proposedSlotIndex = 1)
                    } else {
                        null
                    },
                ),
                com.hitster.core.model.PlayerState(
                    id = guestId,
                    displayName = "Guest",
                    timeline = com.hitster.core.model.PlayerTimeline(listOf(entry("seed-guest", 2000))),
                    pendingCard = if (activePlayerId == guestId) {
                        com.hitster.core.model.PendingCard(waitingEntry, proposedSlotIndex = 1)
                    } else {
                        null
                    },
                ),
            ),
            activePlayerIndex = if (activePlayerId == hostId) 0 else 1,
            deck = com.hitster.core.model.DeckState(emptyList()),
            turn = com.hitster.core.model.TurnState(
                number = 2,
                activePlayerId = activePlayerId,
                phase = com.hitster.core.model.TurnPhase.CARD_POSITIONED,
            ),
        )
    }

    private fun acceptedState(result: ReducerResult) = (result as ReducerResult.Accepted).state

    private fun deckEntries() = listOf(
        entry("seed-host", 1980),
        entry("seed-guest", 2000),
        entry("draw-host", 1990),
        entry("draw-guest", 2010),
    )

    private fun entry(id: String, year: Int) = PlaylistEntry(
        id = id,
        title = id,
        artist = "Artist",
        releaseYear = year,
        playbackReference = PlaybackReference("spotify:track:$id"),
    )

    private class FakePlaybackController(
        initialState: PlaybackSessionState = PlaybackSessionState.Ready,
    ) : PlaybackController {
        private var listener: PlaybackEventListener? = null
        private var state: PlaybackSessionState = initialState
        var prepareCalls: Int = 0
            private set
        var pauseCalls: Int = 0
            private set
        var resumeCalls: Int = 0
            private set

        override fun prepareSession(): PlaybackCommandResult {
            prepareCalls += 1
            return PlaybackCommandResult.Success
        }

        override fun playTrack(reference: PlaybackReference): PlaybackCommandResult = PlaybackCommandResult.Success

        override fun pause(): PlaybackCommandResult {
            pauseCalls += 1
            return PlaybackCommandResult.Success
        }

        override fun resume(): PlaybackCommandResult {
            resumeCalls += 1
            return PlaybackCommandResult.Success
        }

        override fun currentState(): PlaybackSessionState = state

        override fun setListener(listener: PlaybackEventListener?) {
            this.listener = listener
            listener?.onSessionStateChanged(state)
        }

        fun dispatchIssue(issue: PlaybackIssue?) {
            listener?.onIssue(issue)
        }

        fun dispatchSessionState(state: PlaybackSessionState) {
            this.state = state
            listener?.onSessionStateChanged(state)
        }
    }
}
