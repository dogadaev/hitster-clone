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
              -webkit-touch-callout: none;
              -webkit-tap-highlight-color: transparent;
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
              -webkit-tap-highlight-color: transparent;
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
              canvas.setAttribute("tabindex", "0");
              canvas.style.touchAction = "none";
              var activeTouchId = null;
              function canvasRect() {
                return canvas.getBoundingClientRect();
              }
              function isInsideCanvas(touch) {
                if (!touch) {
                  return false;
                }
                var rect = canvasRect();
                return touch.clientX >= rect.left &&
                  touch.clientX <= rect.right &&
                  touch.clientY >= rect.top &&
                  touch.clientY <= rect.bottom;
              }
              function findTrackedTouch(touchList) {
                if (activeTouchId === null || !touchList) {
                  return null;
                }
                for (var index = 0; index < touchList.length; index += 1) {
                  var touch = touchList[index];
                  if (touch.identifier === activeTouchId) {
                    return touch;
                  }
                }
                return null;
              }
              function dispatchSyntheticMouse(type, touch) {
                if (!touch) {
                  return;
                }
                canvas.dispatchEvent(new MouseEvent(type, {
                  bubbles: true,
                  cancelable: true,
                  composed: true,
                  view: window,
                  clientX: touch.clientX,
                  clientY: touch.clientY,
                  screenX: touch.screenX,
                  screenY: touch.screenY,
                  button: 0,
                  buttons: type === "mouseup" ? 0 : 1
                }));
              }
              document.addEventListener("touchstart", function(event) {
                var touch = event.changedTouches && event.changedTouches[0];
                if (!isInsideCanvas(touch)) {
                  return;
                }
                activeTouchId = touch.identifier;
                canvas.focus();
                dispatchSyntheticMouse("mousedown", touch);
                event.preventDefault();
                event.stopImmediatePropagation();
              }, { passive: false, capture: true });
              document.addEventListener("touchmove", function(event) {
                var touch = findTrackedTouch(event.changedTouches);
                if (!touch) {
                  return;
                }
                dispatchSyntheticMouse("mousemove", touch);
                event.preventDefault();
                event.stopImmediatePropagation();
              }, { passive: false, capture: true });
              ["touchend", "touchcancel"].forEach(function(type) {
                document.addEventListener(type, function(event) {
                  var touch = findTrackedTouch(event.changedTouches);
                  if (!touch) {
                    return;
                  }
                  dispatchSyntheticMouse("mouseup", touch);
                  activeTouchId = null;
                  event.preventDefault();
                  event.stopImmediatePropagation();
                }, { passive: false, capture: true });
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
