package com.hitster.transport.jvm

import com.hitster.networking.ClientCommandDto
import com.hitster.networking.HostCommandListener
import com.hitster.networking.HostEventDto
import com.hitster.networking.protocolJson
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.concurrent.ConcurrentHashMap

class LanSessionServer(
    private val port: Int = DEFAULT_SESSION_SERVER_PORT,
    private val commandListener: HostCommandListener,
    private val discoveryAnnouncer: LanHostDiscoveryAnnouncer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = ConcurrentHashMap.newKeySet<DefaultWebSocketServerSession>()
    private var engine: ApplicationEngine? = null

    fun start() {
        if (engine != null) {
            return
        }

        engine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
            install(WebSockets)
            routing {
                get("/health") {
                    call.respondText("ok", status = HttpStatusCode.OK)
                }

                webSocket("/session") {
                    sessions.add(this)
                    try {
                        for (frame in incoming) {
                            val text = (frame as? Frame.Text)?.readText() ?: continue
                            val command = runCatching {
                                protocolJson.decodeFromString<ClientCommandDto>(text)
                            }.getOrNull() ?: continue
                            commandListener.onCommand(command)
                        }
                    } finally {
                        sessions.remove(this)
                    }
                }
            }
        }.start(wait = false)

        discoveryAnnouncer.start()
    }

    fun broadcast(event: HostEventDto) {
        val payload = protocolJson.encodeToString(event)
        sessions.forEach { session ->
            scope.launch {
                runCatching {
                    session.send(Frame.Text(payload))
                }.onFailure {
                    sessions.remove(session)
                }
            }
        }
    }

    fun stop() {
        discoveryAnnouncer.stop()
        engine?.stop(250, 1_000)
        engine = null
        sessions.clear()
        scope.cancel()
    }
}
