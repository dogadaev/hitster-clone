package com.hitster.platform.web.server

/**
 * Patches the generated TeaVM shell so mobile browsers get the viewport, touch, wake, and safe-area behavior the game expects.
 */

internal object WebIndexHtmlPatcher {
    private const val viewportMeta = """<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover, interactive-widget=resizes-content">"""
    private const val mobileWebAppMeta = """
        <meta name="apple-mobile-web-app-capable" content="yes">
        <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
        <meta name="apple-mobile-web-app-title" content="Hitster Clone">
        <meta name="mobile-web-app-capable" content="yes">
    """
    private val noSleepLibrarySource = loadResourceText("nosleep.min.js")

    private val mobileShellStyle = """
        <style id="hitster-mobile-shell">
            :root {
              --hitster-visible-width: 100vw;
              --hitster-visible-height: 100vh;
              --hitster-safe-top: env(safe-area-inset-top, 0px);
              --hitster-safe-right: env(safe-area-inset-right, 0px);
              --hitster-safe-bottom: env(safe-area-inset-bottom, 0px);
              --hitster-safe-left: env(safe-area-inset-left, 0px);
            }
            html, body {
              margin: 0;
              padding: 0;
              width: 100%;
              height: 100%;
              overflow: hidden;
              overscroll-behavior: none;
              background: #000;
              touch-action: none;
              -webkit-text-size-adjust: 100%;
            }
            body {
              position: fixed;
              inset: 0;
              width: var(--hitster-visible-width);
              height: var(--hitster-visible-height);
              display: block;
              touch-action: none;
              -webkit-touch-callout: none;
              -webkit-tap-highlight-color: transparent;
              -webkit-user-select: none;
              user-select: none;
            }
            body > div {
              position: relative;
              box-sizing: border-box;
              width: 100%;
              height: 100%;
              padding: var(--hitster-safe-top) var(--hitster-safe-right) var(--hitster-safe-bottom) var(--hitster-safe-left);
            }
            #progress {
              pointer-events: none;
            }
            #canvas {
              width: 100% !important;
              height: 100% !important;
              display: block;
              touch-action: none;
              -webkit-touch-callout: none;
              -webkit-tap-highlight-color: transparent;
              user-select: none;
              outline: none;
            }
            #hitster-wake-debug {
              position: fixed;
              top: calc(var(--hitster-safe-top) + 12px);
              left: calc(var(--hitster-safe-left) + 12px);
              z-index: 2147483646;
              min-width: 188px;
              max-width: min(52vw, 280px);
              padding: 10px 12px;
              border-radius: 12px;
              background: rgba(7, 11, 22, 0.78);
              border: 1px solid rgba(255, 255, 255, 0.12);
              box-shadow: 0 10px 24px rgba(0, 0, 0, 0.26);
              color: rgba(240, 245, 255, 0.96);
              font: 600 11px/1.35 ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
              white-space: pre-wrap;
              pointer-events: none;
              backdrop-filter: blur(12px);
              -webkit-backdrop-filter: blur(12px);
            }
        </style>
    """.trimIndent()

