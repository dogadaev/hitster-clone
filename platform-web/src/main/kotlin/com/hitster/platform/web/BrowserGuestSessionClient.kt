package com.hitster.platform.web

/**
 * Browser-side guest transport that talks to the local HTTP proxy, handles reconnects, and streams host snapshots into the shared UI.
 */

import com.hitster.networking.ClientCommandDto
import com.hitster.networking.HostEventDto
import com.hitster.networking.SessionAdvertisementDto
import com.hitster.networking.decodeHostEventPayload
import com.hitster.networking.encodeClientCommandPayload
import com.hitster.networking.protocolJson
import com.hitster.transport.jvm.browser.BrowserGuestSessionCommandRequest
import com.hitster.transport.jvm.browser.BrowserGuestSessionPollResponse
import com.hitster.transport.jvm.browser.BrowserGuestSessionStartRequest
import com.hitster.transport.jvm.browser.BrowserGuestSessionStartResponse
import com.hitster.ui.GuestSessionClient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.teavm.jso.ajax.XMLHttpRequest
import org.teavm.jso.browser.TimerHandler
import org.teavm.jso.browser.Window
import org.teavm.jso.dom.events.Event
import org.teavm.jso.dom.events.EventListener

private const val initialReconnectDelayMillis = 400
private const val maximumInitialConnectAttempts = 3
private const val hostEventTimeoutMillis = 4_000
private const val guestPollIntervalMillis = 120

