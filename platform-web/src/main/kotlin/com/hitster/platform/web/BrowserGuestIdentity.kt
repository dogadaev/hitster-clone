package com.hitster.platform.web

import com.hitster.core.model.PlayerId
import org.teavm.jso.browser.Window

private const val guestNameStorageKey = "hitsterCloneGuestName"
private const val guestPlayerIdStoragePrefix = "hitsterCloneGuestPlayerId:"

fun resolveBrowserDisplayName(): String {
    val storage = Window.current().localStorage
    val existing = storage?.getItem(guestNameStorageKey)?.trim().orEmpty()
    if (existing.isNotEmpty()) {
        return existing
    }

    val generatedName = "Browser ${(System.currentTimeMillis() % 10_000).toString().padStart(4, '0')}"
    storage?.setItem(guestNameStorageKey, generatedName)
    return generatedName
}

fun persistBrowserDisplayName(displayName: String) {
    Window.current().localStorage?.setItem(guestNameStorageKey, displayName)
}

fun resolveBrowserGuestPlayerId(sessionId: String): PlayerId {
    val storage = Window.current().localStorage
    val storageKey = "$guestPlayerIdStoragePrefix$sessionId"
    val existing = storage?.getItem(storageKey)?.trim().orEmpty()
    if (existing.isNotEmpty()) {
        return PlayerId(existing)
    }

    val generatedId = "guest-${
        java.lang.Long.toString(System.currentTimeMillis(), 36)
    }-${
        Integer.toString((Math.random() * 1_000_000).toInt(), 36)
    }"
    storage?.setItem(storageKey, generatedId)
    return PlayerId(generatedId)
}
