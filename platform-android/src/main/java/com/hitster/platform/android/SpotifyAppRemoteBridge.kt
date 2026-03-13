package com.hitster.platform.android

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hitster.playback.api.PlaybackCommandResult
import com.hitster.playback.api.PlaybackEventListener
import com.hitster.playback.api.PlaybackIssue
import com.hitster.playback.api.PlaybackIssueCode
import com.hitster.playback.api.PlaybackSessionState
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.AuthenticationFailedException
import com.spotify.android.appremote.api.error.NotLoggedInException
import com.spotify.android.appremote.api.error.OfflineModeException
import com.spotify.android.appremote.api.error.SpotifyConnectionTerminatedException
import com.spotify.android.appremote.api.error.SpotifyDisconnectedException
import com.spotify.android.appremote.api.error.SpotifyRemoteServiceException
import com.spotify.android.appremote.api.error.UnsupportedFeatureVersionException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.error.SpotifyAppRemoteException
import com.spotify.protocol.types.PlayerState

class SpotifyAppRemoteBridge(
    private val activity: Activity,
    private val configuration: SpotifyAppRemoteConfiguration,
) : AndroidSpotifyBridge {
    private val tag = "HitsterSpotify"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var playbackListener: PlaybackEventListener? = null
    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var playerStateSubscription: Subscription<PlayerState>? = null
    private var sessionState: PlaybackSessionState = PlaybackSessionState.Idle
    private var lastIssue: PlaybackIssue? = null
    private var started = false
    private var connectionInFlight = false
    private var pendingCommand: PendingCommand? = null
    private var reconnectOnStart = false

    override fun play(spotifyUri: String): PlaybackCommandResult {
        if (!configuration.isConfigured()) {
            return fail(
                code = PlaybackIssueCode.MISSING_CONFIGURATION,
                message = "Spotify playback is not configured. Add spotifyClientId and spotifyRedirectUri to local.properties.",
            )
        }
        if (!SpotifyAppRemote.isSpotifyInstalled(activity)) {
            return fail(
                code = PlaybackIssueCode.APP_NOT_INSTALLED,
                message = "Install the Spotify app on this device to start playback.",
            )
        }

        pendingCommand = PendingCommand.Play(spotifyUri)
        Log.d(tag, "Queueing play command for $spotifyUri")
        onMainThread {
            connectIfNeeded()
        }
        return PlaybackCommandResult.Success
    }

    override fun pause(): PlaybackCommandResult {
        pendingCommand = PendingCommand.Pause
        onMainThread {
            val connectedRemote = spotifyAppRemote?.takeIf { it.isConnected }
            if (connectedRemote == null) {
                pendingCommand = null
                clearIssue()
                updateState(PlaybackSessionState.Idle)
                return@onMainThread
            }

            connectedRemote.playerApi.pause()
                .setResultCallback {
                    pendingCommand = null
                    clearIssue()
                    updateState(PlaybackSessionState.Idle)
                }
                .setErrorCallback(::handleAsyncError)
        }
        return PlaybackCommandResult.Success
    }

    override fun currentState(): PlaybackSessionState = sessionState

    override fun setListener(listener: PlaybackEventListener?) {
        playbackListener = listener
        listener?.onIssue(lastIssue)
        listener?.onSessionStateChanged(sessionState)
    }

    override fun onStart() {
        started = true
        Log.d(
            tag,
            "onStart pending=$pendingCommand reconnectOnStart=$reconnectOnStart connected=${spotifyAppRemote?.isConnected == true}",
        )
        if (pendingCommand == null && !reconnectOnStart) {
            return
        }
        onMainThread {
            connectIfNeeded()
        }
    }

    override fun onStop() {
        started = false
        reconnectOnStart = pendingCommand != null || spotifyAppRemote?.isConnected == true
        Log.d(
            tag,
            "onStop pending=$pendingCommand reconnectOnStart=$reconnectOnStart connected=${spotifyAppRemote?.isConnected == true}",
        )
        onMainThread {
            disconnectRemote(
                clearPendingCommand = false,
                clearIssue = false,
            )
        }
    }

    override fun disconnect() {
        onMainThread {
            reconnectOnStart = false
            disconnectRemote(
                clearPendingCommand = true,
                clearIssue = true,
            )
        }
    }

    private fun connectIfNeeded() {
        if (!started) {
            Log.d(tag, "Skipping connect; activity is not started.")
            reconnectOnStart = true
            return
        }
        val connectedRemote = spotifyAppRemote?.takeIf { it.isConnected }
        if (connectedRemote != null) {
            Log.d(tag, "Reusing active Spotify connection.")
            runPendingCommand(connectedRemote)
            return
        }
        if (connectionInFlight) {
            Log.d(tag, "Spotify connection already in flight.")
            return
        }

        Log.d(tag, "Connecting to Spotify App Remote.")
        connectionInFlight = true
        SpotifyAppRemote.connect(
            activity,
            ConnectionParams.Builder(configuration.clientId)
                .setRedirectUri(configuration.redirectUri)
                .showAuthView(true)
                .build(),
            object : Connector.ConnectionListener {
                override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                    Log.d(tag, "Spotify App Remote connected.")
                    connectionInFlight = false
                    reconnectOnStart = false
                    this@SpotifyAppRemoteBridge.spotifyAppRemote = spotifyAppRemote
                    subscribeToPlayerState(spotifyAppRemote)
                    clearIssue()
                    runPendingCommand(spotifyAppRemote)
                }

                override fun onFailure(error: Throwable) {
                    Log.e(tag, "Spotify App Remote connection failed.", error)
                    connectionInFlight = false
                    handleAsyncError(error)
                }
            },
        )
    }

    private fun runPendingCommand(spotifyAppRemote: SpotifyAppRemote) {
        when (val command = pendingCommand) {
            is PendingCommand.Play -> {
                Log.d(tag, "Executing play command for ${command.spotifyUri}.")
                spotifyAppRemote.playerApi.play(command.spotifyUri)
                    .setResultCallback {
                        Log.d(tag, "Spotify play command accepted for ${command.spotifyUri}.")
                        pendingCommand = null
                        clearIssue()
                        updateState(PlaybackSessionState.Playing(command.spotifyUri))
                    }
                    .setErrorCallback(::handleAsyncError)
            }

            PendingCommand.Pause -> {
                Log.d(tag, "Executing pause command.")
                spotifyAppRemote.playerApi.pause()
                    .setResultCallback {
                        Log.d(tag, "Spotify pause command accepted.")
                        pendingCommand = null
                        clearIssue()
                        updateState(PlaybackSessionState.Idle)
                    }
                    .setErrorCallback(::handleAsyncError)
            }

            null -> {
                Log.d(tag, "Connected without a pending playback command.")
                reconnectOnStart = false
            }
        }
    }

    private fun subscribeToPlayerState(spotifyAppRemote: SpotifyAppRemote) {
        playerStateSubscription?.cancel()
        val subscription = spotifyAppRemote.playerApi.subscribeToPlayerState()
        subscription.setEventCallback { playerState ->
            Log.d(
                tag,
                "Player state update paused=${playerState.isPaused} track=${playerState.track?.uri.orEmpty()}",
            )
            clearIssue()
            updateState(playerState.toSessionState())
        }
        subscription.setErrorCallback(::handleAsyncError)
        playerStateSubscription = subscription
    }

    private fun PlayerState.toSessionState(): PlaybackSessionState {
        val spotifyUri = track?.uri.orEmpty()
        return if (!isPaused && spotifyUri.isNotBlank()) {
            PlaybackSessionState.Playing(spotifyUri)
        } else {
            PlaybackSessionState.Idle
        }
    }

    private fun handleAsyncError(error: Throwable) {
        Log.e(tag, "Spotify App Remote error.", error)
        pendingCommand = null
        playerStateSubscription?.cancel()
        playerStateSubscription = null
        spotifyAppRemote = spotifyAppRemote?.takeIf { it.isConnected }
        reportIssue(mapIssue(error))
        updateState(PlaybackSessionState.Idle)
    }

    private fun fail(
        code: PlaybackIssueCode,
        message: String,
    ): PlaybackCommandResult.Failure {
        val issue = PlaybackIssue(code = code, message = message)
        reportIssue(issue)
        updateState(PlaybackSessionState.Idle)
        return PlaybackCommandResult.Failure(issue)
    }

    private fun reportIssue(issue: PlaybackIssue) {
        lastIssue = issue
        playbackListener?.onIssue(issue)
    }

    private fun clearIssue() {
        if (lastIssue == null) {
            return
        }
        lastIssue = null
        playbackListener?.onIssue(null)
    }

    private fun updateState(state: PlaybackSessionState) {
        sessionState = state
        playbackListener?.onSessionStateChanged(state)
    }

    private fun disconnectRemote(
        clearPendingCommand: Boolean,
        clearIssue: Boolean,
    ) {
        if (clearPendingCommand) {
            pendingCommand = null
        }
        connectionInFlight = false
        playerStateSubscription?.cancel()
        playerStateSubscription = null
        spotifyAppRemote?.let {
            Log.d(tag, "Disconnecting Spotify App Remote.")
            SpotifyAppRemote.disconnect(it)
        }
        spotifyAppRemote = null
        if (clearIssue) {
            clearIssue()
        }
        updateState(PlaybackSessionState.Idle)
    }

    private inline fun onMainThread(crossinline action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post {
                action()
            }
        }
    }

    private fun mapIssue(error: Throwable): PlaybackIssue {
        val code = when (error) {
            is NotLoggedInException,
            is AuthenticationFailedException,
            is UserNotAuthorizedException,
            -> PlaybackIssueCode.NOT_AUTHENTICATED

            is OfflineModeException,
            is SpotifyDisconnectedException,
            is SpotifyConnectionTerminatedException,
            is SpotifyRemoteServiceException,
            -> PlaybackIssueCode.PLAYBACK_UNAVAILABLE

            is UnsupportedFeatureVersionException -> PlaybackIssueCode.PLATFORM_RESTRICTION
            is SpotifyAppRemoteException -> PlaybackIssueCode.PLAYBACK_UNAVAILABLE
            else -> PlaybackIssueCode.UNKNOWN
        }

        return PlaybackIssue(
            code = code,
            message = error.message ?: "Spotify playback failed.",
        )
    }

    private sealed interface PendingCommand {
        data class Play(
            val spotifyUri: String,
        ) : PendingCommand

        data object Pause : PendingCommand
    }
}
