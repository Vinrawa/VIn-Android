package com.vin.browser

import android.app.Application
import android.graphics.Bitmap
import android.net.http.SslCertificate
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vin.browser.adblock.AdBlockEngine
import com.vin.browser.data.*
import com.vin.browser.doctor.*
import com.vin.browser.engine.TrustEvaluator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.UUID

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = StorageService(application.applicationContext)
    private var searchJob: Job? = null

    // Tabs
    private val _tabs = MutableStateFlow(listOf(TabState(id = UUID.randomUUID().toString())))
    val tabs: StateFlow<List<TabState>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow(_tabs.value.first().id)
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    private val _showTabTray = MutableStateFlow(false)
    val showTabTray: StateFlow<Boolean> = _showTabTray.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _selectedEngineId = MutableStateFlow(storage.getSelectedEngine())
    val selectedEngineId: StateFlow<String> = _selectedEngineId.asStateFlow()

    // Live search suggestions (history matches + debounced network suggestions)
    private val _suggestions = MutableStateFlow<List<SuggestionItem>>(emptyList())
    val suggestions: StateFlow<List<SuggestionItem>> = _suggestions.asStateFlow()
    private var suggestionJob: Job? = null

    // Navigation
    private val _currentScreen = MutableStateFlow("home")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    private val _webViewUrl = MutableStateFlow("")
    val webViewUrl: StateFlow<String> = _webViewUrl.asStateFlow()

    // Explicit Load Event with Nonce (Decouples user intent from internal SPA navigations)
    private var loadNonce = 0
    private val _loadEvent = MutableStateFlow(0 to "")
    val loadEvent: StateFlow<Pair<Int, String>> = _loadEvent.asStateFlow()

    // Per-site Trust & Settings
    private val _currentDomain = MutableStateFlow("")
    val currentDomain: StateFlow<String> = _currentDomain.asStateFlow()

    private val _trustInfo = MutableStateFlow<SiteTrustInfo?>(null)
    val trustInfo: StateFlow<SiteTrustInfo?> = _trustInfo.asStateFlow()

    private val _siteSettings = MutableStateFlow(SiteControlSettings())
    val siteSettings: StateFlow<SiteControlSettings> = _siteSettings.asStateFlow()

    private val _showTrustSheet = MutableStateFlow(false)
    val showTrustSheet: StateFlow<Boolean> = _showTrustSheet.asStateFlow()

    // Speed Dial
    private val _speedDials = MutableStateFlow(storage.getSpeedDials())
    val speedDials: StateFlow<List<SpeedDialItem>> = _speedDials.asStateFlow()

    // Engine selector
    private val _showEngineSelector = MutableStateFlow(false)
    val showEngineSelector: StateFlow<Boolean> = _showEngineSelector.asStateFlow()

    // Lite mode
    private val _isLiteMode = MutableStateFlow(storage.isLiteModeEnabled())
    val isLiteMode: StateFlow<Boolean> = _isLiteMode.asStateFlow()

    // Forced AMOLED dark mode for webpages
    private val _isForcedDark = MutableStateFlow(storage.isForcedDarkEnabled())
    val isForcedDark: StateFlow<Boolean> = _isForcedDark.asStateFlow()

    // Google Safe Browsing (WebView malware/phishing protection)
    private val _isSafeBrowsing = MutableStateFlow(storage.isSafeBrowsingEnabled())
    val isSafeBrowsing: StateFlow<Boolean> = _isSafeBrowsing.asStateFlow()

    // Trackers blocked count
    private val _trackersBlocked = MutableStateFlow(0)
    val trackersBlocked: StateFlow<Int> = _trackersBlocked.asStateFlow()

    // History & Bookmarks panels
    private val _showHistory = MutableStateFlow(false)
    val showHistory: StateFlow<Boolean> = _showHistory.asStateFlow()

    private val _showBookmarks = MutableStateFlow(false)
    val showBookmarks: StateFlow<Boolean> = _showBookmarks.asStateFlow()

    private val _showMenu = MutableStateFlow(false)
    val showMenu: StateFlow<Boolean> = _showMenu.asStateFlow()

    private val _showPrivacyDashboard = MutableStateFlow(false)
    val showPrivacyDashboard: StateFlow<Boolean> = _showPrivacyDashboard.asStateFlow()

    // System Health & Doctor Telemetry
    private val _showDoctorSheet = MutableStateFlow(false)
    val showDoctorSheet: StateFlow<Boolean> = _showDoctorSheet.asStateFlow()

    private val _doctorMetrics = MutableStateFlow(BrowserDoctor.instance.getLiveMetrics())
    val doctorMetrics: StateFlow<HealthMetrics> = _doctorMetrics.asStateFlow()

    private val _autoTuneResult = MutableStateFlow<AutoTuneResult?>(null)
    val autoTuneResult: StateFlow<AutoTuneResult?> = _autoTuneResult.asStateFlow()

    private val _isTuning = MutableStateFlow(false)
    val isTuning: StateFlow<Boolean> = _isTuning.asStateFlow()

    // Global Privacy & Ad-Blocker Settings
    private val _isGlobalAdBlock = MutableStateFlow(storage.isGlobalAdBlockEnabled())
    val isGlobalAdBlock: StateFlow<Boolean> = _isGlobalAdBlock.asStateFlow()

    private val _isCosmeticFilter = MutableStateFlow(storage.isCosmeticFilterEnabled())
    val isCosmeticFilter: StateFlow<Boolean> = _isCosmeticFilter.asStateFlow()

    private val _isTrackerBlock = MutableStateFlow(storage.isTrackerBlockEnabled())
    val isTrackerBlock: StateFlow<Boolean> = _isTrackerBlock.asStateFlow()

    private val _isHttpsUpgrade = MutableStateFlow(storage.isHttpsUpgradeEnabled())
    val isHttpsUpgrade: StateFlow<Boolean> = _isHttpsUpgrade.asStateFlow()

    private val _isCryptoBlock = MutableStateFlow(storage.isCryptoBlockEnabled())
    val isCryptoBlock: StateFlow<Boolean> = _isCryptoBlock.asStateFlow()

    fun toggleGlobalAdBlock() {
        val newVal = !_isGlobalAdBlock.value
        _isGlobalAdBlock.value = newVal
        storage.setGlobalAdBlock(newVal)
        AdBlockEngine.instance.isGlobalAdBlockEnabled = newVal
    }

    fun toggleCosmeticFilter() {
        val newVal = !_isCosmeticFilter.value
        _isCosmeticFilter.value = newVal
        storage.setCosmeticFilter(newVal)
        AdBlockEngine.instance.isCosmeticFilterEnabled = newVal
    }

    fun toggleTrackerBlock() {
        val newVal = !_isTrackerBlock.value
        _isTrackerBlock.value = newVal
        storage.setTrackerBlock(newVal)
        AdBlockEngine.instance.isTrackerBlockEnabled = newVal
    }

    fun toggleHttpsUpgrade() {
        val newVal = !_isHttpsUpgrade.value
        _isHttpsUpgrade.value = newVal
        storage.setHttpsUpgrade(newVal)
    }

    fun toggleCryptoBlock() {
        val newVal = !_isCryptoBlock.value
        _isCryptoBlock.value = newVal
        storage.setCryptoBlock(newVal)
        AdBlockEngine.instance.isCryptoBlockEnabled = newVal
    }

    fun toggleDoctorSheet() {
        _doctorMetrics.value = BrowserDoctor.instance.getLiveMetrics(_tabs.value.size, _isBackgroundPlay.value)
        _showDoctorSheet.value = !_showDoctorSheet.value
    }

    fun dismissDoctorSheet() {
        _showDoctorSheet.value = false
    }

    fun runDoctorAutoTune(activeWebView: android.webkit.WebView?) {
        viewModelScope.launch {
            _isTuning.value = true
            delay(500)
            val res = BrowserDoctor.instance.performAutoTune(getApplication(), activeWebView)
            _autoTuneResult.value = res
            _doctorMetrics.value = BrowserDoctor.instance.getLiveMetrics(_tabs.value.size, _isBackgroundPlay.value)
            _isTuning.value = false
        }
    }

    // Background Play
    private val _isBackgroundPlay = MutableStateFlow(storage.isBackgroundPlayEnabled())
    val isBackgroundPlay: StateFlow<Boolean> = _isBackgroundPlay.asStateFlow()

    private val commonTlds = setOf("com", "org", "net", "edu", "gov", "io", "co", "in", "app", "dev", "ai", "me", "xyz", "info", "tech", "site", "online", "tv", "cc")

    // Engines whose privacy stance should route suggestion lookups through DDG
    private val privacySuggestionEngines = setOf("duckduckgo", "brave", "searxng", "startpage", "marginalia")

    // Bang shortcut → target engine (search is routed through that engine for this query only)
    private val engineBangs = mapOf(
        "g" to "google",
        "d" to "duckduckgo",
        "ddg" to "duckduckgo",
        "b" to "bing",
        "r" to "reddit",
        "br" to "brave"
    )

    // Bang shortcut → (home URL, "%s" search URL template)
    private val urlBangs = mapOf(
        "yt" to ("https://m.youtube.com" to "https://m.youtube.com/results?search_query=%s"),
        "w" to ("https://www.wikipedia.org" to "https://en.wikipedia.org/w/index.php?search=%s"),
        "wiki" to ("https://www.wikipedia.org" to "https://en.wikipedia.org/w/index.php?search=%s"),
        "gh" to ("https://github.com" to "https://github.com/search?q=%s"),
        "a" to ("https://www.amazon.in" to "https://www.amazon.in/s?k=%s"),
        "s" to ("https://stackoverflow.com" to "https://stackoverflow.com/search?q=%s"),
        "x" to ("https://x.com" to "https://x.com/search?q=%s")
    )

    private val bangRegex = Regex("^!(\\w+)(?:\\s+(.*))?$")

    fun togglePrivacyDashboard() { _showPrivacyDashboard.value = !_showPrivacyDashboard.value }
    fun dismissPrivacyDashboard() { _showPrivacyDashboard.value = false }

    fun toggleBackgroundPlay() {
        val newVal = !_isBackgroundPlay.value
        _isBackgroundPlay.value = newVal
        storage.setBackgroundPlay(newVal)
        if (!newVal) {
            com.vin.browser.service.MediaPlaybackService.stop(getApplication())
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun performSearch() {
        suggestionJob?.cancel()
        val q = _searchQuery.value.trim()
        if (q.isEmpty()) return
        _suggestions.value = emptyList()

        // 0. Bang shortcuts ("!yt cats", "!g phones") — unknown bangs fall through
        if (handleBangSearch(q)) return

        // 1. Check if direct URL (e.g. "youtube.com", "https://chess.com", "localhost:8080")
        val isExplicitUrl = q.startsWith("http://", ignoreCase = true) || q.startsWith("https://", ignoreCase = true)
        val hostPart = q.substringBefore("/").substringBefore(":")
        val tld = hostPart.substringAfterLast(".", "").lowercase()
        val isLocalhost = hostPart.equals("localhost", ignoreCase = true) || hostPart.startsWith("192.168.") || hostPart.startsWith("10.") || hostPart.startsWith("127.0.0.1")
        val isValidWebUrl = isExplicitUrl || isLocalhost || (!q.contains(" ") && (commonTlds.contains(tld) || Patterns.WEB_URL.matcher("https://$q").matches()))

        if (isValidWebUrl) {
            val url = if (isExplicitUrl) q else "https://$q"
            navigateToUrl(url)
            return
        }

        // 2. Engine Routing via selected engine
        routeQueryThroughEngine(q)
    }

    /**
     * Handles "!bang" queries. Returns true when a known bang consumed the query.
     * Engine bangs temporarily override the selected engine for THIS search only
     * (storage persistence untouched); URL bangs navigate to their site search.
     */
    private fun handleBangSearch(q: String): Boolean {
        val match = bangRegex.find(q) ?: return false
        val bang = match.groupValues[1].lowercase()
        val rest = match.groupValues[2].trim()

        engineBangs[bang]?.let { targetEngine ->
            val previousEngineId = _selectedEngineId.value
            _selectedEngineId.value = targetEngine
            try {
                if (rest.isEmpty()) {
                    navigateToUrl(SearchProviders.getEngine(targetEngine).directUrl)
                } else {
                    routeQueryThroughEngine(rest)
                }
            } finally {
                _selectedEngineId.value = previousEngineId
            }
            return true
        }

        urlBangs[bang]?.let { (homeUrl, searchTemplate) ->
            navigateToUrl(
                if (rest.isEmpty()) homeUrl
                else String.format(searchTemplate, try { URLEncoder.encode(rest, "UTF-8") } catch (_: Exception) { rest })
            )
            return true
        }

        return false // Unknown bang → normal search of the whole string
    }

    private fun routeQueryThroughEngine(q: String) {
        if (_selectedEngineId.value == "all") {
            searchJob?.cancel()
            _isSearching.value = true
            _currentScreen.value = "search"
            updateActiveTab(title = "Search: $q", url = "vin://search?q=$q", isHome = false)

            searchJob = viewModelScope.launch {
                try {
                    val results = SearchProviders.executeSearch(q, "all")
                    _searchResults.value = results
                } catch (_: Exception) {
                    _searchResults.value = emptyList()
                } finally {
                    _isSearching.value = false
                }
            }
        } else {
            // Navigate directly to provider search interface (Google / Yahoo / DDG / Bing UI)
            val engine = SearchProviders.getEngine(_selectedEngineId.value)
            val encodedQuery = try { URLEncoder.encode(q, "UTF-8") } catch (_: Exception) { q }
            val queryUrl = try { String.format(engine.queryUrl, encodedQuery) } catch (_: Exception) { "https://www.google.com/search?q=$encodedQuery" }
            navigateToUrl(queryUrl)
        }
    }

    /** Debounced live suggestions: instant history matches + 250ms-delayed network lookups. */
    fun onSuggestionQueryChanged(query: String) {
        suggestionJob?.cancel()
        val q = query.trim()
        // Skip network + lookups for empty/1-char queries
        if (q.length < 2) {
            _suggestions.value = emptyList()
            return
        }

        val historyItems = storage.getHistory()
            .filter { it.first.contains(q, ignoreCase = true) || it.second.contains(q, ignoreCase = true) }
            .distinctBy { it.second }
            .take(3)
            .map { SuggestionItem(value = it.second, label = it.first.ifBlank { it.second }, isHistory = true) }
        _suggestions.value = historyItems

        suggestionJob = viewModelScope.launch {
            delay(250)
            val isPrivacyEngine = _selectedEngineId.value in privacySuggestionEngines
            val network = try {
                SearchProviders.suggest(q, isPrivacyEngine)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            val networkItems = network
                .filter { s -> historyItems.none { it.value.equals(s, ignoreCase = true) } }
                .map { s -> SuggestionItem(value = s, label = s) }
            _suggestions.value = historyItems + networkItems
        }
    }

    fun navigateToUrl(url: String) {
        val finalUrl = if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) url else "https://$url"
        _webViewUrl.value = finalUrl
        _currentScreen.value = "web"

        val domain = extractDomain(finalUrl)
        _currentDomain.value = domain
        _siteSettings.value = storage.getSiteSettings(domain)
        applySiteAdBlockSetting(domain, _siteSettings.value)
        _trustInfo.value = TrustEvaluator.evaluate(finalUrl, null)
        _trackersBlocked.value = AdBlockEngine.instance.getSiteBlockedCount(domain)

        updateActiveTab(title = domain, url = finalUrl, isHome = false, isLoading = true)

        loadNonce++
        _loadEvent.value = loadNonce to finalUrl
    }

    fun goHome() {
        _currentScreen.value = "home"
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        updateActiveTab(title = "New Tab", url = "", isHome = true)
    }

    fun selectEngine(id: String) {
        _selectedEngineId.value = id
        storage.setSelectedEngine(id)
        _showEngineSelector.value = false
        if (_searchQuery.value.isNotBlank() && _currentScreen.value == "search") {
            performSearch()
        }
    }

    fun toggleEngineSelector() { _showEngineSelector.value = !_showEngineSelector.value }
    fun toggleLiteMode() { _isLiteMode.value = !_isLiteMode.value; storage.setLiteMode(_isLiteMode.value) }
    fun toggleForcedDark() { _isForcedDark.value = !_isForcedDark.value; storage.setForcedDark(_isForcedDark.value) }
    fun toggleSafeBrowsing() { _isSafeBrowsing.value = !_isSafeBrowsing.value; storage.setSafeBrowsing(_isSafeBrowsing.value) }
    fun toggleTrustSheet() { _showTrustSheet.value = !_showTrustSheet.value }

    fun updateSiteSettings(settings: SiteControlSettings) {
        _siteSettings.value = settings
        val domain = _currentDomain.value
        if (domain.isNotBlank()) {
            storage.saveSiteSettings(domain, settings)
            applySiteAdBlockSetting(domain, settings)
        }
    }

    private fun applySiteAdBlockSetting(domain: String, settings: SiteControlSettings) {
        if (domain.isBlank()) return
        if (settings.adsBlocked) {
            AdBlockEngine.instance.enableForSite(domain)
        } else {
            AdBlockEngine.instance.disableForSite(domain)
        }
    }

    fun toggleHistory() { _showHistory.value = !_showHistory.value }
    fun toggleBookmarks() { _showBookmarks.value = !_showBookmarks.value }
    fun toggleTabTray() { _showTabTray.value = !_showTabTray.value }
    fun dismissTabTray() { _showTabTray.value = false }
    fun toggleMenu() { _showMenu.value = !_showMenu.value }
    fun dismissMenu() { _showMenu.value = false }

    fun setTranslateLang(code: String) { storage.setTranslateTargetLang(code) }

    fun addBookmark() {
        val tab = getActiveTab()
        if (tab != null && tab.url.isNotBlank()) {
            storage.addBookmark(tab.title, tab.url)
        }
    }

    fun getHistoryItems() = storage.getHistory()
    fun getBookmarkItems() = storage.getBookmarks()
    fun clearHistory() = storage.clearHistory()

    private val _showOnboarding = MutableStateFlow(!storage.isOnboardingCompleted())
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    private var tabsBackup: List<TabState> = emptyList()

    fun completeOnboarding() {
        storage.setOnboardingCompleted(true)
        _showOnboarding.value = false
    }

    fun toggleDesktopMode() {
        val current = _siteSettings.value
        val updated = current.copy(desktopMode = !current.desktopMode)
        updateSiteSettings(updated)
    }

    fun addSpeedDial(title: String, url: String) {
        storage.addSpeedDial(title, url)
        _speedDials.value = storage.getSpeedDials()
    }

    fun updateSpeedDial(id: String, title: String, url: String) {
        storage.updateSpeedDial(id, title, url)
        _speedDials.value = storage.getSpeedDials()
    }

    fun deleteSpeedDial(id: String) {
        storage.deleteSpeedDial(id)
        _speedDials.value = storage.getSpeedDials()
    }

    fun swipeNextTab() {
        val currentTabs = _tabs.value
        if (currentTabs.size <= 1) return
        val currentIndex = currentTabs.indexOfFirst { it.id == _activeTabId.value }
        val nextIndex = (currentIndex + 1) % currentTabs.size
        switchTab(currentTabs[nextIndex].id)
    }

    fun swipePrevTab() {
        val currentTabs = _tabs.value
        if (currentTabs.size <= 1) return
        val currentIndex = currentTabs.indexOfFirst { it.id == _activeTabId.value }
        val prevIndex = if (currentIndex <= 0) currentTabs.size - 1 else currentIndex - 1
        switchTab(currentTabs[prevIndex].id)
    }

    fun closeAllTabs(): List<TabState> {
        val backup = _tabs.value
        tabsBackup = backup
        val hadIncognito = backup.any { it.isIncognito }
        val newTab = TabState(id = UUID.randomUUID().toString())
        _tabs.value = listOf(newTab)
        _activeTabId.value = newTab.id
        goHome()
        if (hadIncognito) wipeIncognitoData()
        return backup
    }

    fun restoreTabs(restored: List<TabState>) {
        if (restored.isNotEmpty()) {
            _tabs.value = restored
            _activeTabId.value = restored.first().id
            switchTab(restored.first().id)
        }
    }

    fun addTab() {
        val newTab = TabState(id = UUID.randomUUID().toString())
        _tabs.value = _tabs.value + newTab
        _activeTabId.value = newTab.id
        _showTabTray.value = false
        goHome()
        hibernateExcessTabs()
    }

    /**
     * Opens [url] in a new tab without switching to it ("Open in background").
     * The tab joins the tab list as-is; WebView creation happens when the user
     * switches to it, matching the single-live-WebView architecture.
     */
    fun openInBackgroundTab(url: String): String {
        val isIncognito = getActiveTab()?.isIncognito == true
        val newTab = TabState(
            id = UUID.randomUUID().toString(),
            title = extractDomain(url),
            url = url,
            isHome = false,
            isIncognito = isIncognito,
            isLoading = false
        )
        _tabs.value = _tabs.value + newTab
        hibernateExcessTabs()
        return newTab.id
    }

    /** Opens [url] in a fresh tab and switches to it. */
    fun openInNewTab(url: String) {
        val id = openInBackgroundTab(url)
        switchTab(id)
        loadNonce++
        _loadEvent.value = loadNonce to url
    }

    fun addIncognitoTab() {
        val newTab = TabState(id = UUID.randomUUID().toString(), isIncognito = true)
        _tabs.value = _tabs.value + newTab
        _activeTabId.value = newTab.id
        _showTabTray.value = false
        goHome()
        hibernateExcessTabs()
    }

    val incognitoCount: Int get() = _tabs.value.count { it.isIncognito }

    fun switchTab(id: String) {
        // Wake a hibernated tab on open; its URL reloads through the existing loadEvent flow
        val wakingTab = _tabs.value.find { it.id == id }
        if (wakingTab?.isHibernated == true) {
            _tabs.value = _tabs.value.map { if (it.id == id) it.copy(isHibernated = false) else it }
        }
        _activeTabId.value = id
        _showTabTray.value = false
        val tab = _tabs.value.find { it.id == id }
        if (tab != null) {
            if (tab.isHome) {
                _currentScreen.value = "home"
            } else if (tab.url.startsWith("vin://search")) {
                _currentScreen.value = "search"
            } else if (tab.url.isNotBlank()) {
                _currentScreen.value = "web"
                _webViewUrl.value = tab.url
                val domain = extractDomain(tab.url)
                _currentDomain.value = domain
                _siteSettings.value = storage.getSiteSettings(domain)
                applySiteAdBlockSetting(domain, _siteSettings.value)
                _trustInfo.value = TrustEvaluator.evaluate(tab.url, null)
                _trackersBlocked.value = AdBlockEngine.instance.getSiteBlockedCount(domain)

                loadNonce++
                _loadEvent.value = loadNonce to tab.url
            }
        }
    }

    /**
     * Smart Tab Hibernation: architecture keeps a single live WebView (only the active
     * tab is real), so hibernation releases favicon/thumbnail bitmaps only. Once the
     * tab count reaches 10, inactive tabs beyond the active tab + last 7 in list order
     * (approximate recency) drop their favicon and preview bitmap. Hibernated tabs
     * wake on switchTab(); incognito tabs need nothing extra — closing the last one
     * wipes session data anyway.
     */
    private fun hibernateExcessTabs() {
        val tabs = _tabs.value
        if (tabs.size < 10) return
        val keepSet = buildSet {
            add(_activeTabId.value)
            tabs.takeLast(7).forEach { add(it.id) }
        }
        _tabs.value = tabs.map { tab ->
            if (tab.id in keepSet || tab.isHibernated) tab
            else {
                TabThumbnailManager.removeThumbnail(tab.id)
                tab.copy(isHibernated = true, favicon = null)
            }
        }
    }

    fun closeTab(id: String) {
        val closingTab = _tabs.value.find { it.id == id } ?: return
        TabThumbnailManager.removeThumbnail(id)
        if (_tabs.value.size <= 1) {
            if (closingTab.isIncognito) {
                // Exit incognito completely: swap in a fresh normal home tab
                val newTab = TabState(id = UUID.randomUUID().toString())
                _tabs.value = listOf(newTab)
                _activeTabId.value = newTab.id
                goHome()
                wipeIncognitoData()
            } else {
                goHome()
            }
            return
        }
        val idx = _tabs.value.indexOfFirst { it.id == id }
        val remaining = _tabs.value.filter { it.id != id }
        _tabs.value = remaining
        if (_activeTabId.value == id) {
            val nextId = remaining[maxOf(0, minOf(idx, remaining.size - 1))].id
            switchTab(nextId)
        }
        // Last incognito tab just closed → wipe session cookies + cache traces
        if (closingTab.isIncognito && remaining.none { it.isIncognito }) {
            wipeIncognitoData()
        }
    }

    /**
     * Clears traces left by incognito browsing once the last incognito tab is gone:
     * session cookies (not all cookies — that would log out normal tabs) plus the
     * WebView disk cache directories. Runs off the main thread where possible.
     */
    private fun wipeIncognitoData() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                android.webkit.CookieManager.getInstance().removeSessionCookies(null)
                android.webkit.CookieManager.getInstance().flush()
            } catch (_: Exception) { }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = getApplication<Application>().cacheDir
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name == "WebView" || file.name == "Cache" ||
                        file.name == "Code Cache" || file.name.startsWith("org.chromium")) {
                        file.deleteRecursively()
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun onPageStarted(url: String) {
        BrowserDoctor.instance.recordPageStarted(url)
        _webViewUrl.value = url
        val domain = extractDomain(url)
        _currentDomain.value = domain
        _siteSettings.value = storage.getSiteSettings(domain)
        updateActiveTab(url = url, isLoading = true)
    }

    fun onPageFinished(title: String, url: String, certificate: SslCertificate?, favicon: Bitmap?) {
        BrowserDoctor.instance.recordPageFinished(url)
        _doctorMetrics.value = BrowserDoctor.instance.getLiveMetrics(_tabs.value.size, _isBackgroundPlay.value)
        _webViewUrl.value = url
        val domain = extractDomain(url)
        _currentDomain.value = domain
        _trustInfo.value = TrustEvaluator.evaluate(url, certificate)
        _trackersBlocked.value = AdBlockEngine.instance.getSiteBlockedCount(domain)
        val resolvedTitle = if (title.isNotBlank()) title else domain
        updateActiveTab(title = resolvedTitle, url = url, isLoading = false, isHome = false, favicon = favicon)

        // Record history on page finish for all legitimate web navigations & in-page link clicks
        val activeTabIsIncognito = getActiveTab()?.isIncognito == true
        if (!activeTabIsIncognito && url.isNotBlank() && !url.startsWith("vin://") && !url.startsWith("about:")) {
            viewModelScope.launch(Dispatchers.IO) {
                storage.addToHistory(resolvedTitle, url)
            }
        }
    }

    fun refreshTrackerCount() {
        val domain = _currentDomain.value
        _trackersBlocked.value = AdBlockEngine.instance.getSiteBlockedCount(domain)
    }

    private fun extractDomain(url: String): String {
        return try {
            java.net.URI(url).host?.removePrefix("www.")?.lowercase() ?: url
        } catch (_: Exception) { url }
    }

    private fun getActiveTab(): TabState? = _tabs.value.find { it.id == _activeTabId.value }

    private fun updateActiveTab(
        title: String? = null,
        url: String? = null,
        isHome: Boolean? = null,
        isLoading: Boolean? = null,
        favicon: Bitmap? = null
    ) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == _activeTabId.value) {
                tab.copy(
                    title = title ?: tab.title,
                    url = url ?: tab.url,
                    isHome = isHome ?: tab.isHome,
                    isLoading = isLoading ?: tab.isLoading,
                    favicon = favicon ?: tab.favicon
                )
            } else tab
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Reading List
    // ──────────────────────────────────────────────────────────────
    private val _showReadingList = MutableStateFlow(false)
    val showReadingList: StateFlow<Boolean> = _showReadingList.asStateFlow()

    private val _readingListItems = MutableStateFlow(storage.getReadingList())
    val readingListItems: StateFlow<List<ReadingListItem>> = _readingListItems.asStateFlow()

    fun toggleReadingList() {
        _readingListItems.value = storage.getReadingList()
        _showReadingList.value = !_showReadingList.value
    }
    fun dismissReadingList() { _showReadingList.value = false }

    fun addToReadingList() {
        val tab = getActiveTab()
        if (tab != null && tab.url.isNotBlank() && !tab.isHome) {
            storage.addToReadingList(tab.title, tab.url)
            _readingListItems.value = storage.getReadingList()
        }
    }

    fun removeFromReadingList(id: String) {
        storage.removeFromReadingList(id)
        _readingListItems.value = storage.getReadingList()
    }

    fun clearReadingList() {
        storage.clearReadingList()
        _readingListItems.value = emptyList()
    }

    fun saveReadingOffline(id: String, webView: android.webkit.WebView?) {
        if (webView == null) return
        val dir = getApplication<Application>().filesDir.resolve("reading_list").apply { mkdirs() }
        val file = dir.resolve("$id.html")
        try {
            webView.saveWebArchive(file.absolutePath)
            storage.updateReadingListItem(id, file.absolutePath)
            _readingListItems.value = storage.getReadingList()
        } catch (_: Exception) { }
    }

    // ──────────────────────────────────────────────────────────────
    // User Scripts
    // ──────────────────────────────────────────────────────────────
    private val _showUserScripts = MutableStateFlow(false)
    val showUserScripts: StateFlow<Boolean> = _showUserScripts.asStateFlow()

    private val _userScripts = MutableStateFlow(storage.getUserScripts())
    val userScripts: StateFlow<List<UserScript>> = _userScripts.asStateFlow()

    fun toggleUserScriptsSheet() {
        _userScripts.value = storage.getUserScripts()
        _showUserScripts.value = !_showUserScripts.value
    }
    fun dismissUserScripts() { _showUserScripts.value = false }

    fun saveUserScript(script: UserScript) {
        storage.saveUserScript(script)
        _userScripts.value = storage.getUserScripts()
    }

    fun deleteUserScript(id: String) {
        storage.deleteUserScript(id)
        _userScripts.value = storage.getUserScripts()
    }

    fun toggleUserScriptEnabled(id: String) {
        val scripts = _userScripts.value.toMutableList()
        val idx = scripts.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val updated = scripts[idx].copy(isEnabled = !scripts[idx].isEnabled)
            scripts[idx] = updated
            storage.saveUserScript(updated)
            _userScripts.value = scripts
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Tab Groups
    // ──────────────────────────────────────────────────────────────
    private val _tabGroups = MutableStateFlow(storage.getTabGroups())
    val tabGroups: StateFlow<List<TabGroup>> = _tabGroups.asStateFlow()

    fun createTabGroup(name: String, color: Long) {
        val group = TabGroup(
            id = UUID.randomUUID().toString(),
            name = name,
            color = color,
            createdAt = System.currentTimeMillis()
        )
        storage.saveTabGroup(group)
        _tabGroups.value = storage.getTabGroups()
    }

    fun deleteTabGroup(id: String) {
        storage.deleteTabGroup(id)
        _tabGroups.value = storage.getTabGroups()
    }

    fun assignTabToGroup(tabId: String, groupId: String?) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId) tab.copy(groupId = groupId) else tab
        }
    }

    fun closeGroupTabs(groupId: String) {
        val toClose = _tabs.value.filter { it.groupId == groupId }.map { it.id }
        toClose.forEach { closeTab(it) }
    }

    // ──────────────────────────────────────────────────────────────
    // Theme
    // ──────────────────────────────────────────────────────────────
    private val _themePreset = MutableStateFlow(storage.getThemePreset())
    val themePreset: StateFlow<String> = _themePreset.asStateFlow()

    private val _showThemePicker = MutableStateFlow(false)
    val showThemePicker: StateFlow<Boolean> = _showThemePicker.asStateFlow()

    fun toggleThemePicker() { _showThemePicker.value = !_showThemePicker.value }
    fun dismissThemePicker() { _showThemePicker.value = false }

    fun setThemePreset(preset: String) {
        _themePreset.value = preset
        storage.setThemePreset(preset)
    }

    // ──────────────────────────────────────────────────────────────
    // Cookie Manager
    // ──────────────────────────────────────────────────────────────
    private val _showCookieManager = MutableStateFlow(false)
    val showCookieManager: StateFlow<Boolean> = _showCookieManager.asStateFlow()

    fun toggleCookieManager() { _showCookieManager.value = !_showCookieManager.value }
    fun dismissCookieManager() { _showCookieManager.value = false }

    // ──────────────────────────────────────────────────────────────
    // QR Code
    // ──────────────────────────────────────────────────────────────
    private val _showQrScanner = MutableStateFlow(false)
    val showQrScanner: StateFlow<Boolean> = _showQrScanner.asStateFlow()

    private val _showQrGenerator = MutableStateFlow(false)
    val showQrGenerator: StateFlow<Boolean> = _showQrGenerator.asStateFlow()

    fun toggleQrScanner() { _showQrScanner.value = !_showQrScanner.value }
    fun dismissQrScanner() { _showQrScanner.value = false }
    fun toggleQrGenerator() { _showQrGenerator.value = !_showQrGenerator.value }
    fun dismissQrGenerator() { _showQrGenerator.value = false }

    // ──────────────────────────────────────────────────────────────
    // Download Manager
    // ──────────────────────────────────────────────────────────────
    private val _showDownloadManager = MutableStateFlow(false)
    val showDownloadManager: StateFlow<Boolean> = _showDownloadManager.asStateFlow()

    private val _downloads = MutableStateFlow(storage.getTrackedDownloads())
    val downloads: StateFlow<List<DownloadInfo>> = _downloads.asStateFlow()

    fun toggleDownloadManager() {
        _downloads.value = storage.getTrackedDownloads()
        _showDownloadManager.value = !_showDownloadManager.value
    }
    fun dismissDownloadManager() { _showDownloadManager.value = false }

    fun clearDownloads() {
        storage.clearTrackedDownloads()
        _downloads.value = emptyList()
    }
}
