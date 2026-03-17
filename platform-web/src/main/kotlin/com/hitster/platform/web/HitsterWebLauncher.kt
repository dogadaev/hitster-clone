package com.hitster.platform.web

import com.github.xpenatan.gdx.backends.teavm.TeaApplication
import com.github.xpenatan.gdx.backends.teavm.TeaApplicationConfiguration
import com.github.xpenatan.gdx.backends.teavm.TeaAssetPreloadListener
import com.github.xpenatan.gdx.backends.teavm.assetloader.AssetLoader
import com.hitster.playback.api.NoOpPlaybackController
import com.hitster.ui.HitsterGameApp
import org.teavm.jso.browser.Window

object HitsterWebLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = TeaApplicationConfiguration("canvas").apply {
            width = Window.current().innerWidth.coerceAtLeast(1)
            height = Window.current().innerHeight.coerceAtLeast(1)
            usePhysicalPixels = true
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
