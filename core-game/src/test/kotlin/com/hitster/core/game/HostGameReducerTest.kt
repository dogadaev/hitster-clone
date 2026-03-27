package com.hitster.core.game

/**
 * Regression coverage for HostGameReducer, keeping the documented behavior of this module stable as gameplay and transport code evolve.
 */

import com.hitster.core.model.GameState
import com.hitster.core.model.DoubtPhase
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
        assertEquals(1, accepted.state.players[0].score)
        assertEquals(1, accepted.state.players[1].score)
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
    fun `players can rename themselves in the lobby`() {
        val state = lobbyWithGuest(
            listOf(
                entry("seed-host", 1980),
                entry("seed-guest", 2000),
                entry("draw-host", 1990),
            ),
        )

        val accepted = assertIs<ReducerResult.Accepted>(
            reducer.reduce(
                state,
                GameCommand.UpdatePlayerName(
                    actorId = guestId,
                    displayName = "Moon Raccoon",
                ),
            ),
        )

        assertEquals("Moon Raccoon", accepted.state.requirePlayer(guestId)?.displayName)
        assertTrue(accepted.effects.any { it is GameEffect.PublishSnapshot })
    }

    @Test
    fun `host can reorder players in the lobby`() {
        val state = acceptedState(
            reducer.reduce(
                lobbyWithGuest(
                    listOf(
                        entry("seed-host", 1980),
                        entry("seed-guest", 2000),
                        entry("draw-host", 1990),
                    ),
                ),
                GameCommand.JoinSession(
                    playerId = PlayerId("guest-2"),
                    displayName = "Guest Two",
                ),
            ),
        )

        val accepted = assertIs<ReducerResult.Accepted>(
            reducer.reduce(
                state,
                GameCommand.ReorderLobbyPlayers(
                    actorId = hostId,
                    playerId = PlayerId("guest-2"),
                    targetIndex = 1,
                ),
            ),
        )

        assertEquals(listOf(hostId.value, "guest-2", guestId.value), accepted.state.players.map { it.id.value })
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
    fun `guest can arm and clear a doubt while waiting`() {
        var state = lobbyWithGuest(
            listOf(
                entry("seed-host", 1980),
                entry("seed-guest", 2000),
                entry("draw-host", 1990),
                entry("draw-guest", 2010),
            ),
        )

        state = acceptedState(reducer.reduce(state, GameCommand.StartGame(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.AdjustPlayerCoins(hostId, guestId, 1)))
        state = acceptedState(reducer.reduce(state, GameCommand.DrawCard(hostId)))

        val armed = assertIs<ReducerResult.Accepted>(reducer.reduce(state, GameCommand.ToggleDoubt(guestId)))
        assertEquals(guestId, armed.state.doubt?.doubterId)
        assertEquals(DoubtPhase.ARMED, armed.state.doubt?.phase)

        val cleared = assertIs<ReducerResult.Accepted>(reducer.reduce(armed.state, GameCommand.ToggleDoubt(guestId)))
        assertEquals(null, cleared.state.doubt)
    }

    @Test
    fun `redraw discards the current pending card and replaces it with the next deck card`() {
        var state = lobbyWithGuest(
            listOf(
                entry("seed-host", 1980),
                entry("seed-guest", 2000),
                entry("first-draw", 1990),
                entry("replacement", 1995),
                entry("reserve", 2010),
            ),
        )

        state = acceptedState(reducer.reduce(state, GameCommand.StartGame(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.DrawCard(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.MovePendingCard(hostId, 0)))

        val accepted = assertIs<ReducerResult.Accepted>(reducer.reduce(state, GameCommand.RedrawCard(hostId)))

        assertEquals("replacement", accepted.state.requirePlayer(hostId)?.pendingCard?.entry?.id)
        assertEquals(0, accepted.state.requirePlayer(hostId)?.pendingCard?.proposedSlotIndex)
        assertEquals(listOf("first-draw"), accepted.state.discardPile.map { it.id })
        assertEquals(1, accepted.state.deck.size)
        assertEquals(TurnPhase.CARD_POSITIONED, accepted.state.turn?.phase)
        assertTrue(accepted.effects.any { it is GameEffect.PausePlayback })
        assertTrue(
            accepted.effects.any {
                it is GameEffect.PlayTrack && it.reference.spotifyUri == "spotify:track:replacement"
            },
        )
    }

    @Test
    fun `redraw clears an armed doubt for the discarded card`() {
        var state = lobbyWithGuest(
            listOf(
                entry("seed-host", 1980),
                entry("seed-guest", 2000),
                entry("first-draw", 1990),
                entry("replacement", 1995),
                entry("reserve", 2010),
            ),
        )

        state = acceptedState(reducer.reduce(state, GameCommand.StartGame(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.AdjustPlayerCoins(hostId, guestId, 1)))
        state = acceptedState(reducer.reduce(state, GameCommand.DrawCard(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.MovePendingCard(hostId, 1)))
        state = acceptedState(reducer.reduce(state, GameCommand.ToggleDoubt(guestId)))

        val accepted = assertIs<ReducerResult.Accepted>(reducer.reduce(state, GameCommand.RedrawCard(hostId)))

        assertEquals(null, accepted.state.doubt)
        assertEquals("replacement", accepted.state.requirePlayer(hostId)?.pendingCard?.entry?.id)
    }

    @Test
    fun `successful doubt steals the card and spends the coin`() {
        var state = lobbyWithGuest(
            listOf(
                entry("seed-host", 1990),
                entry("seed-guest", 2020),
                entry("late", 2010),
                entry("reserve", 2022),
            ),
        )

        state = acceptedState(reducer.reduce(state, GameCommand.StartGame(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.AdjustPlayerCoins(hostId, guestId, 1)))
        state = acceptedState(reducer.reduce(state, GameCommand.DrawCard(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.MovePendingCard(hostId, 0)))
        state = acceptedState(reducer.reduce(state, GameCommand.ToggleDoubt(guestId)))
        state = acceptedState(reducer.reduce(state, GameCommand.EndTurn(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.MoveDoubtCard(guestId, 1)))

        val accepted = assertIs<ReducerResult.Accepted>(reducer.reduce(state, GameCommand.EndTurn(guestId)))
        val host = accepted.state.requirePlayer(hostId)
        val guest = accepted.state.requirePlayer(guestId)

        assertEquals(guestId, accepted.state.turn?.activePlayerId)
        assertEquals(1, host?.timeline?.cards?.size)
        assertEquals(2, guest?.timeline?.cards?.size)
        assertEquals(listOf("late", "seed-guest"), guest?.timeline?.cards?.map { it.id })
        assertEquals(0, guest?.coins)
        assertEquals(2, guest?.score)
        assertEquals(null, accepted.state.doubt)
        assertEquals(guestId, accepted.state.lastResolution?.playerId)
        assertEquals(true, accepted.state.lastResolution?.correct)
        assertEquals(0, accepted.state.lastResolution?.attemptedSlotIndex)
    }

    @Test
    fun `failed doubt spends the coin and keeps the card with the original player when correct`() {
        var state = lobbyWithGuest(
            listOf(
                entry("seed-host", 1990),
                entry("seed-guest", 2005),
                entry("mid", 1995),
                entry("reserve", 2022),
            ),
        )

        state = acceptedState(reducer.reduce(state, GameCommand.StartGame(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.AdjustPlayerCoins(hostId, guestId, 1)))
        state = acceptedState(reducer.reduce(state, GameCommand.DrawCard(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.MovePendingCard(hostId, 1)))
        state = acceptedState(reducer.reduce(state, GameCommand.ToggleDoubt(guestId)))
        state = acceptedState(reducer.reduce(state, GameCommand.EndTurn(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.MoveDoubtCard(guestId, 0)))

        val accepted = assertIs<ReducerResult.Accepted>(reducer.reduce(state, GameCommand.EndTurn(guestId)))
        val host = accepted.state.requirePlayer(hostId)
        val guest = accepted.state.requirePlayer(guestId)

        assertEquals(guestId, accepted.state.turn?.activePlayerId)
        assertEquals(2, host?.timeline?.cards?.size)
        assertEquals("mid", host?.timeline?.cards?.last()?.id)
        assertEquals(2, host?.score)
        assertEquals(0, guest?.coins)
        assertEquals(1, guest?.score)
        assertEquals(true, accepted.state.lastResolution?.correct)
        assertEquals(hostId, accepted.state.lastResolution?.playerId)
    }

    @Test
    fun `armed doubt keeps playback running until doubt resolution completes`() {
        var state = lobbyWithGuest(
            listOf(
                entry("seed-host", 1990),
                entry("seed-guest", 2005),
                entry("mid", 1995),
                entry("reserve", 2022),
            ),
        )

        state = acceptedState(reducer.reduce(state, GameCommand.StartGame(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.AdjustPlayerCoins(hostId, guestId, 1)))
        state = acceptedState(reducer.reduce(state, GameCommand.DrawCard(hostId)))
        state = acceptedState(reducer.reduce(state, GameCommand.MovePendingCard(hostId, 1)))
        state = acceptedState(reducer.reduce(state, GameCommand.ToggleDoubt(guestId)))

        val enterDoubt = assertIs<ReducerResult.Accepted>(reducer.reduce(state, GameCommand.EndTurn(hostId)))
        assertEquals(TurnPhase.AWAITING_DOUBT_PLACEMENT, enterDoubt.state.turn?.phase)
        assertTrue(enterDoubt.effects.none { it is GameEffect.PausePlayback })

        val placedDoubt = acceptedState(reducer.reduce(enterDoubt.state, GameCommand.MoveDoubtCard(guestId, 0)))
        val resolveDoubt = assertIs<ReducerResult.Accepted>(reducer.reduce(placedDoubt, GameCommand.EndTurn(guestId)))
        assertTrue(resolveDoubt.effects.any { it is GameEffect.PausePlayback })
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
        assertEquals(1, accepted.state.players.first().score)
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
