package com.hitster.ui

import com.hitster.networking.ClientCommandDto
import com.hitster.networking.HostEventDto
import com.hitster.networking.SessionAdvertisementDto
import com.hitster.playback.api.PlaybackController

interface AppPlatformServices {
    val supportsHosting: Boolean

    fun createHostedMatchController(
        playbackController: PlaybackController,
        localDisplayName: String,
    ): MatchController

    fun createGuestDiscoveryService(): HostDiscoveryService

    fun createRemoteGuestController(
        advertisement: SessionAdvertisementDto,
        displayName: String,
    ): MatchController
}

interface HostDiscoveryService {
    fun start(onUpdate: (List<SessionAdvertisementDto>) -> Unit = {})

    fun stop()
}

interface GuestSessionClient {
    fun connect()

    fun sendCommand(command: ClientCommandDto)

    fun close()
}

interface HostedSessionTransport {
    fun start()

    fun broadcast(event: HostEventDto)

    fun close()
}
