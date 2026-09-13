package com.vin.browser.data

import android.graphics.Bitmap

data class SearchEngine(
    val id: String,
    val name: String,
    val shortName: String,
    val color: Long,
    val queryUrl: String,
    val directUrl: String,
    val isPrivacyFocused: Boolean = false,
    val isIndie: Boolean = false
)

data class SearchResult(
    val id: String,
    val title: String,
    val url: String,
    val domain: String,
    val snippet: String,
    val engineName: String,
    val isOfficial: Boolean = false,
    val isSmallWeb: Boolean = false,
    val score: Int = 0
)

data class TabState(
    val id: String,
    val title: String = "New Tab",
    val url: String = "",
    val isLoading: Boolean = false,
    val isHome: Boolean = true,
    val favicon: Bitmap? = null,
    val isIncognito: Boolean = false,
    val isHibernated: Boolean = false,
    val groupId: String? = null
)

data class SuggestionItem(
    val value: String,          // what gets passed to onSearch
    val label: String,          // what is displayed in the row
    val isHistory: Boolean = false
)

data class SpeedDialItem(
    val id: String,
    val title: String,
    val url: String,
    val domain: String,
    val iconLetter: String = ""
)

data class SiteTrustInfo(
    val domain: String,
    val isSecure: Boolean,
    val sslIssuer: String = "None",
    val sslIssuedTo: String = "",
    val sslValidFrom: String = "N/A",
    val sslExpireOn: String = "N/A",
    val encryption: String = "TLS 1.3 AES-256-GCM",
    val trackersBlocked: Int = 0,
    val safetyRating: String = "Verified Safe & Secure",
    val phishingRisk: String = "Low"
)

data class SiteControlSettings(
    val camera: String = "Ask",        // "Ask", "Allow", "Block"
    val microphone: String = "Ask",    // "Ask", "Allow", "Block"
    val location: String = "Ask",      // "Ask", "Allow", "Block"
    val downloads: String = "Default",  // "Default", "Allow", "Block"
    val adsBlocked: Boolean = true,
    val imagesEnabled: Boolean = true,
    val popupsBlocked: Boolean = true,
    val desktopMode: Boolean = false,
    val audioMuted: Boolean = false,
    val openApps: Boolean = false,
    val backgroundPlay: Boolean = true
)

data class ReadingListItem(
    val id: String,
    val title: String,
    val url: String,
    val addedAt: Long,
    val isOfflineReady: Boolean = false,
    val offlineHtmlPath: String? = null
)

data class UserScript(
    val id: String,
    val name: String,
    val domain: String?,    // null = global
    val code: String,
    val isEnabled: Boolean = true,
    val isCss: Boolean = false
)

data class TabGroup(
    val id: String,
    val name: String,
    val color: Long,
    val createdAt: Long
)

data class DownloadInfo(
    val downloadId: Long,
    val fileName: String,
    val url: String,
    val status: Int,         // DownloadManager.STATUS_*
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val timestamp: Long
)
