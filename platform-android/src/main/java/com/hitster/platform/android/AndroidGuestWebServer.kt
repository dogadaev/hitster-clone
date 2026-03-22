package com.hitster.platform.android

/**
 * Embeds the guest web build into the Android host so browsers on the LAN can join without a separate helper machine.
 */

import android.content.Context
import com.hitster.networking.SessionAdvertisementDto
import com.hitster.transport.jvm.browser.BrowserGuestSessionRegistry
import com.hitster.transport.jvm.browser.installBrowserGuestSessionApi
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.path
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.head
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import java.io.FileNotFoundException
import java.util.Locale
import kotlinx.coroutines.runBlocking

private const val hostedWebPort = 8080
private const val hostedWebRootAssetPath = "webapp"

class AndroidGuestWebServer(
    private val applicationContext: Context,
    private val hostSnapshotProvider: () -> List<SessionAdvertisementDto>,
) {
    private var server: ApplicationEngine? = null
    private var browserGuestSessions: BrowserGuestSessionRegistry? = null
    private var mdnsAliasAdvertiser: MdnsAliasAdvertiser? = null

    fun start() {
        if (server != null) {
            return
        }
        val guestSessions = BrowserGuestSessionRegistry()
        browserGuestSessions = guestSessions
        server = embeddedServer(CIO, host = "0.0.0.0", port = hostedWebPort) {
            install(WebSockets)
            routing {
                installBrowserGuestSessionApi(
                    browserGuestSessions = guestSessions,
                    hostSnapshotProvider = hostSnapshotProvider,
                )
                head("/") {
                    call.respondNoStoreEmpty(contentTypeFor("$hostedWebRootAssetPath/index.html"))
                }
                get("/") {
                    call.respondNoStoreAsset("$hostedWebRootAssetPath/index.html")
                }
                head("{...}") {
                    val relativePath = call.request.path().removePrefix("/").trim()
                    val assetPath = resolveStaticAssetPath(relativePath)
                    if (assetPath == null) {
                        call.respondText("Not Found", status = HttpStatusCode.NotFound)
                        return@head
                    }
                    call.respondNoStoreEmpty(contentTypeFor(assetPath))
                }
                get("{...}") {
                    val relativePath = call.request.path().removePrefix("/").trim()
                    val assetPath = resolveStaticAssetPath(relativePath)
                    if (assetPath == null) {
                        call.respondText("Not Found", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    call.respondNoStoreAsset(assetPath)
                }
            }
        }.also { it.start(wait = false) }
        hostSnapshotProvider().firstOrNull()?.hostAddress?.let { hostAddress ->
            mdnsAliasAdvertiser = MdnsAliasAdvertiser().also { it.start(hostAddress) }
        }
    }

    fun stop() {
        mdnsAliasAdvertiser?.stop()
        mdnsAliasAdvertiser = null
        runCatching {
            browserGuestSessions?.let { registry ->
                runBlocking { registry.shutdown() }
            }
        }
        browserGuestSessions = null
        server?.stop(250, 1_000)
        server = null
    }

    fun guestEntryUrls(): List<String> {
        val urls = linkedSetOf<String>()
        urls += "http://${MdnsAliasAdvertiser.hostAlias}.local:$hostedWebPort"
        hostSnapshotProvider().firstOrNull()?.hostAddress?.let { hostAddress ->
            urls += "http://$hostAddress:$hostedWebPort"
        }
        return urls.toList()
    }

    private fun resolveStaticAssetPath(relativePath: String): String? {
        if (relativePath.isBlank()) {
            return "$hostedWebRootAssetPath/index.html"
        }
        val normalizedPath = relativePath
            .removePrefix("/")
            .replace('\\', '/')
        if (normalizedPath.contains("..")) {
            return null
        }
        return "$hostedWebRootAssetPath/$normalizedPath"
    }

    private suspend fun ApplicationCall.respondNoStoreAsset(assetPath: String) {
        val bytes = try {
            applicationContext.assets.open(assetPath).use { it.readBytes() }
        } catch (_: FileNotFoundException) {
            respondText("Not Found", status = HttpStatusCode.NotFound)
            return
        }
        response.headers.append(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate, max-age=0")
        response.headers.append(HttpHeaders.Pragma, "no-cache")
        response.headers.append(HttpHeaders.Expires, "0")
        respondBytes(
            bytes = bytes,
            contentType = contentTypeFor(assetPath),
            status = HttpStatusCode.OK,
        )
    }

    private suspend fun ApplicationCall.respondNoStoreEmpty(contentType: ContentType) {
        response.headers.append(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate, max-age=0")
        response.headers.append(HttpHeaders.Pragma, "no-cache")
        response.headers.append(HttpHeaders.Expires, "0")
        respondText(text = "", contentType = contentType, status = HttpStatusCode.OK)
    }

    private fun contentTypeFor(assetPath: String): ContentType {
        return when (assetPath.substringAfterLast('.', "").lowercase(Locale.US)) {
            "html" -> ContentType.Text.Html
            "js" -> ContentType.Application.JavaScript
            "json" -> ContentType.Application.Json
            "css" -> ContentType.Text.CSS
            "wasm" -> ContentType.parse("application/wasm")
            "png" -> ContentType.Image.PNG
            "jpg", "jpeg" -> ContentType.Image.JPEG
            "svg" -> ContentType.parse("image/svg+xml")
            "ttf" -> ContentType.parse("font/ttf")
            "fnt", "glsl", "txt" -> ContentType.Text.Plain
            else -> ContentType.Application.OctetStream
        }
    }

    companion object {
        const val port: Int = hostedWebPort
    }
}
