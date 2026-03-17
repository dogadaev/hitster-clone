package com.hitster.ui

import com.hitster.core.model.GameState
import com.hitster.core.model.PlayerId
import com.hitster.core.model.PlayerState
import com.hitster.playback.api.PlaybackIssue
import com.hitster.playback.api.PlaybackSessionState

interface MatchController {
    val state: GameState
    val localPlayerId: PlayerId
    val isLocalHost: Boolean
    val localPlayer: PlayerState?
    val lastError: String?
    val lastPlaybackIssue: PlaybackIssue?
    val playbackSessionState: PlaybackSessionState

    fun startMatch()

    fun prepareHostPlayback()

    fun drawCard()

    fun movePendingCard(requestedSlotIndex: Int)

    fun endTurn()

    fun requiresHostPlaybackPairing(): Boolean

    fun canStartLobbyMatch(): Boolean

    fun dispose() = Unit
}
