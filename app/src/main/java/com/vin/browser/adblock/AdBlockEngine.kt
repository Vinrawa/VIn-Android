package com.vin.browser.adblock

import java.util.concurrent.ConcurrentHashMap

enum class BlockCategory { AD, TRACKER, FINGERPRINT, OTHER }

data class BlockRule(
    val pattern: String,      // domain or substring, e.g. "doubleclick.net" or "/pagead/"
    val category: BlockCategory,
    val isDomainRule: Boolean, // true = match against request host, false = match against full URL
    val thirdPartyOnly: Boolean = false,
    val firstPartyOnly: Boolean = false,
    val includedDomains: Set<String> = emptySet(),
    val excludedDomains: Set<String> = emptySet()
) {
    private val matcher = if (isDomainRule) null else NetworkPattern(pattern)
    fun matches(url: String, host: String, page: String, thirdParty: Boolean): Boolean {
        if (thirdPartyOnly && !thirdParty || firstPartyOnly && thirdParty) return false
        fun scoped(domain: String) = page == domain || page.endsWith(".$domain")
        if (excludedDomains.any(::scoped)) return false
        if (includedDomains.isNotEmpty() && includedDomains.none(::scoped)) return false
        return if (isDomainRule) host == pattern || host.endsWith(".$pattern") else matcher!!.matches(url)
    }
}

data class BlockResult(val rule: BlockRule)

data class PageStats(
    var totalRequests: Int = 0,
    var blockedRequests: Int = 0,
    val blockedByCategory: MutableMap<BlockCategory, Int> = mutableMapOf(),
    var estimatedBytesSaved: Long = 0L
) {
    fun record(result: BlockResult?) {
        totalRequests++
        if (result != null) {
            blockedRequests++
            blockedByCategory[result.rule.category] =
                (blockedByCategory[result.rule.category] ?: 0) + 1
            // rough per-category payload estimate — replace with real measured averages once you have device data
            estimatedBytesSaved += when (result.rule.category) {
                BlockCategory.AD -> 45_000L
                BlockCategory.TRACKER -> 4_000L
                BlockCategory.FINGERPRINT -> 2_000L
                BlockCategory.OTHER -> 8_000L
            }
        }
    }

    fun privacyScore(): Int {
        if (totalRequests == 0) return 100
        val blockedRatio = blockedRequests.toFloat() / totalRequests
        val fingerprintPenalty = (blockedByCategory[BlockCategory.FINGERPRINT] ?: 0) * 2
        val raw = 100 - (blockedRatio * 40) - fingerprintPenalty
        return raw.toInt().coerceIn(0, 100)
    }
}

class AdBlockEngine {

    companion object {
        val instance by lazy { AdBlockEngine() }
    }

    /**
     * Immutable snapshot of blocking rules. Readers (shouldBlock) grab the @Volatile
     * reference once and work against it, so rule reloads never mutate a collection
     * mid-request. Writers swap the whole snapshot under [reloadLock].
     */
    private data class RuleSet(
        val network: RuleIndex, val popup: RuleIndex,
        val exceptions: RuleIndex, val popupExceptions: RuleIndex,
        val cosmeticRules: Map<String, List<String>>,
        val cosmeticExceptions: Map<String, List<String>>
    )

    /** Domain rules stay O(host labels); only URL patterns need a literal scan. */
    private class RuleIndex(rules: List<BlockRule>) {
        private val domains = rules.filter { it.isDomainRule }.groupBy { it.pattern }
        private val patterns = rules.filterNot { it.isDomainRule }.distinct()

        fun candidates(host: String): Sequence<BlockRule> = sequence {
            var probe = host
            while (probe.isNotEmpty()) {
                domains[probe]?.let { yieldAll(it) }
                val dot = probe.indexOf('.')
                if (dot < 0) break
                probe = probe.substring(dot + 1)
            }
            yieldAll(patterns)
        }
    }

    private val reloadLock = Any()

