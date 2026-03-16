package com.hitster.platform.web

import com.github.xpenatan.gdx.backends.teavm.TeaApplication
import com.github.xpenatan.gdx.backends.teavm.TeaApplicationConfiguration
import com.github.xpenatan.gdx.backends.teavm.TeaAssetPreloadListener
import com.github.xpenatan.gdx.backends.teavm.assetloader.AssetLoader
import com.hitster.playback.api.NoOpPlaybackController
import com.hitster.ui.HitsterGameApp

object HitsterWebLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = TeaApplicationConfiguration("canvas").apply {
            width = 0
            height = 0
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
