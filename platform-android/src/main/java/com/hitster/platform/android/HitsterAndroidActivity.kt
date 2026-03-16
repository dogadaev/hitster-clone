package com.hitster.platform.android

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.hitster.ui.HitsterGameApp

class HitsterAndroidActivity : AndroidApplication() {
    private val tag = "HitsterSpotify"
    private val keepScreenAwakeController = AndroidKeepScreenAwakeController()
    private lateinit var spotifyBridge: AndroidSpotifyBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate intent=${intent.describeForLogs()} savedState=${savedInstanceState != null}")
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
                localDisplayName = Build.MODEL.ifBlank { "Android Player" },
                platformServices = AndroidPlatformServices(),
            ),
            configuration,
        )
    }

    override fun onStart() {
        super.onStart()
        Log.d(tag, "onStart intent=${intent.describeForLogs()}")
        spotifyBridge.onStart()
    }

    override fun onResume() {
        super.onResume()
        Log.d(tag, "onResume")
        enterImmersiveMode()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d(tag, "onNewIntent intent=${intent.describeForLogs()}")
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(tag, "onActivityResult requestCode=$requestCode resultCode=$resultCode intent=${data.describeForLogs()}")
        spotifyBridge.onActivityResult(requestCode, resultCode, data)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    override fun onStop() {
        Log.d(tag, "onStop")
        spotifyBridge.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        Log.d(tag, "onDestroy")
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

    private fun Intent?.describeForLogs(): String {
        if (this == null) {
            return "<null>"
        }
        return buildString {
            append(action ?: "<no-action>")
            append(" data=")
            append(dataString ?: "<no-data>")
            append(" categories=")
            append(categories?.joinToString(prefix = "[", postfix = "]") ?: "[]")
        }
    }
}
