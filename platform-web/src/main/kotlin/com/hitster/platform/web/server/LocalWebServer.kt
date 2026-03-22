package com.hitster.platform.web

/**
 * Development helper server that serves the browser guest build, host discovery API, and proxy routes from the desktop environment.
 */

import com.hitster.networking.protocolJson
import com.hitster.transport.jvm.LanHostDiscoveryListener
import com.hitster.transport.jvm.browser.BrowserGuestSessionRegistry
import com.hitster.transport.jvm.browser.installBrowserGuestSessionApi
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.path
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.close
import kotlinx.serialization.encodeToString
import java.io.File

private const val defaultWebPort = 8080

fun main() {
    val distDir = File("build/dist/webapp")
    require(distDir.exists()) {
        "Web dist not found at ${distDir.absolutePath}. Run :platform-web:buildWebDist first."
    }

    val discoveryListener = LanHostDiscoveryListener(sessionTtlMillis = 8_000L)
    discoveryListener.start()
    val browserGuestSessions = BrowserGuestSessionRegistry()

    val server = embeddedServer(CIO, host = "0.0.0.0", port = defaultWebPort) {
        install(WebSockets)
        routing {
            get("/") {
                call.respondNoStoreFile(distDir.resolve("index.html"))
            }
            installBrowserGuestSessionApi(
                browserGuestSessions = browserGuestSessions,
                hostSnapshotProvider = discoveryListener::snapshot,
            )
            webSocket("/session-proxy") {
                val hostAddress = call.request.queryParameters["hostAddress"]
                val serverPort = call.request.queryParameters["serverPort"]?.toIntOrNull()
                val userAgent = call.request.headers[HttpHeaders.UserAgent].orEmpty()

                if (hostAddress.isNullOrBlank() || serverPort == null) {
                    println("Web guest proxy rejected request: missing hostAddress/serverPort ua=$userAgent")
                    close()
                    return@webSocket
                }

                println(
                    "Web guest proxy open: host=$hostAddress:$serverPort ua=$userAgent"
                )

                WebGuestSessionProxy(
                    hostAddress = hostAddress,
                    serverPort = serverPort,
                ).bridge(this)
            }
            get("{...}") {
                val relativePath = call.request.path().removePrefix("/").trim()
                val file = distDir.resolveStaticChild(relativePath)
                if (file == null || !file.exists() || file.isDirectory) {
                    call.respondText("Not Found", status = HttpStatusCode.NotFound)
                    return@get
                }
                call.respondNoStoreFile(file)
            }
        }
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            discoveryListener.stop()
            kotlinx.coroutines.runBlocking {
                browserGuestSessions.shutdown()
            }
        },
    )

    println("Local web guest server running at http://0.0.0.0:$defaultWebPort")
    server.start(wait = true)
}

private fun File.resolveStaticChild(relativePath: String): File? {
    if (relativePath.isBlank()) {
        return resolve("index.html").canonicalFile
    }

    val normalizedPath = relativePath
        .removePrefix("/")
        .replace('\\', '/')
    val resolved = resolve(normalizedPath).canonicalFile
    val root = canonicalFile
    return resolved.takeIf { candidate ->
        candidate.path == root.path || candidate.path.startsWith(root.path + File.separator)
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondNoStoreFile(file: File) {
    response.headers.append(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate, max-age=0")
    response.headers.append(HttpHeaders.Pragma, "no-cache")
    response.headers.append(HttpHeaders.Expires, "0")
    respondFile(file)
}

private suspend fun io.ktor.server.application.ApplicationCall.respondNoStoreText(
    text: String,
    contentType: ContentType,
    status: HttpStatusCode,
) {
    response.headers.append(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate, max-age=0")
    response.headers.append(HttpHeaders.Pragma, "no-cache")
    response.headers.append(HttpHeaders.Expires, "0")
    respondText(text = text, contentType = contentType, status = status)
}
