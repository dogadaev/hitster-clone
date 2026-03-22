package com.hitster.platform.web

/**
 * TeaVM bridge for the browser-native name prompt used before a guest joins the lobby.
 */

import org.teavm.jso.JSBody

@JSBody(
    params = ["message", "initialValue"],
    script = """
        return window.prompt(message, initialValue);
    """,
)
external fun showBrowserNamePrompt(message: String, initialValue: String): String?
