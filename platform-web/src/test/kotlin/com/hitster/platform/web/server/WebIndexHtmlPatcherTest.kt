package com.hitster.platform.web.server

/**
 * Regression coverage for WebIndexHtmlPatcher, keeping the documented behavior of this module stable as gameplay and transport code evolve.
 */

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

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
        assertContains(patchedHtml, """<script id="hitster-mobile-runtime">""")
        assertContains(patchedHtml, "var wakeDebugEnabled = /(?:\\?|&)wakeDebug=1(?:&|$)/.test(window.location.search || \"\");")
        assertContains(patchedHtml, "var wakeDebug = null;")
        assertContains(patchedHtml, "wakeDebug = document.createElement(\"div\");")
        assertContains(patchedHtml, "document.body.appendChild(wakeDebug);")
        assertContains(patchedHtml, "canvas.addEventListener(type, handleInteractiveFocus")
        assertContains(patchedHtml, "if (supportsTouchBridge())")
        assertContains(patchedHtml, "function isIosBrowser() {")
        assertContains(patchedHtml, "function isStandaloneMode() {")
        assertContains(patchedHtml, "document.addEventListener(\"touchstart\", function(event)")
        assertContains(patchedHtml, "activateWakeFromGesture();")
        assertContains(patchedHtml, "dispatchSyntheticMouse(\"mousedown\", touch);")
        assertContains(patchedHtml, "window.visualViewport.addEventListener(\"resize\", scheduleViewportSync")
        assertContains(patchedHtml, "var pixelRatio = window.devicePixelRatio || 1;")
        assertContains(patchedHtml, "var canvasContainer = canvas.parentElement || canvas.parentNode;")
        assertContains(patchedHtml, "canvas.style.width = Math.max(0, targetCssWidth) + \"px\";")
        assertContains(patchedHtml, "canvas.style.height = Math.max(0, targetCssHeight) + \"px\";")
        assertContains(patchedHtml, "if (canvas.width !== backingWidth) {")
        assertContains(patchedHtml, "if (canvas.height !== backingHeight) {")
        assertContains(patchedHtml, "navigator.wakeLock.request(\"screen\")")
        assertContains(patchedHtml, "function createWakeController() {")
        assertContains(patchedHtml, "wakeFallbackVideo.id = \"hitster-wake-video\";")
        assertContains(patchedHtml, "function appendWakeSource(videoElement, type, uri) {")
        assertContains(patchedHtml, """appendWakeSource(wakeFallbackVideo, "webm"""")
        assertContains(patchedHtml, """appendWakeSource(wakeFallbackVideo, "mp4"""")
        assertContains(patchedHtml, """source.type = "video/" + type;""")
        assertContains(patchedHtml, "[\"play\", \"playing\", \"pause\", \"waiting\", \"stalled\", \"suspend\", \"abort\", \"ended\", \"canplay\", \"canplaythrough\", \"loadeddata\"].forEach(function(type) {")
        assertContains(patchedHtml, "function updateMediaDetail(type) {")
        assertContains(patchedHtml, "wakeFallbackVideo.addEventListener(\"loadedmetadata\"")
        assertContains(patchedHtml, "wakeFallbackVideo.currentTime > 0.5")
        assertContains(patchedHtml, "var wakeActivationReceived = false;")
        assertContains(patchedHtml, "var wakeState = {")
        assertContains(patchedHtml, "pending: false,")
        assertContains(patchedHtml, "media: \"none\",")
        assertContains(patchedHtml, "mediaDetail: \"none\",")
        assertContains(patchedHtml, "function updateWakeDebug() {")
        assertContains(patchedHtml, "wakeDebug.textContent = [")
        assertContains(patchedHtml, "if (!wakeDebugEnabled || !wakeDebug) {")
        assertContains(patchedHtml, "\"pending \" + (wakeState.pending ? \"yes\" : \"no\")")
        assertContains(patchedHtml, "\"media \" + wakeState.media")
        assertContains(patchedHtml, "\"detail \" + wakeState.mediaDetail")
        assertContains(patchedHtml, "var currentSource = wakeFallbackVideo.currentSrc || \"\";")
        assertContains(patchedHtml, "\"src=\" + sourceType")
        assertContains(patchedHtml, "\"wake \" + wakeState.mode + \" \" + (wakeState.enabled ? \"on\" : \"off\")")
        assertContains(patchedHtml, "var wakeController = createWakeController();")
        assertContains(patchedHtml, "function activateWakeFromGesture() {")
        assertContains(patchedHtml, "if (document.hidden || !wakeActivationReceived) {")
        assertContains(patchedHtml, "wakeActivationReceived = true;")
        assertContains(patchedHtml, "wakeController.enable().catch(function() {});")
        assertContains(patchedHtml, "!wakeController.isEnabled()")
        assertContains(patchedHtml, "wakeState.mode !== \"video-fallback\"")
        assertContains(patchedHtml, "if (pendingPromise) {")
        assertContains(patchedHtml, "if (!wakeDebugEnabled) {")
        assertContains(patchedHtml, "canvas.addEventListener(type, function() {")
        assertContains(patchedHtml, "handleWakeGesture();")
        assertContains(patchedHtml, "window.location.href = window.location.href.split(\"#\")[0];")
        assertContains(patchedHtml, "var shouldKeepScreenAwake = true;")
        assertContains(patchedHtml, "window.setInterval(function() {")
        assertContains(patchedHtml, "updateWakeDebug();")
        assertContains(patchedHtml, "window.dispatchEvent(new Event(\"resize\"))")
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
