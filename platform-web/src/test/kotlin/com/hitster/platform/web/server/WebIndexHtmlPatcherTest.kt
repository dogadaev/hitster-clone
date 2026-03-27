package com.hitster.platform.web.server

/**
 * Regression coverage for WebIndexHtmlPatcher, keeping the documented behavior of this module stable as gameplay and transport code evolve.
 */

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WebIndexHtmlPatcherTest {
    @Test
    fun patchInjectsMobileViewportTouchHandlingCanvasSizingAndWakeLockSupport() {
        val originalHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>gdx-teavm</title>
            </head>
            <body>
            <div>
                <canvas id="canvas" width="800" height="600"></canvas>
            </div>
            </body>
            </html>
        """.trimIndent()

        val patchedHtml = WebIndexHtmlPatcher.patch(originalHtml)

        assertContains(patchedHtml, """interactive-widget=resizes-content""")
        assertContains(patchedHtml, """<meta name="apple-mobile-web-app-capable" content="yes">""")
        assertContains(patchedHtml, """<style id="hitster-mobile-shell">""")
        assertContains(patchedHtml, "--hitster-visible-height")
        assertContains(patchedHtml, "width: 100% !important;")
        assertContains(patchedHtml, """#hitster-wake-debug {""")
        assertContains(patchedHtml, """white-space: pre-wrap;""")
        assertContains(patchedHtml, """<script id="hitster-nosleep-lib">""")
        assertContains(patchedHtml, """NoSleep.min.js v0.12.0""")
        assertContains(patchedHtml, """<script id="hitster-mobile-runtime">""")
        assertContains(patchedHtml, "var wakeDebugEnabled = /(?:\\?|&)wakeDebug=1(?:&|$)/.test(window.location.search || \"\");")
        assertContains(patchedHtml, "var wakeDebug = null;")
        assertContains(patchedHtml, "wakeDebug = document.createElement(\"div\");")
        assertContains(patchedHtml, "document.body.appendChild(wakeDebug);")
        assertContains(patchedHtml, "canvas.addEventListener(type, handleInteractiveFocus")
        assertContains(patchedHtml, "if (supportsTouchBridge())")
        assertContains(patchedHtml, "function isIosBrowser() {")
        assertContains(patchedHtml, "document.addEventListener(\"touchstart\", function(event)")
        assertContains(patchedHtml, "dispatchSyntheticMouse(\"mousedown\", touch);")
        assertContains(patchedHtml, "window.visualViewport.addEventListener(\"resize\", scheduleViewportSync")
        assertContains(patchedHtml, "function effectivePixelRatio() {")
        assertContains(patchedHtml, "var pixelRatio = window.devicePixelRatio || 1;")
        assertContains(patchedHtml, "return Math.min(pixelRatio, 1.5);")
        assertContains(patchedHtml, "return Math.min(pixelRatio, 2);")
        assertContains(patchedHtml, "var pixelRatio = effectivePixelRatio();")
        assertContains(patchedHtml, "var canvasContainer = canvas.parentElement || canvas.parentNode;")
        assertContains(patchedHtml, "canvas.style.width = Math.max(0, targetCssWidth) + \"px\";")
        assertContains(patchedHtml, "canvas.style.height = Math.max(0, targetCssHeight) + \"px\";")
        assertContains(patchedHtml, "if (canvas.width !== backingWidth) {")
        assertContains(patchedHtml, "if (canvas.height !== backingHeight) {")
        assertContains(patchedHtml, "var noSleep = typeof NoSleep === \"function\" ? new NoSleep() : null;")
        assertContains(patchedHtml, "function enableNoSleep(event) {")
        assertContains(patchedHtml, "Promise.resolve(noSleep.enable()).then(function() {")
        assertContains(patchedHtml, "document.addEventListener(\"click\", enableNoSleep, false);")
        assertContains(patchedHtml, "document.addEventListener(\"touchend\", enableNoSleep, true);")
        assertContains(patchedHtml, "detachWakeActivation();")
        assertContains(patchedHtml, "armWakeActivation();")
        assertContains(patchedHtml, "var wakeState = {")
        assertContains(patchedHtml, "media: \"managed-by-nosleep\",")
        assertContains(patchedHtml, "mediaDetail: \"readme-flow\",")
        assertContains(patchedHtml, "function updateWakeDebug() {")
        assertContains(patchedHtml, "wakeDebug.textContent = [")
        assertContains(patchedHtml, "if (!wakeDebugEnabled || !wakeDebug) {")
        assertContains(patchedHtml, "\"handlers \" + (wakeActivationArmed ? \"armed\" : \"off\")")
        assertContains(patchedHtml, "\"pending no\"")
        assertContains(patchedHtml, "\"media \" + wakeState.media")
        assertContains(patchedHtml, "\"detail \" + wakeState.mediaDetail")
        assertContains(patchedHtml, "\"event \" + wakeState.lastEvent")
        assertContains(patchedHtml, "\"wake nosleep \" + (wakeState.enabled ? \"on\" : \"off\")")
        assertContains(patchedHtml, "fallbackTouchId = touch.identifier;")
        assertContains(patchedHtml, "updateWakeDebug();")
        assertContains(patchedHtml, "window.dispatchEvent(new Event(\"resize\"))")
        assertFalse(patchedHtml.contains("createWakeController()"))
        assertFalse(patchedHtml.contains("noSleep.disable();"))
        assertFalse(patchedHtml.contains("document.addEventListener(\"pointerup\", enableNoSleep, false);"))
    }

    @Test
    fun patchIsIdempotent() {
        val originalHtml = """
            <!DOCTYPE html>
            <html>
            <head></head>
            <body>
            <div>
                <canvas id="canvas"></canvas>
            </div>
            </body>
            </html>
        """.trimIndent()

        val patchedOnce = WebIndexHtmlPatcher.patch(originalHtml)
        val patchedTwice = WebIndexHtmlPatcher.patch(patchedOnce)

        assertEquals(patchedOnce, patchedTwice)
    }

    @Test
    fun patchAddsCacheBustingToTeaVmBundleReference() {
        val originalHtml = """
            <!DOCTYPE html>
            <html>
            <head></head>
            <body>
            <script type="text/javascript" charset="utf-8" src="app.js"></script>
            </body>
            </html>
        """.trimIndent()

        val patchedHtml = WebIndexHtmlPatcher.patch(originalHtml, cacheBustToken = "123")

        assertContains(patchedHtml, """src="app.js?v=123"""")
    }
}
