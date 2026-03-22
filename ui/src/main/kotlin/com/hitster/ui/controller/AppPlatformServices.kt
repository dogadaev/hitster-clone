package com.hitster.ui.controller

/**
 * Platform abstraction layer that the shared libGDX application uses to create host or guest controllers and platform helpers.
 */

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Texture
import com.hitster.networking.ClientCommandDto
import com.hitster.networking.HostEventDto
import com.hitster.networking.SessionAdvertisementDto
import com.hitster.playback.api.PlaybackController

interface AppPlatformServices {
    val supportsHosting: Boolean

    /** Creates the authoritative host-side controller plus any platform transport details needed for lobby hosting. */
    fun createHostedMatchController(
        playbackController: PlaybackController,
        localDisplayName: String,
    ): MatchController

    /** Starts the platform-specific discovery flow that surfaces currently reachable local hosts. */
    fun createGuestDiscoveryService(): HostDiscoveryService

    /** Builds the guest controller that will connect to an already discovered host session. */
    fun createRemoteGuestController(
        advertisement: SessionAdvertisementDto,
        displayName: String,
    ): MatchController

    /** Requests a player name using the platform's preferred input mechanism before entering the lobby flow. */
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
    /** Begins pushing host-list updates until [stop] is called. */
    fun start(onUpdate: (List<SessionAdvertisementDto>) -> Unit = {})

    /** Releases sockets, timers, and callbacks used by host discovery. */
    fun stop()
}

interface GuestSessionClient {
    /** Opens the guest transport and performs the initial join handshake. */
    fun connect()

    /** Sends one client command to the authoritative host. */
    fun sendCommand(command: ClientCommandDto)

    /** Closes the guest session and frees any platform resources behind it. */
    fun close()
}

interface HostedSessionTransport {
    val guestJoinUrl: String?
        get() = null
    val guestJoinQrTexture: Texture?
        get() = null

    /** Starts the host-side transport and begins accepting guest connections. */
    fun start()

    /** Publishes the latest authoritative host event to all connected guests. */
    fun broadcast(event: HostEventDto)

    /** Stops hosting and disposes transport-specific resources such as QR textures or web servers. */
    fun close()
}
