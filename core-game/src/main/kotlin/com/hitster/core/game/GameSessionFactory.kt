package com.hitster.core.game

/**
 * Creates the initial host-owned game session with shuffled deck state and the minimal player roster required by the reducer.
 */

import com.hitster.core.model.DeckState
import com.hitster.core.model.GameState
import com.hitster.core.model.PlayerId
import com.hitster.core.model.PlayerState
import com.hitster.core.model.PlaylistEntry
import com.hitster.core.model.SessionId

object GameSessionFactory {
    fun createLobby(
        sessionId: SessionId,
        hostId: PlayerId,
        hostName: String,
        deckEntries: List<PlaylistEntry>,
        shuffleSeed: Long? = null,
    ): GameState {
        val deck = DeckState.fromEntries(deckEntries).let { initialDeck ->
            shuffleSeed?.let(initialDeck::shuffled) ?: initialDeck
        }

        return GameState(
            sessionId = sessionId,
            hostId = hostId,
            players = listOf(
                PlayerState(
                    id = hostId,
                    displayName = hostName,
                ),
            ),
            deck = deck,
        )
    }
}
