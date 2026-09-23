package com.vin.browser.adblock

import android.webkit.WebView

object CleanPage {

    // Curated selectors for common cookie/consent/newsletter patterns -- extend
    // this from a maintained cosmetic filter list over time, it's a starting set
    private val knownSelectors = listOf(
        "[class*='cookie-banner']",
        "[class*='cookie-consent']",
        "[id*='cookie-consent']",
        "[id*='cookiebanner']",
        "[class*='gdpr']",
        "[id*='gdpr']",
        "[class*='newsletter-popup']",
        "[class*='newsletter-modal']",
        "[class*='onesignal']",
        "[class*='push-notification-prompt']",
        ".adsbygoogle",
        ".ad-banner",
        ".ad-container",
        ".ad-wrapper",
        ".taboola-container",
        ".outbrain-container",
        "[id^='google_ads_']",
        "[id^='div-gpt-ad']",
        // ---- Popup / interstitial ad patterns ("buy this" overlays, popunders) ----
        "[class*='popup-ad']",
        "[class*='popup_ad']",
        "[id*='popup-ad']",
        "[id*='popupad']",
        "[class*='ad-popup']",
        "[class*='ad-popup-']",
        "[class*='ad_modal']",
        "[class*='ad-modal']",
        "[id*='ad-modal']",
        "[class*='ad-overlay']",
        "[id*='ad-overlay']",
        "[class*='ad-interstitial']",
        "[id*='interstitial-ad']",
        "[class*='interstitial-ad']",
        "[class*='popunder']",
        "[id*='popunder']",
        "[class*='ad-popout']",
        "[class*='ad-sticky']",
        "[class*='ad-float']",
        "[class*='floating-ad']",
        "[id*='floatingad']",
        "[class*='promo-popup']",
        "[class*='promo-modal']",
        "[id*='sponsored-popup']",
        "[class*='sponsored-popup']",
        "[class*='site-promo']",
        "[id*='dp-popup']",
        "[class*='offer-modal']",
        "[class*='deal-popup']",
        "[class*='discount-popup']",
        "[class*='subscribe-popup']",
        "[id*='shopOurAds']",
        "[class*='vi-sticky-ad']",
        "div[id^='taboola-']",
        "div[class^='taboola']",
        "[class*='banner300']",
        "[class*='banner728']"
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

              function killIfOverlay(el) {
                try {
                  // Skip YouTube essential elements
                  if (el.closest && (
                    el.closest('ytm-bottom-sheet-renderer') ||
                    el.closest('.ytp-settings-menu') ||
                    el.closest('#movie_player') ||
                    el.closest('.html5-video-player') ||
                    el.closest('ytd-player') ||
                    el.closest('video') ||
                    el.closest('#player') ||
                    el.closest('.ytp-chrome-bottom') ||
                    el.closest('.ytp-chrome-top') ||
                    el.closest('.ytp-gradient-top') ||
                    el.closest('.ytp-gradient-bottom') ||
                    el.closest('.ytp-pause-overlay') ||
                    el.closest('ytd-engagement-panel-section-list-renderer')
                  )) return;
                  const cs = getComputedStyle(el);
                  if (cs.position !== 'fixed' && cs.position !== 'sticky') return;
                  const r = el.getBoundingClientRect();
                  const coverage = (r.width * r.height) / (window.innerWidth * window.innerHeight);
                  const zIndex = parseInt(cs.zIndex) || 0;
                  // Only hide overlays that are very likely ads/popups, not legitimate UI
                  if (coverage > 0.7 && zIndex > 50) {
                    el.style.setProperty('display', 'none', 'important');
                  }
                } catch(e) {}
              }

              // 2. Remove known popup/consent nodes outright
              try {
                document.querySelectorAll("$selectorList").forEach(el => el.remove());
              } catch(e) {}

              // 3. Full-screen takeover heuristic (fixed overlays covering the viewport)
              try {
                document.querySelectorAll('body *').forEach(killIfOverlay);
              } catch(e) {}

              // 4. Watch the DOM for late arrivals (scroll-triggered / delayed popups)
              try {
                var observer = new MutationObserver(muts => {
                  muts.forEach(m => {
                    m.addedNodes.forEach(node => {
                      if (node.nodeType !== 1) return;
                      try {
                        if (node.matches && node.matches("$selectorList")) { node.remove(); return; }
                      } catch(e) {}
                      killIfOverlay(node);
                      if (node.querySelectorAll) node.querySelectorAll("$selectorList").forEach(el => el.remove());
                    });
                  });
                });
                observer.observe(document.body, { childList: true, subtree: true });
                setTimeout(() => observer.disconnect(), 15000);
              } catch(e) {}
            })();
        """.trimIndent()
    }

    private fun escapeJs(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'")
}