package com.hitster.core.game

import com.hitster.core.model.GameState
import com.hitster.core.model.MatchStatus
import com.hitster.core.model.PlaybackReference
import com.hitster.core.model.PlayerId
import com.hitster.core.model.PlaylistEntry
import com.hitster.core.model.SessionId
import com.hitster.core.model.TurnPhase
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
    fun `start game seeds one revealed opening card for each player`() {
        val state = lobbyWithGuest(
            listOf(
                entry("seed-host", 1980),
                entry("seed-guest", 2000),
                entry("draw-host", 1990),
                entry("draw-guest", 2010),
            ),
        )

        val accepted = assertIs<ReducerResult.Accepted>(reducer.reduce(state, GameCommand.StartGame(hostId)))

        assertEquals(MatchStatus.ACTIVE, accepted.state.status)
        assertEquals(TurnPhase.WAITING_FOR_DRAW, accepted.state.turn?.phase)
        assertEquals("seed-host", accepted.state.players[0].timeline.cards.single().id)
        assertEquals("seed-guest", accepted.state.players[1].timeline.cards.single().id)
        assertEquals(2, accepted.state.deck.size)
    }

    @Test
    fun `start game requires enough cards for opening deal and first draw`() {
        val state = lobbyWithGuest(listOf(entry("seed-host", 1980), entry("seed-guest", 2000)))

        val rejected = assertIs<ReducerResult.Rejected>(reducer.reduce(state, GameCommand.StartGame(hostId)))

        assertEquals("At least one revealed starting card per player and one draw card are required to start.", rejected.reason)
    }

    @Test
    fun `leave session removes guest from lobby and publishes snapshot`() {
        val state = lobbyWithGuest(
            listOf(
                entry("seed-host", 1980),
                entry("seed-guest", 2000),
                entry("draw-host", 1990),
            ),
        )

        val accepted = assertIs<ReducerResult.Accepted>(reducer.reduce(state, GameCommand.LeaveSession(guestId)))

        assertEquals(listOf(hostId), accepted.state.players.map { it.id })
        assertTrue(accepted.effects.any { it is GameEffect.PublishSnapshot })
    }

    @Test
    fun `leave session is rejected after the match starts`() {
        var state = lobbyWithGuest(
            listOf(
                entry("seed-host", 1980),
                entry("seed-guest", 2000),
                entry("draw-host", 1990),
                entry("draw-guest", 2010),
            ),
        )
        state = acceptedState(reducer.reduce(state, GameCommand.StartGame(hostId)))

        val rejected = assertIs<ReducerResult.Rejected>(reducer.reduce(state, GameCommand.LeaveSession(guestId)))

        assertEquals("Players can only leave while the match is still in the lobby.", rejected.reason)
    }

    @Test
    fun `existing player can reattach during an active match without duplicating the roster`() {
        var state = lobbyWithGuest(
            listOf(
                entry("seed-host", 1980),
                entry("seed-guest", 2000),
                entry("draw-host", 1990),
                entry("draw-guest", 2010),
            ),
        )
        state = acceptedState(reducer.reduce(state, GameCommand.StartGame(hostId)))
        state = state.copy(
            players = state.players.map { player ->
                if (player.id == guestId) player.copy(connected = false) else player
            },
        )

        val accepted = assertIs<ReducerResult.Accepted>(
            reducer.reduce(
                state,
                GameCommand.JoinSession(
                    playerId = guestId,
                    displayName = "Guest",
                ),
            ),
        )

        assertEquals(2, accepted.state.players.size)
        assertEquals(true, accepted.state.requirePlayer(guestId)?.connected)
        assertTrue(accepted.effects.any { it is GameEffect.PublishSnapshot })
    }

    @Test
    fun `start draw move and end turn advances game`() {
        var state = lobbyWithGuest(
            listOf(
                entry("seed-host", 1980),
                entry("seed-guest", 2000),
                entry("draw-host", 1990),
                entry("draw-guest", 2010),
            ),
        )

        state = acceptedState(reducer.reduce(state, GameCommand.StartGame(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.DrawCard(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.MovePendingCard(hostId, 1)))
        val result = reducer.reduce(state, GameCommand.EndTurn(hostId))
        val accepted = assertIs<ReducerResult.Accepted>(result)

        assertEquals(MatchStatus.ACTIVE, accepted.state.status)
        assertEquals(guestId, accepted.state.turn?.activePlayerId)
        assertEquals(2, accepted.state.players.first().timeline.cards.size)
        assertTrue(accepted.effects.any { it is GameEffect.PausePlayback })
        assertTrue(accepted.effects.any { it is GameEffect.PublishSnapshot })
    }

    @Test
    fun `invalid placement discards card and completes match when deck is exhausted`() {
        var state = lobbyWithGuest(
            listOf(
                entry("seed-host", 1990),
                entry("seed-guest", 2005),
                entry("late", 2010),
            ),
        )
        state = acceptedState(reducer.reduce(state, GameCommand.StartGame(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.DrawCard(hostId)))
        state = state.copy(
            players = state.players.mapIndexed { index, player ->
                if (index == 0) {
                    player.copy(
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
    fun `correct placement completes match when a player reaches ten cards`() {
        var state = lobbyWithGuest(
            listOf(
                entry("seed-host", 1980),
                entry("seed-guest", 2005),
                entry("winner", 2022),
                entry("spare", 2023),
            ),
        )
        state = acceptedState(reducer.reduce(state, GameCommand.StartGame(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.DrawCard(hostId)))
        state = state.copy(
            players = state.players.mapIndexed { index, player ->
                if (index == 0) {
                    player.copy(
                        timeline = timelineEntries(1980, 1985, 1990, 1993, 1997, 2001, 2005, 2010, 2016),
                        pendingCard = player.pendingCard?.copy(proposedSlotIndex = 9),
                    )
                } else {
                    player
                }
            },
        )

        val accepted = assertIs<ReducerResult.Accepted>(reducer.reduce(state, GameCommand.EndTurn(hostId)))

        assertEquals(MatchStatus.COMPLETE, accepted.state.status)
        assertEquals(TurnPhase.COMPLETE, accepted.state.turn?.phase)
        assertEquals(10, accepted.state.players.first().timeline.cards.size)
        assertEquals("Correct placement. Timeline complete.", accepted.state.lastResolution?.message)
    }

    @Test
    fun `every accepted command increments revision and publishes snapshot`() {
        var state = lobbyWithGuest(
            listOf(
                entry("seed-host", 1980),
                entry("seed-guest", 2000),
                entry("draw-host", 1990),
                entry("draw-guest", 2010),
            ),
        )
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

    private fun timelineEntries(vararg years: Int) = com.hitster.core.model.PlayerTimeline(
        cards = years.mapIndexed { index, year ->
            entry("timeline-$index-$year", year)
        },
    )
}
