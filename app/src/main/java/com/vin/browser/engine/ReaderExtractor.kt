package com.vin.browser.engine

import android.webkit.WebView
import com.vin.browser.ui.screens.ReaderArticle
import org.json.JSONObject
import org.json.JSONTokener

/**
 * One-shot DOM reader extraction: picks the best content container on the live page,
 * strips boilerplate, and returns a [ReaderArticle]. Null when the page has no
 * readable content (fewer than 2 paragraphs) or anything throws.
 */
object ReaderExtractor {

    private const val EXTRACT_JS = """(function() {
  var root = document.querySelector('article') || document.querySelector('[role="main"]') || document.querySelector('main');
  if (!root) {
    var candidates = document.querySelectorAll('div,section,article');
    var limit = Math.min(candidates.length, 300);
    var best = null, bestLen = 0;
    for (var i = 0; i < limit; i++) {
      var len = (candidates[i].innerText || '').length;
      if (len > bestLen) { bestLen = len; best = candidates[i]; }
    }
    root = best;
  }
  if (!root) {
    return JSON.stringify({ title: document.title || '', byline: location.hostname || '', paragraphs: [] });
  }
  var clone = root.cloneNode(true);
  var junk = clone.querySelectorAll('script,style,noscript,nav,header,footer,aside,iframe,fo rm,button');
  for (var j = 0; j < junk.length; j++) {
    if (junk[j].parentNode) junk[j].parentNode.removeChild(junk[j]);
  }
  var all = clone.querySelectorAll('*');
  for (var k = 0; k < all.length; k++) {
    var el = all[k];
    var cls = (el.className && typeof el.className === 'string') ? el.className : '';
    var marker = cls + ' ' + (el.id || '');
    if (/advert|promo|related|comment|sidebar/i.test(marker) && el.parentNode) {
      el.parentNode.removeChild(el);
    }
  }
  var nodes = clone.querySelectorAll('h1,h2,h3,p,li');
  var paragraphs = [];
  var headings = 0;
  var bodyStarted = false;
  for (var m = 0; m < nodes.length && paragraphs.length < 400; m++) {
    var node = nodes[m];
    var text = (node.innerText || '').replace(/\s+/g, ' ').trim();
    if (!text) continue;
    var tag = node.tagName;
    var isHeading = (tag === 'H1' || tag === 'H2' || tag === 'H3');
    if (isHeading) {
      if (bodyStarted || text.length < 3 || headings >= 5) continue;
      paragraphs.push(text);
      headings++;
    } else {
      if (text.length < 40) continue;
      bodyStarted = true;
      paragraphs.push(text);
    }
  }
  return JSON.stringify({ title: document.title || '', byline: location.hostname || '', paragraphs: paragraphs });
})()"""

    fun extract(webView: WebView, onResult: (ReaderArticle?) -> Unit) {
        webView.evaluateJavascript(EXTRACT_JS) { raw ->
            var article: ReaderArticle? = null
            try {
                // evaluateJavascript returns the JS string result JSON-quoted -- unwrap it first
                val unquoted = JSONTokener(raw).nextValue().toString()
                val obj = JSONObject(unquoted)
                val arr = obj.getJSONArray("paragraphs")
                val paragraphs = (0 until arr.length()).map { arr.getString(it) }
                if (paragraphs.size >= 2) {
                    article = ReaderArticle(
                        title = obj.optString("title", ""),
                        byline = obj.optString("byline", ""),
                        paragraphs = paragraphs
                    )
                }
            } catch (_: Exception) { }
            onResult(article)
        }
    }
}