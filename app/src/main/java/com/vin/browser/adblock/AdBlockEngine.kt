package com.vin.browser.adblock

import java.util.concurrent.ConcurrentHashMap

enum class BlockCategory { AD, TRACKER, FINGERPRINT, OTHER }

data class BlockRule(
    val pattern: String,      // domain or substring, e.g. "doubleclick.net" or "/pagead/"
    val category: BlockCategory,
    val isDomainRule: Boolean, // true = match against request host, false = match against full URL
    val thirdPartyOnly: Boolean = false // true = never block when request is first-party (same site as page)
)

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
        val domainRules: Map<String, Boolean>,     // "doubleclick.net" -> thirdPartyOnly
        val patternRules: List<BlockRule>,         // substring rules
        val popupDomains: Set<String>,             // $popup domain rules — block navigation
        val popupPatterns: List<String>,           // $popup path patterns — block navigation
        val cosmeticRules: Map<String, List<String>>,          // domain -> hide selectors
        val cosmeticExceptions: Map<String, List<String>>      // domain -> exception selectors
    )

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

    // Bootstrap state: builtin rules, extended at runtime by asset/remote filter lists.
    @Volatile private var ruleSet = RuleSet(
        builtinDomains.associateWith { false }, builtinPatterns,
        builtinPopupDomains, emptyList(), emptyCosmetic(), emptyCosmetic()
    )

    // Base state (builtin + asset lists) that remote syncs rebuild on top of,
    // so a fresh sync replaces previous remote rules instead of accumulating them.
    private var baseDomains: Map<String, Boolean> = builtinDomains.associateWith { false }
    private var basePatterns: List<BlockRule> = builtinPatterns
    private var basePopupDomains: Set<String> = builtinPopupDomains
    private var basePopupPatterns: List<String> = emptyList()
    private var baseCosmetic: Map<String, List<String>> = emptyCosmetic()
    private var baseCosmeticExceptions: Map<String, List<String>> = emptyCosmetic()

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
            val domains = HashMap(baseDomains)
            val patterns = ArrayList(basePatterns)
            result.networkRules.forEach { rule ->
                if (rule.isDomainRule) {
                    domains[rule.pattern.lowercase()] = rule.thirdPartyOnly
                } else if (patterns.size < 500) {
                    patterns.add(rule)
                }
            }
            baseDomains = domains
            basePatterns = patterns
            basePopupDomains = basePopupDomains + result.popupRules.filter { it.isDomainRule }.map { it.pattern.lowercase() }
            basePopupPatterns = basePopupPatterns + result.popupRules.filter { !it.isDomainRule }.map { it.pattern }
            baseCosmetic = mergeCosmetic(baseCosmetic, result.cosmeticRules)
            baseCosmeticExceptions = mergeCosmetic(baseCosmeticExceptions, result.cosmeticExceptions)
            ruleSet = buildRuleSet(domains, patterns, basePopupDomains, basePopupPatterns, baseCosmetic, baseCosmeticExceptions)
        }
    }

    /**
     * Atomically swaps in a freshly-synced remote filter list on top of the current
     * base state (builtin + asset lists). Called off the main thread by the worker;
     * readers keep using the old snapshot until the swap completes.
     */
    fun replaceRemoteRules(result: FilterParseResult) {
        synchronized(reloadLock) {
            val domains = HashMap(baseDomains)
            val patterns = ArrayList(basePatterns)
            result.networkRules.forEach { rule ->
                if (rule.isDomainRule) {
                    domains[rule.pattern.lowercase()] = rule.thirdPartyOnly
                } else if (patterns.size < 500) {
                    patterns.add(rule)
                }
            }
            val popupDomains = basePopupDomains + result.popupRules.filter { it.isDomainRule }.map { it.pattern.lowercase() }
            val popupPatterns = result.popupRules.filter { !it.isDomainRule }.map { it.pattern }
            val cosmetic = mergeCosmetic(baseCosmetic, result.cosmeticRules)
            val cosmeticExceptions = mergeCosmetic(baseCosmeticExceptions, result.cosmeticExceptions)
            ruleSet = buildRuleSet(domains, patterns, popupDomains, popupPatterns, cosmetic, cosmeticExceptions)
        }
    }

    private fun buildRuleSet(
        domains: Map<String, Boolean>,
        patterns: List<BlockRule>,
        popupDomains: Set<String>,
        popupPatterns: List<String>,
        cosmetic: Map<String, List<String>>,
        cosmeticExceptions: Map<String, List<String>>
    ) = RuleSet(domains, patterns, popupDomains, popupPatterns, cosmetic, cosmeticExceptions)

    private fun mergeCosmetic(
        base: Map<String, List<String>>,
        extra: Map<String, List<String>>
    ): Map<String, List<String>> {
        if (extra.isEmpty()) return base
        val merged = HashMap<String, MutableList<String>>(base.mapValues { it.value.toMutableList() } as Map<String, MutableList<String>>)
        extra.forEach { (domain, sels) ->
            merged.getOrPut(domain) { mutableListOf() }.addAll(sels)
        }
        return merged
    }

    fun allowForSite(siteHost: String, requestUrl: String) {
        siteExceptions.getOrPut(siteHost) { mutableSetOf() }.add(requestUrl)
    }

    fun disableForSite(siteHost: String) { siteDisabled[siteHost] = true }
    fun enableForSite(siteHost: String) { siteDisabled.remove(siteHost) }
    fun isSiteDisabled(siteHost: String): Boolean = siteDisabled[siteHost] == true

    // First-party hosts of the bundled search engines. Even with correct filter
    // parsing, a bad remote rule must never blank the user's search page.
    private val searchEngineAllowlist = setOf(
        "google.com", "bing.com", "duckduckgo.com", "brave.com", "startpage.com",
        "yahoo.com", "qwant.com", "marginalia-search.com", "marginalia.nu",
        "searx.be", "reddit.com", "ecosia.org", "mojeek.com"
    )

    /** Naive registrable domain: last two labels ("a.b.cdn.com" -> "cdn.com"). */
    private fun baseDomain(host: String): String {
        val parts = host.split('.')
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
    }

    fun shouldBlock(requestUrl: String, requestHost: String, pageHost: String): BlockResult? {
        if (!isGlobalAdBlockEnabled) return null
        if (siteDisabled[pageHost] == true) return null
        siteExceptions[pageHost]?.let { if (requestUrl in it) return null }

        // Local immutable snapshot — safe to iterate even while a reload swaps ruleSet
        val snapshot = ruleSet
        val host = requestHost.lowercase()

        // Third-party = request site differs from the page site (used by $third-party rules)
        val isThirdParty = pageHost.isNotBlank() && baseDomain(host) != baseDomain(pageHost)

        // First-party requests to a search provider itself are never ad/tracker traffic.
        // (Third-party calls, e.g. bat.bing.com fired from another site, stay blockable.)
        if (!isThirdParty && (host in searchEngineAllowlist || baseDomain(host) in searchEngineAllowlist)) return null

        // walk up subdomains: ads.doubleclick.net -> doubleclick.net -> net
        var probe = host
        while (probe.isNotEmpty()) {
            val thirdPartyOnly = snapshot.domainRules[probe]
            if (thirdPartyOnly != null) {
                if (!thirdPartyOnly || isThirdParty) {
                    val cat = categorize(probe)
                    if (cat == BlockCategory.TRACKER && !isTrackerBlockEnabled) {
                        // Tracker blocking toggle disabled
                    } else {
                        recordGlobalBlock(cat, pageHost)
                        return BlockResult(BlockRule(probe, cat, true, thirdPartyOnly))
                    }
                }
            }
            val dot = probe.indexOf('.')
            if (dot == -1) break
            probe = probe.substring(dot + 1)
        }

        val urlLower = requestUrl.lowercase()
        // Fast pre-filter: Only inspect pattern rules if URL contains ad/tracker path keywords
        if (urlLower.contains("/ad") || urlLower.contains("pixel") || urlLower.contains("track") ||
            urlLower.contains("telemetry") || urlLower.contains("analytics") || urlLower.contains("banner") ||
            urlLower.contains("doubleclick") || urlLower.contains("pagead")) {
            for (rule in snapshot.patternRules) {
                if ((!rule.thirdPartyOnly || isThirdParty) && urlLower.contains(rule.pattern.lowercase())) {
                    if (rule.category == BlockCategory.TRACKER && !isTrackerBlockEnabled) {
                        continue
                    }
                    recordGlobalBlock(rule.category, pageHost)
                    return BlockResult(rule)
                }
            }
        }
        return null
    }

    /**
     * Popup / redirect blocker: decides whether a NAVIGATION (main-frame load,
     * popup window, or JS redirect) to [requestUrl] should be cancelled.
     * Uses the $popup rule set + builtin redirect-network domains.
     * Same-site navigations are never blocked — this only kills cross-site hijacks.
     */
    fun shouldBlockPopup(requestUrl: String, requestHost: String, pageHost: String): Boolean {
        if (!isGlobalAdBlockEnabled) return false
        if (pageHost.isNotBlank() && siteDisabled[pageHost] == true) return false
        val host = requestHost.lowercase()
        if (host.isEmpty()) return false

        // Navigating within the current site is always legitimate
        if (pageHost.isNotBlank() && baseDomain(host) == baseDomain(pageHost)) return false

        val snapshot = ruleSet

        // Domain walk-up: ads.popcash.net -> popcash.net
        var probe = host
        while (probe.isNotEmpty()) {
            if (probe in snapshot.popupDomains) {
                recordGlobalBlock(BlockCategory.AD, pageHost)
                return true
            }
            val dot = probe.indexOf('.')
            if (dot == -1) break
            probe = probe.substring(dot + 1)
        }

        val urlLower = requestUrl.lowercase()
        for (pattern in snapshot.popupPatterns) {
            if (urlLower.contains(pattern)) {
                recordGlobalBlock(BlockCategory.AD, pageHost)
                return true
            }
        }
        return false
    }

    /**
     * Cosmetic (element-hiding) selectors that apply to the given page domain.
     * Combines EasyList domain rules (rule domain matches page domain or any
     * parent suffix) and removes exception (#@#) selectors. Capped for safety.
     */
    fun getCosmeticSelectors(pageDomain: String): List<String> {
        if (!isGlobalAdBlockEnabled || !isCosmeticFilterEnabled) return emptyList()
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
