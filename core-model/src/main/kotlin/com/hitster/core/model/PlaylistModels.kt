package com.hitster.core.model

data class PlaybackReference(
    val spotifyUri: String,
    val previewUrl: String? = null,
)

data class PlaylistEntry(
    val id: String,
    val title: String,
    val artist: String,
    val releaseYear: Int,
    val playbackReference: PlaybackReference,
    val coverImageUrl: String? = null,
    val extraMetadata: Map<String, String> = emptyMap(),
)

data class PlaylistCatalog(
    val id: String,
    val name: String,
    val entries: List<PlaylistEntry>,
)

