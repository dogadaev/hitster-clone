package com.hitster.platform.web.server

/**
 * Regression coverage for WebIndexHtmlPatcher, keeping the documented behavior of this module stable as gameplay and transport code evolve.
 */

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class WebIndexHtmlPatcherTest {
    @Test
    fun patchInjectsMobileViewportTouchHandlingCanvasSizingWakeLockAndFullscreenSupport() {
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
        assertContains(patchedHtml, """<div id="hitster-web-controls"""")
        assertContains(patchedHtml, """<button id="hitster-fullscreen-button"""")
        assertContains(patchedHtml, """<svg id="hitster-fullscreen-enter-icon"""")
        assertContains(patchedHtml, """<svg id="hitster-fullscreen-exit-icon"""")
        assertContains(patchedHtml, """border-radius: 18px;""")
        assertContains(patchedHtml, """#hitster-fullscreen-button[data-hitster-supported="false"]""")
        assertContains(patchedHtml, """<script id="hitster-mobile-runtime">""")
        assertContains(patchedHtml, "var fullscreenButton = document.getElementById(\"hitster-fullscreen-button\");")
        assertContains(patchedHtml, "canvas.addEventListener(type, handleInteractiveFocus")
        assertContains(patchedHtml, "document.addEventListener(type, handleWakeGesture, { passive: true, capture: true });")
        assertContains(patchedHtml, "if (supportsTouchBridge())")
        assertContains(patchedHtml, "function isIosBrowser() {")
        assertContains(patchedHtml, "function isStandaloneMode() {")
        assertContains(patchedHtml, "function eventTargetsShellControls(target) {")
        assertContains(patchedHtml, "document.addEventListener(\"touchstart\", function(event)")
        assertContains(patchedHtml, "if (eventTargetsShellControls(event.target)) {")
        assertContains(patchedHtml, "dispatchSyntheticMouse(\"mousedown\", touch);")
        assertContains(patchedHtml, "window.visualViewport.addEventListener(\"resize\", scheduleViewportSync")
        assertContains(patchedHtml, "var pixelRatio = window.devicePixelRatio || 1;")
        assertContains(patchedHtml, "var canvasContainer = canvas.parentElement || canvas.parentNode;")
        assertContains(patchedHtml, "canvas.style.width = Math.max(0, targetCssWidth) + \"px\";")
        assertContains(patchedHtml, "canvas.style.height = Math.max(0, targetCssHeight) + \"px\";")
        assertContains(patchedHtml, "if (canvas.width !== backingWidth) {")
        assertContains(patchedHtml, "if (canvas.height !== backingHeight) {")
        assertContains(patchedHtml, "navigator.wakeLock.request(\"screen\")")
        assertContains(patchedHtml, "wakeSource.src = \"data:video/mp4;base64,")
        assertContains(patchedHtml, "wakeFallbackVideo.setAttribute(\"webkit-playsinline\", \"\")")
        assertContains(patchedHtml, "wakeFallbackVideo.appendChild(wakeSource);")
        assertContains(patchedHtml, "wakeFallbackVideo.load();")
        assertContains(patchedHtml, "wakeFallbackVideo.addEventListener(\"loadedmetadata\"")
        assertContains(patchedHtml, "wakeFallbackVideo.currentTime > 0.5")
        assertContains(patchedHtml, "window.location.href = window.location.href.split(\"#\")[0];")
        assertContains(patchedHtml, "var shouldKeepScreenAwake = true;")
        assertContains(patchedHtml, "function shouldIgnoreFullscreenToggle(eventType) {")
        assertContains(patchedHtml, "return (navigator.maxTouchPoints || 0) > 0 || \"ontouchstart\" in window;")
        assertContains(patchedHtml, "function supportsFullscreen() {")
        assertContains(patchedHtml, "target.requestFullscreen({ navigationUI: \"hide\" })")
        assertContains(patchedHtml, "document.exitFullscreen")
        assertContains(patchedHtml, "fullscreenButton.dataset.hitsterSupported = supported ? \"true\" : \"false\";")
        assertContains(patchedHtml, "fullscreenButton.setAttribute(\"title\", fullscreenActive ? \"Minimize game\" : \"Fullscreen game\");")
        assertContains(patchedHtml, "[\"touchend\", \"pointerup\", \"mouseup\", \"click\"].forEach(function(type) {")
        assertContains(patchedHtml, "fullscreenButton.addEventListener(type, function(event) {")
        assertContains(patchedHtml, "if (shouldIgnoreFullscreenToggle(event.type)) {")
        assertContains(patchedHtml, "window.alert(\"iPhone Safari does not support real page fullscreen in a browser tab. Use Share → Add to Home Screen to open the game fullscreen.\");")
        assertContains(patchedHtml, "\"fullscreenchange\", \"webkitfullscreenchange\", \"msfullscreenchange\"")
        assertContains(patchedHtml, "window.setInterval(function() {")
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
