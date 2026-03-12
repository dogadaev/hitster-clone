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
    private val hostId = PlayerId("host")
    private val guestId = PlayerId("guest")

    fun createPresenter(
        playbackController: PlaybackController = NoOpPlaybackController(),
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
            initialState = withGuest,
        )
    }

    private fun loadEntries(): List<PlaylistEntry> {
        val parseResult = PlaylistParser().parseCatalog(SamplePlaylist.json)
        return when (parseResult) {
            is PlaylistParseResult.Success -> parseResult.catalog.entries
            is PlaylistParseResult.Failure -> fallbackEntries()
        }
    }

    private fun fallbackEntries(): List<PlaylistEntry> {
        return listOf(
            fallbackEntry("fallback-1", "Take On Me", "a-ha", 1985),
            fallbackEntry("fallback-2", "Wonderwall", "Oasis", 1995),
            fallbackEntry("fallback-3", "Crazy in Love", "Beyonce", 2003),
            fallbackEntry("fallback-4", "Blinding Lights", "The Weeknd", 2019),
        )
    }

    private fun fallbackEntry(
        id: String,
        title: String,
        artist: String,
        year: Int,
    ): PlaylistEntry {
        return PlaylistEntry(
            id = id,
            title = title,
            artist = artist,
            releaseYear = year,
            playbackReference = PlaybackReference("spotify:track:$id"),
        )
    }
}

private object SamplePlaylist {
    val json = """
        {
          "id": "starter-pack",
          "name": "Starter Pack",
          "entries": [
            {
              "id": "track-01",
              "title": "Take On Me",
              "artist": "a-ha",
              "releaseYear": 1985,
              "spotifyUri": "spotify:track:track-01",
              "extraMetadata": {
                "genre": "Synth-pop"
              }
            },
            {
              "id": "track-02",
              "title": "Smells Like Teen Spirit",
              "artist": "Nirvana",
              "releaseYear": 1991,
              "spotifyUri": "spotify:track:track-02"
            },
            {
              "id": "track-03",
              "title": "Wonderwall",
              "artist": "Oasis",
              "releaseYear": 1995,
              "spotifyUri": "spotify:track:track-03"
            },
            {
              "id": "track-04",
              "title": "Crazy in Love",
              "artist": "Beyonce",
              "releaseYear": 2003,
              "spotifyUri": "spotify:track:track-04"
            },
            {
              "id": "track-05",
              "title": "Mr. Brightside",
              "artist": "The Killers",
              "releaseYear": 2004,
              "spotifyUri": "spotify:track:track-05"
            },
            {
              "id": "track-06",
              "title": "Rolling in the Deep",
              "artist": "Adele",
              "releaseYear": 2010,
              "spotifyUri": "spotify:track:track-06"
            },
            {
              "id": "track-07",
              "title": "Uptown Funk",
              "artist": "Mark Ronson ft. Bruno Mars",
              "releaseYear": 2014,
              "spotifyUri": "spotify:track:track-07"
            },
            {
              "id": "track-08",
              "title": "Blinding Lights",
              "artist": "The Weeknd",
              "releaseYear": 2019,
              "spotifyUri": "spotify:track:track-08"
            }
          ]
        }
    """.trimIndent()
}

