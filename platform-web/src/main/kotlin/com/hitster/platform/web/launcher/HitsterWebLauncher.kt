package com.hitster.platform.web.launcher

/**
 * TeaVM launcher that boots the shared libGDX app into the browser with the current viewport sizing rules.
 */

import com.github.xpenatan.gdx.backends.teavm.TeaApplication
import com.github.xpenatan.gdx.backends.teavm.TeaApplicationConfiguration
import com.github.xpenatan.gdx.backends.teavm.TeaAssetPreloadListener
import com.github.xpenatan.gdx.backends.teavm.assetloader.AssetLoader
import com.hitster.platform.web.browser.resolveBrowserDisplayName
import com.hitster.platform.web.integration.WebPlatformServices
import com.hitster.playback.api.NoOpPlaybackController
import com.hitster.ui.app.HitsterGameApp
import org.teavm.jso.browser.Window

object HitsterWebLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = TeaApplicationConfiguration("canvas").apply {
            width = Window.current().innerWidth.coerceAtLeast(1)
            height = Window.current().innerHeight.coerceAtLeast(1)
            usePhysicalPixels = false
            showDownloadLogs = false
            preloadListener = object : TeaAssetPreloadListener {
                override fun onPreload(assetLoader: AssetLoader) {
                    assetLoader.loadScript("freetype.js")
                }
            }
        }
        TeaApplication(
            HitsterGameApp(
                playbackController = NoOpPlaybackController(),
                localDisplayName = resolveBrowserDisplayName(),
                platformServices = WebPlatformServices(),
            ),
            config,
        )
    }
}
