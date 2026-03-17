package com.hitster.platform.web

import kotlinx.serialization.Serializable

@Serializable
data class BrowserGuestSessionStartRequest(
    val hostAddress: String,
    val serverPort: Int,
    val actorId: String,
    val displayName: String,
)

@Serializable
data class BrowserGuestSessionStartResponse(
    val sessionId: String,
)

@Serializable
data class BrowserGuestSessionCommandRequest(
    val payload: String,
)

@Serializable
data class BrowserGuestSessionPollResponse(
    val status: String,
    val events: List<String>,
    val nextSequence: Long,
    val terminalError: String? = null,
)
