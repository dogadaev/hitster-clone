package com.hitster.platform.android

import com.hitster.core.model.PlaybackReference
import com.hitster.playback.api.PlaybackCommandResult
import com.hitster.playback.api.PlaybackController
import com.hitster.playback.api.PlaybackIssue
import com.hitster.playback.api.PlaybackIssueCode
import com.hitster.playback.api.PlaybackSessionState

class AndroidPlaybackController(
    private val spotifyBridge: AndroidSpotifyBridge,
) : PlaybackController {
    private var sessionState: PlaybackSessionState = PlaybackSessionState.Idle

    override fun playTrack(reference: PlaybackReference): PlaybackCommandResult {
        if (reference.spotifyUri.isBlank()) {
            return PlaybackCommandResult.Failure(
                PlaybackIssue(
                    code = PlaybackIssueCode.MISSING_METADATA,
                    message = "Track metadata is missing a Spotify URI.",
                ),
            )
        }

        val result = spotifyBridge.play(reference.spotifyUri)
        if (result is PlaybackCommandResult.Success) {
            sessionState = PlaybackSessionState.Playing(reference.spotifyUri)
        }
        return result
    }

    override fun pause(): PlaybackCommandResult {
        val result = spotifyBridge.pause()
        if (result is PlaybackCommandResult.Success) {
            sessionState = PlaybackSessionState.Idle
        }
        return result
    }

    override fun currentState(): PlaybackSessionState = sessionState
}

interface AndroidSpotifyBridge {
    fun play(spotifyUri: String): PlaybackCommandResult

    fun pause(): PlaybackCommandResult
}

class StubAndroidSpotifyBridge : AndroidSpotifyBridge {
    override fun play(spotifyUri: String): PlaybackCommandResult {
        return PlaybackCommandResult.Failure(
            PlaybackIssue(
                code = PlaybackIssueCode.NOT_AUTHENTICATED,
                message = "Spotify SDK bridge is not wired yet on Android.",
            ),
        )
    }

    override fun pause(): PlaybackCommandResult = PlaybackCommandResult.Success
}
