package com.hitster.playback.api

import com.hitster.core.model.PlaybackReference

interface PlaybackController {
    fun playTrack(reference: PlaybackReference): PlaybackCommandResult

    fun pause(): PlaybackCommandResult

    fun currentState(): PlaybackSessionState
}

sealed interface PlaybackSessionState {
    data object Idle : PlaybackSessionState

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
    PLATFORM_RESTRICTION,
    UNKNOWN,
}

class NoOpPlaybackController : PlaybackController {
    private var state: PlaybackSessionState = PlaybackSessionState.Idle

    override fun playTrack(reference: PlaybackReference): PlaybackCommandResult {
        state = PlaybackSessionState.Playing(reference.spotifyUri)
        return PlaybackCommandResult.Success
    }

    override fun pause(): PlaybackCommandResult {
        state = PlaybackSessionState.Idle
        return PlaybackCommandResult.Success
    }

    override fun currentState(): PlaybackSessionState = state
}
