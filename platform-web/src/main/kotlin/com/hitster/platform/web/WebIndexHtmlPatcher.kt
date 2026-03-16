package com.hitster.platform.web

internal object WebIndexHtmlPatcher {
    private const val viewportMeta = """<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">"""

    private val mobileShellStyle = """
        <style>
            html, body {
              width: 100%;
              height: 100%;
              overscroll-behavior: none;
            }
            body {
              display: flex;
              justify-content: center;
              align-items: center;
              touch-action: none;
              -webkit-user-select: none;
              user-select: none;
            }
            body > div {
              width: 100%;
              height: 100%;
            }
            #progress {
              pointer-events: none;
            }
            #canvas {
              width: 100vw !important;
              height: 100vh !important;
              display: block;
              touch-action: none;
              -webkit-touch-callout: none;
              user-select: none;
              outline: none;
            }
        </style>
    """.trimIndent()

    private val mobileTouchScript = """
        <script>
            (function() {
              var canvas = document.getElementById("canvas");
              if (!canvas) {
                return;
              }
              canvas.style.touchAction = "none";
              ["touchstart", "touchmove", "touchend", "touchcancel"].forEach(function(type) {
                canvas.addEventListener(type, function(event) {
                  event.preventDefault();
                }, { passive: false });
              });
              ["gesturestart", "gesturechange", "gestureend"].forEach(function(type) {
                document.addEventListener(type, function(event) {
                  event.preventDefault();
                }, { passive: false });
              });
            })();
        </script>
    """.trimIndent()

    fun patch(html: String): String {
        var patched = html
        if (!patched.contains(viewportMeta)) {
            patched = patched.replace("</head>", "    $viewportMeta\n</head>")
        }
        if (!patched.contains("#canvas {\n              width: 100vw !important;")) {
            patched = patched.replace("</head>", "$mobileShellStyle\n</head>")
        }
        if (!patched.contains("canvas.style.touchAction = \"none\";")) {
            patched = patched.replace("</body>", "$mobileTouchScript\n</body>")
        }
        return patched
    }
}
