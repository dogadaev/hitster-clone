package com.hitster.platform.web

import kotlin.test.Test
import kotlin.test.assertContains

class WebIndexHtmlPatcherTest {
    @Test
    fun patchInjectsMobileViewportTouchHandlingAndCanvasSizing() {
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

        assertContains(patchedHtml, """<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">""")
        assertContains(patchedHtml, "#canvas {")
        assertContains(patchedHtml, "width: 100vw !important;")
        assertContains(patchedHtml, "canvas.style.touchAction = \"none\";")
        assertContains(patchedHtml, "document.addEventListener(\"touchstart\"")
        assertContains(patchedHtml, "dispatchSyntheticMouse(\"mousedown\", touch);")
        assertContains(patchedHtml, "event.stopImmediatePropagation();")
    }
}
