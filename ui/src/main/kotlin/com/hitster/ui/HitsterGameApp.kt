package com.hitster.ui

import com.badlogic.gdx.Game
import com.hitster.animations.AnimationCatalog
import com.hitster.playback.api.NoOpPlaybackController
import com.hitster.playback.api.PlaybackController

class HitsterGameApp(
    playbackController: PlaybackController = NoOpPlaybackController(),
    private val localDisplayName: String = "Player",
    private val platformServices: AppPlatformServices,
) : Game() {
    private val animationCatalog = AnimationCatalog.default()
    private val playbackController = playbackController
    private var activeMatchController: MatchController? = null

    override fun create() {
        if (platformServices.supportsHosting) {
            openRoleSelection()
        } else {
            openGuestDiscovery(canGoBack = false)
        }
    }

    override fun dispose() {
        activeMatchController?.dispose()
        super.dispose()
    }

    private fun openRoleSelection() {
        activeMatchController?.dispose()
        activeMatchController = null
        setScreen(
            RoleSelectionScreen(
                onHostSelected = {
                    val controller = platformServices.createHostedMatchController(playbackController, localDisplayName)
                    activeMatchController = controller
                    setScreen(MatchScreen(controller, animationCatalog))
                },
                onGuestSelected = { openGuestDiscovery(canGoBack = true) },
            ),
        )
    }

    private fun openGuestDiscovery(canGoBack: Boolean) {
        activeMatchController?.dispose()
        activeMatchController = null
        setScreen(
            GuestDiscoveryScreen(
                discoveryService = platformServices.createGuestDiscoveryService(),
                showBackButton = canGoBack,
                onBack = ::openRoleSelection,
                onHostSelected = { advertisement ->
                    val controller = platformServices.createRemoteGuestController(
                        advertisement = advertisement,
                        displayName = localDisplayName,
                    )
                    activeMatchController = controller
                    setScreen(MatchScreen(controller, animationCatalog))
                },
            ),
        )
    }
}