    private val mobileRuntimeScript = """
        <script id="hitster-mobile-runtime">
            (function() {
              var root = document.documentElement;
              var canvas = document.getElementById("canvas");
              var wakeDebugEnabled = /(?:\?|&)wakeDebug=1(?:&|$)/.test(window.location.search || "");
              var wakeDebug = null;
              if (!canvas) {
                return;
              }
              if (wakeDebugEnabled) {
                wakeDebug = document.createElement("div");
                wakeDebug.id = "hitster-wake-debug";
                wakeDebug.setAttribute("aria-live", "polite");
                document.body.appendChild(wakeDebug);
              }
              canvas.setAttribute("tabindex", "0");
              canvas.style.touchAction = "none";
              var noSleep = typeof NoSleep === "function" ? new NoSleep() : null;
              var wakeActivationArmed = false;
              var wakeState = {
                enabled: false,
                attempts: 0,
                media: "managed-by-nosleep",
                mediaDetail: "readme-flow",
                lastError: "none",
                lastGesture: "never",
                lastEnable: "never",
                lastEvent: "none"
              };
              var viewportSyncFrame = 0;
              var lastViewportSignature = "";
              var fallbackTouchId = null;

              function wakeNowLabel() {
                try {
                  return new Date().toLocaleTimeString();
                } catch (error) {
                  return String(Date.now());
                }
              }

              function formatWakeError(error) {
                if (!error) {
                  return "none";
                }
                if (typeof error === "string") {
                  return error;
                }
                var name = error.name || "Error";
                var message = error.message || String(error);
                return name + ": " + message;
              }

              function updateWakeDebug() {
                if (!wakeDebugEnabled || !wakeDebug) {
                  return;
                }
                wakeDebug.textContent = [
                  "wake nosleep " + (wakeState.enabled ? "on" : "off"),
                  "handlers " + (wakeActivationArmed ? "armed" : "off"),
                  "visible " + (!document.hidden ? "yes" : "no") + " secure " + (window.isSecureContext ? "yes" : "no"),
                  "pending no",
                  "media " + wakeState.media,
                  "detail " + wakeState.mediaDetail,
                  "event " + wakeState.lastEvent,
                  "gesture " + wakeState.lastGesture,
                  "enable " + wakeState.lastEnable,
                  "error " + wakeState.lastError
                ].join("\n");
              }

              function focusCanvas() {
                try {
                  canvas.focus({ preventScroll: true });
                } catch (focusError) {
                  canvas.focus();
                }
              }

              function scheduleViewportSync() {
                if (viewportSyncFrame !== 0) {
                  return;
                }
                viewportSyncFrame = window.requestAnimationFrame(function() {
                  viewportSyncFrame = 0;
                  syncVisibleViewport();
                });
              }

              function effectivePixelRatio() {
                var pixelRatio = window.devicePixelRatio || 1;
                if (!supportsTouchBridge()) {
                  return pixelRatio;
                }
                if (isIosBrowser()) {
                  return Math.min(pixelRatio, 1);
                }
                return Math.min(pixelRatio, 1.5);
              }

              function canvasContainsTouch(touch) {
                if (!touch) {
                  return false;
                }
                var rect = canvas.getBoundingClientRect();
                return touch.clientX >= rect.left &&
                  touch.clientX <= rect.right &&
                  touch.clientY >= rect.top &&
                  touch.clientY <= rect.bottom;
              }

              function findTouchById(touchList, touchId) {
                if (touchId === null || !touchList) {
                  return null;
                }
                for (var index = 0; index < touchList.length; index += 1) {
                  var touch = touchList[index];
                  if (touch.identifier === touchId) {
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
                  buttons: type === "mouseup" ? 0 : 1,
                }));
              }

              function supportsTouchBridge() {
                return (navigator.maxTouchPoints || 0) > 0 || "ontouchstart" in window;
              }

              function isIosBrowser() {
                return /iP(ad|hone|od)/i.test(navigator.userAgent || "");
              }

              function readRootPixels(variableName) {
                var value = window.getComputedStyle(root).getPropertyValue(variableName).trim();
                if (!value) {
                  return 0;
                }
                var parsed = parseFloat(value);
                return Number.isFinite(parsed) ? parsed : 0;
              }

              function syncVisibleViewport() {
                var viewport = window.visualViewport;
                var visibleWidth = viewport ? viewport.width : window.innerWidth;
                var visibleHeight = viewport ? viewport.height : window.innerHeight;
                var pixelRatio = effectivePixelRatio();
                var canvasContainer = canvas.parentElement || canvas.parentNode;
                var targetCssWidth = canvasContainer && canvasContainer.clientWidth ? canvasContainer.clientWidth : visibleWidth;
                var targetCssHeight = canvasContainer && canvasContainer.clientHeight ? canvasContainer.clientHeight : visibleHeight;
                var signature = [
                  Math.round(visibleWidth * 100),
                  Math.round(visibleHeight * 100),
                  Math.round(targetCssWidth * 100),
                  Math.round(targetCssHeight * 100),
                  Math.round(pixelRatio * 100),
                  Math.round(readRootPixels("--hitster-safe-top") * 100),
                  Math.round(readRootPixels("--hitster-safe-right") * 100),
                  Math.round(readRootPixels("--hitster-safe-bottom") * 100),
                  Math.round(readRootPixels("--hitster-safe-left") * 100),
                ].join(":");
                if (signature === lastViewportSignature) {
                  return;
                }
                lastViewportSignature = signature;
                root.style.setProperty("--hitster-visible-width", Math.max(0, visibleWidth) + "px");
                root.style.setProperty("--hitster-visible-height", Math.max(0, visibleHeight) + "px");
                targetCssWidth = canvasContainer && canvasContainer.clientWidth ? canvasContainer.clientWidth : visibleWidth;
                targetCssHeight = canvasContainer && canvasContainer.clientHeight ? canvasContainer.clientHeight : visibleHeight;
                canvas.style.width = Math.max(0, targetCssWidth) + "px";
                canvas.style.height = Math.max(0, targetCssHeight) + "px";
                var backingWidth = Math.max(1, Math.round(targetCssWidth * pixelRatio));
                var backingHeight = Math.max(1, Math.round(targetCssHeight * pixelRatio));
                if (canvas.width !== backingWidth) {
                  canvas.width = backingWidth;
                }
                if (canvas.height !== backingHeight) {
                  canvas.height = backingHeight;
                }
                window.scrollTo(0, 0);
                window.setTimeout(function() {
                  window.dispatchEvent(new Event("resize"));
                }, 0);
              }

              function detachWakeActivation() {
                if (!wakeActivationArmed) {
                  return;
                }
                wakeActivationArmed = false;
                document.removeEventListener("click", enableNoSleep, false);
                document.removeEventListener("touchend", enableNoSleep, true);
                updateWakeDebug();
              }

              function armWakeActivation() {
                if (wakeActivationArmed || !noSleep || noSleep.isEnabled) {
                  return;
                }
                wakeActivationArmed = true;
                document.addEventListener("click", enableNoSleep, false);
                document.addEventListener("touchend", enableNoSleep, true);
                updateWakeDebug();
              }

              function enableNoSleep(event) {
                if (!noSleep || noSleep.isEnabled || document.hidden) {
                  return;
                }
                detachWakeActivation();
                wakeState.attempts += 1;
                wakeState.lastEvent = event && event.type ? event.type : "manual";
                wakeState.lastGesture = wakeNowLabel();
                updateWakeDebug();
                Promise.resolve(noSleep.enable()).then(function() {
                  wakeState.enabled = !!noSleep.isEnabled;
                  wakeState.lastEnable = wakeNowLabel();
                  wakeState.lastError = "none";
                  updateWakeDebug();
                }).catch(function(error) {
                  wakeState.enabled = !!(noSleep && noSleep.isEnabled);
                  wakeState.lastError = formatWakeError(error);
                  armWakeActivation();
                  updateWakeDebug();
                });
              }

              function handleInteractiveFocus() {
                focusCanvas();
              }

              ["touchstart", "pointerdown", "mousedown"].forEach(function(type) {
                canvas.addEventListener(type, handleInteractiveFocus, { passive: true });
              });

              ["touchend", "pointerup", "mouseup", "click"].forEach(function(type) {
                canvas.addEventListener(type, handleInteractiveFocus, { passive: true });
              });

              if (supportsTouchBridge()) {
                document.addEventListener("touchstart", function(event) {
                  var touch = event.changedTouches && event.changedTouches[0];
                  if (!canvasContainsTouch(touch)) {
                    return;
                  }
                  fallbackTouchId = touch.identifier;
                  handleInteractiveFocus();
                  dispatchSyntheticMouse("mousedown", touch);
                  event.preventDefault();
                  event.stopPropagation();
                }, { passive: false, capture: true });

                document.addEventListener("touchmove", function(event) {
                  var touch = findTouchById(event.changedTouches, fallbackTouchId);
                  if (!touch) {
                    return;
                  }
                  dispatchSyntheticMouse("mousemove", touch);
                  event.preventDefault();
                  event.stopPropagation();
                }, { passive: false, capture: true });

                ["touchend", "touchcancel"].forEach(function(type) {
                  document.addEventListener(type, function(event) {
                    var touch = findTouchById(event.changedTouches, fallbackTouchId);
                    if (!touch) {
                      return;
                    }
                    dispatchSyntheticMouse("mouseup", touch);
                    fallbackTouchId = null;
                    event.preventDefault();
                    event.stopPropagation();
                  }, { passive: false, capture: true });
                });
              }

              document.addEventListener("visibilitychange", function() {
                scheduleViewportSync();
              });

              window.addEventListener("resize", scheduleViewportSync, { passive: true });
              window.addEventListener("orientationchange", function() {
                scheduleViewportSync();
                window.setTimeout(scheduleViewportSync, 250);
              }, { passive: true });
              window.addEventListener("pageshow", function() {
                scheduleViewportSync();
              }, { passive: true });
              window.addEventListener("focus", function() {
                scheduleViewportSync();
              }, { passive: true });
              if (window.visualViewport) {
                window.visualViewport.addEventListener("resize", scheduleViewportSync, { passive: true });
                window.visualViewport.addEventListener("scroll", scheduleViewportSync, { passive: true });
              }
              ["gesturestart", "gesturechange", "gestureend"].forEach(function(type) {
                document.addEventListener(type, function(event) {
                  event.preventDefault();
                }, { passive: false });
              });

              armWakeActivation();
              updateWakeDebug();
              scheduleViewportSync();
              window.setTimeout(scheduleViewportSync, 250);
              window.setTimeout(scheduleViewportSync, 1000);
            })();
        </script>
    """.trimIndent()

