package com.hitster.platform.web

import com.hitster.networking.ClientCommandDto
import com.hitster.networking.SessionAdvertisementDto
import com.hitster.playback.api.PlaybackController
import com.hitster.ui.AppPlatformServices
import com.hitster.ui.GuestSessionClient
import com.hitster.ui.HostDiscoveryService
import com.hitster.ui.MatchController
import com.hitster.ui.UiBootstrapper

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
