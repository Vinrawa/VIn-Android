package com.vin.browser.adblock

import android.webkit.WebView
import org.json.JSONObject

data class YouTubeFocusSettings(
    val skipAds: Boolean,
    val hideShorts: Boolean,
    val hideRecommendations: Boolean,
    val hideComments: Boolean,
    val hideEndScreenSuggestions: Boolean,
    val disableAutoplay: Boolean
) {
    companion object {
        fun default() = YouTubeFocusSettings(
            skipAds = true,          // network-level blocking cannot see YouTube ads
                                     // (same googlevideo.com hosts as content), so the
                                     // in-page skip watchdog must ALWAYS run.
            hideShorts = false,
            hideRecommendations = false,
            hideComments = false,
            hideEndScreenSuggestions = false,
            disableAutoplay = false
        )

        /** Returns true if any focus feature is active (ad skipper should run). */
        fun hasActiveFeature(s: YouTubeFocusSettings) =
            s.skipAds || s.hideShorts || s.hideRecommendations || s.hideComments ||
            s.hideEndScreenSuggestions || s.disableAutoplay
    }
}

object YouTubeFocus {

    fun inject(view: WebView, settings: YouTubeFocusSettings = YouTubeFocusSettings.default()) {
        if (!YouTubeFocusSettings.hasActiveFeature(settings)) return
        view.evaluateJavascript(buildScript(settings), null)
    }

    private fun buildScript(s: YouTubeFocusSettings): String {
        val css = buildString {
            // --- Ad hiding (ONLY ad elements are hidden) ----------------------------
            // NOTE: no player-geometry CSS here on purpose. The polymer mobile player
            // computes its container size with JS; forcing object-fit/max-width with
            // !important broke that height calculation (player collapsed to 0 height
            // or a thin strip). The original stretch came from the LEGACY frontend
            // served to old UAs -- fixed at the UA level instead (pinned modern UA).
            // Video and banner ads + Open App promo banner removal.
            // NOTE: `.ad-showing` / `.ad-interrupting` are set ON the player element
            // itself while an ad plays -- hiding them hides the whole player, so they
            // are intentionally NOT in this list. The watchdog below skips ads
            // functionally (mute -> 16x -> seek -> click skip).
            append(".video-ads, .ytp-ad-overlay-container, .ytp-ad-message-container, ytm-promoted-sparkles-web-renderer, ytd-promoted-video-renderer, ytd-banner-promo-renderer-background, ytd-action-companion-ad-renderer, ytd-in-feed-ad-layout-renderer, ytm-promoted-video-renderer, .ytp-ad-overlay-slot, .ytp-ad-player-overlay, ytm-app-banner, .ytm-app-banner { display: none !important; }")

            if (s.hideShorts) {
                append(" ytd-rich-shelf-renderer[is-shorts], ytd-reel-shelf-renderer, ytd-mini-guide-entry-renderer[aria-label='Shorts'], ytm-reel-shelf-renderer, ytm-pivot-bar-item-renderer:nth-child(2) { display: none !important; }")
            }
            if (s.hideRecommendations) {
                append(" #related, ytd-watch-next-secondary-results-renderer, ytm-item-section-renderer[section-identifier='related-items'] { display: none !important; }")
            }
            if (s.hideComments) {
                append(" #comments, ytd-comments, ytm-comments-entry-point-header-renderer, ytm-comment-section-renderer { display: none !important; }")
            }
            if (s.hideEndScreenSuggestions) {
                append(" .ytp-endscreen-content, .ytp-ce-element, .ytp-ce-covering-overlay { display: none !important; }")
            }
        }

        val jsonCss = JSONObject.quote(css)

        // Watchdog body: auto-skip + mute + seek-to-end while an ad is showing.
        val watchdog = if (s.skipAds) """
              try {
                if (!window._vinYtAdWatchdogStarted) {
                  window._vinYtAdWatchdogStarted = true;
                  setInterval(function() {
                    try {
                      var video = document.querySelector('video');
                      var ad = document.querySelector('.ad-showing, .ad-interrupting');
                      if (video && ad) {
                        video.muted = true;
                        video.playbackRate = 16.0;
                        var now = Date.now();
                        var lastSeek = window._vinYtLastAdSeek || 0;
                        if (typeof video.duration === 'number' && isFinite(video.duration) && video.duration > 0 && (now - lastSeek) >= 1500) {
                          video.currentTime = video.duration;
                          window._vinYtLastAdSeek = now;
                        }
                      }
                      var skipButtons = document.querySelectorAll('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-skip-button-slot');
                      for (var i = 0; i < skipButtons.length; i++) {
                        skipButtons[i].click();
                      }
                    } catch(e) {}
                  }, 300);
                }
              } catch(e) {}
        """.trimIndent() else ""

        return """
            (function() {
              try {
                var style = document.getElementById('vin-yt-focus-style');
                if (!style) {
                  style = document.createElement('style');
                  style.id = 'vin-yt-focus-style';
                  document.head.appendChild(style);
                }
                style.textContent = $jsonCss;
              } catch(e) {}

              $watchdog
            })();
        """.trimIndent()
    }
}
