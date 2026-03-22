package com.hitster.platform.web

/**
 * Regression coverage for HostDiscoverySnapshotSmoother, keeping the documented behavior of this module stable as gameplay and transport code evolve.
 */

import com.hitster.networking.SessionAdvertisementDto
import com.hitster.networking.TransportModeDto
import kotlin.test.Test
import kotlin.test.assertEquals

class HostDiscoverySnapshotSmootherTest {
    @Test
    fun `keeps last hosts through short empty gap`() {
        var now = 1_000L
        val smoother = HostDiscoverySnapshotSmoother(
            emptyResultGraceMillis = 4_000L,
            clockMillis = { now },
        )
        val hosts = listOf(sampleHost("host-a"))

        assertEquals(hosts, smoother.stabilize(hosts))

        now += 2_000L
        assertEquals(hosts, smoother.stabilize(emptyList()))
    }

    @Test
    fun `drops stale hosts after grace window`() {
        var now = 1_000L
        val smoother = HostDiscoverySnapshotSmoother(
            emptyResultGraceMillis = 4_000L,
            clockMillis = { now },
        )
        val hosts = listOf(sampleHost("host-a"))

        assertEquals(hosts, smoother.stabilize(hosts))

        now += 4_500L
        assertEquals(emptyList(), smoother.stabilize(emptyList()))
    }

    @Test
    fun `reset clears cached hosts`() {
        val smoother = HostDiscoverySnapshotSmoother()
        val hosts = listOf(sampleHost("host-a"))

        assertEquals(hosts, smoother.stabilize(hosts))
        smoother.reset()

        assertEquals(emptyList(), smoother.stabilize(emptyList()))
    }

    private fun sampleHost(sessionId: String): SessionAdvertisementDto {
        return SessionAdvertisementDto(
            protocolVersion = 1,
            sessionId = sessionId,
            hostPlayerId = "host",
            hostDisplayName = "Pixel 6",
            hostAddress = "192.168.188.49",
            serverPort = 28761,
            playerCount = 1,
            transportMode = TransportModeDto.LOCAL_NETWORK,
        )
    }
}
