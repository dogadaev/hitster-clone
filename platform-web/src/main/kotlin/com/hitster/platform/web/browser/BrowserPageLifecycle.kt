package com.hitster.platform.web.browser

/**
 * TeaVM bridges for page-lifecycle hooks so the browser can close guest sessions when the tab disappears.
 */

import org.teavm.jso.JSBody

@JSBody(
    script = """
        if (!window.__hitsterGuestCloseHookInstalled) {
            window.__hitsterGuestCloseHookInstalled = true;
            window.__hitsterGuestCloseUrl = "";
            var sendGuestClose = function() {
                var closeUrl = window.__hitsterGuestCloseUrl;
                if (!closeUrl) {
                    return;
                }
                try {
                    if (navigator.sendBeacon) {
                        navigator.sendBeacon(closeUrl, new Blob([], { type: "application/json" }));
                    } else if (window.fetch) {
                        fetch(closeUrl, {
                            method: "POST",
                            headers: { "Content-Type": "application/json" },
                            body: "",
                            keepalive: true
                        });
                    } else {
                        var xhr = new XMLHttpRequest();
                        xhr.open("POST", closeUrl, false);
                        xhr.setRequestHeader("Content-Type", "application/json");
                        xhr.send("");
                    }
                } catch (closeError) {
                    try {
                        var fallbackXhr = new XMLHttpRequest();
                        fallbackXhr.open("POST", closeUrl, false);
                        fallbackXhr.setRequestHeader("Content-Type", "application/json");
                        fallbackXhr.send("");
                    } catch (ignored) {
                    }
                }
                window.__hitsterGuestCloseUrl = "";
            };
            window.addEventListener("pagehide", sendGuestClose, { capture: true });
            window.addEventListener("beforeunload", sendGuestClose, { capture: true });
        }
    """,
)
external fun ensureGuestCloseHookInstalled()

@JSBody(
    params = ["closeUrl"],
    script = """
        window.__hitsterGuestCloseUrl = closeUrl;
    """,
)
external fun registerGuestCloseUrl(closeUrl: String)

@JSBody(
    script = """
        window.__hitsterGuestCloseUrl = "";
    """,
)
external fun clearGuestCloseUrl()
