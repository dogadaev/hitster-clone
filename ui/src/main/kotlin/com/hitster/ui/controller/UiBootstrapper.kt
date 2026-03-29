package com.hitster.ui.controller

/**
 * Builds hosted and guest controllers from platform services, bundled playlist data, and stable player identity inputs.
 */

import com.hitster.core.game.GameSessionFactory
import com.hitster.core.game.HostGameReducer
import com.hitster.core.model.PlaybackReference
import com.hitster.core.model.PlayerId
import com.hitster.core.model.PlaylistEntry
import com.hitster.core.model.SessionId
import com.hitster.networking.SessionAdvertisementDto
import com.hitster.playback.api.NoOpPlaybackController
import com.hitster.playback.api.PlaybackController
import com.hitster.playlist.data.PlaylistParseResult
import com.hitster.playlist.data.PlaylistParser
import com.hitster.ui.threading.runOnGameThread
import com.badlogic.gdx.graphics.Texture
import kotlin.random.Random

object UiBootstrapper {
    private const val samplePlaylistResourcePath = "sample-playlist.json"
    private val hostId = PlayerId("host")
    private val bundledEntries: List<PlaylistEntry> by lazy { loadBundledEntries() }
    private const val idAlphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    private const val maxDisplayNameLength = 24
    private val funnyNamePrefixes = listOf(
        "Disco",
        "Turbo",
        "Cosmic",
        "Neon",
        "Spicy",
        "Chaotic",
        "Lucky",
        "Groovy",
        "Funky",
        "Sassy",
        "Moon",
        "Captain",
    )
    private val funnyNameSuffixes = listOf(
        "Pelmen",
        "Raccoon",
        "Cucumber",
        "Beat",
        "Pigeon",
        "Otter",
        "Noodle",
        "Banjo",
        "Meteor",
        "Potato",
        "Fox",
        "Dancer",
    )

    /**
     * Creates the host-side controller with a fresh shuffled playlist and the local host identity already attached.
     *
     * Supplying an explicit [shuffleSeed] keeps tests deterministic without affecting real sessions.
     */
    fun createHostedMatchController(
        playbackController: PlaybackController = NoOpPlaybackController(),
        hostDisplayName: String = "Host Player",
        shuffleSeed: Long = nextShuffleSeed(),
        sessionTransportFactory: (MatchPresenter) -> HostedSessionTransport,
    ): HostedMatchController {
        val resolvedHostName = sanitizeDisplayName(hostDisplayName).ifBlank { "Host Player" }
        val reducer = HostGameReducer()
        val lobby = GameSessionFactory.createLobby(
            sessionId = SessionId("local-session-${randomIdSuffix()}"),
            hostId = hostId,
            hostName = resolvedHostName,
            deckEntries = bundledEntries,
            shuffleSeed = shuffleSeed,
        )
        val presenter = MatchPresenter(
            reducer = reducer,
            playbackController = playbackController,
            hostId = hostId,
            localPlayerId = hostId,
            initialState = lobby,
        )

        return HostedMatchController(
            presenter = presenter,
            sessionTransport = sessionTransportFactory(presenter),
        )
    }

    /**
     * Creates a guest controller bound to one discovered advertisement and immediately starts its join flow.
     *
     * The guest identity is intentionally injected so Android and web can preserve stable reconnect identities.
     */
    fun createRemoteGuestController(
        advertisement: SessionAdvertisementDto,
        displayName: String = "Guest Player",
        playerIdFactory: () -> PlayerId = { PlayerId("guest-${randomIdSuffix()}") },
        guestJoinQrTextureFactory: ((String) -> Texture?)? = null,
        clientFactory: (
            advertisement: SessionAdvertisementDto,
            actorId: PlayerId,
            displayName: String,
            onEvent: (com.hitster.networking.HostEventDto) -> Unit,
            onDisconnected: (String) -> Unit,
            onStatusChanged: (String) -> Unit,
        ) -> GuestSessionClient,
    ): RemoteGuestMatchController {
        val resolvedDisplayName = sanitizeDisplayName(displayName).ifBlank { "Guest Player" }
        val playerId = playerIdFactory()
        val controller = RemoteGuestMatchController(
            advertisement = advertisement,
            localPlayerId = playerId,
            guestJoinUrl = advertisement.guestJoinUrl,
            guestJoinQrTexture = advertisement.guestJoinUrl?.let { joinUrl ->
                guestJoinQrTextureFactory?.invoke(joinUrl)
            },
        )
        controller.attachClient(
            clientFactory(
                advertisement,
                playerId,
                resolvedDisplayName,
                { event -> runOnGameThread { controller.handleEvent(event) } },
                { reason -> runOnGameThread { controller.handleDisconnect(reason) } },
                { status -> runOnGameThread { controller.updateConnectionStatus(status) } },
            ),
        )
        controller.connect()
        return controller
    }

    /** Generates a compact id suffix for sessions and guest identities. */
    internal fun randomIdSuffix(random: Random = Random.Default): String =
        buildString(8) {
            repeat(8) {
                append(idAlphabet[random.nextInt(idAlphabet.length)])
            }
        }

    /**
     * Preloads the bundled playlist while an entry screen is already visible so the first host
     * selection does not pay the full JSON parse cost on the touch handler path.
     */
    fun warmBundledPlaylist() {
        bundledEntries.size
    }

    /** Produces a fresh per-session shuffle seed for real host lobbies. */
    internal fun nextShuffleSeed(random: Random = Random.Default): Long = random.nextLong()

    /** Creates a short funny default name so the join flow starts from something usable instead of a blank field. */
    fun randomFunnyDisplayName(random: Random = Random.Default): String {
        val prefix = funnyNamePrefixes[random.nextInt(funnyNamePrefixes.size)]
        val suffix = funnyNameSuffixes[random.nextInt(funnyNameSuffixes.size)]
        return sanitizeDisplayName("$prefix $suffix").ifBlank { "Groovy Otter" }
    }

    /** Normalizes user-entered display names into a short single-line value suitable for transport and UI. */
    fun sanitizeDisplayName(raw: String): String {
        return raw
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .take(maxDisplayNameLength)
    }

    /** Loads the bundled playlist, falling back to a tiny curated deck if the resource is unavailable or malformed. */
    private fun loadBundledEntries(): List<PlaylistEntry> {
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

    /** Provides a minimal real-Spotify fallback deck so local development stays playable without the bundled resource. */
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

    /** Builds one fallback entry with a real Spotify URI so playback flows still exercise the platform bridge. */
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
