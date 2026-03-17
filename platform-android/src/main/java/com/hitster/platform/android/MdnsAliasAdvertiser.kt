package com.hitster.platform.android

import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import java.net.InetAddress

class MdnsAliasAdvertiser(
    private val aliasName: String = hostAlias,
    private val port: Int = AndroidGuestWebServer.port,
) {
    private var jmdns: JmDNS? = null
    private var serviceInfo: ServiceInfo? = null

    fun start(hostAddress: String) {
        if (jmdns != null) {
            return
        }
        runCatching {
            val address = InetAddress.getByName(hostAddress)
            val instance = JmDNS.create(address, aliasName)
            val service = ServiceInfo.create("_http._tcp.local.", aliasName, port, "path=/")
            instance.registerService(service)
            jmdns = instance
            serviceInfo = service
            println("Advertised local web guest alias at http://$aliasName.local:$port")
        }.onFailure { error ->
            println("Failed to advertise local web guest alias: ${error.message}")
        }
    }

    fun stop() {
        runCatching {
            serviceInfo?.let { info ->
                jmdns?.unregisterService(info)
            }
            jmdns?.close()
        }
        serviceInfo = null
        jmdns = null
    }

    companion object {
        const val hostAlias = "melonman"
    }
}
