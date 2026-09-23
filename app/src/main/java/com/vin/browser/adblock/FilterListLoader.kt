package com.vin.browser.adblock

/**
 * Result of parsing one raw filter list (EasyList-style) text.
 */
data class FilterParseResult(
    val networkRules: List<BlockRule>,               // request-level blocking rules
    val popupRules: List<BlockRule>,                 // $popup rules -- block navigations/popups
    val cosmeticRules: Map<String, List<String>>,    // domain -> element-hiding CSS selectors
    val cosmeticExceptions: Map<String, List<String>>, // domain -> exception selectors (#@#)
    val networkExceptions: List<BlockRule> = emptyList(),
    val popupExceptions: List<BlockRule> = emptyList()
)

object FilterListLoader {

    // Procedural pseudo-classes Chromium does not support natively (ABP/uBO extensions)
    private val unsupportedProcedural = listOf(
        ":has-text", ":matches-attr", ":matches-css", ":matches-path",
        ":min-text-length", ":remove(", ":style(", ":upward", ":xpath",
        ":others", ":if(", ":then(", ":matches-prop"
    )

    // Merges several parse results into one (used when combining EasyList + EasyPrivacy).
    // Network/popup rules are de-duplicated so repeated loads (assets + synced copies)
    // never double the working set. Cosmetic maps merge and de-duplicate per domain.
    fun merge(results: List<FilterParseResult>): FilterParseResult {
        val network = mutableListOf<BlockRule>()
        val popup = mutableListOf<BlockRule>()
        val cosmetic = mutableMapOf<String, MutableList<String>>()
        val exceptions = mutableMapOf<String, MutableList<String>>()
        results.forEach { r ->
            network += r.networkRules
            popup += r.popupRules
            r.cosmeticRules.forEach { (d, sels) ->
                cosmetic.getOrPut(d) { mutableListOf() }.addAll(sels)
            }
            r.cosmeticExceptions.forEach { (d, sels) ->
                exceptions.getOrPut(d) { mutableListOf() }.addAll(sels)
            }
        }
        // data-class equality covers primary-constructor props only, so distinct()
        // here is stable and cheap relative to rule matching.
        return FilterParseResult(
            network.distinct(), popup.distinct(),
            cosmetic.mapValues { it.value.distinct() },
            exceptions.mapValues { it.value.distinct() },
            results.flatMap { it.networkExceptions }.distinct(),
            results.flatMap { it.popupExceptions }.distinct()
        )
    }

