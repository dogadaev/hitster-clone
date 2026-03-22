package com.hitster.core.model

/**
 * Tiny strongly typed wrappers for session and player identifiers so networking and reducer code avoid raw strings.
 */

@JvmInline
value class SessionId(val value: String)

@JvmInline
value class PlayerId(val value: String)
