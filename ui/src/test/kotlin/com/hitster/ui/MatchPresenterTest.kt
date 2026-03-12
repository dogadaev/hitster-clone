package com.hitster.ui

import com.hitster.core.game.GameCommand
import com.hitster.core.game.GameSessionFactory
import com.hitster.core.game.HostGameReducer
import com.hitster.core.game.ReducerResult
import com.hitster.core.model.PlaybackReference
import com.hitster.core.model.PlayerId
import com.hitster.core.model.PlaylistEntry
import com.hitster.core.model.SessionId
import com.hitster.playback.api.NoOpPlaybackController
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
