package com.hitster.ui

import com.hitster.core.model.MatchStatus
import com.hitster.core.model.PlayerId
import com.hitster.core.model.PlayerState
import com.hitster.core.model.TurnPhase

class AutomatedGuestPlayerBot(
    private val presenter: MatchPresenter,
    private val playerId: PlayerId,
    private val drawDelaySeconds: Float = 0.45f,
    private val placeDelaySeconds: Float = 0.35f,
    private val endTurnDelaySeconds: Float = 0.55f,
) {
    private var cooldownSeconds = 0f

    fun update(deltaSeconds: Float) {
        cooldownSeconds = (cooldownSeconds - deltaSeconds).coerceAtLeast(0f)
        if (cooldownSeconds > 0f) {
            return
        }

        val state = presenter.state
        if (state.status != MatchStatus.ACTIVE) {
            return
        }

        val turn = state.turn ?: return
        if (turn.activePlayerId != playerId) {
            return
        }

        val player = state.requirePlayer(playerId) ?: return
        val pendingCard = player.pendingCard

        when {
            pendingCard == null && turn.phase == TurnPhase.WAITING_FOR_DRAW -> {
                presenter.drawCardAs(playerId)
                cooldownSeconds = drawDelaySeconds
            }

            pendingCard != null && turn.phase == TurnPhase.AWAITING_PLACEMENT -> {
                presenter.movePendingCardAs(playerId, chooseSlotIndex(player))
                cooldownSeconds = placeDelaySeconds
            }

            pendingCard != null && turn.phase == TurnPhase.CARD_POSITIONED -> {
                presenter.endTurnAs(playerId)
                cooldownSeconds = endTurnDelaySeconds
            }
        }
    }

    private fun chooseSlotIndex(player: PlayerState): Int {
        val pendingCard = player.pendingCard ?: return player.timeline.cards.size
        val timeline = player.timeline.cards
        val firstGreaterIndex = timeline.indexOfFirst { it.releaseYear > pendingCard.entry.releaseYear }
        return if (firstGreaterIndex == -1) timeline.size else firstGreaterIndex
    }
}
