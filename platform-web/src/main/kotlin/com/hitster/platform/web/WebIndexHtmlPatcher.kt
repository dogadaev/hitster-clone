package com.hitster.platform.web

internal object WebIndexHtmlPatcher {
    private const val viewportMeta = """<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover, interactive-widget=resizes-content">"""

    private val wakeLockFallbackVideoUri by lazy {
        checkNotNull(WebIndexHtmlPatcher::class.java.getResourceAsStream("/wake-lock-fallback-video-uri.txt")) {
            "Missing wake-lock fallback video resource."
        }.bufferedReader().use { resource -> resource.readText().trim() }
    }

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
        </style>
    """.trimIndent()

    private val mobileRuntimeScript = """
        <script id="hitster-mobile-runtime">
            (function() {
              var root = document.documentElement;
              var canvas = document.getElementById("canvas");
              if (!canvas) {
                return;
              }
              canvas.setAttribute("tabindex", "0");
              canvas.style.touchAction = "none";
              var wakeLockSentinel = null;
              var wakeFallbackVideo = null;
              var shouldKeepScreenAwake = false;
              var viewportSyncFrame = 0;
              var lastViewportSignature = "";
              var fallbackTouchId = null;

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
                var signature = [
                  Math.round(visibleWidth * 100),
                  Math.round(visibleHeight * 100),
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
                window.scrollTo(0, 0);
                window.setTimeout(function() {
                  window.dispatchEvent(new Event("resize"));
                }, 0);
              }

              function ensureWakeFallbackVideo() {
                if (wakeFallbackVideo) {
                  return wakeFallbackVideo;
                }
                wakeFallbackVideo = document.createElement("video");
                wakeFallbackVideo.setAttribute("title", "No Sleep");
                wakeFallbackVideo.setAttribute("playsinline", "");
                wakeFallbackVideo.setAttribute("webkit-playsinline", "");
                wakeFallbackVideo.setAttribute("muted", "");
                wakeFallbackVideo.setAttribute("disableRemotePlayback", "");
                wakeFallbackVideo.muted = true;
                wakeFallbackVideo.preload = "auto";
                wakeFallbackVideo.src = "${wakeLockFallbackVideoUri}";
                if (typeof wakeFallbackVideo.disableRemotePlayback !== "undefined") {
                  wakeFallbackVideo.disableRemotePlayback = true;
                }
                wakeFallbackVideo.addEventListener("loadedmetadata", function() {
                  if (wakeFallbackVideo.duration <= 1) {
                    wakeFallbackVideo.loop = true;
                    wakeFallbackVideo.setAttribute("loop", "");
                    return;
                  }
                  if (wakeFallbackVideo.dataset.hitsterLoopHack === "true") {
                    return;
                  }
                  wakeFallbackVideo.dataset.hitsterLoopHack = "true";
                  wakeFallbackVideo.addEventListener("timeupdate", function() {
                    if (wakeFallbackVideo.currentTime > 0.5) {
                      wakeFallbackVideo.currentTime = Math.random() * 0.5;
                    }
                  });
                });
                wakeFallbackVideo.style.position = "fixed";
                wakeFallbackVideo.style.right = "0";
                wakeFallbackVideo.style.bottom = "0";
                wakeFallbackVideo.style.width = "1px";
                wakeFallbackVideo.style.height = "1px";
                wakeFallbackVideo.style.opacity = "0.01";
                wakeFallbackVideo.style.pointerEvents = "none";
                wakeFallbackVideo.style.zIndex = "-1";
                document.body.appendChild(wakeFallbackVideo);
                return wakeFallbackVideo;
              }

              function releaseWakeLock() {
                if (wakeLockSentinel && wakeLockSentinel.release) {
                  wakeLockSentinel.release().catch(function() {});
                  wakeLockSentinel = null;
                }
                if (!wakeFallbackVideo) {
                  return;
                }
                wakeFallbackVideo.currentTime = 0;
                wakeFallbackVideo.pause();
              }

              function requestWakeLock() {
                shouldKeepScreenAwake = true;
                if (document.hidden) {
                  return;
                }
                if (navigator.wakeLock && window.isSecureContext) {
                  if (wakeLockSentinel) {
                    return;
                  }
                  navigator.wakeLock.request("screen").then(function(lock) {
                    wakeLockSentinel = lock;
                    wakeLockSentinel.addEventListener("release", function() {
                      wakeLockSentinel = null;
                      if (shouldKeepScreenAwake && !document.hidden) {
                        requestWakeLock();
                      }
                    });
                  }).catch(function() {});
                  return;
                }
                var wakeVideo = ensureWakeFallbackVideo();
                var playPromise = wakeVideo.play();
                if (playPromise && playPromise.catch) {
                  playPromise.catch(function() {});
                }
              }

              function handleInteractiveFocus() {
                focusCanvas();
                requestWakeLock();
                scheduleViewportSync();
              }

              ["touchstart", "pointerdown", "mousedown"].forEach(function(type) {
                canvas.addEventListener(type, handleInteractiveFocus, { passive: true });
              });

              canvas.addEventListener("touchstart", function(event) {
                if (event.defaultPrevented) {
                  fallbackTouchId = null;
                  return;
                }
                var touch = event.changedTouches && event.changedTouches[0];
                if (!canvasContainsTouch(touch)) {
                  return;
                }
                fallbackTouchId = touch.identifier;
                dispatchSyntheticMouse("mousedown", touch);
              }, { passive: false, capture: false });

              canvas.addEventListener("touchmove", function(event) {
                if (event.defaultPrevented) {
                  return;
                }
                var touch = findTouchById(event.changedTouches, fallbackTouchId);
                if (!touch) {
                  return;
                }
                dispatchSyntheticMouse("mousemove", touch);
                event.preventDefault();
              }, { passive: false, capture: false });

              ["touchend", "touchcancel"].forEach(function(type) {
                canvas.addEventListener(type, function(event) {
                  if (event.defaultPrevented) {
                    fallbackTouchId = null;
                    return;
                  }
                  var touch = findTouchById(event.changedTouches, fallbackTouchId);
                  if (!touch) {
                    return;
                  }
                  dispatchSyntheticMouse("mouseup", touch);
                  fallbackTouchId = null;
                  event.preventDefault();
                }, { passive: false, capture: false });
              });

              document.addEventListener("visibilitychange", function() {
                if (document.hidden) {
                  releaseWakeLock();
                  return;
                }
                scheduleViewportSync();
                if (shouldKeepScreenAwake) {
                  requestWakeLock();
                }
              });

              window.addEventListener("resize", scheduleViewportSync, { passive: true });
              window.addEventListener("orientationchange", function() {
                scheduleViewportSync();
                window.setTimeout(scheduleViewportSync, 250);
              }, { passive: true });
              window.addEventListener("pageshow", scheduleViewportSync, { passive: true });
              window.addEventListener("pagehide", releaseWakeLock, { passive: true });
              window.addEventListener("focus", scheduleViewportSync, { passive: true });
              if (window.visualViewport) {
                window.visualViewport.addEventListener("resize", scheduleViewportSync, { passive: true });
                window.visualViewport.addEventListener("scroll", scheduleViewportSync, { passive: true });
              }
              ["gesturestart", "gesturechange", "gestureend"].forEach(function(type) {
                document.addEventListener(type, function(event) {
                  event.preventDefault();
                }, { passive: false });
              });

              scheduleViewportSync();
              window.setTimeout(scheduleViewportSync, 250);
              window.setTimeout(scheduleViewportSync, 1000);
            })();
        </script>
    """.trimIndent()

    fun patch(html: String): String {
        var patched = html
        if (!patched.contains(viewportMeta)) {
            patched = patched.replace("</head>", "    $viewportMeta\n</head>")
        }
        if (!patched.contains("""<style id="hitster-mobile-shell">""")) {
            patched = patched.replace("</head>", "$mobileShellStyle\n</head>")
        }
        if (!patched.contains("""<script id="hitster-mobile-runtime">""")) {
            patched = patched.replace("</body>", "$mobileRuntimeScript\n</body>")
        }
        return patched
    }
}
