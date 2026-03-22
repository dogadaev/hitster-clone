package com.hitster.platform.web.integration

/**
 * Web-specific platform services used by the shared libGDX guest flow.
 */

import com.hitster.networking.ClientCommandDto
import com.hitster.networking.SessionAdvertisementDto
import com.hitster.playback.api.PlaybackController
import com.hitster.platform.web.browser.persistBrowserDisplayName
import com.hitster.platform.web.browser.resolveBrowserGuestPlayerId
import com.hitster.platform.web.browser.showBrowserNamePrompt
import com.hitster.platform.web.discovery.BrowserHostDiscoveryService
import com.hitster.platform.web.guest.BrowserGuestSessionClient
import com.hitster.ui.controller.AppPlatformServices
import com.hitster.ui.controller.GuestSessionClient
import com.hitster.ui.controller.HostDiscoveryService
import com.hitster.ui.controller.MatchController
import com.hitster.ui.controller.UiBootstrapper

class WebPlatformServices : AppPlatformServices {
    override val supportsHosting: Boolean = false

    override fun createHostedMatchController(
        playbackController: PlaybackController,
        localDisplayName: String,
    ): MatchController {
        error("Web build is guest-only.")
    }

    override fun createGuestDiscoveryService(): HostDiscoveryService {
        return BrowserHostDiscoveryService()
    }

    override fun createRemoteGuestController(
        advertisement: SessionAdvertisementDto,
        displayName: String,
    ): MatchController {
        return UiBootstrapper.createRemoteGuestController(
            advertisement = advertisement,
            displayName = displayName,
            playerIdFactory = {
                resolveBrowserGuestPlayerId(advertisement.sessionId)
            },
            clientFactory = { sessionAdvertisement, actorId, playerDisplayName, onEvent, onDisconnected, onStatusChanged ->
                BrowserGuestSessionClient(
                    startEndpoint = "/api/guest-sessions/start",
                    joinCommand = ClientCommandDto.JoinSession(
                        actorId = actorId.value,
                        displayName = playerDisplayName,
                    ),
                    advertisement = sessionAdvertisement,
                    onEvent = onEvent,
                    onDisconnected = onDisconnected,
                    onStatusChanged = onStatusChanged,
                )
            },
        )
    }

    override fun requestDisplayNameInput(
        currentName: String,
        onSubmitted: (String?) -> Unit,
    ) {
        val promptResult = showBrowserNamePrompt("Your name", currentName)
        if (promptResult != null) {
            persistBrowserDisplayName(UiBootstrapper.sanitizeDisplayName(promptResult))
        }
        onSubmitted(promptResult)
    }
}
