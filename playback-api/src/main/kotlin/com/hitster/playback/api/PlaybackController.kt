package com.hitster.playback.api

/**
 * Shared playback boundary that keeps the core match flow independent from Spotify SDK classes and platform lifecycles.
 */

import com.hitster.core.model.PlaybackReference

interface PlaybackController {
    /** Prepares or reconnects the host playback session before a lobby can start a real match. */
    fun prepareSession(): PlaybackCommandResult

    /** Starts playback for the currently drawn card using the platform-specific playback backend. */
    fun playTrack(reference: PlaybackReference): PlaybackCommandResult

    /** Pauses the current preview track when a turn resolves or playback must stop. */
    fun pause(): PlaybackCommandResult

    /** Returns the latest known platform playback state for UI and lobby gating. */
    fun currentState(): PlaybackSessionState

    /** Registers the shared listener that should receive state changes and platform issues. */
    fun setListener(listener: PlaybackEventListener?)
}

interface PlaybackEventListener {
    /** Called when the underlying playback bridge changes between disconnected, ready, or actively playing. */
    fun onSessionStateChanged(sessionState: PlaybackSessionState) = Unit

    /** Reports the latest platform issue, or `null` when a prior issue has been cleared. */
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

    /** Simulates a successful host pairing flow for tests and non-Spotify environments. */
    override fun prepareSession(): PlaybackCommandResult {
        state = PlaybackSessionState.Ready
        listener?.onIssue(null)
        listener?.onSessionStateChanged(state)
        return PlaybackCommandResult.Success
    }

    /** Pretends playback started so shared UI and reducer flows can be exercised without a real backend. */
    override fun playTrack(reference: PlaybackReference): PlaybackCommandResult {
        state = PlaybackSessionState.Playing(reference.spotifyUri)
        listener?.onIssue(null)
        listener?.onSessionStateChanged(state)
        return PlaybackCommandResult.Success
    }

    /** Returns the mock session to the ready state after a preview is finished. */
    override fun pause(): PlaybackCommandResult {
        state = PlaybackSessionState.Ready
        listener?.onIssue(null)
        listener?.onSessionStateChanged(state)
        return PlaybackCommandResult.Success
    }

    /** Exposes the latest mock state to the rest of the shared app. */
    override fun currentState(): PlaybackSessionState = state

    /** Immediately synchronizes new listeners with the current mock state. */
    override fun setListener(listener: PlaybackEventListener?) {
        this.listener = listener
        listener?.onIssue(null)
        listener?.onSessionStateChanged(state)
    }
}
