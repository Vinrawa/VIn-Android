package com.vin.browser.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslCertificate
import android.os.Build
import android.os.Environment
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vin.browser.adblock.AdBlockEngine
import com.vin.browser.adblock.VinWebViewClient
import com.vin.browser.data.SiteControlSettings
import com.vin.browser.data.TabThumbnailManager
import com.vin.browser.engine.BackgroundAudioEngine
import com.vin.browser.engine.MediaBridge
import com.vin.browser.service.MediaPlaybackService
import com.vin.browser.ui.theme.Sizes
import com.vin.browser.ui.theme.Space
import kotlin.math.abs

private fun mobileUserAgent(context: Context): String =
    WebSettings.getDefaultUserAgent(context).replace("; wv", "")

/**
 * Forced AMOLED Dark Mode for webpages.
 * API 33+: androidx.webkit algorithmic darkening; API 26-32: legacy force dark strategy.
 * Applies to settings live (no reload); diff-checked against the current settings state.
 */
private fun applyForceDarkSettings(webView: WebView, enabled: Boolean) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (WebSettingsCompat.isAlgorithmicDarkeningAllowed(webView.settings) != enabled) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, enabled)
            }
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            // Legacy force dark getters/setters are deprecated but still functional pre-API 33
            @Suppress("DEPRECATION")
            val targetMode = if (enabled) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
            @Suppress("DEPRECATION")
            if (WebSettingsCompat.getForceDark(webView.settings) != targetMode) {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(webView.settings, targetMode)
            }
        }
    } catch (_: Exception) { }
}

/**
 * Google Safe Browsing for WebView navigations (malware / phishing warnings).
 * androidx.webkit 1.13 removed WebViewFeature.SAFE_BROWSING; the settings
 * getter/setter pair remains and diff-checks like forced dark does.
 */