    /**
     * Safe subset: URL/domain anchors, *, ^, @@, domain=, party constraints, popup,
     * all, important. Unsupported options (including resource types) are skipped,
     * not broadened. This is not a full ABP implementation.
     *
     * [stats] (optional) receives per-category line counters so dropped rules are
     * visible instead of vanishing silently. Keys: total, skipped, network, popup,
     * networkExceptions, popupExceptions, cosmetic, cosmeticDropped, networkDropped.
     */
    fun parse(rawText: String, stats: MutableMap<String, Int>? = null): FilterParseResult {
        val network = mutableListOf<BlockRule>()
        val popup = mutableListOf<BlockRule>()
        val cosmetic = mutableMapOf<String, MutableList<String>>()
        val exceptions = mutableMapOf<String, MutableList<String>>()
        val networkExceptions = mutableListOf<BlockRule>()
        val popupExceptions = mutableListOf<BlockRule>()
        fun bump(key: String) { stats?.let { it[key] = (it[key] ?: 0) + 1 } }

        rawText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            bump("total")
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) { bump("skipped"); return@forEach }
            if (line.contains("##") || line.contains("#@#") || line.contains("#?#")) {
                if (parseCosmeticLine(line, cosmetic, exceptions)) bump("cosmetic")
                else bump("cosmeticDropped")
                return@forEach
            }
            val exception = line.startsWith("@@")
            val parsed = parseNetworkRule(line.removePrefix("@@"))
            if (parsed == null) { bump("networkDropped"); return@forEach }
            val (rule, isPopup) = parsed
            when {
                exception && isPopup -> { popupExceptions.add(rule); bump("popupExceptions") }
                exception -> { networkExceptions.add(rule); bump("networkExceptions") }
                isPopup -> { popup.add(rule); bump("popup") }
                else -> { network.add(rule); bump("network") }
            }
        }
        return FilterParseResult(network, popup, cosmetic, exceptions, networkExceptions, popupExceptions)
    }

    private val domainSyntax = Regex("[a-z0-9_-]+(?:\\.[a-z0-9_-]+)*")
    // "all" matches any request (no party constraint) and "important" only affects
    // rule precedence -- both are safe no-ops here. "popup" maps to the popup index.
    private val supportedOptions = setOf(
        "third-party", "3p", "~third-party", "~3p", "first-party", "1p",
        "popup", "all", "important"
    )

    private fun parseNetworkRule(line: String): Pair<BlockRule, Boolean>? {
        val pattern = line.substringBefore('$').lowercase()
        if (pattern.isBlank() || pattern.none { it.isLetterOrDigit() }) return null
        // Regex filters and non-network extension syntax are not supported.
        if (pattern.contains('#') || pattern.contains('\\') || pattern.contains('[')) return null
        val options = line.substringAfter('$', "").lowercase().split(',').filter { it.isNotEmpty() }
        if (options.any { it !in supportedOptions && !it.startsWith("domain=") }) return null
        val domains = options.filter { it.startsWith("domain=") }.flatMap { it.removePrefix("domain=").split('|') }
        if (domains.any { !domainSyntax.matches(it.removePrefix("~")) }) return null
        val thirdParty = options.any { it == "third-party" || it == "3p" }
        val firstParty = options.any { it in setOf("~third-party", "~3p", "first-party", "1p") }
        if (thirdParty && firstParty) return null
        val bareDomain = if (pattern.startsWith("||") && pattern.endsWith('^')) pattern.removePrefix("||").dropLast(1) else ""
        val isDomain = bareDomain.contains('.') && domainSyntax.matches(bareDomain)
        return BlockRule(
            if (isDomain) bareDomain else pattern, guessCategory(pattern), isDomain,
            thirdParty, firstParty,
            domains.filterNot { it.startsWith('~') }.toSet(),
            domains.filter { it.startsWith('~') }.map { it.drop(1) }.toSet()
        ) to ("popup" in options)
    }

    /**
     * Cosmetic line formats:
     *   domain1,domain2##selector     hide on these domains
     *   domain##selector              hide on domain (+subdomains)
     *   ##selector / *##selector      generic -- skipped (too broad to inject everywhere)
     *   domain#@#selector             exception -- never hide this selector
     *   domain#?#selector             procedural (extended) -- kept only if Chromium-compatible
     */
    /** Returns true when at least one domain-scoped selector was stored. */
    private fun parseCosmeticLine(
        line: String,
        cosmetic: MutableMap<String, MutableList<String>>,
        exceptions: MutableMap<String, MutableList<String>>
    ): Boolean {
        val isException = line.contains("#@#")
        val marker: String = when {
            isException -> "#@#"
            line.contains("#?#") -> "#?#"
            else -> "##"
        }
        val idx = line.indexOf(marker)
        if (idx == -1) return false

        val domainPart = line.substring(0, idx).trim().lowercase()
        val selector = line.substring(idx + marker.length).trim()
        if (selector.isEmpty()) return false

        // Generic rules apply to all sites -- too broad (and heavy) to inject everywhere.
        // The curated generic list in CleanPage covers common popup-ad patterns instead.
        if (domainPart.isEmpty() || domainPart == "*") return false
        // Negated domains / weird domain parts can't be honored
        if (domainPart.contains("~") || domainPart.contains("/") || domainPart.contains("$")) return false
        if (domainPart.startsWith("|") || domainPart.startsWith(".")) return false

        val validated = if (isCompatibleSelector(selector)) selector else return false

        var stored = false
        domainPart.split(",").map { it.trim() }.filter { it.isNotEmpty() && !it.contains("~") }.forEach { domain ->
            if (isException) {
                exceptions.getOrPut(domain) { mutableListOf() }.add(validated)
            } else {
                cosmetic.getOrPut(domain) { mutableListOf() }.add(validated)
            }
            stored = true
        }
        return stored
    }

    /** Only standard-CSS selectors Chromium can apply; reject ABP/uBO procedural extensions. */
    private fun isCompatibleSelector(selector: String): Boolean {
        if (selector.length > 160) return false
        if (unsupportedProcedural.any { selector.contains(it) }) return false
        // Snippet injections (##^...) and uBO scriptlets
        if (selector.startsWith("^") || selector.startsWith("+js(")) return false
        // Guard against style/CSS injection -- only plain selectors allowed
        if (selector.contains("{") || selector.contains("}") || selector.contains(";")) return false
        if (selector.contains("<") || selector.contains("\\") || selector.contains("`")) return false
        if (selector.contains("$$")) return false
        // Must look like a selector: starts with letter, #, ., [, :, *, or space-combinator content
        if (selector.firstOrNull()?.let { it.isLetterOrDigit() || it in "#[.:* " } != true) return false
        return true
    }

    private fun guessCategory(pattern: String): BlockCategory = when {
        pattern.contains("doubleclick") || pattern.contains("adservice") ||
                pattern.contains("/pagead/") || pattern.contains("googlesyndication") ||
                pattern.contains("criteo") || pattern.contains("taboola") ||
                pattern.contains("outbrain") || pattern.contains("rubicon") ->
            BlockCategory.AD
        pattern.contains("fingerprint") || pattern.contains("fpjs") ||
                pattern.contains("coinhive") || pattern.contains("cryptoloot") ->
            BlockCategory.FINGERPRINT
        else -> BlockCategory.TRACKER
    }
}