package com.hitster.platform.web

import com.hitster.networking.ClientCommandDto
import com.hitster.networking.HostEventDto
import com.hitster.networking.protocolJson
import com.hitster.ui.GuestSessionClient
import org.teavm.jso.dom.events.Event
import org.teavm.jso.dom.events.EventListener
import org.teavm.jso.dom.events.MessageEvent
import org.teavm.jso.websocket.CloseEvent
import org.teavm.jso.websocket.WebSocket
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private const val webSocketOpenReadyState = 1

class BrowserGuestSessionClient(
    private val websocketUrl: String,
    private val joinCommand: ClientCommandDto.JoinSession,
    private val onEvent: (HostEventDto) -> Unit,
    private val onDisconnected: (String) -> Unit,
) : GuestSessionClient {
    private var socket: WebSocket? = null
    private val queuedPayloads = mutableListOf<String>()
    private var disconnected = false

    override fun connect() {
        if (socket != null) {
            return
        }

        val createdSocket = WebSocket.create(websocketUrl)
        socket = createdSocket
        createdSocket.onOpen(
            object : EventListener<Event> {
                override fun handleEvent(evt: Event) {
                    disconnected = false
                    sendCommand(joinCommand)
                    flushQueuedPayloads()
                }
            },
        )
        createdSocket.onMessage(
            object : EventListener<MessageEvent> {
                override fun handleEvent(evt: MessageEvent) {
                    val text = evt.dataAsString
                    runCatching {
                        protocolJson.decodeFromString<HostEventDto>(text)
                    }.getOrNull()?.let { event ->
                        onEvent(event)
                    }
                }
            },
        )
        createdSocket.onError(
            object : EventListener<Event> {
                override fun handleEvent(evt: Event) {
                    disconnect("Web guest connection failed.")
                }
            },
        )
        createdSocket.onClose(
            object : EventListener<CloseEvent> {
                override fun handleEvent(evt: CloseEvent) {
                    disconnect("Connection to the host closed.")
                }
            },
        )
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
        socket?.close()
        socket = null
        queuedPayloads.clear()
        disconnected = true
    }

    private fun flushQueuedPayloads() {
        val activeSocket = socket ?: return
        if (activeSocket.readyState != webSocketOpenReadyState) {
            return
        }
        queuedPayloads.forEach(activeSocket::send)
        queuedPayloads.clear()
    }

    private fun disconnect(message: String) {
        if (disconnected) {
            return
        }
        disconnected = true
        socket = null
        queuedPayloads.clear()
        onDisconnected(message)
    }
}
