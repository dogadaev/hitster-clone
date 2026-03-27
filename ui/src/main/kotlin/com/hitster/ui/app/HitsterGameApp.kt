package com.hitster.ui.app

/**
 * Top-level libGDX application coordinator that switches between entry screens and active match screens.
 */

import com.badlogic.gdx.Game
import com.hitster.animations.AnimationCatalog
import com.hitster.playback.api.NoOpPlaybackController
import com.hitster.playback.api.PlaybackController
import com.hitster.ui.controller.AppPlatformServices
import com.hitster.ui.controller.MatchController
import com.hitster.ui.controller.UiBootstrapper
import com.hitster.ui.screen.GuestConnectingScreen
import com.hitster.ui.screen.MatchScreen
import com.hitster.ui.screen.RoleSelectionScreen

class HitsterGameApp(
    playbackController: PlaybackController = NoOpPlaybackController(),
    private val localDisplayName: String = "Player",
    private val platformServices: AppPlatformServices,
) : Game() {
    private val animationCatalog = AnimationCatalog.default()
    private val playbackController = playbackController
    private val suggestedDisplayName = UiBootstrapper.randomFunnyDisplayName()
    private var activeMatchController: MatchController? = null
    private var enteredDisplayName: String = UiBootstrapper.sanitizeDisplayName(suggestedDisplayName)

    override fun create() {
        if (platformServices.supportsHosting) {
            openRoleSelection()
        } else {
            openGuestConnect(canGoBack = false)
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
                    val controller = platformServices.createHostedMatchController(
                        playbackController,
                        resolvedDisplayName(),
                    )
                    activeMatchController = controller
                    setScreen(
                        MatchScreen(
                            presenter = controller,
                            animationCatalog = animationCatalog,
                            requestDisplayNameInput = platformServices::requestDisplayNameInput,
                            onLocalDisplayNameEdited = ::updateEnteredDisplayName,
                        ),
                    )
                },
                onGuestSelected = {
                    openGuestConnect(canGoBack = true)
                },
            ),
        )
    }

    private fun openGuestConnect(canGoBack: Boolean) {
        activeMatchController?.dispose()
        activeMatchController = null
        setScreen(
            GuestConnectingScreen(
                discoveryService = platformServices.createGuestDiscoveryService(),
                showBackButton = canGoBack,
                createController = { advertisement ->
                    platformServices.createRemoteGuestController(
                        advertisement = advertisement,
                        displayName = resolvedDisplayName(),
                    ).also { controller ->
                        activeMatchController = controller
                    }
                },
                onConnected = { controller ->
                    activeMatchController = controller
                    setScreen(
                        MatchScreen(
                            presenter = controller,
                            animationCatalog = animationCatalog,
                            requestDisplayNameInput = platformServices::requestDisplayNameInput,
                            onLocalDisplayNameEdited = ::updateEnteredDisplayName,
                        ),
                    )
                },
                onCancel = {
                    if (canGoBack) {
                        openRoleSelection()
                    } else {
                        openGuestConnect(canGoBack = false)
                    }
                },
            ),
        )
    }

    private fun resolvedDisplayName(): String {
        return enteredDisplayName.takeIf { it.isNotBlank() }
            ?: UiBootstrapper.sanitizeDisplayName(localDisplayName).ifBlank { suggestedDisplayName }
    }

    private fun updateEnteredDisplayName(displayName: String) {
        enteredDisplayName = UiBootstrapper.sanitizeDisplayName(displayName).ifBlank { suggestedDisplayName }
    }
}
