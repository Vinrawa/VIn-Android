package com.vin.browser.engine

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.vin.browser.service.MediaPlaybackService

class MediaBridge(private val context: Context, private val isEnabled: () -> Boolean) {

    @JavascriptInterface
    fun onMediaPlay(title: String?, domain: String?) {
        if (!isEnabled()) return
        val safeTitle = if (title.isNullOrBlank()) "Media Playing" else title
        val safeDomain = if (domain.isNullOrBlank()) "ViN Browser" else domain
        if (!MediaPlaybackService.isServiceRunning) {
            MediaPlaybackService.start(context, safeTitle, safeDomain)
        } else {
            MediaPlaybackService.notifyPlaying(context, safeTitle, safeDomain)
        }
    }

    @JavascriptInterface
    fun onMediaPause() {
        if (MediaPlaybackService.isServiceRunning) {
            MediaPlaybackService.notifyPaused(context)
        }
    }
}

object BackgroundAudioEngine {

    const val JS_BRIDGE_NAME = "NativeMediaBridge"

    val visibilitySpoofJs: String = """
        (function() {
            try {
                if (window._vinBackgroundSpoofInjected) return;
                window._vinBackgroundSpoofInjected = true;

                // 1. Hook EventTarget.prototype.addEventListener to drop visibilitychange & blur listeners
                try {
                    var origAddEventListener = EventTarget.prototype.addEventListener;
                    var blockedEventTypes = {
                        'visibilitychange': true,
                        'webkitvisibilitychange': true,
                        'pagehide': true,
                        'blur': true,
                        'freeze': true
                    };

                    EventTarget.prototype.addEventListener = function(type, listener, options) {
                        if (blockedEventTypes[type] && (this === document || this === window)) {
                            // Suppress website visibility listener registration
                            return;
                        }
                        return origAddEventListener.apply(this, arguments);
                    };
                } catch(e) {}

                // 2. Override document.hidden and visibilityState
                try {
                    Object.defineProperty(document, 'hidden', {
                        get: function() { return false; },
                        configurable: true
                    });
                    Object.defineProperty(document, 'visibilityState', {
                        get: function() { return 'visible'; },
                        configurable: true
                    });
                    Object.defineProperty(document, 'webkitHidden', {
                        get: function() { return false; },
                        configurable: true
                    });
                    Object.defineProperty(document, 'webkitVisibilityState', {
                        get: function() { return 'visible'; },
                        configurable: true
                    });
                } catch(e) {}

                // 3. Spoof blur, focus and visibility property assignments
                try {
                    window.onblur = null;
                    document.onvisibilitychange = null;
                    document.hasFocus = function() { return true; };
                    window.hasFocus = function() { return true; };
                } catch(e) {}

                // 4. Override HTMLMediaElement.prototype.pause when backgrounded
                try {
                    var nativePause = HTMLMediaElement.prototype.pause;
                    HTMLMediaElement.prototype.pause = function() {
                        // If pause was called by website while in background
                        if (document.hidden || !document.hasFocus()) {
                            return Promise.resolve();
                        }
                        return nativePause.apply(this, arguments);
                    };
                } catch(e) {}

                // 5. Listen for HTML5 Media Play/Pause events to notify Android Native Service
                function checkAndNotifyPlay() {
                    try {
                        if (window.NativeMediaBridge && window.NativeMediaBridge.onMediaPlay) {
                            var title = document.title || 'Playing in background';
                            window.NativeMediaBridge.onMediaPlay(title, window.location.hostname);
                        }
                    } catch(err) {}
                }

                function checkAndNotifyPause() {
                    try {
                        var anyPlaying = false;
                        var mediaElements = document.querySelectorAll('video, audio');
                        for (var i = 0; i < mediaElements.length; i++) {
                            var m = mediaElements[i];
                            if (!m.paused && !m.ended && m.currentTime > 0) {
                                anyPlaying = true;
                                break;
                            }
                        }
                        if (!anyPlaying && window.NativeMediaBridge && window.NativeMediaBridge.onMediaPause) {
                            window.NativeMediaBridge.onMediaPause();
                        }
                    } catch(err) {}
                }

                document.addEventListener('play', checkAndNotifyPlay, true);
                document.addEventListener('playing', checkAndNotifyPlay, true);
                document.addEventListener('pause', checkAndNotifyPause, true);
                document.addEventListener('ended', checkAndNotifyPause, true);

            } catch(e) {}
        })();
    """.trimIndent().replace("\n", " ").replace(Regex("\\s+"), " ")

    const val PAUSE_ALL_MEDIA_JS = """
        (function() {
            try {
                document.querySelectorAll('video, audio').forEach(function(el) {
                    el.pause();
                });
            } catch(e) {}
        })();
    """

    const val TOGGLE_PLAY_PAUSE_MEDIA_JS = """
        (function() {
            try {
                var mediaList = document.querySelectorAll('video, audio');
                mediaList.forEach(function(m) {
                    if (m.paused) {
                        m.play();
                    } else {
                        m.pause();
                    }
                });
            } catch(e) {}
        })();
    """

    fun inject(webView: WebView?, isEnabled: Boolean) {
        if (!isEnabled || webView == null) return
        webView.evaluateJavascript(visibilitySpoofJs, null)
    }
}
