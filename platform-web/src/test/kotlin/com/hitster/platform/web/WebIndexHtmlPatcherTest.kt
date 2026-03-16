package com.hitster.platform.web

import kotlin.test.Test
import kotlin.test.assertContains

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
        assertContains(patchedHtml, "window.visualViewport.addEventListener(\"resize\", scheduleViewportSync")
        assertContains(patchedHtml, "navigator.wakeLock.request(\"screen\")")
        assertContains(patchedHtml, "wakeFallbackVideo.src = \"data:video/mp4;base64,")
        assertContains(patchedHtml, "if (event.defaultPrevented)")
        assertContains(patchedHtml, "dispatchSyntheticMouse(\"mousedown\", touch);")
        assertContains(patchedHtml, "wakeFallbackVideo.setAttribute(\"webkit-playsinline\", \"\")")
        assertContains(patchedHtml, "wakeFallbackVideo.addEventListener(\"loadedmetadata\"")
        assertContains(patchedHtml, "wakeFallbackVideo.currentTime > 0.5")
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

        kotlin.test.assertEquals(patchedOnce, patchedTwice)
    }
}
