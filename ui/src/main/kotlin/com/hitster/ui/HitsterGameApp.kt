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
    private var enteredDisplayName: String? = null

    override fun create() {
        if (platformServices.supportsHosting) {
            openRoleSelection()
        } else {
            openNameEntry(
                showBackButton = false,
                onBack = {},
            ) {
                openGuestDiscovery(canGoBack = false)
            }
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
                    openNameEntry(
                        showBackButton = true,
                        onBack = ::openRoleSelection,
                    ) {
                        val controller = platformServices.createHostedMatchController(
                            playbackController,
                            resolvedDisplayName(),
                        )
                        activeMatchController = controller
                        setScreen(MatchScreen(controller, animationCatalog))
                    }
                },
                onGuestSelected = {
                    openNameEntry(
                        showBackButton = true,
                        onBack = ::openRoleSelection,
                    ) {
                        openGuestDiscovery(canGoBack = true)
                    }
                },
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
                autoJoinSingleHost = !platformServices.supportsHosting,
                onBack = ::openRoleSelection,
                onHostSelected = { advertisement ->
                    val controller = platformServices.createRemoteGuestController(
                        advertisement = advertisement,
                        displayName = resolvedDisplayName(),
                    )
                    activeMatchController = controller
                    setScreen(
                        GuestConnectingScreen(
                            controller = controller,
                            hostDisplayName = advertisement.hostDisplayName,
                            onConnected = { setScreen(MatchScreen(controller, animationCatalog)) },
                            onCancel = { openGuestDiscovery(canGoBack = canGoBack) },
                        ),
                    )
                },
            ),
        )
    }

    private fun openNameEntry(
        showBackButton: Boolean,
        onBack: () -> Unit,
        onConfirmed: () -> Unit,
    ) {
        setScreen(
            NameEntryScreen(
                initialName = enteredDisplayName.orEmpty(),
                showBackButton = showBackButton,
                requestDisplayNameInput = platformServices::requestDisplayNameInput,
                onBack = onBack,
                onConfirmed = { displayName ->
                    enteredDisplayName = UiBootstrapper.sanitizeDisplayName(displayName)
                    onConfirmed()
                },
            ),
        )
    }

    private fun resolvedDisplayName(): String {
        return enteredDisplayName?.takeIf { it.isNotBlank() }
            ?: UiBootstrapper.sanitizeDisplayName(localDisplayName).ifBlank { "Player" }
    }
}
