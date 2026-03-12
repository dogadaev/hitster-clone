package com.hitster.platform.android

import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.hitster.ui.HitsterGameApp

class HitsterAndroidActivity : AndroidApplication() {
    private val keepScreenAwakeController = AndroidKeepScreenAwakeController()
    private lateinit var spotifyBridge: AndroidSpotifyBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
        keepScreenAwakeController.enable(window)

        val configuration = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
        }
        spotifyBridge = createSpotifyBridge()

        initialize(
            HitsterGameApp(
                playbackController = AndroidPlaybackController(
                    spotifyBridge = spotifyBridge,
                ),
            ),
            configuration,
        )
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    override fun onStop() {
        spotifyBridge.disconnect()
        super.onStop()
    }

    override fun onDestroy() {
        spotifyBridge.disconnect()
        keepScreenAwakeController.disable(window)
        super.onDestroy()
    }

    private fun createSpotifyBridge(): AndroidSpotifyBridge {
        val configuration = SpotifyAppRemoteConfigurationLoader.load()
        if (!configuration.isConfigured()) {
            return StubAndroidSpotifyBridge(
                issue = com.hitster.playback.api.PlaybackIssue(
                    code = com.hitster.playback.api.PlaybackIssueCode.MISSING_CONFIGURATION,
                    message = "Configure spotifyClientId and spotifyRedirectUri in local.properties to enable Spotify playback.",
                ),
            )
        }
        return SpotifyAppRemoteBridge(
            activity = this,
            configuration = configuration,
        )
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
