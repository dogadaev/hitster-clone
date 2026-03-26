package com.hitster.platform.web.server

/**
 * Patches the generated TeaVM shell so mobile browsers get the viewport, touch, wake, and safe-area behavior the game expects.
 */

internal object WebIndexHtmlPatcher {
    private const val viewportMeta = """<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover, interactive-widget=resizes-content">"""
    private const val fullscreenControls = """
        <div id="hitster-web-controls" aria-hidden="false">
            <button id="hitster-fullscreen-button" type="button" aria-label="Toggle fullscreen">
                <svg id="hitster-fullscreen-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                    <path d="M8 3H4v4" />
                    <path d="M16 3h4v4" />
                    <path d="M20 16v4h-4" />
                    <path d="M4 16v4h4" />
                </svg>
            </button>
        </div>
    """

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
            #hitster-web-controls {
              position: fixed;
              top: calc(var(--hitster-safe-top) + 12px);
              right: calc(var(--hitster-safe-right) + 12px);
              z-index: 2147483647;
              display: flex;
              align-items: center;
              justify-content: flex-end;
              pointer-events: none;
            }
            #hitster-fullscreen-button {
              pointer-events: auto;
              appearance: none;
              border: 1px solid rgba(255, 255, 255, 0.22);
              border-radius: 18px;
              width: 56px;
              height: 56px;
              padding: 0;
              background:
                linear-gradient(180deg, rgba(28, 42, 76, 0.9) 0%, rgba(10, 18, 36, 0.94) 100%);
              color: rgba(255, 249, 235, 0.96);
              display: inline-flex;
              align-items: center;
              justify-content: center;
              box-shadow:
                0 14px 34px rgba(0, 0, 0, 0.42),
                inset 0 1px 0 rgba(255, 255, 255, 0.24);
              backdrop-filter: blur(16px);
              -webkit-backdrop-filter: blur(16px);
              touch-action: manipulation;
            }
            #hitster-fullscreen-icon {
              width: 24px;
              height: 24px;
              stroke: currentColor;
              stroke-width: 2.4;
              stroke-linecap: round;
              stroke-linejoin: round;
              fill: none;
            }
            #hitster-fullscreen-button:active {
              transform: translateY(1px) scale(0.99);
            }
            #hitster-fullscreen-button[data-hitster-active="true"] {
              background:
                linear-gradient(180deg, rgba(214, 167, 76, 0.96) 0%, rgba(149, 101, 18, 0.94) 100%);
              border-color: rgba(255, 231, 183, 0.44);
              color: rgba(26, 14, 2, 0.92);
              box-shadow:
                0 16px 36px rgba(0, 0, 0, 0.44),
                0 0 20px rgba(236, 191, 96, 0.32),
                inset 0 1px 0 rgba(255, 247, 221, 0.42);
            }
            #hitster-fullscreen-button[data-hitster-active="true"] #hitster-fullscreen-icon {
              transform: scale(1.02);
            }
            #hitster-fullscreen-button[data-hitster-supported="false"] {
              opacity: 0.74;
            }
        </style>
    """.trimIndent()

    private val mobileRuntimeScript = """
        <script id="hitster-mobile-runtime">
            (function() {
              var root = document.documentElement;
              var canvas = document.getElementById("canvas");
              var fullscreenButton = document.getElementById("hitster-fullscreen-button");
              if (!canvas) {
                return;
              }
              canvas.setAttribute("tabindex", "0");
              canvas.style.touchAction = "none";
              var wakeLockSentinel = null;
              var wakeFallbackVideo = null;
              var shouldKeepScreenAwake = true;
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

              function eventTargetsShellControls(target) {
                return !!(target && target.closest && target.closest("#hitster-web-controls"));
              }

              function supportsTouchBridge() {
                return (navigator.maxTouchPoints || 0) > 0 || "ontouchstart" in window;
              }

              function readRootPixels(variableName) {
                var value = window.getComputedStyle(root).getPropertyValue(variableName).trim();
                if (!value) {
                  return 0;
                }
                var parsed = parseFloat(value);
                return Number.isFinite(parsed) ? parsed : 0;
              }

              function currentFullscreenElement() {
                return document.fullscreenElement ||
                  document.webkitFullscreenElement ||
                  document.msFullscreenElement ||
                  null;
              }

              function isFullscreenActive() {
                return currentFullscreenElement() !== null;
              }

              function fullscreenRequestCandidates() {
                var container = canvas.parentElement || canvas.parentNode;
                return [container, document.body, root, canvas].filter(function(target) {
                  return !!target;
                });
              }

              function supportsFullscreen() {
                return fullscreenRequestCandidates().some(function(target) {
                  return !!(target.requestFullscreen || target.webkitRequestFullscreen || target.msRequestFullscreen);
                }) || !!(document.exitFullscreen || document.webkitExitFullscreen || document.msExitFullscreen);
              }

              function setFullscreenButtonState() {
                if (!fullscreenButton) {
                  return;
                }
                var supported = supportsFullscreen();
                fullscreenButton.dataset.hitsterSupported = supported ? "true" : "false";
                var fullscreenActive = isFullscreenActive();
                fullscreenButton.setAttribute("title", fullscreenActive ? "Exit fullscreen" : "Enter fullscreen");
                fullscreenButton.setAttribute("aria-pressed", fullscreenActive ? "true" : "false");
                fullscreenButton.dataset.hitsterActive = fullscreenActive ? "true" : "false";
              }

              function requestElementFullscreen(target) {
                if (!target) {
                  return Promise.reject(new Error("No fullscreen target."));
                }
                try {
                  if (target.requestFullscreen) {
                    try {
                      var withOptions = target.requestFullscreen({ navigationUI: "hide" });
                      return Promise.resolve(withOptions).catch(function() {
                        return target.requestFullscreen();
                      });
                    } catch (navigationUiError) {
                      return Promise.resolve(target.requestFullscreen());
                    }
                  }
                  if (target.webkitRequestFullscreen) {
                    return Promise.resolve(target.webkitRequestFullscreen());
                  }
                  if (target.msRequestFullscreen) {
                    return Promise.resolve(target.msRequestFullscreen());
                  }
                } catch (error) {
                  return Promise.reject(error);
                }
                return Promise.reject(new Error("Fullscreen unsupported."));
              }

              function requestFullscreen() {
                var candidates = fullscreenRequestCandidates();
                var index = 0;
                function attemptNext() {
                  if (index >= candidates.length) {
                    return Promise.reject(new Error("Fullscreen unsupported."));
                  }
                  var candidate = candidates[index];
                  index += 1;
                  return requestElementFullscreen(candidate).catch(function() {
                    return attemptNext();
                  });
                }
                return attemptNext();
              }

              function exitFullscreen() {
                try {
                  if (document.exitFullscreen) {
                    return Promise.resolve(document.exitFullscreen());
                  }
                  if (document.webkitExitFullscreen) {
                    return Promise.resolve(document.webkitExitFullscreen());
                  }
                  if (document.msExitFullscreen) {
                    return Promise.resolve(document.msExitFullscreen());
                  }
                } catch (error) {
                  return Promise.reject(error);
                }
                return Promise.resolve();
              }

              function syncVisibleViewport() {
                var viewport = window.visualViewport;
                var visibleWidth = viewport ? viewport.width : window.innerWidth;
                var visibleHeight = viewport ? viewport.height : window.innerHeight;
                var pixelRatio = window.devicePixelRatio || 1;
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
                if (typeof wakeFallbackVideo.disableRemotePlayback !== "undefined") {
                  wakeFallbackVideo.disableRemotePlayback = true;
                }
                var wakeSource = document.createElement("source");
                wakeSource.src = "${wakeLockFallbackVideoUri}";
                wakeSource.type = "video/mp4";
                wakeFallbackVideo.appendChild(wakeSource);
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
                wakeFallbackVideo.load();
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

              function handleWakeGesture() {
                requestWakeLock();
                scheduleViewportSync();
              }

              function handleInteractiveFocus() {
                focusCanvas();
                requestWakeLock();
                scheduleViewportSync();
              }

              ["touchstart", "pointerdown", "mousedown"].forEach(function(type) {
                canvas.addEventListener(type, handleInteractiveFocus, { passive: true });
                document.addEventListener(type, handleWakeGesture, { passive: true, capture: true });
              });

              if (supportsTouchBridge()) {
                document.addEventListener("touchstart", function(event) {
                  if (eventTargetsShellControls(event.target)) {
                    return;
                  }
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
                if (document.hidden) {
                  releaseWakeLock();
                  return;
                }
                scheduleViewportSync();
                if (shouldKeepScreenAwake) {
                  requestWakeLock();
                }
              });

              if (fullscreenButton) {
                fullscreenButton.addEventListener("click", function(event) {
                  event.preventDefault();
                  event.stopPropagation();
                  handleInteractiveFocus();
                  if (!supportsFullscreen()) {
                    setFullscreenButtonState();
                    return;
                  }
                  var togglePromise = isFullscreenActive() ? exitFullscreen() : requestFullscreen();
                  Promise.resolve(togglePromise).then(function() {
                    setFullscreenButtonState();
                    if (shouldKeepScreenAwake) {
                      requestWakeLock();
                    }
                    scheduleViewportSync();
                  }, function() {
                    setFullscreenButtonState();
                    if (shouldKeepScreenAwake) {
                      requestWakeLock();
                    }
                    scheduleViewportSync();
                  });
                });
              }

              ["fullscreenchange", "webkitfullscreenchange", "msfullscreenchange"].forEach(function(type) {
                document.addEventListener(type, function() {
                  setFullscreenButtonState();
                  if (shouldKeepScreenAwake) {
                    requestWakeLock();
                  }
                  scheduleViewportSync();
                });
              });

              window.addEventListener("resize", scheduleViewportSync, { passive: true });
              window.addEventListener("orientationchange", function() {
                scheduleViewportSync();
                window.setTimeout(scheduleViewportSync, 250);
              }, { passive: true });
              window.addEventListener("pageshow", function() {
                scheduleViewportSync();
                if (shouldKeepScreenAwake) {
                  requestWakeLock();
                }
              }, { passive: true });
              window.addEventListener("pagehide", releaseWakeLock, { passive: true });
              window.addEventListener("focus", function() {
                scheduleViewportSync();
                if (shouldKeepScreenAwake) {
                  requestWakeLock();
                }
              }, { passive: true });
              window.setInterval(function() {
                if (shouldKeepScreenAwake && !document.hidden) {
                  requestWakeLock();
                }
              }, 15000);
              if (window.visualViewport) {
                window.visualViewport.addEventListener("resize", scheduleViewportSync, { passive: true });
                window.visualViewport.addEventListener("scroll", scheduleViewportSync, { passive: true });
              }
              ["gesturestart", "gesturechange", "gestureend"].forEach(function(type) {
                document.addEventListener(type, function(event) {
                  event.preventDefault();
                }, { passive: false });
              });

              setFullscreenButtonState();
              scheduleViewportSync();
              requestWakeLock();
              window.setTimeout(scheduleViewportSync, 250);
              window.setTimeout(scheduleViewportSync, 1000);
            })();
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
        if (!patched.contains("""<style id="hitster-mobile-shell">""")) {
            patched = patched.replace("</head>", "$mobileShellStyle\n</head>")
        }
        if (!patched.contains("""<div id="hitster-web-controls"""")) {
            patched = patched.replace("</body>", "$fullscreenControls\n</body>")
        }
        if (!patched.contains("""<script id="hitster-mobile-runtime">""")) {
            patched = patched.replace("</body>", "$mobileRuntimeScript\n</body>")
        }
        return patched
    }
}
