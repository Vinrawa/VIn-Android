package com.vin.browser.adblock

import android.webkit.WebView

object CleanPage {

    /**
     * Curated selectors for well-known ad / consent / popup containers.
     *
     * Only unambiguous tokens are allowed here. Substring selectors such as
     * `[class*='ad-container']` looked harmless but also matched
     * lo(ad-container), he(ad-banner), uplo(ad-modal), thre(ad-wrapper) and
     * removed legitimate UI on many sites (chess boards, upload dialogs, page
     * headers). Brave / uBlock never guess like that: they rely on exact
     * EasyList cosmetic rules, which we already inject via [inject].
     */
    private val knownSelectors = listOf(
        // Google / GPT / Taboola / Outbrain ad slots (exact, ad-only tokens)
        "ins.adsbygoogle",
        ".adsbygoogle",
        "[id^='google_ads_']",
        "[id^='div-gpt-ad']",
        "div[id^='taboola-']",
        ".taboola-container",
        ".outbrain-container",
        ".OUTBRAIN",
        // Cookie / consent banners
        "[class*='cookie-banner']",
        "[class*='cookie-consent']",
        "[id*='cookie-consent']",
        "[id*='cookiebanner']",
        // Newsletter / push-notification nags
        "[class*='newsletter-popup']",
        "[class*='newsletter-modal']",
        "[class*='onesignal']",
        "[class*='push-notification-prompt']",
        // Popunders and fixed-size banner slots
        "[class*='popunder']",
        "[id*='popunder']",
        "[class*='banner300']",
        "[class*='banner728']",
        "[class*='vi-sticky-ad']",
        "[id*='shopOurAds']"
    )

    /**
     * Injects the cosmetic filter layer into the page.
     * [cosmeticSelectors] are EasyList element-hiding selectors for this domain --
     * they go into a persistent <style> tag so late-loading elements are hidden
     * automatically without any observer overhead.
     */
    fun inject(view: WebView, cosmeticSelectors: List<String> = emptyList()) {
        view.evaluateJavascript(buildScript(cosmeticSelectors), null)
    }

    private fun buildScript(cosmeticSelectors: List<String>): String {
        val selectorList = escapeJs(knownSelectors.joinToString(","))
        val cosmeticCss = cosmeticSelectors.joinToString(",") { escapeJs(it) }
        return """
            (function() {
              // 1. Persistent style: hides current AND future matching elements.
              //    Cheap, auto-applies to late arrivals (SPA renders, AJAX popups).
              try {
                var cssParts = [];
                if ("$cosmeticCss") cssParts.push("$cosmeticCss");
                if (cssParts.length) {
                  var st = document.createElement('style');
                  st.setAttribute('data-vin-cosmetic', '1');
                  st.textContent = cssParts.join(',') + '{display:none !important;visibility:hidden !important;pointer-events:none !important;}';
                  document.documentElement.appendChild(st);
                }
              } catch(e) {}

              // Hide, never remove: deleting nodes crashes site scripts that still
              // hold references to them (broken players, frozen SPAs).
              function hideNode(el) {
                try { el.style.setProperty('display', 'none', 'important'); } catch(e) {}
              }

              var AD_MARKER = /(^|[\s_\-])(ad|ads|advert|advertisement|sponsor|sponsored|promo|popup|popunder|interstitial)([\s_\-]|$)/i;

              function killIfOverlay(el) {
                try {
                  if (el === document.body || el === document.documentElement) return;
                  // Never touch players, menus, dialogs, navigation or anything that
                  // carries interactive / rendered content (forms, canvases, iframes,
                  // games). Those are legitimate full-screen layers, not ads.
                  if (el.matches && el.matches('dialog, [role="dialog"], [role="menu"], [role="navigation"], nav, header, video, canvas, iframe, ytm-bottom-sheet-renderer, ytd-engagement-panel-section-list-renderer')) return;
                  if (el.closest && el.closest('#movie_player, .html5-video-player, ytd-player, #player, dialog, [role="dialog"]')) return;
                  if (el.querySelector && el.querySelector('input, textarea, select, video, canvas, iframe, [contenteditable]')) return;
                  var cs = getComputedStyle(el);
                  if (cs.position !== 'fixed') return;
                  var r = el.getBoundingClientRect();
                  var coverage = (r.width * r.height) / (window.innerWidth * window.innerHeight);
                  var zIndex = parseInt(cs.zIndex) || 0;
                  var cls = (typeof el.className === 'string') ? el.className : '';
                  var sig = (el.id || '') + ' ' + cls;
                  // Near-full-screen, very high z-index AND named like an ad/promo.
                  if (coverage > 0.9 && zIndex >= 999 && AD_MARKER.test(sig)) {
                    hideNode(el);
                  }
                } catch(e) {}
              }

              // 2. Hide known ad / consent / popup nodes
              try {
                document.querySelectorAll("$selectorList").forEach(hideNode);
              } catch(e) {}

              // 3. Full-screen takeover heuristic (conservative, see killIfOverlay)
              try {
                document.querySelectorAll('body > *, body > * > *').forEach(killIfOverlay);
              } catch(e) {}

              // 4. Watch the DOM for late arrivals (scroll-triggered / delayed popups)
              try {
                var observer = new MutationObserver(function(muts) {
                  muts.forEach(function(m) {
                    m.addedNodes.forEach(function(node) {
                      if (node.nodeType !== 1) return;
                      try {
                        if (node.matches && node.matches("$selectorList")) { hideNode(node); return; }
                      } catch(e) {}
                      killIfOverlay(node);
                      if (node.querySelectorAll) node.querySelectorAll("$selectorList").forEach(hideNode);
                    });
                  });
                });
                observer.observe(document.body, { childList: true, subtree: true });
                setTimeout(function() { observer.disconnect(); }, 15000);
              } catch(e) {}
            })();
        """.trimIndent()
    }

    private fun escapeJs(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'")
}
