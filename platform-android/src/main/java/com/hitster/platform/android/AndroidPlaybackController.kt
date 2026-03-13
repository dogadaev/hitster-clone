package com.hitster.platform.android

import android.content.Intent
import com.hitster.core.model.PlaybackReference
import com.hitster.playback.api.PlaybackCommandResult
import com.hitster.playback.api.PlaybackController
import com.hitster.playback.api.PlaybackEventListener
import com.hitster.playback.api.PlaybackIssue
import com.hitster.playback.api.PlaybackIssueCode
import com.hitster.playback.api.PlaybackSessionState

class AndroidPlaybackController(
    private val spotifyBridge: AndroidSpotifyBridge,
) : PlaybackController {
    override fun prepareSession(): PlaybackCommandResult = spotifyBridge.prepareSession()

    override fun playTrack(reference: PlaybackReference): PlaybackCommandResult {
        if (reference.spotifyUri.isBlank()) {
            return PlaybackCommandResult.Failure(
                PlaybackIssue(
                    code = PlaybackIssueCode.MISSING_METADATA,
                    message = "Track metadata is missing a Spotify URI.",
                ),
            )
        }

        return spotifyBridge.play(reference.spotifyUri)
    }

    override fun pause(): PlaybackCommandResult = spotifyBridge.pause()

    override fun currentState(): PlaybackSessionState = spotifyBridge.currentState()

    override fun setListener(listener: PlaybackEventListener?) {
        spotifyBridge.setListener(listener)
    }
}

interface AndroidSpotifyBridge {
    fun prepareSession(): PlaybackCommandResult

    fun play(spotifyUri: String): PlaybackCommandResult

    fun pause(): PlaybackCommandResult

    fun currentState(): PlaybackSessionState

    fun setListener(listener: PlaybackEventListener?)

    fun onStart()

    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    )

    fun onStop()

    fun disconnect()
}

class StubAndroidSpotifyBridge(
    private val issue: PlaybackIssue = PlaybackIssue(
        code = PlaybackIssueCode.MISSING_CONFIGURATION,
        message = "Spotify Android playback is not configured yet.",
    ),
) : AndroidSpotifyBridge {
    private var listener: PlaybackEventListener? = null

    override fun prepareSession(): PlaybackCommandResult {
        listener?.onIssue(issue)
        listener?.onSessionStateChanged(PlaybackSessionState.Disconnected)
        return PlaybackCommandResult.Failure(issue)
    }

    override fun play(spotifyUri: String): PlaybackCommandResult {
        listener?.onIssue(issue)
        listener?.onSessionStateChanged(PlaybackSessionState.Disconnected)
        return PlaybackCommandResult.Failure(issue)
    }

    override fun pause(): PlaybackCommandResult = PlaybackCommandResult.Success

    override fun currentState(): PlaybackSessionState = PlaybackSessionState.Disconnected

    override fun setListener(listener: PlaybackEventListener?) {
        this.listener = listener
        listener?.onSessionStateChanged(PlaybackSessionState.Disconnected)
    }

    override fun onStart() = Unit

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) = Unit

    override fun onStop() = Unit

    override fun disconnect() = Unit
}
