package com.hitster.transport.jvm.browser

import com.hitster.networking.SessionAdvertisementDto
import com.hitster.networking.protocolJson
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

fun Route.installBrowserGuestSessionApi(
    browserGuestSessions: BrowserGuestSessionRegistry,
    hostSnapshotProvider: () -> List<SessionAdvertisementDto>,
) {
    head("/api/hosts") {
        call.respondNoStoreEmpty(
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.OK,
        )
    }
    get("/api/hosts") {
        call.respondNoStoreText(
            text = protocolJson.encodeToString(hostSnapshotProvider()),
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.OK,
        )
    }
    post("/api/guest-sessions/start") {
        val request = runCatching {
            protocolJson.decodeFromString<BrowserGuestSessionStartRequest>(call.receiveText())
        }.getOrNull()
        if (request == null) {
            call.respondText("Bad Request", status = HttpStatusCode.BadRequest)
            return@post
        }
        val response = browserGuestSessions.startSession(request)
        println(
            "Web guest HTTP session started: session=${response.sessionId} host=${request.hostAddress}:${request.serverPort} ua=${call.request.headers[HttpHeaders.UserAgent].orEmpty()}"
        )
        call.respondNoStoreText(
            text = protocolJson.encodeToString(response),
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.OK,
        )
    }
    head("/api/guest-sessions/{sessionId}/poll") {
        val sessionId = call.parameters["sessionId"]
        if (sessionId.isNullOrBlank()) {
            call.respondText("Not Found", status = HttpStatusCode.NotFound)
            return@head
        }
        call.respondNoStoreEmpty(
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.OK,
        )
    }
    get("/api/guest-sessions/{sessionId}/poll") {
        val sessionId = call.parameters["sessionId"]
        val afterSequence = call.request.queryParameters["afterSequence"]?.toLongOrNull() ?: 0L
        if (sessionId.isNullOrBlank()) {
            call.respondText("Not Found", status = HttpStatusCode.NotFound)
            return@get
        }
        val response = browserGuestSessions.pollSession(sessionId, afterSequence)
        if (response == null) {
            call.respondText("Not Found", status = HttpStatusCode.NotFound)
            return@get
        }
        call.respondNoStoreText(
            text = protocolJson.encodeToString(response),
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.OK,
        )
    }
    post("/api/guest-sessions/{sessionId}/command") {
        val sessionId = call.parameters["sessionId"]
        val request = runCatching {
            protocolJson.decodeFromString<BrowserGuestSessionCommandRequest>(call.receiveText())
        }.getOrNull()
        if (sessionId.isNullOrBlank() || request == null) {
            call.respondText("Bad Request", status = HttpStatusCode.BadRequest)
            return@post
        }
        val forwarded = browserGuestSessions.sendCommand(sessionId, request.payload)
        when (forwarded) {
            true -> call.respondText("OK", status = HttpStatusCode.OK)
            false -> call.respondText("Conflict", status = HttpStatusCode.Conflict)
            null -> call.respondText("Not Found", status = HttpStatusCode.NotFound)
        }
    }
    post("/api/guest-sessions/{sessionId}/close") {
        val sessionId = call.parameters["sessionId"]
        if (sessionId.isNullOrBlank()) {
            call.respondText("Not Found", status = HttpStatusCode.NotFound)
            return@post
        }
        browserGuestSessions.closeSession(sessionId)
        call.respondText("OK", status = HttpStatusCode.OK)
    }
}

private suspend fun ApplicationCall.respondNoStoreText(
    text: String,
    contentType: ContentType,
    status: HttpStatusCode,
) {
    response.headers.append(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate, max-age=0")
    response.headers.append(HttpHeaders.Pragma, "no-cache")
    response.headers.append(HttpHeaders.Expires, "0")
    respondText(text = text, contentType = contentType, status = status)
}

private suspend fun ApplicationCall.respondNoStoreEmpty(
    contentType: ContentType,
    status: HttpStatusCode,
) {
    response.headers.append(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate, max-age=0")
    response.headers.append(HttpHeaders.Pragma, "no-cache")
    response.headers.append(HttpHeaders.Expires, "0")
    respondText(text = "", contentType = contentType, status = status)
}
