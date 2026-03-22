package com.hitster.platform.android

/**
 * Runs the explicit Android Spotify authorization flow before the app connects the App Remote session.
 */

import android.app.Activity
import android.content.Intent
import com.hitster.playback.api.PlaybackIssue
import com.hitster.playback.api.PlaybackIssueCode
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse

class SpotifyAuthorizationCoordinator(
    private val activity: Activity,
    private val configuration: SpotifyAppRemoteConfiguration,
) {
    fun startAuthorization() {
        val request = AuthorizationRequest.Builder(
            configuration.clientId,
            AuthorizationResponse.Type.CODE,
            configuration.redirectUri,
        )
            .setScopes(arrayOf(APP_REMOTE_SCOPE))
            .setShowDialog(false)
            .build()

        AuthorizationClient.openLoginActivity(activity, AUTH_REQUEST_CODE, request)
    }

    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        intent: Intent?,
    ): AuthorizationResult? {
        if (requestCode != AUTH_REQUEST_CODE) {
            return null
        }

        val response = AuthorizationClient.getResponse(resultCode, intent)
        return when (response.type) {
            AuthorizationResponse.Type.CODE,
            AuthorizationResponse.Type.TOKEN,
            -> AuthorizationResult.Success

            AuthorizationResponse.Type.ERROR -> AuthorizationResult.Failure(
                PlaybackIssue(
                    code = PlaybackIssueCode.NOT_AUTHENTICATED,
                    message = response.error ?: "Spotify authorization failed.",
                ),
            )

            AuthorizationResponse.Type.EMPTY -> AuthorizationResult.Failure(
                PlaybackIssue(
                    code = PlaybackIssueCode.NOT_AUTHENTICATED,
                    message = "Spotify pairing was canceled.",
                ),
            )

            else -> AuthorizationResult.Failure(
                PlaybackIssue(
                    code = PlaybackIssueCode.UNKNOWN,
                    message = "Spotify authorization returned an unexpected response.",
                ),
            )
        }
    }

    sealed interface AuthorizationResult {
        data object Success : AuthorizationResult

        data class Failure(
            val issue: PlaybackIssue,
        ) : AuthorizationResult
    }

    private companion object {
        const val APP_REMOTE_SCOPE = "app-remote-control"
        const val AUTH_REQUEST_CODE = 47027
    }
}
