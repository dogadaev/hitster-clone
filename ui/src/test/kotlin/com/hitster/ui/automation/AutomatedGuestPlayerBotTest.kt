package com.hitster.ui.automation

import com.hitster.ui.controller.MatchPresenter

/**
 * Regression coverage for AutomatedGuestPlayerBot, keeping the documented behavior of this module stable as gameplay and transport code evolve.
 */

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
import kotlin.test.assertNull

class AutomatedGuestPlayerBotTest {
    private val hostId = PlayerId("host")
    private val guestId = PlayerId("guest")

    @Test
    fun `bot resolves the guest turn and returns control to the host`() {
        val reducer = HostGameReducer(doubtWindowDurationMillis = 15L)
        val presenter = MatchPresenter(
            reducer = reducer,
            playbackController = NoOpPlaybackController(),
            hostId = hostId,
            localPlayerId = hostId,
            initialState = lobbyWithGuest(
                reducer = reducer,
                entries = listOf(
                    entry("seed-host", 1980),
                    entry("seed-guest", 1990),
                    entry("draw-host", 1985),
                    entry("draw-guest", 2000),
                    entry("reserve", 2010),
                ),
            ),
        )
        val bot = AutomatedGuestPlayerBot(
            presenter = presenter,
            playerId = guestId,
            drawDelaySeconds = 0.01f,
            placeDelaySeconds = 0.01f,
            endTurnDelaySeconds = 0.01f,
        )

        presenter.startMatch()
        presenter.drawCard()
        presenter.movePendingCard(requestedSlotIndex = 1)
        presenter.endTurn()

        repeat(10) {
            bot.update(0.02f)
            Thread.sleep(25)
        }

        val guest = presenter.state.requirePlayer(guestId)

        assertEquals(hostId, presenter.state.turn?.activePlayerId)
        assertEquals(2, guest?.timeline?.cards?.size)
        assertEquals(2, guest?.score)
        assertEquals(hostId, presenter.localPlayerId)
        assertNull(presenter.lastError)
    }

    private fun lobbyWithGuest(
        reducer: HostGameReducer,
        entries: List<PlaylistEntry>,
    ) =
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
