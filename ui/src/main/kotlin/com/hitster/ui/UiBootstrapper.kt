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
import com.hitster.playback.api.PlaybackController
import com.hitster.playlist.data.PlaylistParseResult
import com.hitster.playlist.data.PlaylistParser

object UiBootstrapper {
    private const val samplePlaylistResourcePath = "sample-playlist.json"
    private val hostId = PlayerId("host")
    private val guestId = PlayerId("guest")

    fun createPresenter(
        playbackController: PlaybackController = NoOpPlaybackController(),
        localPlayerId: PlayerId = hostId,
    ): MatchPresenter {
        val reducer = HostGameReducer()
        val lobby = GameSessionFactory.createLobby(
            sessionId = SessionId("local-session"),
            hostId = hostId,
            hostName = "Host Player",
            deckEntries = loadEntries(),
            shuffleSeed = 42L,
        )

        val withGuest = (reducer.reduce(
            lobby,
            GameCommand.JoinSession(
                playerId = guestId,
                displayName = "Guest Player",
            ),
        ) as ReducerResult.Accepted).state

        return MatchPresenter(
            reducer = reducer,
            playbackController = playbackController,
            hostId = hostId,
            localPlayerId = localPlayerId,
            initialState = withGuest,
        )
    }

    fun createAutomatedGuestBot(presenter: MatchPresenter): AutomatedGuestPlayerBot? {
        if (presenter.localPlayerId != hostId) {
            return null
        }

        return AutomatedGuestPlayerBot(
            presenter = presenter,
            playerId = guestId,
        )
    }

    private fun loadEntries(): List<PlaylistEntry> {
        val sampleJson = UiBootstrapper::class.java.classLoader
            ?.getResourceAsStream(samplePlaylistResourcePath)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return fallbackEntries()
        val parseResult = PlaylistParser().parseCatalog(sampleJson)
        return when (parseResult) {
            is PlaylistParseResult.Success -> parseResult.catalog.entries
            is PlaylistParseResult.Failure -> fallbackEntries()
        }
    }

    private fun fallbackEntries(): List<PlaylistEntry> {
        return listOf(
            fallbackEntry("fallback-1", "Take On Me", "a-ha", 1985, "spotify:track:2WfaOiMkCvy7F5fcp2zZ8L"),
            fallbackEntry("fallback-2", "Billie Jean", "Michael Jackson", 1982, "spotify:track:7J1uxwnxfQLu4APicE5Rnj"),
            fallbackEntry("fallback-3", "Sweet Dreams (Are Made of This)", "Eurythmics", 1983, "spotify:track:1TfqLAPs4K3s2rJMoCokcS"),
            fallbackEntry("fallback-4", "Like a Prayer", "Madonna", 1989, "spotify:track:2v7ywbUzCgcVohHaKUcacV"),
            fallbackEntry("fallback-5", "Smells Like Teen Spirit", "Nirvana", 1991, "spotify:track:4CeeEOM32jQcH3eN9Q2dGj"),
            fallbackEntry("fallback-6", "Wonderwall", "Oasis", 1995, "spotify:track:1qPbGZqppFwLwcBC1JQ6Vr"),
            fallbackEntry("fallback-7", "No Scrubs", "TLC", 1999, "spotify:track:1KGi9sZVMeszgZOWivFpxs"),
            fallbackEntry("fallback-8", "Crazy in Love", "Beyonce", 2003, "spotify:track:0TwBtDAWpkpM3srywFVOV5"),
            fallbackEntry("fallback-9", "Mr. Brightside", "The Killers", 2004, "spotify:track:003vvx7Niy0yvhvHt4a68B"),
            fallbackEntry("fallback-10", "Rolling in the Deep", "Adele", 2010, "spotify:track:4OSBTYWVwsQhGLF9NHvIbR"),
            fallbackEntry("fallback-11", "Uptown Funk", "Mark Ronson ft. Bruno Mars", 2014, "spotify:track:32OlwWuMpZ6b0aN2RZOeMS"),
            fallbackEntry("fallback-12", "Blinding Lights", "The Weeknd", 2019, "spotify:track:0VjIjW4GlUZAMYd2vXMi3b"),
            fallbackEntry("fallback-13", "Levitating", "Dua Lipa", 2020, "spotify:track:5uu0D02efCoFMQiLYFT32e"),
        )
    }

    private fun fallbackEntry(
        id: String,
        title: String,
        artist: String,
        year: Int,
        spotifyUri: String,
    ): PlaylistEntry {
        return PlaylistEntry(
            id = id,
            title = title,
            artist = artist,
            releaseYear = year,
            playbackReference = PlaybackReference(spotifyUri),
        )
    }
}
