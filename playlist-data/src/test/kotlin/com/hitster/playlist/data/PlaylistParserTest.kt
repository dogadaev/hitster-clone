package com.hitster.playlist.data

/**
 * Regression coverage for PlaylistParser, keeping the documented behavior of this module stable as gameplay and transport code evolve.
 */

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaylistParserTest {
    private val parser = PlaylistParser()

    @Test
    fun `valid playlist array is parsed into catalog`() {
        val payload = """
            [
              {
                "id": "a",
                "title": "One More Time",
                "artist": "Daft Punk",
                "releaseYear": 2000,
                "spotifyUri": "spotify:track:a",
                "coverImageUrl": "https://example.com/a.jpg",
                "extraMetadata": {
                  "genre": "House"
                }
              }
            ]
        """.trimIndent()

        val result = parser.parseCatalog(payload)
        val success = assertIs<PlaylistParseResult.Success>(result)

        assertEquals(1, success.catalog.entries.size)
        assertEquals("One More Time", success.catalog.entries.single().title)
        assertEquals("House", success.catalog.entries.single().extraMetadata["genre"])
    }

    @Test
    fun `invalid playlist returns field level errors`() {
        val payload = """
            {
              "entries": [
                {
                  "id": "a",
                  "title": "",
                  "artist": "Artist",
                  "releaseYear": 1800,
                  "spotifyUri": ""
                }
              ]
            }
        """.trimIndent()

        val result = parser.parseCatalog(payload)
        val failure = assertIs<PlaylistParseResult.Failure>(result)

        assertTrue(failure.errors.any { it.field == "title" })
        assertTrue(failure.errors.any { it.field == "releaseYear" })
        assertTrue(failure.errors.any { it.field == "spotifyUri" })
    }
}
