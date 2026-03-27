package com.hitster.ui.controller

/**
 * Thin host-side controller wrapper that exposes the authoritative presenter through the shared match controller API.
 */

import com.hitster.networking.HostEventDto
import com.hitster.networking.PlaybackStateDto
import com.hitster.networking.PlaybackStatusDto
import com.hitster.playback.api.PlaybackSessionState

class HostedMatchController(
    private val presenter: MatchPresenter,
    private val sessionTransport: HostedSessionTransport,
) : MatchController by presenter {
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
        sessionTransport.start()
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
