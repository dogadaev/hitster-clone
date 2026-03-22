package com.hitster.platform.web

/**
 * Suppresses brief discovery flicker so transient LAN hiccups do not clear the visible host list immediately.
 */

import com.hitster.networking.SessionAdvertisementDto

internal class HostDiscoverySnapshotSmoother(
    private val emptyResultGraceMillis: Long = 4_000L,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) {
    private var lastNonEmptyHosts: List<SessionAdvertisementDto> = emptyList()
    private var lastNonEmptyAtMillis: Long = Long.MIN_VALUE

    fun reset() {
        lastNonEmptyHosts = emptyList()
        lastNonEmptyAtMillis = Long.MIN_VALUE
    }

    fun stabilize(latestHosts: List<SessionAdvertisementDto>): List<SessionAdvertisementDto> {
        if (latestHosts.isNotEmpty()) {
            lastNonEmptyHosts = latestHosts
            lastNonEmptyAtMillis = clockMillis()
            return latestHosts
        }

        if (lastNonEmptyHosts.isEmpty()) {
            return emptyList()
        }

        val ageMillis = clockMillis() - lastNonEmptyAtMillis
        if (ageMillis <= emptyResultGraceMillis) {
            return lastNonEmptyHosts
        }

        lastNonEmptyHosts = emptyList()
        lastNonEmptyAtMillis = Long.MIN_VALUE
        return emptyList()
    }
}