class BrowserGuestSessionClient(
    private val startEndpoint: String,
    private val joinCommand: ClientCommandDto.JoinSession,
    private val advertisement: SessionAdvertisementDto,
    private val onEvent: (HostEventDto) -> Unit,
    private val onDisconnected: (String) -> Unit,
    private val onStatusChanged: (String) -> Unit,
) : GuestSessionClient {
    private val commandBuffer = BrowserGuestCommandBuffer()
    private var disconnected = false
    private var hasReceivedHostEvent = false
    private var connectAttempts = 0
    private var reconnectScheduled = false
    private var closedByClient = false
    private var pollScheduled = false
    private var hostEventTimeoutGeneration = 0
    private var sessionId: String? = null
    private var nextSequence = 0L
    private var commandRequestInFlight = false

    override fun connect() {
        if (sessionId != null || reconnectScheduled) {
            return
        }
        ensureGuestCloseHookInstalled()
        disconnected = false
        closedByClient = false
        hasReceivedHostEvent = false
        connectAttempts = 0
        nextSequence = 0L
        onStatusChanged("Creating guest session...")
        openSession()
    }

    override fun sendCommand(command: ClientCommandDto) {
        val payload = encodeClientCommandPayload(command)
        commandBuffer.enqueue(browserGuestCommandKind(command), payload)
        flushBufferedCommands()
    }

    override fun close() {
        closedByClient = true
        reconnectScheduled = false
        pollScheduled = false
        commandRequestInFlight = false
        commandBuffer.clear()
        hostEventTimeoutGeneration += 1
        clearGuestCloseUrl()
        sessionId?.let { activeSessionId ->
            postJson(
                path = "/api/guest-sessions/$activeSessionId/close",
                body = "",
                onSuccess = {},
                onFailure = {},
            )
        }
        sessionId = null
        disconnected = true
        onStatusChanged("Guest connection closed.")
    }

    private fun openSession() {
        connectAttempts += 1
        onStatusChanged("Connecting attempt $connectAttempts...")
        val request = BrowserGuestSessionStartRequest(
            hostAddress = advertisement.hostAddress,
            serverPort = advertisement.serverPort,
            actorId = joinCommand.actorId,
            displayName = joinCommand.displayName,
        )
        postJson(
            path = startEndpoint,
            body = protocolJson.encodeToString(request),
            onSuccess = { body ->
                val response = runCatching {
                    protocolJson.decodeFromString<BrowserGuestSessionStartResponse>(body)
                }.getOrNull()
                if (response == null || closedByClient) {
                    handleConnectFailure("Failed to open guest session.")
                    return@postJson
                }
                disconnected = false
                sessionId = response.sessionId
                registerGuestCloseUrl("/api/guest-sessions/${response.sessionId}/close")
                onStatusChanged("Guest session created. Waiting for host snapshot...")
                commandRequestInFlight = false
                flushBufferedCommands()
                scheduleHostEventTimeout()
                schedulePoll()
            },
            onFailure = {
                handleConnectFailure("Failed to open guest session.")
            },
        )
    }

    private fun flushBufferedCommands() {
        val activeSessionId = sessionId ?: return
        if (closedByClient || commandRequestInFlight) {
            return
        }
        val command = commandBuffer.poll() ?: return
        commandRequestInFlight = true
        postJson(
            path = "/api/guest-sessions/$activeSessionId/command",
            body = protocolJson.encodeToString(BrowserGuestSessionCommandRequest(command.payload)),
            onSuccess = {
                commandRequestInFlight = false
                flushBufferedCommands()
            },
            onFailure = {
                commandRequestInFlight = false
                if (closedByClient) {
                    return@postJson
                }
                commandBuffer.prepend(command)
                onStatusChanged("Failed to forward guest command.")
                scheduleReconnect("Reconnecting to the host...")
            },
        )
    }

    private fun handleConnectFailure(message: String) {
        sessionId = null
        if (closedByClient) {
            return
        }
        if (!hasReceivedHostEvent && connectAttempts < maximumInitialConnectAttempts) {
            scheduleReconnect()
            return
        }
        if (hasReceivedHostEvent) {
            scheduleReconnect(message)
            return
        }
        disconnect(message)
    }

    private fun scheduleReconnect(reason: String? = null) {
        if (reconnectScheduled || closedByClient) {
            return
        }
        sessionId = null
        nextSequence = 0L
        pollScheduled = false
        commandRequestInFlight = false
        hostEventTimeoutGeneration += 1
        clearGuestCloseUrl()
        reconnectScheduled = true
        onStatusChanged(reason ?: "Retrying guest connection...")
        Window.setTimeout(
            object : TimerHandler {
                override fun onTimer() {
                    reconnectScheduled = false
                    if (closedByClient || sessionId != null) {
                        return
                    }
                    openSession()
                }
            },
            initialReconnectDelayMillis,
        )
    }

    private fun scheduleHostEventTimeout() {
        val timeoutGeneration = ++hostEventTimeoutGeneration
        Window.setTimeout(
            object : TimerHandler {
                override fun onTimer() {
                    if (timeoutGeneration != hostEventTimeoutGeneration) {
                        return
                    }
                    if (closedByClient || sessionId == null || hasReceivedHostEvent) {
                        return
                    }
                    onStatusChanged("Waiting for host snapshot timed out.")
                    disconnect("The host did not confirm the join request.")
                }
            },
            hostEventTimeoutMillis,
        )
    }

    private fun schedulePoll() {
        if (pollScheduled || closedByClient || sessionId == null) {
            return
        }
        pollScheduled = true
        Window.setTimeout(
            object : TimerHandler {
                override fun onTimer() {
                    pollScheduled = false
                    pollSession()
                }
            },
            guestPollIntervalMillis,
        )
    }

    private fun pollSession() {
        val activeSessionId = sessionId
        if (closedByClient || activeSessionId == null) {
            return
        }
        getJson(
            path = "/api/guest-sessions/$activeSessionId/poll?afterSequence=$nextSequence",
            onSuccess = { body ->
                val response = runCatching {
                    protocolJson.decodeFromString<BrowserGuestSessionPollResponse>(body)
                }.getOrNull()
                if (response == null) {
                    disconnect("Received an invalid host session response.")
                    return@getJson
                }
                onStatusChanged(response.status)
                response.events.forEach { payload ->
                    val event = decodeHostEventPayload(payload)
                    if (event == null) {
                        onStatusChanged("Received host message, but Safari could not decode it.")
                        return@forEach
                    }
                    hasReceivedHostEvent = true
                    when (event) {
                        is HostEventDto.SnapshotPublished ->
                            onStatusChanged("Host snapshot received.")

                        is HostEventDto.CommandRejected ->
                            onStatusChanged("Host rejected guest command: ${event.reason}")
                    }
                    onEvent(event)
                }
                nextSequence = response.nextSequence
                val terminalError = response.terminalError
                if (terminalError != null) {
                    if (hasReceivedHostEvent) {
                        scheduleReconnect("Connection to the host was interrupted. Reconnecting...")
                    } else {
                        disconnect(terminalError)
                    }
                    return@getJson
                }
                schedulePoll()
            },
            onFailure = { statusCode ->
                if (closedByClient) {
                    return@getJson
                }
                if (statusCode == 404 && hasReceivedHostEvent) {
                    scheduleReconnect("Reconnecting to the host...")
                    return@getJson
                }
                onStatusChanged("Retrying host poll...")
                schedulePoll()
            },
        )
    }

    private fun getJson(
        path: String,
        onSuccess: (String) -> Unit,
        onFailure: (Int) -> Unit,
    ) {
        val request = XMLHttpRequest.create()
        request.open("GET", path, true)
        request.setRequestHeader("Accept", "application/json")
        request.setOnReadyStateChange(
            object : EventListener<Event> {
                override fun handleEvent(evt: Event) {
                    if (request.readyState != XMLHttpRequest.DONE) {
                        return
                    }
                    if (request.status in 200..299) {
                        onSuccess(request.responseText ?: "")
                    } else {
                        onFailure(request.status.toInt())
                    }
                }
            },
        )
        request.send()
    }

    private fun postJson(
        path: String,
        body: String,
        onSuccess: (String) -> Unit,
        onFailure: () -> Unit,
    ) {
        val request = XMLHttpRequest.create()
        request.open("POST", path, true)
        request.setRequestHeader("Content-Type", "application/json")
        request.setRequestHeader("Accept", "application/json")
        request.setOnReadyStateChange(
            object : EventListener<Event> {
                override fun handleEvent(evt: Event) {
                    if (request.readyState != XMLHttpRequest.DONE) {
                        return
                    }
                    if (request.status in 200..299) {
                        onSuccess(request.responseText ?: "")
                    } else {
                        onFailure()
                    }
                }
            },
        )
        request.send(body)
    }

    private fun disconnect(message: String) {
        if (disconnected) {
            return
        }
        hostEventTimeoutGeneration += 1
        pollScheduled = false
        val activeSessionId = sessionId
        sessionId = null
        clearGuestCloseUrl()
        commandRequestInFlight = false
        commandBuffer.clear()
        disconnected = true
        onStatusChanged(message)
        if (activeSessionId != null) {
            postJson(
                path = "/api/guest-sessions/$activeSessionId/close",
                body = "",
                onSuccess = {},
                onFailure = {},
            )
        }
        onDisconnected(message)
    }
}
