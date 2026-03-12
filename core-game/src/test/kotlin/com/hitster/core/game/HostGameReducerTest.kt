package com.hitster.core.game

import com.hitster.core.model.GameState
import com.hitster.core.model.MatchStatus
import com.hitster.core.model.PlaybackReference
import com.hitster.core.model.PlayerId
import com.hitster.core.model.PlaylistEntry
import com.hitster.core.model.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HostGameReducerTest {
    private val reducer = HostGameReducer()
    private val hostId = PlayerId("host")
    private val guestId = PlayerId("guest")

    @Test
    fun `start draw move and end turn advances game`() {
        var state = lobbyWithGuest(listOf(entry("a", 1985), entry("b", 1999)))

        state = acceptedState(reducer.reduce(state, GameCommand.StartGame(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.DrawCard(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.MovePendingCard(hostId, 0)))
        val result = reducer.reduce(state, GameCommand.EndTurn(hostId))
        val accepted = assertIs<ReducerResult.Accepted>(result)

        assertEquals(MatchStatus.ACTIVE, accepted.state.status)
        assertEquals(guestId, accepted.state.turn?.activePlayerId)
        assertEquals(1, accepted.state.players.first().timeline.cards.size)
        assertTrue(accepted.effects.any { it is GameEffect.PausePlayback })
        assertTrue(accepted.effects.any { it is GameEffect.PublishSnapshot })
    }

    @Test
    fun `invalid placement discards card and completes match when deck is exhausted`() {
        var state = lobbyWithGuest(listOf(entry("late", 2010)))
        state = acceptedState(reducer.reduce(state, GameCommand.StartGame(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.DrawCard(hostId)))
        state = state.copy(
            players = state.players.mapIndexed { index, player ->
                if (index == 0) {
                    player.copy(
                        timeline = player.timeline.insertAt(0, entry("existing", 1990)),
                        pendingCard = player.pendingCard?.copy(proposedSlotIndex = 0),
                    )
                } else {
                    player
                }
            },
        )

        val result = reducer.reduce(state, GameCommand.EndTurn(hostId))
        val accepted = assertIs<ReducerResult.Accepted>(result)

        assertEquals(MatchStatus.COMPLETE, accepted.state.status)
        assertEquals(1, accepted.state.discardPile.size)
        assertEquals(0, accepted.state.players.first().score)
        assertEquals(false, accepted.state.lastResolution?.correct)
    }

    @Test
    fun `every accepted command increments revision and publishes snapshot`() {
        var state = lobbyWithGuest(listOf(entry("a", 1985), entry("b", 1999)))
        val initialRevision = state.revision

        val start = assertIs<ReducerResult.Accepted>(reducer.reduce(state, GameCommand.StartGame(hostId)))
        state = start.state
        assertEquals(initialRevision + 1, state.revision)
        assertSnapshotEffect(start.state, start.effects)

        val draw = assertIs<ReducerResult.Accepted>(reducer.reduce(state, GameCommand.DrawCard(hostId)))
        state = draw.state
        assertEquals(initialRevision + 2, state.revision)
        assertSnapshotEffect(draw.state, draw.effects)

        val move = assertIs<ReducerResult.Accepted>(reducer.reduce(state, GameCommand.MovePendingCard(hostId, 0)))
        assertEquals(initialRevision + 3, move.state.revision)
        assertSnapshotEffect(move.state, move.effects)
    }

    private fun lobbyWithGuest(entries: List<PlaylistEntry>): GameState {
        val lobby = GameSessionFactory.createLobby(
            sessionId = SessionId("session"),
            hostId = hostId,
            hostName = "Host",
            deckEntries = entries,
        )

        return acceptedState(
            reducer.reduce(
                lobby,
                GameCommand.JoinSession(
                    playerId = guestId,
                    displayName = "Guest",
                ),
            ),
        )
    }

    private fun acceptedState(result: ReducerResult): GameState {
        return assertIs<ReducerResult.Accepted>(result).state
    }

    private fun assertSnapshotEffect(state: GameState, effects: List<GameEffect>) {
        val snapshot = assertNotNull(effects.filterIsInstance<GameEffect.PublishSnapshot>().singleOrNull())
        assertEquals(state.revision, snapshot.state.revision)
    }

    private fun entry(id: String, year: Int): PlaylistEntry {
        return PlaylistEntry(
            id = id,
            title = id,
            artist = "Artist",
            releaseYear = year,
            playbackReference = PlaybackReference("spotify:track:$id"),
        )
    }
}