    private val builtinDomains: Set<String> = setOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "adservice.google.com", "pagead2.googlesyndication.com", "tpc.googlesyndication.com",
        "adnxs.com", "criteo.com", "criteo.net", "taboola.com", "outbrain.com",
        "rubiconproject.com", "pubmatic.com", "openx.net", "casalemedia.com",
        "amazon-adsystem.com", "scorecardresearch.com", "quantserve.com",
        "popads.net", "adcolony.com", "unityads.unity3d.com", "vungle.com",
        "applovin.com", "chartboost.com", "ironsrc.com", "inmobi.com",
        "flurry.com", "moatads.com", "hotjar.com", "mixpanel.com", "segment.com",
        "amplitude.com", "newrelic.com", "branch.io", "adjust.com", "appsflyer.com"
    )

    private val builtinPatterns: List<BlockRule> = listOf(
        BlockRule("/pagead/", BlockCategory.AD, false),
        BlockRule("/doubleclick/", BlockCategory.AD, false),
        BlockRule("/adserver", BlockCategory.AD, false),
        BlockRule("/ads/ad_", BlockCategory.AD, false),
        BlockRule("googleads.g.doubleclick.net", BlockCategory.AD, false)
    )

    // Well-known popunder / redirect-ad networks. These fire the "suddenly you're
    // on a random game or adult site" hijacks. EasyList $popup rules extend this.
    private val builtinPopupDomains: Set<String> = setOf(
        "propellerads.com", "propelleradsystem.com", "propellerclick.com",
        "adsterra.com", "adsterra.net", "adsterranetwork.com",
        "popads.net", "popcash.net", "clickadu.com", "hilltopads.com", "hilltopads.net",
        "exoclick.com", "exosrv.com", "exdynsrv.com", "juicyads.com", "juicyads.rocks",
        "trafficjunky.net", "trafficjunky.com", "trafficfactory.biz", "trafficfactory.com",
        "zeropark.com", "revenuehits.com", "galaksion.com", "richads.com", "richpops.com",
        "bidvertiser.com", "adnium.com", "evadav.com", "monetag.com", "vignette.js"
    )

    private fun emptyCosmetic() = emptyMap<String, List<String>>()

    private var baseRules = FilterParseResult(
        builtinDomains.map { BlockRule(it, categorize(it), true) } + builtinPatterns,
        builtinPopupDomains.map { BlockRule(it, BlockCategory.AD, true) },
        emptyCosmetic(), emptyCosmetic()
    )
    private var remoteRules = FilterListLoader.parse("")
    @Volatile private var ruleSet = snapshot(baseRules)
    private fun snapshot(result: FilterParseResult) = RuleSet(
        RuleIndex(result.networkRules.distinct()), RuleIndex(result.popupRules.distinct()),
        RuleIndex(result.networkExceptions.distinct()), RuleIndex(result.popupExceptions.distinct()),
        result.cosmeticRules, result.cosmeticExceptions
    )

    // hostname -> exact request URLs the user explicitly allowed on that site
    private val siteExceptions = ConcurrentHashMap<String, MutableSet<String>>()

    // hostname -> blocking fully off for that site
    private val siteDisabled = ConcurrentHashMap<String, Boolean>()

    // Global toggle flags (synced with StorageService preferences)
    @Volatile var isGlobalAdBlockEnabled: Boolean = true
    @Volatile var isCosmeticFilterEnabled: Boolean = true
    @Volatile var isTrackerBlockEnabled: Boolean = true
    @Volatile var isCryptoBlockEnabled: Boolean = true

    // Global lifetime statistics
    private val totalLifetimeBlocked = java.util.concurrent.atomic.AtomicInteger(0)
    private val totalAdsBlocked = java.util.concurrent.atomic.AtomicInteger(0)
    private val totalTrackersBlocked = java.util.concurrent.atomic.AtomicInteger(0)
    private val totalMalwareBlocked = java.util.concurrent.atomic.AtomicInteger(0)
    private val siteBlockedMap = ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()

    fun loadRules(result: FilterParseResult) {
        synchronized(reloadLock) {
            baseRules = FilterListLoader.merge(listOf(baseRules, result))
            ruleSet = snapshot(FilterListLoader.merge(listOf(baseRules, remoteRules)))
        }
    }
    /** Replace remote rules, retaining bundled rules and both exception sets. */
    fun replaceRemoteRules(result: FilterParseResult) {
        synchronized(reloadLock) {
            remoteRules = result
            ruleSet = snapshot(FilterListLoader.merge(listOf(baseRules, remoteRules)))
        }
    }

    fun allowForSite(siteHost: String, requestUrl: String) {
        siteExceptions.getOrPut(siteHost.lowercase()) { ConcurrentHashMap.newKeySet() }.add(requestUrl)
    }

    fun disableForSite(siteHost: String) { siteDisabled[siteHost.lowercase()] = true }
    fun enableForSite(siteHost: String) { siteDisabled.remove(siteHost.lowercase()) }
    fun isSiteDisabled(siteHost: String): Boolean = siteDisabled[siteHost.lowercase()] == true

    /** Naive registrable domain: last two labels ("a.b.cdn.com" -> "cdn.com"). */
    private fun baseDomain(host: String): String {
        val parts = host.split('.')
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
    }

    fun shouldBlock(requestUrl: String, requestHost: String, pageHost: String): BlockResult? {
        val page = pageHost.lowercase()
        if (!isGlobalAdBlockEnabled || isSiteDisabled(page)) return null
        if (siteExceptions[page]?.contains(requestUrl) == true) return null
        val snapshot = ruleSet
        val host = requestHost.lowercase()
        val thirdParty = page.isNotBlank() && baseDomain(host) != baseDomain(page)
        if (snapshot.exceptions.candidates(host).any { it.matches(requestUrl, host, page, thirdParty) }) return null
        for (rule in snapshot.network.candidates(host)) {
            if (rule.category == BlockCategory.TRACKER && !isTrackerBlockEnabled) continue
            if (rule.matches(requestUrl, host, page, thirdParty)) {
                recordGlobalBlock(rule.category, page)
                return BlockResult(rule)
            }
        }
        return null
    }

    fun shouldBlockPopup(requestUrl: String, requestHost: String, pageHost: String): Boolean {
        val host = requestHost.lowercase()
        val page = pageHost.lowercase()
        if (!isGlobalAdBlockEnabled || isSiteDisabled(page) || host.isEmpty()) return false
        if (siteExceptions[page]?.contains(requestUrl) == true) return false
        val thirdParty = page.isNotBlank() && baseDomain(host) != baseDomain(page)
        if (page.isNotBlank() && !thirdParty) return false
        val snapshot = ruleSet
        if (snapshot.popupExceptions.candidates(host).any { it.matches(requestUrl, host, page, thirdParty) }) return false
        if (snapshot.popup.candidates(host).any { it.matches(requestUrl, host, page, thirdParty) }) {
            recordGlobalBlock(BlockCategory.AD, page)
            return true
        }
        return false
    }

    /**
     * Cosmetic (element-hiding) selectors that apply to the given page domain.
     * Combines EasyList domain rules (rule domain matches page domain or any
     * parent suffix) and removes exception (#@#) selectors. Capped for safety.
     */
    fun getCosmeticSelectors(pageDomain: String): List<String> {
        if (!isGlobalAdBlockEnabled || !isCosmeticFilterEnabled || isSiteDisabled(pageDomain)) return emptyList()
        val d = pageDomain.removePrefix("www.").lowercase()
        if (d.isBlank() || d.isEmpty()) return emptyList()
        val snapshot = ruleSet
        if (snapshot.cosmeticRules.isEmpty()) return emptyList()

        val exceptions = mutableSetOf<String>()
        snapshot.cosmeticExceptions.forEach { (ruleDomain, sels) ->
            if (d == ruleDomain || d.endsWith(".$ruleDomain")) exceptions.addAll(sels)
        }

        val out = LinkedHashSet<String>()
        snapshot.cosmeticRules.forEach { (ruleDomain, sels) ->
            if (d == ruleDomain || d.endsWith(".$ruleDomain")) {
                sels.forEach { sel -> if (sel !in exceptions) out.add(sel) }
            }
            if (out.size >= 600) return out.toList()
        }
        return out.toList()
    }

    private fun recordGlobalBlock(category: BlockCategory, pageHost: String) {
        totalLifetimeBlocked.incrementAndGet()
        when (category) {
            BlockCategory.AD -> totalAdsBlocked.incrementAndGet()
            BlockCategory.TRACKER -> totalTrackersBlocked.incrementAndGet()
            BlockCategory.FINGERPRINT, BlockCategory.OTHER -> totalMalwareBlocked.incrementAndGet()
        }
        if (pageHost.isNotBlank()) {
            val clean = pageHost.removePrefix("www.").lowercase()
            siteBlockedMap.computeIfAbsent(clean) { java.util.concurrent.atomic.AtomicInteger(0) }.incrementAndGet()
        }
    }

    private fun categorize(domain: String): BlockCategory = when {
        domain.contains("doubleclick") || domain.contains("googlesyndication") ||
                domain.contains("googleadservices") || domain.contains("adservice") ||
                domain.contains("admob") || domain.contains("adnxs") || domain.contains("criteo") ->
            BlockCategory.AD
        domain.contains("fingerprint") || domain.contains("fpjs") ||
                domain.contains("coinhive") || domain.contains("cryptoloot") ->
            BlockCategory.FINGERPRINT
        else -> BlockCategory.TRACKER
    }

    fun getTotalBlocked(): Int = totalLifetimeBlocked.get()
    fun getAdsBlockedCount(): Int = totalAdsBlocked.get()
    fun getTrackersBlockedCount(): Int = totalTrackersBlocked.get()
    fun getMalwareBlockedCount(): Int = totalMalwareBlocked.get()

    fun getSiteBlockedCount(domain: String): Int {
        val clean = domain.removePrefix("www.").lowercase()
        return siteBlockedMap[clean]?.get() ?: 0
    }

    fun getEstimatedDataSavedMb(): Double {
        val count = totalLifetimeBlocked.get()
        return String.format("%.2f", (count * 130.0) / 1024.0).toDoubleOrNull() ?: 0.0
    }

    fun getEstimatedTimeSavedSec(): Double {
        val count = totalLifetimeBlocked.get()
        return String.format("%.1f", count * 0.35).toDoubleOrNull() ?: 0.0
    }

    fun resetAllStats() {
        totalLifetimeBlocked.set(0)
        totalAdsBlocked.set(0)
        totalTrackersBlocked.set(0)
        totalMalwareBlocked.set(0)
        siteBlockedMap.clear()
    }
}
