package com.hitster.transport.jvm

/**
 * LAN discovery announcer and browser used by Android hosts and guests to find local sessions.
 */

import com.hitster.networking.SessionAdvertisementDto
import com.hitster.networking.protocolJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

const val DEFAULT_SESSION_SERVER_PORT: Int = 28761
const val DEFAULT_DISCOVERY_PORT: Int = 28762

class LanHostDiscoveryAnnouncer(
    private val advertisementProvider: () -> SessionAdvertisementDto,
    private val discoveryPort: Int = DEFAULT_DISCOVERY_PORT,
    private val broadcastIntervalMillis: Long = 1_000L,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        if (job != null) {
            return
        }

        job = scope.launch {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                while (isActive) {
                    val payload = protocolJson.encodeToString(advertisementProvider())
                    val bytes = payload.toByteArray(Charsets.UTF_8)
                    val packet = DatagramPacket(
                        bytes,
                        bytes.size,
                        InetAddress.getByName("255.255.255.255"),
                        discoveryPort,
                    )
                    runCatching {
                        socket.send(packet)
                    }
                    delay(broadcastIntervalMillis)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        scope.coroutineContext.cancel()
    }
}

data class DiscoveredSession(
    val advertisement: SessionAdvertisementDto,
    val lastSeenAtMillis: Long,
)

class LanHostDiscoveryListener(
    private val discoveryPort: Int = DEFAULT_DISCOVERY_PORT,
    private val sessionTtlMillis: Long = 3_500L,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val discovered = ConcurrentHashMap<String, DiscoveredSession>()
    private var receiveJob: Job? = null
    private var pruneJob: Job? = null
    @Volatile
    private var onUpdate: ((List<SessionAdvertisementDto>) -> Unit)? = null

    fun start(onUpdate: (List<SessionAdvertisementDto>) -> Unit = {}) {
        this.onUpdate = onUpdate
        if (receiveJob != null) {
            onUpdate(snapshot())
            return
        }

        receiveJob = scope.launch {
            DatagramSocket(null).use { socket ->
                socket.broadcast = true
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(discoveryPort))
                socket.soTimeout = 1_000
                val buffer = ByteArray(8 * 1024)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    runCatching {
                        socket.receive(packet)
                    }.onSuccess {
                        val payload = packet.data.decodeToString(0, packet.length)
                        val advertisement = runCatching {
                            protocolJson.decodeFromString<SessionAdvertisementDto>(payload)
                        }.getOrNull() ?: return@onSuccess
                        discovered[advertisement.sessionId] = DiscoveredSession(
                            advertisement = advertisement,
                            lastSeenAtMillis = System.currentTimeMillis(),
                        )
                        publish()
                    }
                }
            }
        }

        pruneJob = scope.launch {
            while (isActive) {
                delay(1_000)
                pruneExpired()
            }
        }
    }

    fun snapshot(): List<SessionAdvertisementDto> {
        return discovered.values
            .sortedByDescending { it.lastSeenAtMillis }
            .map { it.advertisement }
    }

    fun stop() {
        receiveJob?.cancel()
        pruneJob?.cancel()
        receiveJob = null
        pruneJob = null
        discovered.clear()
        scope.coroutineContext.cancel()
    }

    private fun pruneExpired() {
        val cutoff = System.currentTimeMillis() - sessionTtlMillis
        val changed = discovered.entries.removeIf { it.value.lastSeenAtMillis < cutoff }
        if (changed) {
            publish()
        }
    }

    private fun publish() {
        onUpdate?.invoke(snapshot())
    }
}

fun resolveSiteLocalIpv4Address(): String? {
    return NetworkInterface.getNetworkInterfaces()
        ?.toList()
        ?.asSequence()
        ?.filter { it.isUp && !it.isLoopback && !it.isVirtual }
        ?.flatMap { it.inetAddresses.toList().asSequence() }
        ?.filterIsInstance<Inet4Address>()
        ?.firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress
}

private fun <T> java.util.Enumeration<T>.toList(): List<T> {
    val items = ArrayList<T>()
    while (hasMoreElements()) {
        items += nextElement()
    }
    return items
}
