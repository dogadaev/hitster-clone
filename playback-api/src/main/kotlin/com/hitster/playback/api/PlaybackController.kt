package com.hitster.playback.api

import com.hitster.core.model.PlaybackReference

interface PlaybackController {
    fun prepareSession(): PlaybackCommandResult

    fun playTrack(reference: PlaybackReference): PlaybackCommandResult

    fun pause(): PlaybackCommandResult

    fun currentState(): PlaybackSessionState

    fun setListener(listener: PlaybackEventListener?)
}

interface PlaybackEventListener {
    fun onSessionStateChanged(sessionState: PlaybackSessionState) = Unit

    fun onIssue(issue: PlaybackIssue?) = Unit
}

sealed interface PlaybackSessionState {
    data object Disconnected : PlaybackSessionState

    data object Connecting : PlaybackSessionState

    data object Ready : PlaybackSessionState

    data class Playing(
        val spotifyUri: String,
    ) : PlaybackSessionState
}

sealed interface PlaybackCommandResult {
    data object Success : PlaybackCommandResult

    data class Failure(
        val issue: PlaybackIssue,
    ) : PlaybackCommandResult
}

data class PlaybackIssue(
    val code: PlaybackIssueCode,
    val message: String,
)

enum class PlaybackIssueCode {
    APP_NOT_INSTALLED,
    NOT_AUTHENTICATED,
    PLAYBACK_UNAVAILABLE,
    MISSING_METADATA,
    MISSING_CONFIGURATION,
    PLATFORM_RESTRICTION,
    UNKNOWN,
}

class NoOpPlaybackController : PlaybackController {
    private var state: PlaybackSessionState = PlaybackSessionState.Ready
    private var listener: PlaybackEventListener? = null

    override fun prepareSession(): PlaybackCommandResult {
        state = PlaybackSessionState.Ready
        listener?.onIssue(null)
        listener?.onSessionStateChanged(state)
        return PlaybackCommandResult.Success
    }

    override fun playTrack(reference: PlaybackReference): PlaybackCommandResult {
        state = PlaybackSessionState.Playing(reference.spotifyUri)
        listener?.onIssue(null)
        listener?.onSessionStateChanged(state)
        return PlaybackCommandResult.Success
    }

    override fun pause(): PlaybackCommandResult {
        state = PlaybackSessionState.Ready
        listener?.onIssue(null)
        listener?.onSessionStateChanged(state)
        return PlaybackCommandResult.Success
    }

    override fun currentState(): PlaybackSessionState = state

    override fun setListener(listener: PlaybackEventListener?) {
        this.listener = listener
        listener?.onIssue(null)
        listener?.onSessionStateChanged(state)
    }
}
