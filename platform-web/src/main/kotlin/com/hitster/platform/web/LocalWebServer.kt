package com.hitster.platform.web

import com.hitster.networking.protocolJson
import com.hitster.transport.jvm.LanHostDiscoveryListener
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.websocket.close
import kotlinx.serialization.encodeToString
import java.io.File

private const val defaultWebPort = 8080

fun main() {
    val distDir = File("build/dist/webapp")
    require(distDir.exists()) {
        "Web dist not found at ${distDir.absolutePath}. Run :platform-web:buildWebDist first."
    }

    val discoveryListener = LanHostDiscoveryListener()
    discoveryListener.start()

    val server = embeddedServer(CIO, host = "0.0.0.0", port = defaultWebPort) {
        install(WebSockets)
        routing {
            get("/") {
                call.respondFile(distDir.resolve("index.html"))
            }
            get("/api/hosts") {
                call.respondText(
                    text = protocolJson.encodeToString(discoveryListener.snapshot()),
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK,
                )
            }
            webSocket("/session-proxy") {
                val hostAddress = call.request.queryParameters["hostAddress"]
                val serverPort = call.request.queryParameters["serverPort"]?.toIntOrNull()

                if (hostAddress.isNullOrBlank() || serverPort == null) {
                    close()
                    return@webSocket
                }

                WebGuestSessionProxy(
                    hostAddress = hostAddress,
                    serverPort = serverPort,
                ).bridge(this)
            }
            staticFiles("/", distDir)
        }
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            discoveryListener.stop()
        },
    )

    println("Local web guest server running at http://0.0.0.0:$defaultWebPort")
    server.start(wait = true)
}
