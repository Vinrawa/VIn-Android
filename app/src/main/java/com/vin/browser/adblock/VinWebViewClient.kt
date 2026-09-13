package com.vin.browser.adblock

import android.graphics.Bitmap
import android.net.http.SslCertificate
import android.net.http.SslError
import android.webkit.*
import com.vin.browser.engine.BackgroundAudioEngine
import java.io.ByteArrayInputStream
import java.net.URL

class VinWebViewClient(
    private val engine: AdBlockEngine = AdBlockEngine.instance,
    private val isBackgroundPlayEnabled: () -> Boolean = { false },
    private val onStatsUpdated: (PageStats) -> Unit = {},
    private val onResourceBlocked: (url: String, siteHost: String) -> Unit = { _, _ -> },
    private val onPageStartedCallback: (url: String) -> Unit = {},
    private val onPageFinishedCallback: (title: String, url: String, cert: SslCertificate?, icon: Bitmap?) -> Unit = { _, _, _, _ -> },
    private val onPopupBlocked: (url: String) -> Unit = {}
) : WebViewClient() {

    private var currentStats = PageStats()
    private var currentPageHost: String = ""

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return super.shouldOverrideUrlLoading(view, request)
        val host = uri.host?.lowercase() ?: ""
        // Popup / redirect blocker: cancels navigations to known popunder &
        // redirect-ad networks while leaving same-site navigation untouched.
        if (host.isNotEmpty() && engine.shouldBlockPopup(uri.toString(), host, currentPageHost)) {
            onPopupBlocked(uri.toString())
            return true // cancel navigation
        }

        return super.shouldOverrideUrlLoading(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        currentStats = PageStats()
        currentPageHost = url?.let { runCatching { URL(it).host }.getOrNull() } ?: ""

        // 1. Page visibility spoofing injected immediately at document start
        if (view != null && isBackgroundPlayEnabled()) {
            BackgroundAudioEngine.inject(view, true)
        }

        url?.let { onPageStartedCallback(it) }
    }

    /**
     * Handles SPA (Single Page Application) pushState / replaceState URL transitions
     * (e.g. YouTube video change, Twitter timeline navigation) so loading spinners resolve.
     */
    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        if (!url.isNullOrBlank() && view != null) {
            currentPageHost = runCatching { URL(url).host }.getOrNull() ?: ""
            if (currentPageHost.contains("youtube.com")) {
                YouTubeFocus.inject(view, YouTubeFocusSettings.default())
            }
            onPageFinishedCallback(view.title ?: "", url, view.certificate, view.favicon)
        }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val uri = request?.url ?: return super.shouldInterceptRequest(view, request)
        // Never intercept the main document: a wrongly-matched rule would otherwise
        // return an empty page (blank screen) instead of loading the site.
        if (request.isForMainFrame) {
            return super.shouldInterceptRequest(view, request)
        }
        val reqHost = uri.host?.lowercase() ?: return super.shouldInterceptRequest(view, request)
        val reqUrl = uri.toString()

        // 1. Instant Fast Path for media chunks, video decoders & CDN static assets
        if (reqHost.endsWith(".googlevideo.com") || reqHost.endsWith(".ytimg.com") || reqHost.endsWith(".ggpht.com")) {
            return super.shouldInterceptRequest(view, request)
        }

        val result = engine.shouldBlock(reqUrl, reqHost, currentPageHost)
        currentStats.record(result)
        onStatsUpdated(currentStats)

        return if (result != null) {
            onResourceBlocked(reqUrl, currentPageHost)
            // Empty 200 OK body — prevents sites from retrying aggressively on 403/404
            WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
        } else {
            super.shouldInterceptRequest(view, request)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view ?: return
        val title = view.title ?: ""
        val finalUrl = url ?: view.url ?: ""
        val cert = view.certificate
        val icon = view.favicon

        // Anti-cookie banner / overlay killer + EasyList element-hiding selectors
        if (!currentPageHost.contains("youtube.com")) {
            CleanPage.inject(view, engine.getCosmeticSelectors(currentPageHost))
        }

        // YouTube distraction / ad killer
        if (currentPageHost.contains("youtube.com")) {
            YouTubeFocus.inject(view, YouTubeFocusSettings.default())
        }

        // Re-inject visibility spoofing on page finish
        if (isBackgroundPlayEnabled()) {
            BackgroundAudioEngine.inject(view, true)
        }

        onPageFinishedCallback(title, finalUrl, cert, icon)
    }

    /**
     * Renderer Process Crash Protection (C1):
     * Handles Chromium renderer process death due to OS low memory (OOM) or internal GPU crash.
     * Returning true informs Android the host app handled the event, preventing an app crash.
     */
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        view?.let { wv ->
            val currentUrl = wv.url
            if (!currentUrl.isNullOrBlank()) {
                wv.loadUrl(currentUrl)
            }
        }
        return true
    }

    /**
     * Main-Frame Network Error Handling (C2):
     * Displays a clean AMOLED-friendly offline/error page instead of infinite loading or a blank screen.
     */
    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true && view != null) {
            val failingUrl = request.url.toString()
            val description = error?.description?.toString() ?: "Connection failed"
            showErrorPage(view, failingUrl, description)
        }
    }

    /**
     * SSL Certificate Error Handling (C2):
     * Rejects invalid/expired/untrusted SSL certificates and displays security warning.
     */
    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        handler?.cancel()
        if (view != null) {
            val failingUrl = error?.url ?: view.url ?: ""
            showErrorPage(view, failingUrl, "SSL Certificate Untrusted or Expired")
        }
    }

    private fun showErrorPage(view: WebView, failingUrl: String, errorDescription: String) {
        val errorHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                <style>
                    body {
                        background-color: #0A0A0F;
                        color: #E2E8F0;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        height: 100vh;
                        margin: 0;
                        padding: 24px;
                        box-sizing: border-box;
                        text-align: center;
                    }
                    .icon {
                        width: 56px;
                        height: 56px;
                        border-radius: 28px;
                        background: #1A1C2B;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        margin-bottom: 20px;
                        font-size: 24px;
                    }
                    h1 {
                        font-size: 20px;
                        font-weight: 700;
                        margin: 0 0 8px 0;
                        color: #FFFFFF;
                    }
                    p {
                        font-size: 14px;
                        color: #94A3B8;
                        margin: 0 0 24px 0;
                        line-height: 1.5;
                        max-width: 320px;
                        word-break: break-word;
                    }
                    .btn {
                        background: #3B82F6;
                        color: #FFFFFF;
                        border: none;
                        padding: 12px 28px;
                        border-radius: 24px;
                        font-size: 14px;
                        font-weight: 600;
                        cursor: pointer;
                        text-decoration: none;
                    }
                </style>
            </head>
            <body>
                <div class="icon">⚠️</div>
                <h1>Unable to load page</h1>
                <p>${escapeHtml(errorDescription)}</p>
                <button class="btn" onclick="location.href='${escapeHtml(failingUrl)}'">Try Again</button>
            </body>
            </html>
        """.trimIndent()
        view.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
    }

    private fun escapeHtml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
