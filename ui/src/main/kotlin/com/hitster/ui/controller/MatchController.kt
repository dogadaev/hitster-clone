package com.hitster.ui

/**
 * UI-facing contract for match actions, shared state access, and platform-specific lobby details.
 */

import com.badlogic.gdx.graphics.Texture
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
    val connectionStatus: String?
        get() = null
    val guestJoinUrl: String?
        get() = null
    val guestJoinQrTexture: Texture?
        get() = null

    /** Starts the match from the lobby when the current device is allowed to do so. */
    fun startMatch()

    /** Triggers the host-only playback pairing/preparation flow from the lobby. */
    fun prepareHostPlayback()

    /** Draws the next hidden card for the local player. */
    fun drawCard()

    /** Discards the current hidden card and replaces it with a new draw for the same turn. */
    fun redrawCard()

    /** Arms or clears the local player's doubt request when they are eligible to challenge the turn. */
    fun toggleDoubt()

    /** Updates the local player's intended insertion slot for their active hidden card. */
    fun movePendingCard(requestedSlotIndex: Int)

    /** Updates the doubter's temporary insertion slot inside the isolated doubt placement flow. */
    fun moveDoubtCard(requestedSlotIndex: Int)

    /** Applies a manual host-side coin adjustment for a specific player. */
    fun adjustPlayerCoins(playerId: PlayerId, delta: Int)

    /** Commits the currently valid placement step for either the active turn or the doubt flow. */
    fun endTurn()

    /** Indicates whether the lobby must block start until host playback is prepared. */
    fun requiresHostPlaybackPairing(): Boolean

    /** Indicates whether the lobby currently satisfies the start conditions. */
    fun canStartLobbyMatch(): Boolean

    /** Releases controller-owned resources such as sockets, textures, and background jobs. */
    fun dispose() = Unit
}
