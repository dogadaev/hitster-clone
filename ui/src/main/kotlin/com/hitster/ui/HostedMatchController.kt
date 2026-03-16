package com.hitster.ui

import com.hitster.networking.HostEventDto

class HostedMatchController(
    private val presenter: MatchPresenter,
    private val sessionTransport: HostedSessionTransport,
) : MatchController by presenter {
    init {
        presenter.snapshotListener = { snapshot ->
            sessionTransport.broadcast(HostEventDto.SnapshotPublished(snapshot))
        }
        presenter.rejectionListener = { actorId, reason, revision ->
            sessionTransport.broadcast(HostEventDto.CommandRejected(actorId, reason, revision))
        }
        sessionTransport.start()
    }

    override fun dispose() {
        sessionTransport.close()
    }
}
