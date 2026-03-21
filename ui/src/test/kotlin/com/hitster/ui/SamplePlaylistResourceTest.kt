package com.hitster.ui

import com.hitster.playlist.data.PlaylistParseResult
import com.hitster.playlist.data.PlaylistParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SamplePlaylistResourceTest {
    @Test
    fun `bundled sample playlist uses real spotify track uris`() {
        val resourceText = UiBootstrapper::class.java.classLoader
            ?.getResourceAsStream("sample-playlist.json")
            ?.bufferedReader()
            ?.use { it.readText() }

        assertNotNull(resourceText)

        val parseResult = PlaylistParser().parseCatalog(resourceText)
        assertTrue(parseResult is PlaylistParseResult.Success)

        val entries = parseResult.catalog.entries
        assertEquals(1149, entries.size)
        assertTrue(entries.all { it.playbackReference.spotifyUri.startsWith("spotify:track:") })
        assertFalse(entries.any { it.playbackReference.spotifyUri.contains("spotify:track:track-") })
    }
}
