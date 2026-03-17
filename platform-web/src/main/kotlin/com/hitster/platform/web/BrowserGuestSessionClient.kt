package com.hitster.platform.web

import com.hitster.networking.ClientCommandDto
import com.hitster.networking.HostEventDto
import com.hitster.networking.protocolJson
import com.hitster.ui.GuestSessionClient
import org.teavm.jso.browser.TimerHandler
import org.teavm.jso.browser.Window
import org.teavm.jso.dom.events.Event
import org.teavm.jso.dom.events.EventListener
import org.teavm.jso.dom.events.MessageEvent
import org.teavm.jso.websocket.CloseEvent
import org.teavm.jso.websocket.WebSocket
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private const val webSocketOpenReadyState = 1
private const val initialReconnectDelayMillis = 350
private const val maximumInitialConnectAttempts = 3

class BrowserGuestSessionClient(
    private val websocketUrl: String,
    private val joinCommand: ClientCommandDto.JoinSession,
    private val onEvent: (HostEventDto) -> Unit,
    private val onDisconnected: (String) -> Unit,
) : GuestSessionClient {
    private var socket: WebSocket? = null
    private val queuedPayloads = mutableListOf<String>()
    private var disconnected = false
    private var closedByClient = false
    private var hasReceivedHostEvent = false
    private var connectAttempts = 0
    private var reconnectScheduled = false

    override fun connect() {
        if (socket != null || reconnectScheduled) {
            return
        }
        disconnected = false
        closedByClient = false
        openSocket()
    }

    override fun sendCommand(command: ClientCommandDto) {
        val payload = protocolJson.encodeToString(command)
        val activeSocket = socket
        if (activeSocket == null || activeSocket.readyState != webSocketOpenReadyState) {
            queuedPayloads += payload
            return
        }
        activeSocket.send(payload)
    }

    override fun close() {
        closedByClient = true
        reconnectScheduled = false
        socket?.close()
        socket = null
        queuedPayloads.clear()
        disconnected = true
    }

    private fun openSocket() {
        connectAttempts += 1
        val createdSocket = WebSocket.create(websocketUrl)
        socket = createdSocket
        createdSocket.onOpen(
            object : EventListener<Event> {
                override fun handleEvent(evt: Event) {
                    if (socket !== createdSocket || closedByClient) {
                        return
                    }
                    disconnected = false
                    createdSocket.send(protocolJson.encodeToString(joinCommand))
                    flushQueuedPayloads(createdSocket)
                }
            },
        )
        createdSocket.onMessage(
            object : EventListener<MessageEvent> {
                override fun handleEvent(evt: MessageEvent) {
                    if (socket !== createdSocket || closedByClient) {
                        return
                    }
                    val text = evt.dataAsString
                    runCatching {
                        protocolJson.decodeFromString<HostEventDto>(text)
                    }.getOrNull()?.let { event ->
                        hasReceivedHostEvent = true
                        onEvent(event)
                    }
                }
            },
        )
        createdSocket.onError(
            object : EventListener<Event> {
                override fun handleEvent(evt: Event) {
                    handleSocketDisconnect(createdSocket, "Web guest connection failed.")
                }
            },
        )
        createdSocket.onClose(
            object : EventListener<CloseEvent> {
                override fun handleEvent(evt: CloseEvent) {
                    handleSocketDisconnect(createdSocket, "Connection to the host closed.")
                }
            },
        )
    }

    private fun flushQueuedPayloads(activeSocket: WebSocket) {
        if (activeSocket.readyState != webSocketOpenReadyState) {
            return
        }
        queuedPayloads.forEach(activeSocket::send)
        queuedPayloads.clear()
    }

    private fun handleSocketDisconnect(
        closedSocket: WebSocket,
        message: String,
    ) {
        if (socket !== closedSocket) {
            return
        }
        socket = null
        if (closedByClient) {
            return
        }
        if (!hasReceivedHostEvent && connectAttempts < maximumInitialConnectAttempts) {
            scheduleReconnect()
            return
        }
        disconnect(message)
    }

    private fun scheduleReconnect() {
        if (reconnectScheduled || closedByClient) {
            return
        }
        reconnectScheduled = true
        Window.setTimeout(
            object : TimerHandler {
                override fun onTimer() {
                    reconnectScheduled = false
                    if (closedByClient || socket != null) {
                        return
                    }
                    openSocket()
                }
            },
            initialReconnectDelayMillis,
        )
    }

    private fun disconnect(message: String) {
        if (disconnected) {
            return
        }
        disconnected = true
        queuedPayloads.clear()
        onDisconnected(message)
    }
}
