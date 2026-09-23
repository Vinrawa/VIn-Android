package com.vin.browser.engine

import android.webkit.WebView

/**
 * Volume Boost -- 2x media loudness via the Web Audio API.
 *
 * How it works: a GainNode is inserted between the page's <video>/<audio>
 * element and the audio destination (createMediaElementSource -> gain ->
 * destination). System volume caps at 100%; this raises the pre-DSP gain so
 * quiet videos get genuinely louder. It works on YouTube because the player
 * feeds the <video> element through MediaSource Extensions (blob: source),
 * which is same-origin data and never taints the audio graph.
 *
 * Safety rails:
 *  - Opt-in per SITE (SiteControlSettings.volumeBoost) -- never global, so a
 *    CORS-tainted <audio> on a random site (direct cross-origin file without
 *    CORS headers) can never be silently muted by a tainted graph by accident.
 *  - The audio graph is only created AFTER the first user gesture on the
 *    page. An AudioContext created before any interaction stays "suspended"
 *    and would route a playing element into silence; gating on the gesture
 *    makes that impossible. Elements playing before the first tap are left
 *    untouched.
 *  - Boost "off" does not try to tear the graph down (impossible for
 *    MediaElementSource); it writes gain 1.0 which is bit-transparent
 *    passthrough.
 */
object VolumeBoost {

    const val DEFAULT_GAIN = 2.0f // 200%

    /** Inject (or update) the boost graph for the current document. */
    fun inject(view: WebView, gain: Float = DEFAULT_GAIN) {
        view.evaluateJavascript(buildScript(gain), null)
    }

    /** Immediate no-op when gain is 1.0 and nothing is hooked yet. */
    fun buildScript(gain: Float): String {
        val gainValue = gain.coerceIn(0.5f, 3.0f)
        return """
            (function() {
              try {
                // Gesture flag goes in FIRST (cheap, idempotent) so the first
                // tap anywhere on the page is always observed.
                if (!window._vinGestureHooked) {
                  window._vinGestureHooked = true;
                  var flag = function() { window._vinUserGestured = true; };
                  ['click', 'touchstart', 'keydown'].forEach(function(evt) {
                    document.addEventListener(evt, flag, { capture: true, once: true });
                  });
                }
                var G = $gainValue;
                if (!window._vinBoostCtx) {
                  if (!window._vinUserGestured) return;   // no gesture yet: stay inert
                  var AC = window.AudioContext || window.webkitAudioContext;
                  if (!AC) return;
                  window._vinBoostCtx = new AC();
                  window._vinBoostHooked = new WeakSet();
                  window._vinBoostGain = G;
                  var ctx = window._vinBoostCtx;
                  var resume = function() {
                    try { if (ctx.state === 'suspended') ctx.resume(); } catch (e) {}
                  };
                  ['click', 'touchstart', 'keydown'].forEach(function(evt) {
                    document.addEventListener(evt, resume, true);
                  });
                  setInterval(function() {
                    try {
                      var c = window._vinBoostCtx;
                      if (!c || c.state === 'closed') return;
                      if (!window._vinUserGestured) return;
                      var els = document.querySelectorAll('video, audio');
                      for (var i = 0; i < els.length; i++) {
                        var el = els[i];
                        if (!window._vinBoostHooked.has(el)) {
                          window._vinBoostHooked.add(el);
                          try {
                            var src = c.createMediaElementSource(el);
                            var g = c.createGain();
                            g.gain.value = window._vinBoostGain;
                            src.connect(g);
                            g.connect(c.destination);
                            el._vinBoostGainNode = g;
                          } catch (e) { /* already connected elsewhere */ }
                        } else if (el._vinBoostGainNode) {
                          el._vinBoostGainNode.gain.value = window._vinBoostGain;
                        }
                      }
                    } catch (e) {}
                  }, 700);
                } else {
                  window._vinBoostGain = G;
                  var els = document.querySelectorAll('video, audio');
                  for (var j = 0; j < els.length; j++) {
                    if (els[j]._vinBoostGainNode) els[j]._vinBoostGainNode.gain.value = G;
                  }
                }
              } catch (e) {}
            })();
        """.trimIndent()
    }
}
