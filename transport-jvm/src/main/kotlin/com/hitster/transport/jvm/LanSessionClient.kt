package com.hitster.transport.jvm

import com.hitster.networking.ClientCommandDto
import com.hitster.networking.ClientEventListener
import com.hitster.networking.HostEventDto
import com.hitster.networking.protocolJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class LanSessionClient(
    private val hostAddress: String,
    private val serverPort: Int,
    private val actorId: String,
    private val displayName: String,
    private val clientEventListener: ClientEventListener,
    private val onDisconnected: (String) -> Unit = {},
) {
    private val client = HttpClient(CIO) {
        install(WebSockets)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionJob: Job? = null
    @Volatile
    private var socketSession: DefaultClientWebSocketSession? = null

    fun connect() {
        if (sessionJob != null) {
            return
        }

        sessionJob = scope.launch {
            runCatching {
                val session = client.webSocketSession(
                    method = HttpMethod.Get,
                    host = hostAddress,
                    port = serverPort,
                    path = "/session",
                )
                socketSession = session
                session.sendCommand(
                    ClientCommandDto.JoinSession(
                        actorId = actorId,
                        displayName = displayName,
                    ),
                )

                for (frame in session.incoming) {
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    val event = runCatching {
                        protocolJson.decodeFromString<HostEventDto>(text)
                    }.getOrNull() ?: continue
                    clientEventListener.onEvent(event)
                }
            }.onFailure {
                onDisconnected(it.message ?: "Connection to the host closed.")
            }.also {
                socketSession = null
            }
        }
    }

    fun sendCommand(command: ClientCommandDto) {
        scope.launch {
            socketSession?.sendCommand(command)
        }
    }

    fun close() {
        socketSession = null
        sessionJob?.cancel()
        sessionJob = null
        client.close()
        scope.cancel()
    }

    private suspend fun DefaultClientWebSocketSession.sendCommand(command: ClientCommandDto) {
        send(Frame.Text(protocolJson.encodeToString(command)))
    }
}
