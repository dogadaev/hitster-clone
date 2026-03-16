package com.hitster.platform.web

import org.teavm.jso.browser.Window

private const val guestNameStorageKey = "hitsterCloneGuestName"

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
