package com.vin.browser.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class StorageService(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("vin_browser", Context.MODE_PRIVATE)

    fun getSpeedDials(): List<SpeedDialItem> {
        val json = prefs.getString("speed_dials_v2", null) ?: return defaultSpeedDials()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                SpeedDialItem(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    url = obj.getString("url"),
                    domain = obj.optString("domain", extractDomain(obj.getString("url"))),
                    iconLetter = obj.optString("iconLetter", obj.getString("title").take(1))
                )
            }
        } catch (_: Exception) { defaultSpeedDials() }
    }

    fun saveSpeedDials(items: List<SpeedDialItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("url", item.url)
                put("domain", item.domain)
                put("iconLetter", item.iconLetter)
            })
        }
        prefs.edit().putString("speed_dials_v2", arr.toString()).apply()
    }

    fun addSpeedDial(title: String, url: String) {
        val current = getSpeedDials().toMutableList()
        val finalUrl = if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) url else "https://$url"
        val domain = extractDomain(finalUrl)
        val newItem = SpeedDialItem(
            id = java.util.UUID.randomUUID().toString(),
            title = title.ifBlank { domain },
            url = finalUrl,
            domain = domain,
            iconLetter = (if (title.isNotBlank()) title else domain).take(1).uppercase()
        )
        current.add(newItem)
        saveSpeedDials(current)
    }

    fun updateSpeedDial(id: String, newTitle: String, newUrl: String) {
        val current = getSpeedDials().map { item ->
            if (item.id == id) {
                val finalUrl = if (newUrl.startsWith("http://", ignoreCase = true) || newUrl.startsWith("https://", ignoreCase = true)) newUrl else "https://$newUrl"
                val domain = extractDomain(finalUrl)
                item.copy(
                    title = newTitle.ifBlank { domain },
                    url = finalUrl,
                    domain = domain,
                    iconLetter = (if (newTitle.isNotBlank()) newTitle else domain).take(1).uppercase()
                )
            } else item
        }
        saveSpeedDials(current)
    }

    fun deleteSpeedDial(id: String) {
        val current = getSpeedDials().filter { it.id != id }
        saveSpeedDials(current)
    }

    fun isOnboardingCompleted(): Boolean = prefs.getBoolean("onboarding_done", false)
    fun setOnboardingCompleted(done: Boolean) = prefs.edit().putBoolean("onboarding_done", done).apply()

    fun addToHistory(title: String, url: String) {
        val json = prefs.getString("history", "[]") ?: "[]"
        val arr = JSONArray(json)
        val entry = JSONObject().apply {
            put("title", title)
            put("url", url)
            put("timestamp", System.currentTimeMillis())
        }
        arr.put(entry)
        while (arr.length() > 500) arr.remove(0)
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun getHistory(): List<Pair<String, String>> {
        val json = prefs.getString("history", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (arr.length() - 1 downTo 0).map { i ->
                val obj = arr.getJSONObject(i)
                Pair(obj.getString("title"), obj.getString("url"))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun clearHistory() {
        prefs.edit().remove("history").apply()
    }

    fun addBookmark(title: String, url: String) {
        val json = prefs.getString("bookmarks", "[]") ?: "[]"
        val arr = JSONArray(json)
        arr.put(JSONObject().apply {
            put("title", title)
            put("url", url)
        })
        prefs.edit().putString("bookmarks", arr.toString()).apply()
    }

    fun getBookmarks(): List<Pair<String, String>> {
        val json = prefs.getString("bookmarks", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Pair(obj.getString("title"), obj.getString("url"))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun getSiteSettings(domain: String): SiteControlSettings {
        val clean = domain.removePrefix("www.").lowercase()
        val json = prefs.getString("site_settings_$clean", null) ?: return SiteControlSettings()
        return try {
            val obj = JSONObject(json)
            SiteControlSettings(
                camera = obj.optString("camera", "Ask"),
                microphone = obj.optString("microphone", "Ask"),
                location = obj.optString("location", "Ask"),
                downloads = obj.optString("downloads", "Default"),
                adsBlocked = obj.optBoolean("adsBlocked", true),
                imagesEnabled = obj.optBoolean("imagesEnabled", true),
                popupsBlocked = obj.optBoolean("popupsBlocked", true),
                desktopMode = obj.optBoolean("desktopMode", false),
                audioMuted = obj.optBoolean("audioMuted", false),
                openApps = obj.optBoolean("openApps", false),
                backgroundPlay = obj.optBoolean("backgroundPlay", true)
            )
        } catch (_: Exception) { SiteControlSettings() }
    }

    fun saveSiteSettings(domain: String, settings: SiteControlSettings) {
        val clean = domain.removePrefix("www.").lowercase()
        val obj = JSONObject().apply {
            put("camera", settings.camera)
            put("microphone", settings.microphone)
            put("location", settings.location)
            put("downloads", settings.downloads)
            put("adsBlocked", settings.adsBlocked)
            put("imagesEnabled", settings.imagesEnabled)
            put("popupsBlocked", settings.popupsBlocked)
            put("desktopMode", settings.desktopMode)
            put("audioMuted", settings.audioMuted)
            put("openApps", settings.openApps)
            put("backgroundPlay", settings.backgroundPlay)
        }
        prefs.edit().putString("site_settings_$clean", obj.toString()).apply()
    }

    fun getSelectedEngine(): String = prefs.getString("engine", "google") ?: "google"
    fun setSelectedEngine(id: String) = prefs.edit().putString("engine", id).apply()

    fun isLiteModeEnabled(): Boolean = prefs.getBoolean("lite_mode", false)
    fun setLiteMode(enabled: Boolean) = prefs.edit().putBoolean("lite_mode", enabled).apply()

    fun isForcedDarkEnabled(): Boolean = prefs.getBoolean("forced_dark", false)
    fun setForcedDark(enabled: Boolean) = prefs.edit().putBoolean("forced_dark", enabled).apply()

    // Global Privacy & AdBlock Preferences
    fun isGlobalAdBlockEnabled(): Boolean = prefs.getBoolean("global_adblock", true)
    fun setGlobalAdBlock(enabled: Boolean) = prefs.edit().putBoolean("global_adblock", enabled).apply()

    fun isCosmeticFilterEnabled(): Boolean = prefs.getBoolean("cosmetic_filter", true)
    fun setCosmeticFilter(enabled: Boolean) = prefs.edit().putBoolean("cosmetic_filter", enabled).apply()

    fun isTrackerBlockEnabled(): Boolean = prefs.getBoolean("tracker_block", true)
    fun setTrackerBlock(enabled: Boolean) = prefs.edit().putBoolean("tracker_block", enabled).apply()

    fun isHttpsUpgradeEnabled(): Boolean = prefs.getBoolean("https_upgrade", true)
    fun setHttpsUpgrade(enabled: Boolean) = prefs.edit().putBoolean("https_upgrade", enabled).apply()

    fun isCryptoBlockEnabled(): Boolean = prefs.getBoolean("crypto_block", true)
    fun setCryptoBlock(enabled: Boolean) = prefs.edit().putBoolean("crypto_block", enabled).apply()

    fun isBackgroundPlayEnabled(): Boolean = prefs.getBoolean("bg_audio_play", true)
    fun setBackgroundPlay(enabled: Boolean) = prefs.edit().putBoolean("bg_audio_play", enabled).apply()

    // Google Safe Browsing protection for WebView navigations
    fun isSafeBrowsingEnabled(): Boolean = prefs.getBoolean("safe_browsing", true)
    fun setSafeBrowsing(enabled: Boolean) = prefs.edit().putBoolean("safe_browsing", enabled).apply()

    // Remote filter list sync (FilterSyncWorker)
    fun getLastFilterSync(): Long = prefs.getLong("last_filter_sync", 0L)
    fun setLastFilterSync(ms: Long) = prefs.edit().putLong("last_filter_sync", ms).apply()

    // Webpage translator target language (translate.goog proxy)
    fun getTranslateTargetLang(): String = prefs.getString("translate_target_lang", "hi") ?: "hi"
    fun setTranslateTargetLang(code: String) = prefs.edit().putString("translate_target_lang", code).apply()

    // ── Reading List ──────────────────────────────────────────────
    fun getReadingList(): List<ReadingListItem> {
        val json = prefs.getString("reading_list", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ReadingListItem(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    url = obj.getString("url"),
                    addedAt = obj.optLong("addedAt", 0L),
                    isOfflineReady = obj.optBoolean("isOfflineReady", false),
                    offlineHtmlPath = obj.optString("offlineHtmlPath", null)
                )
            }.sortedByDescending { it.addedAt }
        } catch (_: Exception) { emptyList() }
    }

    fun addToReadingList(title: String, url: String) {
        val arr = JSONArray(prefs.getString("reading_list", "[]") ?: "[]")
        // Don't duplicate same URL
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("url") == url) return
        }
        arr.put(JSONObject().apply {
            put("id", java.util.UUID.randomUUID().toString())
            put("title", title)
            put("url", url)
            put("addedAt", System.currentTimeMillis())
            put("isOfflineReady", false)
        })
        while (arr.length() > 200) arr.remove(0)
        prefs.edit().putString("reading_list", arr.toString()).apply()
    }

    fun removeFromReadingList(id: String) {
        val arr = JSONArray(prefs.getString("reading_list", "[]") ?: "[]")
        val result = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("id") != id) result.put(obj)
        }
        prefs.edit().putString("reading_list", result.toString()).apply()
    }

    fun updateReadingListItem(id: String, offlineHtmlPath: String?) {
        val arr = JSONArray(prefs.getString("reading_list", "[]") ?: "[]")
        val result = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("id") == id) {
                obj.put("isOfflineReady", offlineHtmlPath != null)
                if (offlineHtmlPath != null) obj.put("offlineHtmlPath", offlineHtmlPath)
            }
            result.put(obj)
        }
        prefs.edit().putString("reading_list", result.toString()).apply()
    }

    fun clearReadingList() {
        prefs.edit().remove("reading_list").apply()
    }

    // ── User Scripts ──────────────────────────────────────────────
    fun getUserScripts(): List<UserScript> {
        val json = prefs.getString("user_scripts", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                UserScript(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    domain = obj.optString("domain", null),
                    code = obj.getString("code"),
                    isEnabled = obj.optBoolean("isEnabled", true),
                    isCss = obj.optBoolean("isCss", false)
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun saveUserScript(script: UserScript) {
        val arr = JSONArray(prefs.getString("user_scripts", "[]") ?: "[]")
        val result = JSONArray()
        var found = false
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("id") == script.id) {
                result.put(JSONObject().apply {
                    put("id", script.id)
                    put("name", script.name)
                    put("domain", script.domain ?: JSONObject.NULL)
                    put("code", script.code)
                    put("isEnabled", script.isEnabled)
                    put("isCss", script.isCss)
                })
                found = true
            } else {
                result.put(obj)
            }
        }
        if (!found) {
            result.put(JSONObject().apply {
                put("id", script.id)
                put("name", script.name)
                put("domain", script.domain ?: JSONObject.NULL)
                put("code", script.code)
                put("isEnabled", script.isEnabled)
                put("isCss", script.isCss)
            })
        }
        prefs.edit().putString("user_scripts", result.toString()).apply()
    }

    fun deleteUserScript(id: String) {
        val arr = JSONArray(prefs.getString("user_scripts", "[]") ?: "[]")
        val result = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("id") != id) result.put(obj)
        }
        prefs.edit().putString("user_scripts", result.toString()).apply()
    }

    // ── Tab Groups ────────────────────────────────────────────────
    fun getTabGroups(): List<TabGroup> {
        val json = prefs.getString("tab_groups", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                TabGroup(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    color = obj.optLong("color", 0xFF3B82F6),
                    createdAt = obj.optLong("createdAt", 0L)
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun saveTabGroup(group: TabGroup) {
        val arr = JSONArray(prefs.getString("tab_groups", "[]") ?: "[]")
        val result = JSONArray()
        var found = false
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("id") == group.id) {
                result.put(JSONObject().apply {
                    put("id", group.id)
                    put("name", group.name)
                    put("color", group.color)
                    put("createdAt", group.createdAt)
                })
                found = true
            } else {
                result.put(obj)
            }
        }
        if (!found) {
            result.put(JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("color", group.color)
                put("createdAt", group.createdAt)
            })
        }
        prefs.edit().putString("tab_groups", result.toString()).apply()
    }

    fun deleteTabGroup(id: String) {
        val arr = JSONArray(prefs.getString("tab_groups", "[]") ?: "[]")
        val result = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("id") != id) result.put(obj)
        }
        prefs.edit().putString("tab_groups", result.toString()).apply()
    }

    // ── Theme ─────────────────────────────────────────────────────
    fun getThemePreset(): String = prefs.getString("theme_preset", "system") ?: "system"
    fun setThemePreset(preset: String) = prefs.edit().putString("theme_preset", preset).apply()

    fun getAccentColor(): Long = prefs.getLong("accent_color", 0xFF3B82F6)
    fun setAccentColor(color: Long) = prefs.edit().putLong("accent_color", color).apply()

    // ── Downloads Tracking ────────────────────────────────────────
    fun getTrackedDownloads(): List<DownloadInfo> {
        val json = prefs.getString("tracked_downloads", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                DownloadInfo(
                    downloadId = obj.getLong("downloadId"),
                    fileName = obj.getString("fileName"),
                    url = obj.getString("url"),
                    status = obj.optInt("status", 0),
                    bytesDownloaded = obj.optLong("bytesDownloaded", 0),
                    totalBytes = obj.optLong("totalBytes", -1),
                    timestamp = obj.optLong("timestamp", 0L)
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun trackDownload(info: DownloadInfo) {
        val arr = JSONArray(prefs.getString("tracked_downloads", "[]") ?: "[]")
        arr.put(JSONObject().apply {
            put("downloadId", info.downloadId)
            put("fileName", info.fileName)
            put("url", info.url)
            put("status", info.status)
            put("bytesDownloaded", info.bytesDownloaded)
            put("totalBytes", info.totalBytes)
            put("timestamp", info.timestamp)
        })
        while (arr.length() > 100) arr.remove(0)
        prefs.edit().putString("tracked_downloads", arr.toString()).apply()
    }

    fun clearTrackedDownloads() {
        prefs.edit().remove("tracked_downloads").apply()
    }

    // ── Helpers ───────────────────────────────────────────────────
    private fun extractDomain(url: String): String {
        return try {
            java.net.URI(url).host?.removePrefix("www.") ?: "google.com"
        } catch (_: Exception) { "google.com" }
    }

    private fun defaultSpeedDials() = listOf(
        SpeedDialItem("1", "Games", "https://poki.com", "poki.com", "G"),
        SpeedDialItem("2", "YouTube", "https://m.youtube.com", "m.youtube.com", "Y"),
        SpeedDialItem("3", "ChatGPT", "https://chatgpt.com", "chatgpt.com", "C"),
        SpeedDialItem("4", "News", "https://news.google.com", "news.google.com", "N"),
        SpeedDialItem("5", "Anime", "https://www.crunchyroll.com", "crunchyroll.com", "A"),
        SpeedDialItem("6", "Reddit", "https://www.reddit.com", "reddit.com", "R"),
        SpeedDialItem("7", "GitHub", "https://github.com", "github.com", "G"),
        SpeedDialItem("8", "Chess", "https://www.chess.com", "chess.com", "C"),
        SpeedDialItem("9", "Wikipedia", "https://www.wikipedia.org", "wikipedia.org", "W"),
        SpeedDialItem("10", "Twitter", "https://x.com", "x.com", "X")
    )
}
