package com.hitster.platform.web

import org.teavm.jso.JSBody

@JSBody(
    params = ["message", "initialValue"],
    script = """
        return window.prompt(message, initialValue);
    """,
)
external fun showBrowserNamePrompt(message: String, initialValue: String): String?
