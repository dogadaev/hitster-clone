package com.hitster.platform.web

/**
 * Polls the local helper API for advertised Android hosts and exposes a smoothed list to the guest discovery screen.
 */

import com.hitster.networking.SessionAdvertisementDto
import com.hitster.networking.protocolJson
import com.hitster.ui.HostDiscoveryService
import kotlinx.serialization.decodeFromString
import org.teavm.jso.ajax.XMLHttpRequest
import org.teavm.jso.browser.TimerHandler
import org.teavm.jso.browser.Window
import org.teavm.jso.dom.events.Event
import org.teavm.jso.dom.events.EventListener

class BrowserHostDiscoveryService(
    private val discoveryEndpoint: String = "/api/hosts",
    private val pollIntervalMillis: Int = 1_000,
) : HostDiscoveryService {
    private var active = false
    private var onUpdate: ((List<SessionAdvertisementDto>) -> Unit)? = null
    private val snapshotSmoother = HostDiscoverySnapshotSmoother()

    override fun start(onUpdate: (List<SessionAdvertisementDto>) -> Unit) {
        this.onUpdate = onUpdate
        if (active) {
            return
        }
        snapshotSmoother.reset()
        active = true
        requestHosts()
    }

    override fun stop() {
        active = false
        onUpdate = null
        snapshotSmoother.reset()
    }

    private fun requestHosts() {
        if (!active) {
            return
        }

        val request = XMLHttpRequest.create()
        request.open("GET", discoveryEndpoint, true)
        request.setOnReadyStateChange(
            object : EventListener<Event> {
                override fun handleEvent(evt: Event) {
                    if (request.readyState != XMLHttpRequest.DONE) {
                        return
                    }
                    val hosts = if (request.status in 200..299) {
                        runCatching {
                            protocolJson.decodeFromString<List<SessionAdvertisementDto>>(request.responseText ?: "[]")
                        }.getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                    onUpdate?.invoke(snapshotSmoother.stabilize(hosts))
                    scheduleNextPoll()
                }
            },
        )
        request.send()
    }

    private fun scheduleNextPoll() {
        if (!active) {
            return
        }
        Window.setTimeout(
            object : TimerHandler {
                override fun onTimer() {
                    requestHosts()
                }
            },
            pollIntervalMillis,
        )
    }
}
