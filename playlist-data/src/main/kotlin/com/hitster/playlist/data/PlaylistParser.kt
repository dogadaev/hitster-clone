package com.hitster.playlist.data

/**
 * Validates playlist JSON and turns it into strongly typed catalog entries with user-facing validation errors.
 */

import com.hitster.core.model.PlaybackReference
import com.hitster.core.model.PlaylistCatalog
import com.hitster.core.model.PlaylistEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PlaylistValidationError(
    val entryIndex: Int?,
    val field: String,
    val message: String,
)

sealed interface PlaylistParseResult {
    data class Success(
        val catalog: PlaylistCatalog,
    ) : PlaylistParseResult

    data class Failure(
        val errors: List<PlaylistValidationError>,
    ) : PlaylistParseResult
}

class PlaylistParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parseCatalog(
        payload: String,
        fallbackCatalogId: String = "default-playlist",
        fallbackCatalogName: String = "Imported Playlist",
    ): PlaylistParseResult {
        val root = try {
            json.parseToJsonElement(payload)
        } catch (exception: Exception) {
            return PlaylistParseResult.Failure(
                listOf(
                    PlaylistValidationError(
                        entryIndex = null,
                        field = "json",
                        message = "Playlist JSON is malformed: ${exception.message}",
                    ),
                ),
            )
        }

        val catalogObject = root as? JsonObject
        val entriesElement = when (root) {
            is JsonArray -> root
            is JsonObject -> root["entries"] as? JsonArray
            else -> null
        }

        if (entriesElement == null) {
            return PlaylistParseResult.Failure(
                listOf(
                    PlaylistValidationError(
                        entryIndex = null,
                        field = "entries",
                        message = "Playlist JSON must be an array or an object containing an entries array.",
                    ),
                ),
            )
        }

        val errors = mutableListOf<PlaylistValidationError>()
        val entries = entriesElement.mapIndexedNotNull { index, element ->
            parseEntry(index, element, errors)
        }

        if (entries.isEmpty()) {
            errors += PlaylistValidationError(
                entryIndex = null,
                field = "entries",
                message = "Playlist must contain at least one valid entry.",
            )
        }

        return if (errors.isEmpty()) {
            PlaylistParseResult.Success(
                PlaylistCatalog(
                    id = catalogObject.stringValue("id") ?: fallbackCatalogId,
                    name = catalogObject.stringValue("name") ?: fallbackCatalogName,
                    entries = entries,
                ),
            )
        } else {
            PlaylistParseResult.Failure(errors)
        }
    }

    private fun parseEntry(
        index: Int,
        element: JsonElement,
        errors: MutableList<PlaylistValidationError>,
    ): PlaylistEntry? {
        val obj = element as? JsonObject
        if (obj == null) {
            errors += error(index, "entry", "Entry must be a JSON object.")
            return null
        }

        val id = obj.requiredString(index, "id", errors)
        val title = obj.requiredString(index, "title", errors)
        val artist = obj.requiredString(index, "artist", errors)
        val releaseYear = obj.requiredInt(index, "releaseYear", errors) { it in 1900..2100 }
        val spotifyUri = obj.requiredString(index, "spotifyUri", errors)
        val coverImageUrl = obj.optionalString("coverImageUrl")
        val previewUrl = obj.optionalString("previewUrl")
        val extraMetadata = obj.optionalStringMap(index, "extraMetadata", errors)

        val resolvedId = id ?: return null
        val resolvedTitle = title ?: return null
        val resolvedArtist = artist ?: return null
        val resolvedReleaseYear = releaseYear ?: return null
        val resolvedSpotifyUri = spotifyUri ?: return null

        return PlaylistEntry(
            id = resolvedId,
            title = resolvedTitle,
            artist = resolvedArtist,
            releaseYear = resolvedReleaseYear,
            playbackReference = PlaybackReference(
                spotifyUri = resolvedSpotifyUri,
                previewUrl = previewUrl,
            ),
            coverImageUrl = coverImageUrl,
            extraMetadata = extraMetadata,
        )
    }

    private fun error(index: Int?, field: String, message: String): PlaylistValidationError {
        return PlaylistValidationError(
            entryIndex = index,
            field = field,
            message = message,
        )
    }
}

private fun JsonObject.requiredString(
    entryIndex: Int,
    field: String,
    errors: MutableList<PlaylistValidationError>,
): String? {
    val value = optionalString(field)
    return if (value.isNullOrBlank()) {
        errors += PlaylistValidationError(entryIndex, field, "Field '$field' must be a non-empty string.")
        null
    } else {
        value
    }
}

private fun JsonObject.requiredInt(
    entryIndex: Int,
    field: String,
    errors: MutableList<PlaylistValidationError>,
    validator: (Int) -> Boolean,
): Int? {
    val primitive = this[field] as? JsonPrimitive
    val value = primitive?.contentOrNull?.toIntOrNull()
    return when {
        value == null -> {
            errors += PlaylistValidationError(entryIndex, field, "Field '$field' must be an integer.")
            null
        }

        !validator(value) -> {
            errors += PlaylistValidationError(entryIndex, field, "Field '$field' is outside the supported range.")
            null
        }

        else -> value
    }
}

private fun JsonObject.optionalString(field: String): String? {
    return (this[field] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun JsonObject.optionalStringMap(
    entryIndex: Int,
    field: String,
    errors: MutableList<PlaylistValidationError>,
): Map<String, String> {
    val value = this[field] ?: return emptyMap()
    val objectValue = value as? JsonObject
    if (objectValue == null) {
        errors += PlaylistValidationError(entryIndex, field, "Field '$field' must be an object of string values.")
        return emptyMap()
    }

    val invalidKeys = objectValue.entries.filterNot { (_, element) ->
        element is JsonPrimitive && element.isString
    }.map { it.key }

    if (invalidKeys.isNotEmpty()) {
        errors += PlaylistValidationError(
            entryIndex,
            field,
            "Field '$field' must contain only string values. Invalid keys: ${invalidKeys.joinToString()}",
        )
        return emptyMap()
    }

    return objectValue.mapValues { (_, element) -> element.jsonPrimitive.content }
}

private fun JsonObject?.stringValue(field: String): String? = this?.optionalString(field)
