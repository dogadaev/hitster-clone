package com.hitster.platform.web

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
        assertContains(patchedHtml, """<style id="hitster-mobile-shell">""")
        assertContains(patchedHtml, "--hitster-visible-height")
        assertContains(patchedHtml, "width: 100% !important;")
        assertContains(patchedHtml, """<script id="hitster-mobile-runtime">""")
        assertContains(patchedHtml, "canvas.addEventListener(type, handleInteractiveFocus")
        assertContains(patchedHtml, "if (supportsTouchBridge())")
        assertContains(patchedHtml, "document.addEventListener(\"touchstart\", function(event)")
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
        assertContains(patchedHtml, "return (navigator.maxTouchPoints || 0) > 0 || \"ontouchstart\" in window;")
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
