package com.hitster.platform.android

import android.content.Context
import com.hitster.networking.ClientCommandDto
import com.hitster.networking.HostEventDto
import com.hitster.networking.SessionAdvertisementDto
import com.hitster.playback.api.PlaybackController
import com.hitster.transport.jvm.DEFAULT_SESSION_SERVER_PORT
import com.hitster.transport.jvm.LanHostDiscoveryAnnouncer
import com.hitster.transport.jvm.LanHostDiscoveryListener
import com.hitster.transport.jvm.LanSessionClient
import com.hitster.transport.jvm.LanSessionServer
import com.hitster.transport.jvm.resolveSiteLocalIpv4Address
import com.hitster.ui.AppPlatformServices
import com.hitster.ui.GuestSessionClient
import com.hitster.ui.HostDiscoveryService
import com.hitster.ui.HostedSessionTransport
import com.hitster.ui.MatchController
import com.hitster.ui.UiBootstrapper

class AndroidPlatformServices(
    private val applicationContext: Context,
) : AppPlatformServices {
    override val supportsHosting: Boolean = true

    override fun createHostedMatchController(
        playbackController: PlaybackController,
        localDisplayName: String,
    ): MatchController {
        return UiBootstrapper.createHostedMatchController(
            playbackController = playbackController,
            hostDisplayName = localDisplayName,
            sessionTransportFactory = { presenter ->
                val serverPort = DEFAULT_SESSION_SERVER_PORT
                val hostAddress = resolveSiteLocalIpv4Address() ?: "127.0.0.1"
                val discoveryAnnouncer = LanHostDiscoveryAnnouncer(
                    advertisementProvider = {
                        SessionAdvertisementDto(
                            sessionId = presenter.state.sessionId.value,
                            hostPlayerId = presenter.state.hostId.value,
                            hostDisplayName = presenter.state.players.firstOrNull { it.id == presenter.state.hostId }?.displayName ?: "Host",
                            hostAddress = hostAddress,
                            serverPort = serverPort,
                            playerCount = presenter.state.players.size,
                        )
                    },
                )
                object : HostedSessionTransport {
                    private val server = LanSessionServer(
                        port = serverPort,
                        commandListener = presenter::handleRemoteCommand,
                        onClientDisconnected = presenter::handleRemoteDisconnect,
                        discoveryAnnouncer = discoveryAnnouncer,
                    )

                    override fun start() {
                        HostingForegroundService.start(applicationContext)
                        server.start()
                    }

                    override fun broadcast(event: HostEventDto) {
                        server.broadcast(event)
                    }

                    override fun close() {
                        server.stop()
                        HostingForegroundService.stop(applicationContext)
                    }
                }
            },
        )
    }

    override fun createGuestDiscoveryService(): HostDiscoveryService {
        return object : HostDiscoveryService {
            private val listener = LanHostDiscoveryListener()

            override fun start(onUpdate: (List<SessionAdvertisementDto>) -> Unit) {
                listener.start(onUpdate)
            }

            override fun stop() {
                listener.stop()
            }
        }
    }

    override fun createRemoteGuestController(
        advertisement: SessionAdvertisementDto,
        displayName: String,
    ): MatchController {
        return UiBootstrapper.createRemoteGuestController(
            advertisement = advertisement,
            displayName = displayName,
            clientFactory = { sessionAdvertisement, actorId, playerDisplayName, onEvent, onDisconnected, onStatusChanged ->
                object : GuestSessionClient {
                    private val client = LanSessionClient(
                        hostAddress = sessionAdvertisement.hostAddress,
                        serverPort = sessionAdvertisement.serverPort,
                        actorId = actorId.value,
                        displayName = playerDisplayName,
                        clientEventListener = onEvent,
                        onDisconnected = onDisconnected,
                    )

                    override fun connect() {
                        onStatusChanged("Opening guest connection...")
                        client.connect()
                    }

                    override fun sendCommand(command: ClientCommandDto) {
                        client.sendCommand(command)
                    }

                    override fun close() {
                        client.close()
                    }
                }
            },
        )
    }
}