private fun applySafeBrowsingSettings(webView: WebView, enabled: Boolean) {
    try {
        if (WebSettingsCompat.getSafeBrowsingEnabled(webView.settings) != enabled) {
            WebSettingsCompat.setSafeBrowsingEnabled(webView.settings, enabled)
        }
    } catch (_: Exception) { }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    tabId: String,
    url: String,
    loadEvent: Pair<Int, String> = 0 to "",
    isLiteMode: Boolean,
    isBackgroundPlay: Boolean,
    isIncognito: Boolean = false,
    isForcedDark: Boolean = false,
    isSafeBrowsing: Boolean = true,
    isHttpsUpgrade: Boolean = true,
    siteSettings: SiteControlSettings,
    findQuery: String = "",
    findNextTrigger: Int = 0,
    findPrevTrigger: Int = 0,
    onFindResult: (activeMatch: Int, totalMatches: Int) -> Unit = { _, _ -> },
    onProgressUpdate: (Int) -> Unit = {},
    onWebViewCreated: (WebView) -> Unit = {},
    onPageStarted: (String) -> Unit,
    onPageFinished: (title: String, url: String, cert: SslCertificate?, icon: Bitmap?, isPrivate: Boolean) -> Unit,
    onPermissionNeeded: (PermissionRequest) -> Unit = {},
    onTrackerBlocked: () -> Unit,
    onOpenInBackgroundTab: (String) -> Unit = {},
    onOpenInForegroundTab: (String) -> Unit = {},
    userScriptsProvider: () -> List<com.vin.browser.data.UserScript> = { emptyList() },
    modifier: Modifier = Modifier
) {
    var linkTarget by remember { mutableStateOf<com.vin.browser.ui.components.LinkTarget?>(null) }
    val onLinkContext: (com.vin.browser.ui.components.LinkTarget) -> Unit = { linkTarget = it }
    var currentWebView by remember { mutableStateOf<WebView?>(null) }
    val currentSettings by rememberUpdatedState(siteSettings)
    val httpsState by rememberUpdatedState(isHttpsUpgrade)
    val userScriptsState by rememberUpdatedState(userScriptsProvider)
    val permissionHandler by rememberUpdatedState(onPermissionNeeded)
    val scheme = MaterialTheme.colorScheme
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = androidx.compose.ui.platform.LocalContext.current
    // Last WebView background color applied (null until factory ran) -- diff-check anchor
    var appliedWebBg by remember { mutableStateOf<Int?>(null) }
    val view = LocalView.current

    // Pull-to-refresh + top load progress state (drives the 2dp top indicator)
    var ptrRefreshing by remember { mutableStateOf(false) }
    var localProgress by remember { mutableIntStateOf(0) }
    // Edge swipe progress (0..1 per edge) for the back/forward affordances
    var startEdgeDrag by remember { mutableStateOf(0f) }
    var endEdgeDrag by remember { mutableStateOf(0f) }

    // Fullscreen video state (WebChromeClient custom view)
    var customView by remember { mutableStateOf<View?>(null) }

    fun enterFullscreen(v: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) { callback.onCustomViewHidden(); return }
        customView = v
        (v.parent as? ViewGroup)?.removeView(v)
        try {
            val act = context as? Activity
            act?.window?.let { w ->
                WindowCompat.setDecorFitsSystemWindows(w, false)
                WindowInsetsControllerCompat(w, w.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        } catch (_: Exception) { }
    }

    fun exitFullscreen() {
        try {
            val act = context as? Activity
            act?.window?.let { w ->
                WindowInsetsControllerCompat(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(w, true)
            }
        } catch (_: Exception) { }
        customView = null
    }

    // Hardware back exits fullscreen video first (deepest BackHandler wins)
    BackHandler(enabled = customView != null) { exitFullscreen() }

    // Restore system bars if the composable leaves while fullscreen
    DisposableEffect(Unit) {
        onDispose { if (customView != null) exitFullscreen() }
    }

    // Latest values readable inside pointerInput / chrome-client closures
    val onProgressCallback by rememberUpdatedState(onProgressUpdate)
    val ptrRefreshingRef by rememberUpdatedState(ptrRefreshing)
    val webViewRef by rememberUpdatedState(currentWebView)

    val desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    /** Dispatches non-web schemes to external apps, honoring the per-site setting. */
    fun dispatchExternalUri(target: Uri) {
        try {
            val site = currentSettings
            val s = target.scheme?.lowercase() ?: return
            // Standard app intents open by default; exotic schemes only when the
            // user explicitly allowed "Open Apps" for this site.
            val standard = s in setOf("mailto", "tel", "sms", "geo", "market", "youtube", "whatsapp", "tg")
            if (!standard && !site.openApps) return
            val intent = if (s == "intent") {
                Intent.parseUri(target.toString(), Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, target)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Defensive: an intent:// with a browser component is a known redirect trick
            if (intent.component != null && intent.component?.packageName != context.packageName) {
                return
            }
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    // Handle Media Play/Pause toggle and Stop actions from background media notification
    LaunchedEffect(Unit) {
        MediaPlaybackService.onMediaTogglePlayPauseRequested = {
            currentWebView?.evaluateJavascript(BackgroundAudioEngine.TOGGLE_PLAY_PAUSE_MEDIA_JS, null)
        }
        MediaPlaybackService.onMediaPauseRequested = {
            currentWebView?.evaluateJavascript(BackgroundAudioEngine.PAUSE_ALL_MEDIA_JS, null)
        }
    }

    // Lifecycle Observer: Keep WebView alive & timers running when Background Play is active
    DisposableEffect(lifecycleOwner, isBackgroundPlay, currentSettings.backgroundPlay) {
        val observer = LifecycleEventObserver { _, event ->
            val canPlayBackground = isBackgroundPlay && currentSettings.backgroundPlay
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (!canPlayBackground) {
                        currentWebView?.onPause()
                        currentWebView?.pauseTimers()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    currentWebView?.onResume()
                    currentWebView?.resumeTimers()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(modifier = modifier.fillMaxSize().background(scheme.background)) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    com.vin.browser.engine.VinWebView(ctx).apply {
                        currentWebView = this
                        isBackgroundPlayActive = (isBackgroundPlay && currentSettings.backgroundPlay)
                        onWebViewCreated(this)
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            // Incognito: no WebView databases (form/history persistence)
                            databaseEnabled = !isIncognito

                            // Chrome-like viewport: wide layout window + overview scaling.
                            // Pages with a proper <meta viewport> render at device width;
                            // legacy pages without one fit-to-width instead of being
                            // horizontally cropped/stretched.
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            textZoom = 100
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false

                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            setSupportMultipleWindows(false)
                            mediaPlaybackRequiresUserGesture = false
                            cacheMode = when {
                                isIncognito -> WebSettings.LOAD_NO_CACHE
                                isLiteMode -> WebSettings.LOAD_CACHE_ELSE_NETWORK
                                else -> WebSettings.LOAD_DEFAULT
                            }
                            loadsImagesAutomatically = currentSettings.imagesEnabled && !isLiteMode
                            blockNetworkImage = !currentSettings.imagesEnabled || isLiteMode

                            userAgentString = if (currentSettings.desktopMode) desktopUserAgent else mobileUserAgent(ctx)
                        }

                        if (isIncognito) {
                            // Deprecated no-op in modern WebView but harmless; form data must not persist in private mode
                            try { settings.saveFormData = false } catch (_: Exception) { }
                        }
                        // Block third-party cookies for EVERY tab (first-party cookies
                        // still work). Incognito additionally wipes session cookies when
                        // its last tab closes (BrowserViewModel.wipeIncognitoData).
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                        // Forced AMOLED Dark Mode (settings apply live, no reload needed)
                        applyForceDarkSettings(this, isForcedDark)
                        // Google Safe Browsing (diff-checked via settings getter)
                        applySafeBrowsingSettings(this, isSafeBrowsing)
                        val initialWebBg = if (isForcedDark) android.graphics.Color.BLACK else android.graphics.Color.parseColor("#0F0F0F")
                        appliedWebBg = initialWebBg
                        setBackgroundColor(initialWebBg)

                        // Native Media Bridge for Background Playback Service
                        addJavascriptInterface(
                            MediaBridge(ctx) { isBackgroundPlay && currentSettings.backgroundPlay },
                            BackgroundAudioEngine.JS_BRIDGE_NAME
                        )

                        // Find in Page listener
                        setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                            onFindResult(activeMatchOrdinal, numberOfMatches)
                        }

                        // Long-press on links/images opens the link context menu
                        setOnLongClickListener { v ->
                            val result = (v as? WebView)?.hitTestResult ?: return@setOnLongClickListener false
                            val target = when (result.type) {
                                WebView.HitTestResult.SRC_ANCHOR_TYPE ->
                                    result.extra?.let { com.vin.browser.ui.components.LinkTarget(it) }
                                WebView.HitTestResult.IMAGE_TYPE,
                                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ->
                                    result.extra?.let { com.vin.browser.ui.components.LinkTarget(it, it) }
                                else -> null
                            }
                            if (target != null) {
                                onLinkContext(target)
                                true
                            } else false
                        }

                        // Download Listener
                        setDownloadListener { downloadUrl, _, contentDisposition, mimeType, _ ->
                            if (currentSettings.downloads != "Block") {
                                try {
                                    val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                                        setMimeType(mimeType)
                                        val filename = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                                        setTitle(filename)
                                        setDescription("Downloading file...")
                                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                                    }
                                    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                    dm.enqueue(request)
                                    Toast.makeText(ctx, "Download started...", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                    Toast.makeText(ctx, "Download error", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(ctx, "Downloads blocked for this site", Toast.LENGTH_SHORT).show()
                            }
                        }

                        webViewClient = VinWebViewClient(
                            engine = AdBlockEngine.instance,
                            isBackgroundPlayEnabled = { isBackgroundPlay && currentSettings.backgroundPlay },
                            isHttpsUpgradeEnabled = { httpsState },
                            userScriptsProvider = { userScriptsState() },
                            onStatsUpdated = {
                                onTrackerBlocked()
                            },
                            onResourceBlocked = { _, _ ->
                                onTrackerBlocked()
                            },
                            onPopupBlocked = { blockedUrl ->
                                try {
                                    val host = Uri.parse(blockedUrl)?.host?.removePrefix("www.")?.take(30) ?: "site"
                                    Toast.makeText(ctx, "Blocked redirect to $host", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) { }
                            },
                            onExternalScheme = { target -> dispatchExternalUri(target) },
                            onPageStartedCallback = { loadUrl ->
                                onPageStarted(loadUrl)
                            },
                            onPageFinishedCallback = { title, finalUrl, cert, icon ->
                                if (currentSettings.audioMuted) {
                                    currentWebView?.evaluateJavascript(
                                        "document.querySelectorAll('video, audio').forEach(el => el.muted = true);",
                                        null
                                    )
                                }
                                // Capture card thumbnail preview for Tab Tray (never in incognito)
                                if (!isIncognito) {
                                    TabThumbnailManager.captureFromWebView(tabId, this)
                                }
                                // Incognito pages persist no favicon either
                                onPageFinished(title, finalUrl, cert, if (isIncognito) null else icon, isIncognito)
                            }
                        )

                        webChromeClient = object : WebChromeClient() {
                            // target=_blank links: extract the URL and open it in a
                            // background tab instead of silently doing nothing.
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: android.os.Message?
                            ): Boolean {
                                val temp = WebView(ctx).apply { settings.javaScriptEnabled = true }
                                temp.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        v: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        onOpenInBackgroundTab(url)
                                        v?.destroy()
                                        return true
                                    }
                                }
                                // WebView hands us the transport to grab the target URL
                                val transport = resultMsg?.obj as? WebView.WebViewTransport
                                transport?.webView = temp
                                resultMsg?.sendToTarget()
                                return true
                            }

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                onProgressCallback(newProgress)
                                localProgress = newProgress
                                // Reload finished ? arm pull-to-refresh for the next gesture
                                if (newProgress >= 100) ptrRefreshing = false
                            }

                            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                                super.onReceivedIcon(view, icon)
                                if (!isIncognito && icon != null && view?.url != null) {
                                    onPageFinished(view.title ?: "", view.url ?: "", view.certificate, icon, isIncognito)
                                }
                            }

                            // Fullscreen <video> support: the player's fullscreen button
                            // swaps in a native full-screen overlay view (hidden system
                            // bars, back exits). Previously this did nothing at all.
                            override fun onShowCustomView(v: View, callback: CustomViewCallback) {
                                enterFullscreen(v, callback)
                            }

                            override fun onHideCustomView() {
                                exitFullscreen()
                            }

                            // Site permissions (camera / mic / geolocation) are forwarded
                            // to MainActivity which owns runtime permissions + the "Ask" dialog.
                            override fun onPermissionRequest(request: PermissionRequest?) {
                                if (request == null) return
                                permissionHandler(request)
                            }

                            override fun onGeolocationPermissionsShowPrompt(
                                origin: String?,
                                callback: GeolocationPermissions.Callback?
                            ) {
                                val allow = currentSettings.location == "Allow"
                                callback?.invoke(origin, allow, false)
                            }
                        }
                    }
                },
                update = { webView ->
                    currentWebView = webView

                    if (webView is com.vin.browser.engine.VinWebView) {
                        webView.isBackgroundPlayActive = (isBackgroundPlay && currentSettings.backgroundPlay)
                    }

                    val imagesOn = currentSettings.imagesEnabled && !isLiteMode
                    if (webView.settings.loadsImagesAutomatically != imagesOn) {
                        webView.settings.loadsImagesAutomatically = imagesOn
                        webView.settings.blockNetworkImage = !imagesOn
                    }

                    val targetCache = when {
                        isIncognito -> WebSettings.LOAD_NO_CACHE
                        isLiteMode -> WebSettings.LOAD_CACHE_ELSE_NETWORK
                        else -> WebSettings.LOAD_DEFAULT
                    }
                    if (webView.settings.cacheMode != targetCache) {
                        webView.settings.cacheMode = targetCache
                    }

                    val targetUa = if (currentSettings.desktopMode) desktopUserAgent else mobileUserAgent(webView.context)
                    if (webView.settings.userAgentString != targetUa) {
                        webView.settings.userAgentString = targetUa
                        webView.reload()
                    }

                    // Forced AMOLED Dark Mode: diff-checked, applies live without reload
                    applyForceDarkSettings(webView, isForcedDark)
                    val targetWebBg = if (isForcedDark) android.graphics.Color.BLACK else android.graphics.Color.parseColor("#0F0F0F")
                    if (appliedWebBg != targetWebBg) {
                        webView.setBackgroundColor(targetWebBg)
                        appliedWebBg = targetWebBg
                    }

                    // Google Safe Browsing: diff-checked like forced dark
                    applySafeBrowsingSettings(webView, isSafeBrowsing)

                    if (currentSettings.audioMuted) {
                        webView.evaluateJavascript(
                            "document.querySelectorAll('video, audio').forEach(el => el.muted = true);",
                            null
                        )
                    }

                    // Handle Find in Page search query updates
                    if (findQuery.isNotBlank()) {
                        webView.findAllAsync(findQuery)
                    } else {
                        webView.clearMatches()
                    }
                },
                onRelease = { webView ->
                    webView.destroy()
                },
                modifier = Modifier.fillMaxSize()
            )

            // Fullscreen video overlay: covers everything (incl. browser chrome)
            customView?.let { cv ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { cv },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Top load progress line (page navigation + pull-to-refresh reload)
            if (ptrRefreshing || localProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { localProgress / 100f },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(Sizes.progressHeight)
                        .alpha(0.9f),
                    color = scheme.primary,
                    trackColor = Color.Transparent,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }

            // Pull-to-refresh top zone: 26dp top strip overlaps sticky headers -- tradeoff accepted.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(26.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var total = 0f
                                var valid = true
                                drag(down.id) { change ->
                                    val dy = change.positionChange().y
                                    val dx = change.positionChange().x
                                    // Only a mostly-vertical downward drag pulls to refresh
                                    if (dy <= 0f || (dx != 0f && abs(dx) > abs(dy))) valid = false
                                    if (valid) {
                                        total += dy
                                        change.consume()
                                    }
                                }
                                if (valid && total > 130f && !ptrRefreshingRef) {
                                    val wv = webViewRef
                                    if (wv != null && (wv as? com.vin.browser.engine.VinWebView)?.isAtTop() != false) {
                                        ptrRefreshing = true
                                        wv.reload()
                                    }
                                }
                            }
                        }
                    }
            )

            // Start (left) edge strip: swipe right ? go back
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(22.dp)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var totalDx = 0f
                                var valid = true
                                drag(down.id) { change ->
                                    val dx = change.positionChange().x
                                    val dy = change.positionChange().y
                                    // Vertical intent ? don't consume, let the page scroll
                                    if (valid && abs(dy) > abs(dx)) valid = false
                                    if (valid) {
                                        totalDx += dx
                                        startEdgeDrag = (totalDx / 90f).coerceIn(0f, 1f)
                                        change.consume()
                                    }
                                }
                                if (valid && totalDx > 90f) {
                                    val wv = webViewRef
                                    if (wv?.canGoBack() == true) {
                                        wv.goBack()
                                    } else {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    }
                                }
                                startEdgeDrag = 0f
                            }
                        }
                    }
            )

            // Start edge back affordance
            if (startEdgeDrag > 0.05f) {
                Surface(
                    shape = CircleShape,
                    color = scheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = Space.md)
                        .size(56.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = scheme.onSurface.copy(alpha = startEdgeDrag * 0.7f),
                            modifier = Modifier.size(Sizes.iconLg)
                        )
                    }
                }
            }

            // End (right) edge strip: swipe left ? go forward
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(22.dp)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var totalDx = 0f
                                var valid = true
                                drag(down.id) { change ->
                                    val dx = change.positionChange().x
                                    val dy = change.positionChange().y
                                    // Vertical intent ? don't consume, let the page scroll
                                    if (valid && abs(dy) > abs(dx)) valid = false
                                    if (valid) {
                                        totalDx += dx
                                        endEdgeDrag = ((-totalDx) / 90f).coerceIn(0f, 1f)
                                        change.consume()
                                    }
                                }
                                if (valid && totalDx < -90f) {
                                    val wv = webViewRef
                                    if (wv?.canGoForward() == true) {
                                        wv.goForward()
                                    } else {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    }
                                }
                                endEdgeDrag = 0f
                            }
                        }
                    }
            )

            // End edge forward affordance
            if (endEdgeDrag > 0.05f) {
                Surface(
                    shape = CircleShape,
                    color = scheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = Space.md)
                        .size(56.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            tint = scheme.onSurface.copy(alpha = endEdgeDrag * 0.7f),
                            modifier = Modifier.size(Sizes.iconLg)
                        )
                    }
                }
            }
        }
    }

    // Handle External URL navigation (Omnibox, Speed Dial, Tab Switch) via explicit Nonce Event
    LaunchedEffect(loadEvent) {
        val (nonce, targetUrl) = loadEvent
        if (nonce > 0 && targetUrl.isNotBlank()) {
            currentWebView?.loadUrl(targetUrl)
        }
    }

    // Handle Find Next navigation
    LaunchedEffect(findNextTrigger) {
        if (findNextTrigger > 0) {
            currentWebView?.findNext(true)
        }
    }

    // Handle Find Previous navigation
    LaunchedEffect(findPrevTrigger) {
        if (findPrevTrigger > 0) {
            currentWebView?.findNext(false)
        }
    }

    // Long-press link/image context menu
    linkTarget?.let { target ->
        com.vin.browser.ui.components.LinkContextSheet(
            target = target,
            onDismiss = { linkTarget = null },
            onOpenInNewTab = { u -> onOpenInForegroundTab(u) },
            onOpenInBackground = { u -> onOpenInBackgroundTab(u) },
            onDownload = { u ->
                currentWebView?.post {
                    try {
                        val filename = URLUtil.guessFileName(u, null, null)
                        val request = DownloadManager.Request(Uri.parse(u)).apply {
                            setMimeType("application/octet-stream")
                            setTitle(filename)
                            setDescription("Downloading file...")
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                        }
                        val dm = currentWebView!!.context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        dm.enqueue(request)
                        Toast.makeText(currentWebView!!.context, "Download started...", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(currentWebView?.context, "Download error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}
