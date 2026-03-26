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
    private val wakeLockFallbackMp4DataUri = loadResourceText("wake-lock-fallback-video-uri.txt")
    private val wakeLockFallbackWebmDataUri = loadResourceText("wake-lock-fallback-video-webm-uri.txt")

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
                if (!wakeDebugEnabled || !wakeDebug) {
                  return;
                }
                wakeDebug.textContent = [
                  "wake " + wakeState.mode + " " + (wakeState.enabled ? "on" : "off"),
                  "activation " + (wakeActivationReceived ? "yes" : "no") + " attempts " + wakeState.attempts,
                  "visible " + (!document.hidden ? "yes" : "no") + " secure " + (window.isSecureContext ? "yes" : "no"),
                  "native " + (supportsNativeWakeLockApi() ? "yes" : "no"),
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

              function effectivePixelRatio() {
                var pixelRatio = window.devicePixelRatio || 1;
                if (!supportsTouchBridge()) {
                  return pixelRatio;
                }
                if (isIosBrowser()) {
                  return Math.min(pixelRatio, 1.5);
                }
                return Math.min(pixelRatio, 2);
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

                function appendWakeSource(videoElement, type, uri) {
                  var source = document.createElement("source");
                  source.src = uri;
                  source.type = "video/" + type;
                  source.dataset.hitsterSourceType = type;
                  videoElement.appendChild(source);
                }

                function createWakeController() {
                  var wakeLockSentinel = null;
                  var wakeFallbackVideo = null;
                  var wakeFallbackTimer = 0;
                  var enabled = false;
                  var pendingPromise = null;

                function ensureWakeFallbackVideo() {
                  if (wakeFallbackVideo) {
                    return wakeFallbackVideo;
                  }
                  wakeFallbackVideo = document.createElement("video");
                  wakeFallbackVideo.id = "hitster-wake-video";
                  wakeFallbackVideo.setAttribute("title", "No Sleep");
                  wakeFallbackVideo.setAttribute("playsinline", "");
                  wakeFallbackVideo.setAttribute("webkit-playsinline", "");
                  wakeFallbackVideo.setAttribute("preload", "auto");
                  if (!isIosBrowser()) {
                    wakeFallbackVideo.setAttribute("muted", "");
                    wakeFallbackVideo.defaultMuted = true;
                    wakeFallbackVideo.muted = true;
                    wakeFallbackVideo.volume = 0;
                  }
                  wakeFallbackVideo.disableRemotePlayback = true;
                  wakeFallbackVideo.setAttribute("disableremoteplayback", "");
                  appendWakeSource(wakeFallbackVideo, "webm", "${wakeLockFallbackWebmDataUri}");
                  appendWakeSource(wakeFallbackVideo, "mp4", "${wakeLockFallbackMp4DataUri}");
                  function currentWakeSourceType() {
                    var currentSource = wakeFallbackVideo.currentSrc || "";
                    if (currentSource.indexOf("video/webm") !== -1) {
                      return "webm";
                    }
                    if (currentSource.indexOf("video/mp4") !== -1 || currentSource.indexOf("ftyp") !== -1) {
                      return "mp4";
                    }
                    return "unknown";
                  }
                  function updateMediaDetail(type) {
                    wakeState.media = type;
                    if (!wakeDebugEnabled) {
                      return;
                    }
                    var sourceType = currentWakeSourceType();
                    wakeState.mediaDetail = [
                      "src=" + sourceType,
                      "ready=" + wakeFallbackVideo.readyState,
                      "net=" + wakeFallbackVideo.networkState,
                      "time=" + wakeFallbackVideo.currentTime.toFixed(2)
                    ].join(" ");
                    updateWakeDebug();
                  }
                  wakeFallbackVideo.addEventListener("loadedmetadata", function() {
                    updateMediaDetail("loadedmetadata");
                    if (wakeFallbackVideo.duration <= 1 || currentWakeSourceType() === "webm") {
                      wakeFallbackVideo.setAttribute("loop", "");
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
                  if (wakeDebugEnabled) {
                    ["play", "playing", "pause", "waiting", "stalled", "suspend", "abort", "ended", "canplay", "canplaythrough", "loadeddata"].forEach(function(type) {
                      wakeFallbackVideo.addEventListener(type, function() {
                        updateMediaDetail(type);
                      });
                    });
                  }
                  ["pause", "ended", "abort"].forEach(function(type) {
                    wakeFallbackVideo.addEventListener(type, function() {
                      enabled = false;
                      wakeState.enabled = false;
                      wakeState.lastRelease = wakeNowLabel();
                      updateWakeDebug();
                      resumeWakeFallbackPlayback(type);
                    });
                  });
                  wakeFallbackVideo.addEventListener("error", function() {
                    wakeState.media = "error";
                    var mediaError = wakeFallbackVideo.error;
                    if (mediaError && mediaError.code) {
                      wakeState.lastError = "MediaError code " + mediaError.code;
                    }
                    if (!wakeDebugEnabled) {
                      return;
                    }
                    wakeState.mediaDetail = [
                      "src=" + (wakeFallbackVideo.currentSrc || "unknown"),
                      "ready=" + wakeFallbackVideo.readyState,
                      "net=" + wakeFallbackVideo.networkState
                    ].join(" ");
                    updateWakeDebug();
                  });
                  return wakeFallbackVideo;
                }

                function resumeWakeFallbackPlayback(reason) {
                  if (!wakeFallbackVideo || pendingPromise || document.hidden || !shouldKeepScreenAwake || !wakeActivationReceived) {
                    return;
                  }
                  wakeState.pending = true;
                  updateWakeDebug();
                  pendingPromise = Promise.resolve(wakeFallbackVideo.play()).then(function(result) {
                    enabled = true;
                    pendingPromise = null;
                    wakeState.pending = false;
                    wakeState.enabled = true;
                    wakeState.lastEnable = wakeNowLabel();
                    wakeState.lastError = "none";
                    if (wakeDebugEnabled) {
                      wakeState.mediaDetail = [
                        "resume=" + reason,
                        "src=" + (wakeFallbackVideo.currentSrc || "unknown"),
                        "ready=" + wakeFallbackVideo.readyState,
                        "net=" + wakeFallbackVideo.networkState,
                        "time=" + wakeFallbackVideo.currentTime.toFixed(2)
                      ].join(" ");
                    }
                    updateWakeDebug();
                    return result;
                  }).catch(function(error) {
                    enabled = false;
                    pendingPromise = null;
                    wakeState.pending = false;
                    wakeState.enabled = false;
                    wakeState.lastError = formatWakeError(error);
                    if (wakeDebugEnabled) {
                      wakeState.mediaDetail = [
                        "resume=" + reason,
                        "src=" + (wakeFallbackVideo.currentSrc || "unknown"),
                        "ready=" + wakeFallbackVideo.readyState,
                        "net=" + wakeFallbackVideo.networkState,
                        "time=" + wakeFallbackVideo.currentTime.toFixed(2)
                      ].join(" ");
                    }
                    updateWakeDebug();
                    throw error;
                  });
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
                    wakeState.mediaDetail = "src=unknown ready=0 net=0 time=0.00";
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
                        if (wakeDebugEnabled) {
                          wakeState.mediaDetail = [
                            "src=" + (wakeVideo.currentSrc || "unknown"),
                            "ready=" + wakeVideo.readyState,
                            "net=" + wakeVideo.networkState,
                            "time=" + wakeVideo.currentTime.toFixed(2)
                          ].join(" ");
                        }
                        updateWakeDebug();
                        return result;
                    }).catch(function(error) {
                        enabled = false;
                        pendingPromise = null;
                        wakeState.pending = false;
                        wakeState.enabled = false;
                        wakeState.lastError = formatWakeError(error);
                        if (wakeDebugEnabled) {
                          wakeState.mediaDetail = [
                            "src=" + (wakeVideo.currentSrc || "unknown"),
                            "ready=" + wakeVideo.readyState,
                            "net=" + wakeVideo.networkState,
                            "time=" + wakeVideo.currentTime.toFixed(2)
                          ].join(" ");
                        }
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
              }

              function handleInteractiveFocus() {
                focusCanvas();
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
