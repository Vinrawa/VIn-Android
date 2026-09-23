package com.vin.browser

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vin.browser.data.StorageService
import com.vin.browser.engine.ReaderExtractor
import com.vin.browser.ui.components.*
import com.vin.browser.ui.screens.*
import com.vin.browser.ui.theme.Radius
import com.vin.browser.ui.theme.Space
import com.vin.browser.ui.theme.VinBrowserTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val browserVm: BrowserViewModel by viewModels()

    // PiP window state observed by Compose -- UI chrome is hidden while in PiP
    private var isInPipMode by mutableStateOf(false)

    // True only while the web screen is the active surface -- auto-PiP must not fire from home/search
    @Volatile
    private var isWebScreenActive: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themePreset by browserVm.themePreset.collectAsState()
            val isDarkTheme = when (themePreset) {
                "light" -> false
                "dark", "amoled" -> true
                "sepia" -> false
                else -> isSystemInDarkTheme()
            }
            VinBrowserTheme(darkTheme = isDarkTheme, themePreset = themePreset) {
                VinBrowserRoot(
                    browserVm,
                    onExitApp = { moveTaskToBack(true) },
                    isInPipMode = isInPipMode,
                    onWebScreenActiveChange = { isWebScreenActive = it }
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        isInPipMode = isInPictureInPictureMode
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    // Auto-PiP fires only while the background-play service reports media playing on the web screen
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (com.vin.browser.service.MediaPlaybackService.isServiceRunning &&
            com.vin.browser.service.MediaPlaybackService.isMediaPlaying &&
            isWebScreenActive &&
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            try {
                enterPictureInPictureMode(
                    android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(16, 9))
                        .build()
                )
            } catch (_: Exception) { }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            com.vin.browser.service.MediaPlaybackService.stop(this)
        }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VinBrowserRoot(
    vm: BrowserViewModel,
    onExitApp: () -> Unit,
    isInPipMode: Boolean = false,
    onWebScreenActiveChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val tabs by vm.tabs.collectAsState()
    val activeTabId by vm.activeTabId.collectAsState()
    val showTabTray by vm.showTabTray.collectAsState()
    val currentScreen by vm.currentScreen.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val isSearching by vm.isSearching.collectAsState()
    val selectedEngineId by vm.selectedEngineId.collectAsState()
    val webViewUrl by vm.webViewUrl.collectAsState()
    val trustInfo by vm.trustInfo.collectAsState()
    val siteSettings by vm.siteSettings.collectAsState()
    val showTrustSheet by vm.showTrustSheet.collectAsState()
    val showEngineSelector by vm.showEngineSelector.collectAsState()
    val speedDials by vm.speedDials.collectAsState()
    val isLiteMode by vm.isLiteMode.collectAsState()
    val isForcedDark by vm.isForcedDark.collectAsState()
    val isSafeBrowsing by vm.isSafeBrowsing.collectAsState()
    val isHttpsUpgrade by vm.isHttpsUpgrade.collectAsState()
    val isRemoteSuggestions by vm.isRemoteSuggestions.collectAsState()
    val isBackgroundPlay by vm.isBackgroundPlay.collectAsState()
    val trackersBlocked by vm.trackersBlocked.collectAsState()
    val showMenu by vm.showMenu.collectAsState()
    val showHistory by vm.showHistory.collectAsState()
    val showBookmarks by vm.showBookmarks.collectAsState()
    val showPrivacyDashboard by vm.showPrivacyDashboard.collectAsState()
    val currentDomain by vm.currentDomain.collectAsState()
    val showOnboarding by vm.showOnboarding.collectAsState()
    val loadEvent by vm.loadEvent.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val showDoctorSheet by vm.showDoctorSheet.collectAsState()
    val doctorMetrics by vm.doctorMetrics.collectAsState()
    val showQrScanner by vm.showQrScanner.collectAsState()
    val autoTuneResult by vm.autoTuneResult.collectAsState()
    val isTuning by vm.isTuning.collectAsState()

    var showSearchOverlay by remember { mutableStateOf(false) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var pageLoadProgress by remember { mutableIntStateOf(0) }

    // Find in Page State
    var showFindInPage by remember { mutableStateOf(false) }
    var findInPageQuery by remember { mutableStateOf("") }
    var findActiveIndex by remember { mutableIntStateOf(0) }
    var findTotalMatches by remember { mutableIntStateOf(0) }
    var findNextTrigger by remember { mutableIntStateOf(0) }
    var findPrevTrigger by remember { mutableIntStateOf(0) }

    // Reader View State (article null = not yet extracted)
    var showReader by remember { mutableStateOf(false) }
    var readerArticle by remember { mutableStateOf<ReaderArticle?>(null) }
    var readerLoading by remember { mutableStateOf(false) }
    var readerFailed by remember { mutableStateOf(false) }

    // Translate Page State
    var showTranslateDialog by remember { mutableStateOf(false) }

    // Reader extraction failsafe
    LaunchedEffect(readerLoading) {
        if (readerLoading) {
            delay(8000)
            if (readerLoading) {
                readerLoading = false
                readerFailed = true
            }
        }
    }

    // Report web-screen visibility to the Activity so auto-PiP only fires from the web view
    LaunchedEffect(currentScreen) {
        onWebScreenActiveChange(currentScreen == "web")
    }

    // Active Tab Info
    val currentTab = tabs.find { it.id == activeTabId }

    // -- Runtime permissions: requested CONTEXTUALLY, never demanded at startup --
    // (The old code fired CAMERA+MIC+LOCATION+NOTIFICATIONS at every cold start.)
    fun hasPermission(p: String): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(context, p) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    // Pending runtime grant that must complete a WEB permission request afterwards
    var pendingWebPermission by remember {
        mutableStateOf<android.webkit.PermissionRequest?>(null)
    }
    // Web permission waiting on the user's Allow/Deny answer ("Ask" sites)
    var askWebPermission by remember {
        mutableStateOf<android.webkit.PermissionRequest?>(null)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val pending = pendingWebPermission
        pendingWebPermission = null
        if (pending != null) {
            if (grants.values.all { it }) pending.grant(pending.resources)
            else pending.deny()
        }
    }

    fun requestOsPermissions(perms: List<String>, then: android.webkit.PermissionRequest?) {
        pendingWebPermission = then
        permissionLauncher.launch(perms.toTypedArray())
    }

    // Which site-permission resources are being asked for
    fun resourceNames(request: android.webkit.PermissionRequest): Triple<Boolean, Boolean, Boolean> {
        val res = request.resources
        return Triple(
            res.contains(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE),
            res.contains(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE),
            false
        )
    }

    // Central handler owned by the Activity (WebView forwards requests here).
    // [userApproved] is true when the user already answered Allow on the "Ask" dialog.
    fun handleWebPermissionRequest(request: android.webkit.PermissionRequest, userApproved: Boolean = false) {
        val (camera, mic, location) = resourceNames(request)
        val setting = if (camera) siteSettings.camera
            else if (mic) siteSettings.microphone
            else if (location) siteSettings.location
            else "Ask"

        if (setting == "Block") {
            request.deny()
            return
        }

        fun grantOrRequest() {
            val osPerms = buildList {
                if (camera) add(Manifest.permission.CAMERA)
                if (mic) add(Manifest.permission.RECORD_AUDIO)
                if (location) add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            val missing = osPerms.filter { !hasPermission(it) }
            if (missing.isEmpty()) request.grant(request.resources)
            else requestOsPermissions(missing, request)
        }

        if (setting == "Allow" || userApproved) {
            grantOrRequest()
        } else {
            // "Ask": surface an in-app dialog BEFORE touching the OS permission
            askWebPermission = request
        }
    }

    // Camera is requested only when the user actually opens the QR scanner
    LaunchedEffect(showQrScanner) {
        if (showQrScanner && !hasPermission(Manifest.permission.CAMERA)) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    // Notification permission is requested the first time the user turns
    // Background Play ON -- not at every app start.
    val previousBackgroundPlay = remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isBackgroundPlay) {
        val wasOn = previousBackgroundPlay.value
        previousBackgroundPlay.value = isBackgroundPlay
        if (wasOn == false && isBackgroundPlay &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    // Speech to text voice search launcher
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                vm.updateSearchQuery(spokenText)
                vm.performSearch()
            }
        }
    }

    // System Share Page Action
    val shareCurrentPage: () -> Unit = {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "$webViewUrl\n${currentTab?.title ?: ""}")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Page")
        context.startActivity(shareIntent)
    }

    // Open Downloads Action
    val openDownloads: () -> Unit = {
        try {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Downloads manager not found", Toast.LENGTH_SHORT).show()
        }
    }

    // Webpage translation via Google's translate.goog proxy
    fun translateTo(code: String) {
        val currentUrl = webViewUrl
        if (!currentUrl.startsWith("http")) {
            Toast.makeText(context, "Open a page first", Toast.LENGTH_SHORT).show()
        } else {
            try {
                val uri = java.net.URI(currentUrl)
                val host = uri.host ?: throw IllegalArgumentException("no host")
                val path = uri.rawPath ?: ""
                val query = uri.rawQuery
                val base = "https://" + host.replace(".", "-") + ".translate.goog" + path + (if (query != null) "?$query" else "")
                val sep = if (query != null) "&" else "?"
                vm.setTranslateLang(code)
                vm.navigateToUrl(base + sep + "_x_tr_sl=auto&_x_tr_tl=" + code + "&_x_tr_hl=en")
            } catch (_: Exception) {
                Toast.makeText(context, "Could not translate this page", Toast.LENGTH_SHORT).show()
            }
        }
        showTranslateDialog = false
    }

    val languages = listOf(
        "hi" to "Hindi",
        "en" to "English",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "ar" to "Arabic",
        "bn" to "Bengali",
        "ta" to "Tamil",
        "te" to "Telugu",
        "mr" to "Marathi",
        "gu" to "Gujarati",
        "pa" to "Punjabi",
        "ja" to "Japanese",
        "ko" to "Korean",
        "zh-CN" to "Chinese (Simplified)",
        "ru" to "Russian",
        "pt" to "Portuguese",
        "it" to "Italian",
        "ur" to "Urdu"
    )

    // Hardware / System Back Button Navigation Handler
    BackHandler(enabled = true) {
        when {
            showReader -> showReader = false
            showOnboarding -> vm.completeOnboarding()
            showFindInPage -> {
                showFindInPage = false
                findInPageQuery = ""
            }
            showSearchOverlay -> {
                showSearchOverlay = false
                vm.onSuggestionQueryChanged("")
            }
            showMenu -> vm.dismissMenu()
            showPrivacyDashboard -> vm.dismissPrivacyDashboard()
            showDoctorSheet -> vm.dismissDoctorSheet()
            showEngineSelector -> vm.toggleEngineSelector()
            showTrustSheet -> vm.toggleTrustSheet()
            showTabTray -> vm.dismissTabTray()
            showHistory -> vm.toggleHistory()
            showBookmarks -> vm.toggleBookmarks()
            currentScreen == "web" -> {
                if (activeWebView?.canGoBack() == true) {
                    activeWebView?.goBack()
                } else {
                    vm.goHome()
                }
            }
            currentScreen == "search" -> vm.goHome()
            else -> onExitApp()
        }
    }

    Scaffold(
        snackbarHost = {
            Box(Modifier.navigationBarsPadding()) {
                SnackbarHost(snackbarHostState)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.fillMaxSize()
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Contextual Top Bar
                if (currentScreen == "web" && !isInPipMode) {
                    WebTopBar(
                        title = currentTab?.title ?: webViewUrl,
                        url = webViewUrl,
                        isSecure = trustInfo?.isSecure == true,
                        isLoading = currentTab?.isLoading == true,
                        progress = pageLoadProgress,
                        trackersBlocked = trackersBlocked,
                        isIncognito = currentTab?.isIncognito == true,
                        onShieldClick = { vm.toggleTrustSheet() },
                        onUrlClick = { showSearchOverlay = true }
                    )
                } else if (currentScreen == "search" && !isInPipMode) {
                    WebTopBar(
                        title = "Search: $searchQuery",
                        url = "",
                        isSecure = true,
                        isLoading = isSearching,
                        progress = if (isSearching) 50 else 100,
                        trackersBlocked = trackersBlocked,
                        isIncognito = currentTab?.isIncognito == true,
                        onShieldClick = { vm.toggleTrustSheet() },
                        onUrlClick = { showSearchOverlay = true }
                    )
                }

                // Find In Page Bar Overlay with vertical slide animation
                AnimatedVisibility(
                    visible = showFindInPage && currentScreen == "web" && !isInPipMode,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut()
                ) {
                    FindInPageBar(
                        query = findInPageQuery,
                        currentIndex = findActiveIndex,
                        totalMatches = findTotalMatches,
                        onQueryChange = { findInPageQuery = it },
                        onNext = { findNextTrigger++ },
                        onPrevious = { findPrevTrigger++ },
                        onClose = {
                            showFindInPage = false
                            findInPageQuery = ""
                        }
                    )
                }

                // Main Content Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (currentScreen) {
                        "home" -> HomeScreen(
                            selectedEngineId = selectedEngineId,
                            speedDials = speedDials,
                            onOmniboxClick = { showSearchOverlay = true },
                            onSpeedDialClick = { vm.navigateToUrl(it) },
                            onAddSpeedDial = { name, url -> vm.addSpeedDial(name, url) },
                            onUpdateSpeedDial = { id, name, url -> vm.updateSpeedDial(id, name, url) },
                            onDeleteSpeedDial = { id -> vm.deleteSpeedDial(id) },
                            onEngineClick = { vm.toggleEngineSelector() },
                            onMicClick = {
                                try {
                                    val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak to search...")
                                    }
                                    voiceSearchLauncher.launch(intent)
                                } catch (_: Exception) {
                                    showSearchOverlay = true
                                }
                            },
                            onQrScanClick = { vm.toggleQrScanner() },
                            modifier = Modifier.fillMaxSize()
                        )
                        "search" -> SearchResultsScreen(
                            results = searchResults,
                            isSearching = isSearching,
                            query = searchQuery,
                            onResultClick = { vm.navigateToUrl(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                        "web" -> {
                            key(activeTabId) {
                                WebViewScreen(
                                    tabId = activeTabId,
                                    url = webViewUrl,
                                    loadEvent = loadEvent,
                                    isLiteMode = isLiteMode,
                                    isBackgroundPlay = isBackgroundPlay,
                                    isIncognito = currentTab?.isIncognito == true,
                                    isForcedDark = isForcedDark,
                                    isSafeBrowsing = isSafeBrowsing,
                                    isHttpsUpgrade = isHttpsUpgrade,
                                    siteSettings = siteSettings,
                                    findQuery = findInPageQuery,
                                    findNextTrigger = findNextTrigger,
                                    findPrevTrigger = findPrevTrigger,
                                    onFindResult = { active, total ->
                                        findActiveIndex = active
                                        findTotalMatches = total
                                    },
                                    onProgressUpdate = { pageLoadProgress = it },
                                    onWebViewCreated = { activeWebView = it },
                                    onPageStarted = { vm.onPageStarted(it) },
                                    onPageFinished = { title, url, cert, icon, isPrivate ->
                                        pageLoadProgress = 100
                                        vm.onPageFinished(title, url, cert, icon, isPrivate)
                                    },
                                    onPermissionNeeded = { request -> handleWebPermissionRequest(request) },
                                    userScriptsProvider = { vm.userScripts.value },
                                    onTrackerBlocked = { vm.refreshTrackerCount() },
                                    onOpenInBackgroundTab = { backgroundUrl -> vm.openInBackgroundTab(backgroundUrl) },
                                    onOpenInForegroundTab = { newTabUrl -> vm.openInNewTab(newTabUrl) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // Universal 5-Icon Bottom Bar
                if (!isInPipMode) {
                    BottomNavBar(
                        tabCount = tabs.size,
                        isHome = (currentScreen == "home"),
                        onHomeClick = { vm.goHome() },
                        onSearchClick = { showSearchOverlay = true },
                        onNewTab = { vm.addTab() },
                        onTabTrayClick = { vm.toggleTabTray() },
                        onMenuClick = { vm.toggleMenu() },
                        onSwipeNextTab = { vm.swipeNextTab() },
                        onSwipePrevTab = { vm.swipePrevTab() }
                    )
                }
            }

            // Reader Mode overlay
            if (showReader && !isInPipMode) {
                ReaderScreen(
                    article = readerArticle,
                    isLoading = readerLoading,
                    isFailed = readerFailed,
                    onClose = {
                        showReader = false
                        readerLoading = false
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // 0. Site permission "Ask" dialog (camera / mic / location)
    askWebPermission?.let { request ->
        val (wantCam, wantMic, wantLoc) = Triple(
            request.resources.contains(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE),
            request.resources.contains(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE),
            true
        )
        val what = when {
            wantCam && wantMic -> "use your camera and microphone"
            wantCam -> "use your camera"
            wantMic -> "use your microphone"
            wantLoc -> "see your location"
            else -> "access protected features"
        }
        AlertDialog(
            onDismissRequest = {
                request.deny()
                askWebPermission = null
            },
            title = { Text("Permission request") },
            text = { Text("$currentDomain wants to $what.") },
            confirmButton = {
                TextButton(onClick = {
                    askWebPermission = null
                    handleWebPermissionRequest(request, userApproved = true)
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = {
                    request.deny()
                    askWebPermission = null
                }) { Text("Deny") }
            }
        )
    }

    // 1. Mobile Search Overlay
    if (showSearchOverlay && !isInPipMode) {
        SearchOverlaySheet(
            initialQuery = if (currentScreen == "web") webViewUrl else searchQuery,
            selectedEngineId = selectedEngineId,
            suggestions = suggestions,
            onSearch = { query ->
                showSearchOverlay = false
                vm.updateSearchQuery(query)
                vm.performSearch()
            },
            onQueryChange = { vm.onSuggestionQueryChanged(it) },
            onEngineClick = { vm.toggleEngineSelector() },
            onDismiss = {
                showSearchOverlay = false
                vm.onSuggestionQueryChanged("")
            }
        )
    }

    // 2. Mobile Tab Tray Sheet
    if (showTabTray && !isInPipMode) {
        TabTraySheet(
            tabs = tabs,
            activeTabId = activeTabId,
            incognitoCount = tabs.count { it.isIncognito },
            onTabClick = { vm.switchTab(it) },
            onCloseTab = { vm.closeTab(it) },
            onCloseAllTabs = {
                val backup = vm.closeAllTabs()
                coroutineScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "All tabs closed",
                        actionLabel = "UNDO",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.restoreTabs(backup)
                    }
                }
            },
            onNewTab = { vm.addTab() },
            onNewIncognitoTab = { vm.addIncognitoTab() },
            onDismiss = { vm.dismissTabTray() }
        )
    }

    // 3. Engine Selector Modal
    if (showEngineSelector && !isInPipMode) {
        AlertDialog(
            onDismissRequest = { vm.toggleEngineSelector() },
            confirmButton = {},
            containerColor = Color.Transparent,
            text = {
                EngineSelector(
                    selectedId = selectedEngineId,
                    onSelect = { vm.selectEngine(it) },
                    onDismiss = { vm.toggleEngineSelector() }
                )
            }
        )
    }

    // 4. Menu Bottom Sheet
    if (showMenu && !isInPipMode) {
        MenuSheet(
            isLiteMode = isLiteMode,
            isForcedDark = isForcedDark,
            isDesktopMode = siteSettings.desktopMode,
            onDismiss = { vm.dismissMenu() },
            onReload = {
                activeWebView?.reload()
                vm.dismissMenu()
            },
            onShare = {
                vm.dismissMenu()
                shareCurrentPage()
            },
            onFindInPage = {
                vm.dismissMenu()
                showFindInPage = true
            },
            onDesktopModeToggle = {
                vm.toggleDesktopMode()
                vm.dismissMenu()
            },
            onDownloadsClick = {
                vm.dismissMenu()
                openDownloads()
            },
            onNewTab = {
                vm.dismissMenu()
                vm.addTab()
            },
            onNewIncognitoTab = {
                vm.dismissMenu()
                vm.addIncognitoTab()
            },
            onAddBookmark = {
                vm.addBookmark()
                vm.dismissMenu()
            },
            onBookmarkClick = {
                vm.dismissMenu()
                vm.toggleBookmarks()
            },
            onHistoryClick = {
                vm.dismissMenu()
                vm.toggleHistory()
            },
            onLiteModeToggle = {
                vm.toggleLiteMode()
            },
            onForcedDarkToggle = {
                vm.toggleForcedDark()
            },
            onPrivacyDashboardClick = {
                vm.dismissMenu()
                vm.togglePrivacyDashboard()
            },
            onDoctorClick = {
                vm.dismissMenu()
                vm.toggleDoctorSheet()
            },
            onReaderViewClick = {
                vm.dismissMenu()
                showReader = true
                readerLoading = true
                readerFailed = false
                readerArticle = null
                val readerWebView = activeWebView
                if (readerWebView == null) {
                    readerLoading = false
                    readerFailed = true
                } else {
                    ReaderExtractor.extract(readerWebView) { article ->
                        readerLoading = false
                        if (article == null) {
                            readerFailed = true
                        } else {
                            readerArticle = article
                        }
                    }
                }
            },
            onTranslateClick = {
                vm.dismissMenu()
                showTranslateDialog = true
            },
            onSettingsClick = {
                vm.dismissMenu()
                vm.togglePrivacyDashboard()
            },
            onReadingListClick = {
                vm.dismissMenu()
                vm.toggleReadingList()
            },
            onCopyLink = {
                vm.dismissMenu()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("url", webViewUrl)
                clipboard.setPrimaryClip(clip)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("URL copied to clipboard")
                }
            },
            onOpenExternal = {
                vm.dismissMenu()
                try {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(webViewUrl))
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "No app found to open this URL", Toast.LENGTH_SHORT).show()
                }
            },
            onQrGenerate = {
                vm.dismissMenu()
                vm.toggleQrGenerator()
            },
            onScreenshot = {
                vm.dismissMenu()
                try {
                    val webView = activeWebView ?: return@MenuSheet
                    val bitmap = android.graphics.Bitmap.createBitmap(webView.width, webView.height, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    webView.draw(canvas)
                    val filename = "ViN_Screenshot_${System.currentTimeMillis()}.png"
                    val file = java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
                        filename
                    )
                    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                    Toast.makeText(context, "Screenshot saved to Pictures", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(context, "Failed to capture screenshot", Toast.LENGTH_SHORT).show()
                }
            },
            onCookieManager = {
                vm.dismissMenu()
                vm.toggleCookieManager()
            },
            onUserScripts = {
                vm.dismissMenu()
                vm.toggleUserScriptsSheet()
            },
            onThemePicker = {
                vm.dismissMenu()
                vm.toggleThemePicker()
            }
        )
    }

    // 5. Privacy & Ad-Blocker Hub Dashboard Sheet
    if (showPrivacyDashboard && !isInPipMode) {
        val lastFilterSync = remember { StorageService(context).getLastFilterSync() }
        PrivacyDashboardSheet(
            currentDomain = currentDomain,
            isBackgroundPlay = isBackgroundPlay,
            isSafeBrowsing = isSafeBrowsing,
            isHttpsUpgrade = isHttpsUpgrade,
            isRemoteSuggestions = isRemoteSuggestions,
            lastFilterSync = lastFilterSync,
            onBackgroundPlayToggle = { vm.toggleBackgroundPlay() },
            onSafeBrowsingToggle = { vm.toggleSafeBrowsing() },
            onHttpsUpgradeToggle = { vm.toggleHttpsUpgrade() },
            onRemoteSuggestionsToggle = { vm.toggleRemoteSuggestions() },
            onDismiss = { vm.dismissPrivacyDashboard() }
        )
    }

    // 5b. System Health & Doctor Diagnostic Sheet
    if (showDoctorSheet && !isInPipMode) {
        DoctorDiagnosticSheet(
            metrics = doctorMetrics,
            autoTuneResult = autoTuneResult,
            isTuning = isTuning,
            onRunAutoTune = { vm.runDoctorAutoTune(activeWebView) },
            onDismiss = { vm.dismissDoctorSheet() }
        )
    }

    // 6. Site Settings & Trust Bottom Sheet
    if (showTrustSheet && !isInPipMode) {
        trustInfo?.let { info ->
            TrustSheet(
                trustInfo = info,
                siteSettings = siteSettings,
                onUpdateSettings = { vm.updateSiteSettings(it) },
                onDismiss = { vm.toggleTrustSheet() }
            )
        }
    }

    // 7. History Sheet
    if (showHistory && !isInPipMode) {
        HistorySheet(
            historyItems = vm.getHistoryItems(),
            onItemClick = { url ->
                vm.toggleHistory()
                vm.navigateToUrl(url)
            },
            onClearHistory = { vm.clearHistory() },
            onDismiss = { vm.toggleHistory() }
        )
    }

    // 8. Bookmarks Sheet
    if (showBookmarks && !isInPipMode) {
        BookmarksSheet(
            bookmarkItems = vm.getBookmarkItems(),
            onItemClick = { url ->
                vm.toggleBookmarks()
                vm.navigateToUrl(url)
            },
            onDismiss = { vm.toggleBookmarks() }
        )
    }

    // 9. First-Run Onboarding Modal
    if (showOnboarding && !isInPipMode) {
        OnboardingScreen(
            selectedEngineId = selectedEngineId,
            onSelectEngine = { vm.selectEngine(it) },
            onFinish = { vm.completeOnboarding() }
        )
    }

    // 10. Translate Page Language Dialog
    if (showTranslateDialog) {
        val scheme = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = { showTranslateDialog = false },
            confirmButton = {},
            containerColor = scheme.surfaceContainer,
            title = { Text("Translate Page") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    languages.forEach { (code, label) ->
                        Text(
                            label,
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sm))
                                .clickable { translateTo(code) }
                                .padding(vertical = Space.md, horizontal = Space.sm),
                            color = scheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        )
    }

    // 11. Reading List Sheet
    val showReadingList by vm.showReadingList.collectAsState()
    val readingListItems by vm.readingListItems.collectAsState()
    if (showReadingList && !isInPipMode) {
        ReadingListSheet(
            items = readingListItems,
            onItemClick = { url ->
                vm.dismissReadingList()
                vm.navigateToUrl(url)
            },
            onRemoveItem = { vm.removeFromReadingList(it) },
            onSaveOffline = { vm.saveReadingOffline(it, activeWebView) },
            onClearAll = { vm.clearReadingList() },
            onDismiss = { vm.dismissReadingList() }
        )
    }

    // 12. Cookie Manager Sheet
    val showCookieManager by vm.showCookieManager.collectAsState()
    if (showCookieManager && !isInPipMode) {
        CookieManagerSheet(
            domain = currentDomain,
            onDismiss = { vm.dismissCookieManager() }
        )
    }

    // 13. QR Generator Sheet
    val showQrGenerator by vm.showQrGenerator.collectAsState()
    if (showQrGenerator && !isInPipMode) {
        QrGeneratorSheet(
            url = webViewUrl,
            onDismiss = { vm.dismissQrGenerator() }
        )
    }

    // 14. Theme Picker Sheet
    val showThemePicker by vm.showThemePicker.collectAsState()
    val themePreset by vm.themePreset.collectAsState()
    if (showThemePicker && !isInPipMode) {
        ThemePickerSheet(
            currentPreset = themePreset,
            onSelectTheme = { vm.setThemePreset(it); vm.dismissThemePicker() },
            onDismiss = { vm.dismissThemePicker() }
        )
    }

    // 15. Download Manager Sheet
    val showDownloadManager by vm.showDownloadManager.collectAsState()
    val downloads by vm.downloads.collectAsState()
    if (showDownloadManager && !isInPipMode) {
        DownloadManagerSheet(
            downloads = downloads,
            onClearAll = { vm.clearDownloads() },
            onDismiss = { vm.dismissDownloadManager() }
        )
    }

    // 16. User Scripts Sheet
    val showUserScripts by vm.showUserScripts.collectAsState()
    val userScripts by vm.userScripts.collectAsState()
    if (showUserScripts && !isInPipMode) {
        UserScriptSheet(
            scripts = userScripts,
            onToggleEnabled = { vm.toggleUserScriptEnabled(it) },
            onSave = { vm.saveUserScript(it) },
            onDelete = { vm.deleteUserScript(it) },
            onDismiss = { vm.dismissUserScripts() }
        )
    }

    // 17. QR Scanner Screen
    if (showQrScanner && !isInPipMode) {
        QrScannerScreen(
            onDismiss = { vm.dismissQrScanner() },
            onUrlScanned = { url ->
                vm.dismissQrScanner()
                vm.navigateToUrl(url)
            }
        )
    }
}