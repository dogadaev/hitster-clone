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
    private const val fullscreenControls = """
        <div id="hitster-web-controls" aria-hidden="false">
            <button id="hitster-fullscreen-button" type="button" aria-label="Toggle fullscreen">
                <svg id="hitster-fullscreen-enter-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                    <path d="M9 3H4v5" />
                    <path d="M15 3h5v5" />
                    <path d="M20 15v5h-5" />
                    <path d="M4 15v5h5" />
                </svg>
                <svg id="hitster-fullscreen-exit-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                    <path d="M10 4v5H5" />
                    <path d="M14 4v5h5" />
                    <path d="M19 15h-5v5" />
                    <path d="M5 15h5v5" />
                </svg>
            </button>
        </div>
        <div id="hitster-wake-debug" aria-live="polite"></div>
    """

    private val wakeLockFallbackVideoUri by lazy {
        checkNotNull(WebIndexHtmlPatcher::class.java.getResourceAsStream("/wake-lock-fallback-video-uri.txt")) {
            "Missing wake-lock fallback video resource."
        }.bufferedReader().use { resource -> resource.readText().trim() }
    }
    private val wakeLockFallbackWebmUri by lazy {
        checkNotNull(WebIndexHtmlPatcher::class.java.getResourceAsStream("/wake-lock-fallback-video-webm-uri.txt")) {
            "Missing wake-lock fallback webm resource."
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
              bottom: calc(var(--hitster-safe-bottom) + 12px);
              right: calc(var(--hitster-safe-right) + 12px);
              z-index: 2147483647;
              display: flex;
              align-items: flex-end;
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
              -webkit-touch-callout: none;
              -webkit-tap-highlight-color: transparent;
            }
            #hitster-fullscreen-enter-icon,
            #hitster-fullscreen-exit-icon {
              width: 24px;
              height: 24px;
              stroke: currentColor;
              stroke-width: 2.4;
              stroke-linecap: round;
              stroke-linejoin: round;
              fill: none;
            }
            #hitster-fullscreen-exit-icon {
              display: none;
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
            #hitster-fullscreen-button[data-hitster-active="true"] #hitster-fullscreen-enter-icon {
              display: none;
            }
            #hitster-fullscreen-button[data-hitster-active="true"] #hitster-fullscreen-exit-icon {
              display: block;
            }
            #hitster-fullscreen-button[data-hitster-active="true"] #hitster-fullscreen-exit-icon {
              transform: scale(1.02);
            }
            #hitster-fullscreen-button[data-hitster-supported="false"] {
              opacity: 0.74;
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
              var fullscreenButton = document.getElementById("hitster-fullscreen-button");
              var wakeDebug = document.getElementById("hitster-wake-debug");
              if (!canvas) {
                return;
              }
              canvas.setAttribute("tabindex", "0");
              canvas.style.touchAction = "none";
              var shouldKeepScreenAwake = true;
              var wakeActivationReceived = false;
              var wakeState = {
                mode: "idle",
                enabled: false,
                pending: false,
                attempts: 0,
                media: "none",
                mediaDetail: "none",
                lastError: "none",
                lastGesture: "never",
                lastEnable: "never",
                lastRelease: "never"
              };
              var viewportSyncFrame = 0;
              var lastViewportSignature = "";
              var lastFullscreenToggleAt = 0;
              var fallbackTouchId = null;

              function wakeNowLabel() {
                try {
                  return new Date().toLocaleTimeString();
                } catch (error) {
                  return String(Date.now());
                }
              }

              function supportsNativeWakeLockApi() {
                return !!(navigator.wakeLock && window.isSecureContext);
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
                if (!wakeDebug) {
                  return;
                }
                wakeDebug.textContent = [
                  "wake " + wakeState.mode + " " + (wakeState.enabled ? "on" : "off"),
                  "activation " + (wakeActivationReceived ? "yes" : "no") + " attempts " + wakeState.attempts,
                  "visible " + (!document.hidden ? "yes" : "no") + " secure " + (window.isSecureContext ? "yes" : "no"),
                  "native " + (supportsNativeWakeLockApi() ? "yes" : "no") + " fullscreen " + (isFullscreenActive() ? "yes" : "no"),
                  "standalone " + (isStandaloneMode() ? "yes" : "no"),
                  "pending " + (wakeState.pending ? "yes" : "no"),
                  "media " + wakeState.media,
                  "detail " + wakeState.mediaDetail,
                  "gesture " + wakeState.lastGesture,
                  "enable " + wakeState.lastEnable,
                  "release " + wakeState.lastRelease,
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

              function isIosBrowser() {
                return /iP(ad|hone|od)/i.test(navigator.userAgent || "");
              }

              function isLegacyIosBrowser() {
                if (!isIosBrowser()) {
                  return false;
                }
                var versionMatch = /CPU.*OS ([0-9_]{3,4})[0-9_]{0,1}|(CPU like).*AppleWebKit.*Mobile/i.exec(navigator.userAgent || "") || [0, ""];
                var normalized = String(versionMatch[1] || "")
                  .replace("undefined", "3_2")
                  .replace("_", ".")
                  .replace("_", "");
                var parsed = parseFloat(normalized);
                return Number.isFinite(parsed) && parsed < 10 && !window.MSStream;
              }

              function isStandaloneMode() {
                return (window.matchMedia && window.matchMedia("(display-mode: standalone)").matches) ||
                  window.navigator.standalone === true;
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
                return [document.body, root, container, canvas].filter(function(target) {
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
                fullscreenButton.setAttribute("title", fullscreenActive ? "Minimize game" : "Fullscreen game");
                fullscreenButton.setAttribute("aria-label", fullscreenActive ? "Minimize game" : "Fullscreen game");
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

              function createWakeController() {
                var wakeLockSentinel = null;
                var wakeFallbackVideo = null;
                var wakeFallbackTimer = 0;
                var enabled = false;
                var pendingPromise = null;

                function appendSource(videoElement, type, dataUri) {
                  var source = document.createElement("source");
                  source.src = dataUri;
                  source.type = "video/" + type;
                  videoElement.appendChild(source);
                }

                function ensureWakeFallbackVideo() {
                  if (wakeFallbackVideo) {
                    return wakeFallbackVideo;
                  }
                  wakeFallbackVideo = document.createElement("video");
                  wakeFallbackVideo.id = "hitster-wake-video";
                  wakeFallbackVideo.setAttribute("title", "No Sleep");
                  wakeFallbackVideo.setAttribute("playsinline", "");
                  wakeFallbackVideo.setAttribute("webkit-playsinline", "");
                  wakeFallbackVideo.setAttribute("muted", "");
                  wakeFallbackVideo.setAttribute("autoplay", "");
                  wakeFallbackVideo.setAttribute("loop", "");
                  wakeFallbackVideo.setAttribute("aria-hidden", "true");
                  wakeFallbackVideo.setAttribute("disableRemotePlayback", "");
                  wakeFallbackVideo.setAttribute("x-webkit-airplay", "deny");
                  wakeFallbackVideo.preload = "auto";
                  wakeFallbackVideo.defaultMuted = true;
                  wakeFallbackVideo.muted = true;
                  wakeFallbackVideo.autoplay = true;
                  wakeFallbackVideo.loop = true;
                  wakeFallbackVideo.volume = 0;
                  wakeFallbackVideo.playsInline = true;
                  wakeFallbackVideo.style.position = "fixed";
                  wakeFallbackVideo.style.width = "1px";
                  wakeFallbackVideo.style.height = "1px";
                  wakeFallbackVideo.style.left = "-10px";
                  wakeFallbackVideo.style.bottom = "-10px";
                  wakeFallbackVideo.style.opacity = "0.001";
                  wakeFallbackVideo.style.pointerEvents = "none";
                  wakeFallbackVideo.style.zIndex = "-1";
                  if (typeof wakeFallbackVideo.disableRemotePlayback !== "undefined") {
                    wakeFallbackVideo.disableRemotePlayback = true;
                  }
                  appendSource(wakeFallbackVideo, "webm", "${wakeLockFallbackWebmUri}");
                  appendSource(wakeFallbackVideo, "mp4", "${wakeLockFallbackVideoUri}");
                  function updateMediaDetail(type) {
                    wakeState.media = type;
                    wakeState.mediaDetail = [
                      "ready=" + wakeFallbackVideo.readyState,
                      "net=" + wakeFallbackVideo.networkState,
                      "time=" + wakeFallbackVideo.currentTime.toFixed(2)
                    ].join(" ");
                    updateWakeDebug();
                  }
                  wakeFallbackVideo.addEventListener("loadedmetadata", function() {
                    updateMediaDetail("loadedmetadata");
                    if (wakeFallbackVideo.duration <= 1) {
                      wakeFallbackVideo.setAttribute("loop", "");
                      wakeFallbackVideo.loop = true;
                      return;
                    }
                    if (wakeFallbackVideo.dataset.hitsterLoopHack === "true") {
                      return;
                    }
                    wakeFallbackVideo.dataset.hitsterLoopHack = "true";
                    wakeFallbackVideo.addEventListener("timeupdate", function() {
                      if (wakeFallbackVideo.currentTime > 0.5) {
                        wakeFallbackVideo.currentTime = Math.random();
                      }
                      updateMediaDetail("timeupdate");
                    });
                  });
                  ["play", "playing", "pause", "waiting", "stalled", "suspend", "abort", "ended", "canplay", "canplaythrough", "loadeddata"].forEach(function(type) {
                    wakeFallbackVideo.addEventListener(type, function() {
                      updateMediaDetail(type);
                    });
                  });
                  wakeFallbackVideo.addEventListener("error", function() {
                    wakeState.media = "error";
                    var mediaError = wakeFallbackVideo.error;
                    if (mediaError && mediaError.code) {
                      wakeState.lastError = "MediaError code " + mediaError.code;
                    }
                    wakeState.mediaDetail = [
                      "ready=" + wakeFallbackVideo.readyState,
                      "net=" + wakeFallbackVideo.networkState
                    ].join(" ");
                    updateWakeDebug();
                  });
                  if (!wakeFallbackVideo.parentNode && document.body) {
                    document.body.appendChild(wakeFallbackVideo);
                  }
                  wakeFallbackVideo.load();
                  return wakeFallbackVideo;
                }

                return {
                  isEnabled: function() {
                    return enabled;
                  },
                  enable: function() {
                    if (enabled) {
                      return Promise.resolve();
                    }
                    if (pendingPromise) {
                      return pendingPromise;
                    }
                    wakeState.attempts += 1;
                    if (supportsNativeWakeLockApi()) {
                      wakeState.mode = "native";
                      wakeState.pending = true;
                      updateWakeDebug();
                      pendingPromise = navigator.wakeLock.request("screen").then(function(lock) {
                        wakeLockSentinel = lock;
                        enabled = true;
                        pendingPromise = null;
                        wakeState.pending = false;
                        wakeState.enabled = true;
                        wakeState.lastEnable = wakeNowLabel();
                        wakeState.lastError = "none";
                        updateWakeDebug();
                        wakeLockSentinel.addEventListener("release", function() {
                          wakeLockSentinel = null;
                          enabled = false;
                          wakeState.enabled = false;
                          wakeState.lastRelease = wakeNowLabel();
                          updateWakeDebug();
                          if (shouldKeepScreenAwake && !document.hidden) {
                            wakeController.enable().catch(function() {});
                          }
                        });
                      }).catch(function(error) {
                        enabled = false;
                        pendingPromise = null;
                        wakeState.pending = false;
                        wakeState.enabled = false;
                        wakeState.lastError = formatWakeError(error);
                        updateWakeDebug();
                        throw error;
                      });
                      return pendingPromise;
                    }
                    if (isLegacyIosBrowser()) {
                      wakeState.mode = "legacy-ios-timer";
                      if (wakeFallbackTimer !== 0) {
                        enabled = true;
                        wakeState.enabled = true;
                        wakeState.lastEnable = wakeNowLabel();
                        wakeState.lastError = "none";
                        updateWakeDebug();
                        return Promise.resolve();
                      }
                      wakeFallbackTimer = window.setInterval(function() {
                        if (!document.hidden) {
                          window.location.href = window.location.href.split("#")[0];
                          window.setTimeout(window.stop, 0);
                        }
                      }, 15000);
                      enabled = true;
                      wakeState.enabled = true;
                      wakeState.lastEnable = wakeNowLabel();
                      wakeState.lastError = "none";
                      updateWakeDebug();
                      return Promise.resolve();
                    }
                    wakeState.mode = "video-fallback";
                    wakeState.media = "starting";
                    wakeState.mediaDetail = "ready=0 net=0 time=0.00";
                    wakeState.pending = true;
                    updateWakeDebug();
                    var wakeVideo = ensureWakeFallbackVideo();
                    pendingPromise = Promise.resolve(wakeVideo.play()).then(function(result) {
                        enabled = true;
                        pendingPromise = null;
                        wakeState.pending = false;
                        wakeState.enabled = true;
                        wakeState.lastEnable = wakeNowLabel();
                        wakeState.lastError = "none";
                        wakeState.mediaDetail = [
                          "ready=" + wakeVideo.readyState,
                          "net=" + wakeVideo.networkState,
                          "time=" + wakeVideo.currentTime.toFixed(2)
                        ].join(" ");
                        updateWakeDebug();
                        return result;
                    }).catch(function(error) {
                        enabled = false;
                        pendingPromise = null;
                        wakeState.pending = false;
                        wakeState.enabled = false;
                        wakeState.lastError = formatWakeError(error);
                        wakeState.mediaDetail = [
                          "ready=" + wakeVideo.readyState,
                          "net=" + wakeVideo.networkState,
                          "time=" + wakeVideo.currentTime.toFixed(2)
                        ].join(" ");
                        updateWakeDebug();
                        throw error;
                    });
                    return pendingPromise;
                  },
                  disable: function() {
                    if (supportsNativeWakeLockApi()) {
                      if (wakeLockSentinel) {
                          wakeLockSentinel.release().catch(function() {});
                      }
                      wakeLockSentinel = null;
                    } else if (isLegacyIosBrowser()) {
                      if (wakeFallbackTimer !== 0) {
                        window.clearInterval(wakeFallbackTimer);
                        wakeFallbackTimer = 0;
                      }
                    } else if (wakeFallbackVideo) {
                      wakeFallbackVideo.pause();
                    }
                    pendingPromise = null;
                    enabled = false;
                    wakeState.pending = false;
                    wakeState.enabled = false;
                    wakeState.lastRelease = wakeNowLabel();
                    updateWakeDebug();
                  },
                };
              }

              var wakeController = createWakeController();

              function releaseWakeLock() {
                wakeState.lastRelease = wakeNowLabel();
                updateWakeDebug();
              }

              function requestWakeLock() {
                shouldKeepScreenAwake = true;
                if (document.hidden || !wakeActivationReceived) {
                  return;
                }
                wakeController.enable().catch(function() {});
              }

              function activateWakeFromGesture() {
                if (document.hidden) {
                  return;
                }
                shouldKeepScreenAwake = true;
                wakeActivationReceived = true;
                wakeState.lastGesture = wakeNowLabel();
                updateWakeDebug();
                if (wakeController.isEnabled()) {
                  return;
                }
                wakeController.enable().catch(function() {});
              }

              function handleWakeGesture() {
                activateWakeFromGesture();
                scheduleViewportSync();
              }

              function shouldIgnoreFullscreenToggle(eventType) {
                var now = Date.now();
                var isReleaseGesture = eventType === "touchend" || eventType === "pointerup" || eventType === "mouseup";
                if (!isReleaseGesture && (now - lastFullscreenToggleAt) < 600) {
                  return true;
                }
                lastFullscreenToggleAt = now;
                return false;
              }

              function handleInteractiveFocus() {
                focusCanvas();
                scheduleViewportSync();
              }

              ["touchstart", "pointerdown", "mousedown"].forEach(function(type) {
                canvas.addEventListener(type, handleInteractiveFocus, { passive: true });
              });

              ["touchend", "pointerup", "mouseup", "click"].forEach(function(type) {
                canvas.addEventListener(type, handleInteractiveFocus, { passive: true });
                canvas.addEventListener(type, function() {
                  handleWakeGesture();
                }, { passive: false });
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
                  activateWakeFromGesture();
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
                    activateWakeFromGesture();
                    dispatchSyntheticMouse("mouseup", touch);
                    fallbackTouchId = null;
                    event.preventDefault();
                    event.stopPropagation();
                  }, { passive: false, capture: true });
                });
              }

              document.addEventListener("visibilitychange", function() {
                if (document.hidden) {
                  wakeState.lastRelease = wakeNowLabel();
                  updateWakeDebug();
                  return;
                }
                scheduleViewportSync();
                if (shouldKeepScreenAwake && wakeActivationReceived) {
                  requestWakeLock();
                }
              });

              if (fullscreenButton) {
                ["touchend", "pointerup", "mouseup", "click"].forEach(function(type) {
                  fullscreenButton.addEventListener(type, function(event) {
                    if (shouldIgnoreFullscreenToggle(event.type)) {
                      return;
                    }
                    event.preventDefault();
                    event.stopPropagation();
                    focusCanvas();
                    activateWakeFromGesture();
                    if (!supportsFullscreen()) {
                      setFullscreenButtonState();
                      if (isIosBrowser() && !isStandaloneMode()) {
                        window.alert("iPhone Safari does not support real page fullscreen in a browser tab. Use Share → Add to Home Screen to open the game fullscreen.");
                      }
                      scheduleViewportSync();
                      return;
                    }
                    var togglePromise = isFullscreenActive() ? exitFullscreen() : requestFullscreen();
                    Promise.resolve(togglePromise).then(function() {
                      setFullscreenButtonState();
                      if (shouldKeepScreenAwake && wakeActivationReceived) {
                        requestWakeLock();
                      }
                      scheduleViewportSync();
                    }, function() {
                      setFullscreenButtonState();
                      if (shouldKeepScreenAwake && wakeActivationReceived) {
                        requestWakeLock();
                      }
                      scheduleViewportSync();
                    });
                  }, { passive: false });
                });
              }

              ["fullscreenchange", "webkitfullscreenchange", "msfullscreenchange"].forEach(function(type) {
                document.addEventListener(type, function() {
                  setFullscreenButtonState();
                  if (shouldKeepScreenAwake && wakeActivationReceived) {
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
                if (shouldKeepScreenAwake && wakeActivationReceived) {
                  requestWakeLock();
                }
              }, { passive: true });
              window.addEventListener("pagehide", function() {
                wakeState.lastRelease = wakeNowLabel();
                updateWakeDebug();
              }, { passive: true });
              window.addEventListener("focus", function() {
                scheduleViewportSync();
                if (shouldKeepScreenAwake && wakeActivationReceived) {
                  requestWakeLock();
                }
              }, { passive: true });
              window.setInterval(function() {
                if (shouldKeepScreenAwake && wakeActivationReceived && !document.hidden && !wakeController.isEnabled() && wakeState.mode !== "video-fallback") {
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
              updateWakeDebug();
              scheduleViewportSync();
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
        if (!patched.contains("""<meta name="apple-mobile-web-app-capable" content="yes">""")) {
            patched = patched.replace("</head>", "    $mobileWebAppMeta\n</head>")
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
