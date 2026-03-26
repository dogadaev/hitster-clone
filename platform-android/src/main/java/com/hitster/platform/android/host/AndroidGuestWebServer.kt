package com.hitster.platform.android.host

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
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.respond
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
                    call.respondNoStoreAsset("$hostedWebRootAssetPath/index.html")
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
                    call.respondNoStoreAsset(assetPath)
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

    private fun loadAssetBytes(assetPath: String): ByteArray? {
        return try {
            applicationContext.assets.open(assetPath).use { it.readBytes() }
        } catch (_: FileNotFoundException) {
            null
        }
    }

    private suspend fun ApplicationCall.respondNoStoreAsset(assetPath: String) {
        val bytes = loadAssetBytes(assetPath) ?: run {
            respondText("Not Found", status = HttpStatusCode.NotFound)
            return
        }
        val contentType = contentTypeFor(assetPath)
        val totalSize = bytes.size.toLong()
        response.headers.append(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate, max-age=0")
        response.headers.append(HttpHeaders.Pragma, "no-cache")
        response.headers.append(HttpHeaders.Expires, "0")
        response.headers.append(HttpHeaders.AcceptRanges, "bytes")

        val rangeHeader = request.header(HttpHeaders.Range)
        val range = parseByteRange(rangeHeader, totalSize)

        if (range == null) {
            respondBytes(
                bytes = bytes,
                contentType = contentType,
                status = HttpStatusCode.OK,
            )
            return
        }

        if (range.isUnsatisfied) {
            response.headers.append(HttpHeaders.ContentRange, "bytes */$totalSize")
            respond(HttpStatusCode.RequestedRangeNotSatisfiable)
            return
        }

        val slice = bytes.copyOfRange(range.start.toInt(), range.endInclusive.toInt() + 1)
        response.headers.append(
            HttpHeaders.ContentRange,
            "bytes ${range.start}-${range.endInclusive}/$totalSize",
        )
        respondBytes(
            bytes = slice,
            contentType = contentType,
            status = HttpStatusCode.PartialContent,
        )
    }

    private fun contentTypeFor(assetPath: String): ContentType {
        return when (assetPath.substringAfterLast('.', "").lowercase(Locale.US)) {
            "html" -> ContentType.Text.Html
            "js" -> ContentType.Application.JavaScript
            "json" -> ContentType.Application.Json
            "css" -> ContentType.Text.CSS
            "wasm" -> ContentType.parse("application/wasm")
            "mp4", "m4v" -> ContentType.Video.MP4
            "webm" -> ContentType.parse("video/webm")
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

private data class RequestedByteRange(
    val start: Long,
    val endInclusive: Long,
    val isUnsatisfied: Boolean = false,
) {
    val length: Long get() = if (isUnsatisfied) 0 else (endInclusive - start) + 1
}

private fun parseByteRange(headerValue: String?, totalSize: Long): RequestedByteRange? {
    if (headerValue.isNullOrBlank() || totalSize <= 0L) {
        return null
    }
    val normalized = headerValue.trim()
    if (!normalized.startsWith("bytes=")) {
        return null
    }
    val requested = normalized.removePrefix("bytes=").substringBefore(',').trim()
    if (requested.isBlank()) {
        return null
    }

    val parts = requested.split('-', limit = 2)
    val startPart = parts.getOrNull(0).orEmpty().trim()
    val endPart = parts.getOrNull(1).orEmpty().trim()

    if (startPart.isEmpty() && endPart.isEmpty()) {
        return null
    }

    if (startPart.isEmpty()) {
        val suffixLength = endPart.toLongOrNull() ?: return null
        if (suffixLength <= 0L) {
            return RequestedByteRange(0L, 0L, isUnsatisfied = true)
        }
        val actualLength = minOf(suffixLength, totalSize)
        return RequestedByteRange(
            start = totalSize - actualLength,
            endInclusive = totalSize - 1L,
        )
    }

    val start = startPart.toLongOrNull() ?: return null
    if (start >= totalSize || start < 0L) {
        return RequestedByteRange(0L, 0L, isUnsatisfied = true)
    }
    val end = if (endPart.isEmpty()) {
        totalSize - 1L
    } else {
        val parsedEnd = endPart.toLongOrNull() ?: return null
        minOf(parsedEnd, totalSize - 1L)
    }
    if (end < start) {
        return RequestedByteRange(0L, 0L, isUnsatisfied = true)
    }
    return RequestedByteRange(start = start, endInclusive = end)
}
