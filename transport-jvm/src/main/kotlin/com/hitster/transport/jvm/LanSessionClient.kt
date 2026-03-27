package com.hitster.transport.jvm

/**
 * JVM guest websocket client used by Android guests and internal browser proxies to talk to the host session.
 */

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private const val lanGuestReconnectDelayMillis = 750L
private const val lanGuestMaximumInitialConnectAttempts = 3

class LanSessionClient(
    private val hostAddress: String,
    private val serverPort: Int,
    private val actorId: String,
    private val displayName: String,
    private val clientEventListener: ClientEventListener,
    private val onDisconnected: (String) -> Unit = {},
    private val onStatusChanged: (String) -> Unit = {},
) {
    private val client = HttpClient(CIO) {
        install(WebSockets)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionJob: Job? = null
    @Volatile
    private var socketSession: DefaultClientWebSocketSession? = null
    @Volatile
    private var closedByClient = false
    @Volatile
    private var hasReceivedHostEvent = false

    fun connect() {
        if (sessionJob != null) {
            return
        }

        closedByClient = false
        hasReceivedHostEvent = false

        sessionJob = scope.launch {
            var attempt = 0
            while (isActive && !closedByClient) {
                attempt += 1
                onStatusChanged(
                    when {
                        hasReceivedHostEvent -> "Reconnecting to host..."
                        attempt == 1 -> "Opening guest connection..."
                        else -> "Retrying guest connection..."
                    },
                )
                val failureReason = runConnectionAttempt()
                socketSession = null
                if (closedByClient) {
                    break
                }
                if (failureReason == null) {
                    continue
                }
                if (!hasReceivedHostEvent && attempt >= lanGuestMaximumInitialConnectAttempts) {
                    onDisconnected(failureReason)
                    break
                }
                delay(lanGuestReconnectDelayMillis)
            }
            sessionJob = null
        }
    }

    fun sendCommand(command: ClientCommandDto) {
        scope.launch {
            socketSession?.sendCommand(command)
        }
    }

    fun close() {
        closedByClient = true
        socketSession = null
        sessionJob?.cancel()
        sessionJob = null
        client.close()
        scope.cancel()
    }

    private suspend fun runConnectionAttempt(): String? {
        return runCatching {
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
                hasReceivedHostEvent = true
                onStatusChanged(
                    when (event) {
                        is HostEventDto.SnapshotPublished -> "Host snapshot received."
                        is HostEventDto.CommandRejected -> "Host rejected guest command."
                        is HostEventDto.PlaybackStateChanged -> "Playback state updated."
                    },
                )
                clientEventListener.onEvent(event)
            }
            "Connection to the host closed."
        }.getOrElse { throwable ->
            throwable.message ?: "Connection to the host closed."
        }
    }

    private suspend fun DefaultClientWebSocketSession.sendCommand(command: ClientCommandDto) {
        send(Frame.Text(protocolJson.encodeToString(command)))
    }
}
