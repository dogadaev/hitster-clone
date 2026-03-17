package com.hitster.platform.web

import com.hitster.networking.encodeClientCommandPayload
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.HttpMethod
import io.ktor.websocket.close
import io.ktor.websocket.Frame
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val browserGuestSessionTtlMillis = 5_000L
private const val browserGuestSessionCleanupIntervalMillis = 1_000L

class BrowserGuestSessionRegistry {
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.IO)
    private val sessions = ConcurrentHashMap<String, HostedBrowserGuestSession>()
    private val cleanupJob = scope.launch {
        while (isActive) {
            delay(browserGuestSessionCleanupIntervalMillis)
            cleanupExpiredSessions()
        }
    }

    suspend fun startSession(request: BrowserGuestSessionStartRequest): BrowserGuestSessionStartResponse {
        cleanupExpiredSessions()
        val sessionId = "browser-${UUID.randomUUID()}"
        val session = HostedBrowserGuestSession(
            hostAddress = request.hostAddress,
            serverPort = request.serverPort,
            joinPayload = encodeClientCommandPayload(
                com.hitster.networking.ClientCommandDto.JoinSession(
                    actorId = request.actorId,
                    displayName = request.displayName,
                ),
            ),
            scope = scope,
        )
        sessions[sessionId] = session
        return BrowserGuestSessionStartResponse(sessionId = sessionId)
    }

    suspend fun pollSession(sessionId: String, afterSequence: Long): BrowserGuestSessionPollResponse? {
        cleanupExpiredSessions()
        return sessions[sessionId]?.poll(afterSequence)
    }

    suspend fun sendCommand(sessionId: String, payload: String): Boolean? {
        cleanupExpiredSessions()
        return sessions[sessionId]?.send(payload)
    }

    suspend fun closeSession(sessionId: String) {
        sessions.remove(sessionId)?.close("Browser guest session closed.")
    }

    suspend fun shutdown() {
        sessions.values.forEach { it.close("Web guest server shutting down.") }
        sessions.clear()
        cleanupJob.cancelAndJoin()
        scopeJob.cancel()
    }

    private suspend fun cleanupExpiredSessions() {
        val now = System.currentTimeMillis()
        val expiredIds = sessions.entries
            .filter { (_, session) -> session.isExpired(now) }
            .map { it.key }
        expiredIds.forEach { sessionId ->
            println("Web guest HTTP session expired: session=$sessionId")
            sessions.remove(sessionId)?.close("Browser guest session timed out.")
        }
    }
}

private class HostedBrowserGuestSession(
    private val hostAddress: String,
    private val serverPort: Int,
    private val joinPayload: String,
    scope: CoroutineScope,
) {
    private val client = HttpClient(CIO) {
        install(WebSockets)
    }
    private val mutex = Mutex()
    private val events = mutableListOf<QueuedHostEvent>()
    private var nextSequence = 1L
    private var terminalError: String? = null
    private var upstreamSession: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession? = null
    private var closedByServer = false
    private var lastAccessAtMillis = System.currentTimeMillis()
    private val upstreamJob = scope.launch {
        openUpstreamSession()
    }

    suspend fun poll(afterSequence: Long): BrowserGuestSessionPollResponse {
        touch()
        return mutex.withLock {
            val pendingEvents = events
                .asSequence()
                .filter { it.sequence > afterSequence }
                .map { it.payload }
                .toList()
            BrowserGuestSessionPollResponse(
                status = currentStatusLocked(),
                events = pendingEvents,
                nextSequence = nextSequence - 1,
                terminalError = terminalError,
            )
        }
    }

    suspend fun send(payload: String): Boolean {
        touch()
        val session = mutex.withLock { upstreamSession }
        if (session == null) {
            return false
        }
        return runCatching {
            session.send(Frame.Text(payload))
            true
        }.getOrElse {
            setTerminalError("Failed to forward guest command to the host.")
            false
        }
    }

    suspend fun close(reason: String) {
        val session = mutex.withLock {
            if (closedByServer) {
                return@withLock null
            }
            closedByServer = true
            if (terminalError == null) {
                terminalError = reason
            }
            upstreamSession.also { upstreamSession = null }
        }
        println("Web guest HTTP session closing: host=$hostAddress:$serverPort reason=$reason")
        session?.close()
        upstreamJob.cancelAndJoin()
        client.close()
    }

    fun isExpired(nowMillis: Long): Boolean = nowMillis - lastAccessAtMillis > browserGuestSessionTtlMillis

    private suspend fun openUpstreamSession() {
        try {
            val session = client.webSocketSession(
                method = HttpMethod.Get,
                host = hostAddress,
                port = serverPort,
                path = "/session",
            )
            mutex.withLock {
                upstreamSession = session
            }
            session.send(Frame.Text(joinPayload))
            for (frame in session.incoming) {
                when (frame) {
                    is Frame.Text -> enqueueEvent(frame.readText())
                    is Frame.Close -> {
                        if (!closedByServer) {
                            val reason = frame.readReason()?.message?.takeIf { it.isNotBlank() }
                            setTerminalError(reason ?: "Connection to the host closed.")
                        }
                        break
                    }

                    else -> Unit
                }
            }
        } catch (_: Exception) {
            if (!closedByServer) {
                setTerminalError("Failed to connect to the host.")
            }
        } finally {
            mutex.withLock {
                upstreamSession = null
                if (!closedByServer && terminalError == null) {
                    terminalError = "Connection to the host closed."
                }
            }
        }
    }

    private suspend fun enqueueEvent(payload: String) {
        mutex.withLock {
            events += QueuedHostEvent(
                sequence = nextSequence,
                payload = payload,
            )
            nextSequence += 1
            if (events.size > 64) {
                events.removeFirst()
            }
        }
    }

    private suspend fun setTerminalError(message: String) {
        mutex.withLock {
            if (terminalError == null) {
                terminalError = message
            }
        }
    }

    private fun touch() {
        lastAccessAtMillis = System.currentTimeMillis()
    }

    private fun currentStatusLocked(): String {
        return when {
            terminalError != null -> terminalError.orEmpty()
            events.isEmpty() -> "Waiting for host snapshot..."
            else -> "Host snapshot received."
        }
    }
}

private data class QueuedHostEvent(
    val sequence: Long,
    val payload: String,
)
