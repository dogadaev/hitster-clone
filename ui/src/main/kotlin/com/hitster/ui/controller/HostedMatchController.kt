package com.hitster.ui.controller

/**
 * Thin host-side controller wrapper that exposes the authoritative presenter through the shared match controller API.
 */

import com.hitster.networking.HostEventDto
import com.hitster.networking.PlaybackStateDto
import com.hitster.networking.PlaybackStatusDto
import com.hitster.playback.api.PlaybackSessionState
import java.util.concurrent.atomic.AtomicBoolean

class HostedMatchController(
    private val presenter: MatchPresenter,
    private val sessionTransport: HostedSessionTransport,
) : MatchController by presenter {
    private val hostingStarted = AtomicBoolean(false)

    override val guestJoinUrl: String?
        get() = sessionTransport.guestJoinUrl
    override val guestJoinQrTexture
        get() = sessionTransport.guestJoinQrTexture

    init {
        presenter.snapshotListener = { snapshot ->
            sessionTransport.broadcast(HostEventDto.SnapshotPublished(snapshot))
            sessionTransport.broadcast(HostEventDto.PlaybackStateChanged(presenter.playbackSessionState.toDto()))
        }
        presenter.rejectionListener = { actorId, reason, revision ->
            sessionTransport.broadcast(HostEventDto.CommandRejected(actorId, reason, revision))
        }
        presenter.playbackStateListener = { playbackState ->
            sessionTransport.broadcast(HostEventDto.PlaybackStateChanged(playbackState.toDto()))
        }
    }

    /**
     * Starts LAN hosting off the UI thread so choosing the host role does not stall the device
     * while the local session server, guest web server, and discovery services come up.
     */
    fun startHosting() {
        if (!hostingStarted.compareAndSet(false, true)) {
            return
        }
        Thread({
            runCatching { sessionTransport.start() }
                .onFailure { error ->
                    println("Failed to start hosted session transport: ${error.message}")
                }
        }, "hitster-host-transport-start").apply {
            isDaemon = true
            start()
        }
    }

    override fun dispose() {
        sessionTransport.close()
    }
}

private fun PlaybackSessionState.toDto(): PlaybackStateDto {
    return when (this) {
        PlaybackSessionState.Disconnected -> PlaybackStateDto(PlaybackStatusDto.DISCONNECTED)
        PlaybackSessionState.Connecting -> PlaybackStateDto(PlaybackStatusDto.CONNECTING)
        PlaybackSessionState.Ready -> PlaybackStateDto(PlaybackStatusDto.READY)
        is PlaybackSessionState.Playing -> PlaybackStateDto(PlaybackStatusDto.PLAYING, spotifyUri)
        is PlaybackSessionState.Paused -> PlaybackStateDto(PlaybackStatusDto.PAUSED, spotifyUri)
    }
}
