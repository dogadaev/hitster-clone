package com.hitster.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
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

    fun requestDisplayNameInput(
        currentName: String,
        onSubmitted: (String?) -> Unit,
    ) {
        Gdx.input.getTextInput(
            object : Input.TextInputListener {
                override fun input(text: String?) {
                    onSubmitted(text)
                }

                override fun canceled() {
                    onSubmitted(null)
                }
            },
            "Your name",
            currentName,
            "",
        )
    }
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