    private val noSleepLibraryScript = """
        <script id="hitster-nosleep-lib">
        $noSleepLibrarySource
        </script>
    """.trimIndent()

    fun patch(html: String, cacheBustToken: String? = null): String {
        var patched = html
        cacheBustToken?.let { token ->
            patched = patched
                .replace("src=\"app.js\"", "src=\"app.js?v=$token\"")
                .replace("src='app.js'", "src='app.js?v=$token'")
        }
        if (!patched.contains(viewportMeta)) {
            patched = patched.replace("</head>", "    $viewportMeta\n</head>")
        }
        if (!patched.contains("""<meta name="apple-mobile-web-app-capable" content="yes">""")) {
            patched = patched.replace("</head>", "    $mobileWebAppMeta\n</head>")
        }
        if (!patched.contains("""<style id="hitster-mobile-shell">""")) {
            patched = patched.replace("</head>", "$mobileShellStyle\n</head>")
        }
        if (!patched.contains("""<script id="hitster-nosleep-lib">""")) {
            patched = patched.replace("</body>", "$noSleepLibraryScript\n</body>")
        }
        if (!patched.contains("""<script id="hitster-mobile-runtime">""")) {
            patched = patched.replace("</body>", "$mobileRuntimeScript\n</body>")
        }
        return patched
    }
}

private fun loadResourceText(resourceName: String): String {
    return checkNotNull(WebIndexHtmlPatcher::class.java.getResourceAsStream("/$resourceName")) {
        "Missing web shell resource: $resourceName"
    }.bufferedReader().use { it.readText().trim() }
}
