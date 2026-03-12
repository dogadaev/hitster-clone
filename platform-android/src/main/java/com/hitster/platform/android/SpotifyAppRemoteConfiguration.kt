package com.hitster.platform.android

data class SpotifyAppRemoteConfiguration(
    val clientId: String,
    val redirectUri: String,
) {
    fun isConfigured(): Boolean = clientId.isNotBlank() && redirectUri.isNotBlank()
}

object SpotifyAppRemoteConfigurationLoader {
    fun load(): SpotifyAppRemoteConfiguration {
        return SpotifyAppRemoteConfiguration(
            clientId = BuildConfig.SPOTIFY_CLIENT_ID,
            redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI,
        )
    }
}
