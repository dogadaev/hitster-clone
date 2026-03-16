package com.hitster.platform.web

import com.hitster.networking.ClientCommandDto
import com.hitster.networking.SessionAdvertisementDto
import com.hitster.playback.api.PlaybackController
import com.hitster.ui.AppPlatformServices
import com.hitster.ui.GuestSessionClient
import com.hitster.ui.HostDiscoveryService
import com.hitster.ui.MatchController
import com.hitster.ui.UiBootstrapper
import org.teavm.jso.browser.Window

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
            clientFactory = { sessionAdvertisement, actorId, playerDisplayName, onEvent, onDisconnected ->
                BrowserGuestSessionClient(
                    websocketUrl = websocketUrl(sessionAdvertisement),
                    joinCommand = ClientCommandDto.JoinSession(
                        actorId = actorId.value,
                        displayName = playerDisplayName,
                    ),
                    onEvent = onEvent,
                    onDisconnected = onDisconnected,
                )
            },
        )
    }

    private fun websocketUrl(advertisement: SessionAdvertisementDto): String {
        val pageProtocol = Window.current().location.protocol
        val websocketProtocol = if (pageProtocol.startsWith("https")) "wss" else "ws"
        return "$websocketProtocol://${advertisement.hostAddress}:${advertisement.serverPort}/session"
    }
}
