package com.hitster.ui

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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

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

        assertEquals("Only the active player can draw a card.", presenter.lastError)
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

    private fun acceptedState(result: ReducerResult) = (result as ReducerResult.Accepted).state

    private fun entry(id: String, year: Int) = PlaylistEntry(
        id = id,
        title = id,
        artist = "Artist",
        releaseYear = year,
        playbackReference = PlaybackReference("spotify:track:$id"),
    )

    private class FakePlaybackController : PlaybackController {
        private var listener: PlaybackEventListener? = null

        override fun playTrack(reference: PlaybackReference): PlaybackCommandResult = PlaybackCommandResult.Success

        override fun pause(): PlaybackCommandResult = PlaybackCommandResult.Success

        override fun currentState(): PlaybackSessionState = PlaybackSessionState.Idle

        override fun setListener(listener: PlaybackEventListener?) {
            this.listener = listener
        }

        fun dispatchIssue(issue: PlaybackIssue?) {
            listener?.onIssue(issue)
        }
    }
}
