package com.hitster.platform.web

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.HttpMethod
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class WebGuestSessionProxy(
    private val hostAddress: String,
    private val serverPort: Int,
) {
    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    suspend fun bridge(browserSession: DefaultWebSocketServerSession) {
        val hostSession = client.webSocketSession(
            method = HttpMethod.Get,
            host = hostAddress,
            port = serverPort,
            path = "/session",
        )
        try {
            coroutineScope {
                val browserToHost = launch {
                    try {
                        for (frame in browserSession.incoming) {
                            when (frame) {
                                is Frame.Text -> hostSession.send(Frame.Text(frame.readText()))
                                is Frame.Close -> {
                                    hostSession.close(frame.readReason() ?: normalClosure())
                                    break
                                }

                                else -> Unit
                            }
                        }
                    } finally {
                        hostSession.close(normalClosure())
                    }
                }
                val hostToBrowser = launch {
                    try {
                        for (frame in hostSession.incoming) {
                            when (frame) {
                                is Frame.Text -> browserSession.send(Frame.Text(frame.readText()))
                                is Frame.Close -> {
                                    browserSession.close(frame.readReason() ?: normalClosure())
                                    break
                                }

                                else -> Unit
                            }
                        }
                    } finally {
                        browserSession.close(normalClosure())
                    }
                }

                browserToHost.join()
                hostToBrowser.cancelAndJoin()
            }
        } finally {
            hostSession.close(normalClosure())
            client.close()
        }
    }

    private fun normalClosure(): CloseReason = CloseReason(CloseReason.Codes.NORMAL, "Proxy closed")
}
