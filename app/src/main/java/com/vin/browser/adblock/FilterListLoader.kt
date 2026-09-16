package com.vin.browser.adblock

/**
 * Result of parsing one raw filter list (EasyList-style) text.
 */
data class FilterParseResult(
    val networkRules: List<BlockRule>,               // request-level blocking rules
    val popupRules: List<BlockRule>,                 // $popup rules — block navigations/popups
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

    // Merges several parse results into one (used when combining EasyList + EasyPrivacy)
    fun merge(results: List<FilterParseResult>): FilterParseResult {
        val network = mutableListOf<BlockRule>()
        val popup = mutableListOf<BlockRule>()
        val cosmetic = mutableMapOf<String, MutableList<String>>()
        val exceptions = mutableMapOf<String, MutableList<String>>()
        results.forEach { r ->
            network += r.networkRules
            popup += r.popupRules
            r.cosmeticRules.forEach { (d, sels) -> cosmetic.getOrPut(d) { mutableListOf() }.addAll(sels) }
            r.cosmeticExceptions.forEach { (d, sels) -> exceptions.getOrPut(d) { mutableListOf() }.addAll(sels) }
        }
        return FilterParseResult(network, popup, cosmetic, exceptions,
            results.flatMap { it.networkExceptions }, results.flatMap { it.popupExceptions })
    }

    /**
     * Safe subset: URL/domain anchors, *, ^, @@, domain=, party constraints, popup.
     * Unsupported options (including resource types) are skipped, not broadened.
     * This is not a full ABP implementation.
     */
    fun parse(rawText: String): FilterParseResult {
        val network = mutableListOf<BlockRule>()
        val popup = mutableListOf<BlockRule>()
        val cosmetic = mutableMapOf<String, MutableList<String>>()
        val exceptions = mutableMapOf<String, MutableList<String>>()
        val networkExceptions = mutableListOf<BlockRule>()
        val popupExceptions = mutableListOf<BlockRule>()
        rawText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return@forEach
            if (line.contains("##") || line.contains("#@#") || line.contains("#?#")) {
                parseCosmeticLine(line, cosmetic, exceptions)
                return@forEach
            }
            val exception = line.startsWith("@@")
            val (rule, isPopup) = parseNetworkRule(line.removePrefix("@@")) ?: return@forEach
            when {
                exception && isPopup -> popupExceptions.add(rule)
                exception -> networkExceptions.add(rule)
                isPopup -> popup.add(rule)
                else -> network.add(rule)
            }
        }
        return FilterParseResult(network, popup, cosmetic, exceptions, networkExceptions, popupExceptions)
    }

    private val domainSyntax = Regex("[a-z0-9_-]+(?:\\.[a-z0-9_-]+)*")
    private val supportedOptions = setOf("third-party", "3p", "~third-party", "~3p", "first-party", "1p", "popup")

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
     *   ##selector / *##selector      generic — skipped (too broad to inject everywhere)
     *   domain#@#selector             exception — never hide this selector
     *   domain#?#selector             procedural (extended) — kept only if Chromium-compatible
     */
    private fun parseCosmeticLine(
        line: String,
        cosmetic: MutableMap<String, MutableList<String>>,
        exceptions: MutableMap<String, MutableList<String>>
    ) {
        val isException = line.contains("#@#")
        val marker: String = when {
            isException -> "#@#"
            line.contains("#?#") -> "#?#"
            else -> "##"
        }
        val idx = line.indexOf(marker)
        if (idx == -1) return

        val domainPart = line.substring(0, idx).trim().lowercase()
        val selector = line.substring(idx + marker.length).trim()
        if (selector.isEmpty()) return

        // Generic rules apply to all sites — too broad (and heavy) to inject everywhere.
        // The curated generic list in CleanPage covers common popup-ad patterns instead.
        if (domainPart.isEmpty() || domainPart == "*") return
        // Negated domains / weird domain parts can't be honored
        if (domainPart.contains("~") || domainPart.contains("/") || domainPart.contains("$")) return
        if (domainPart.startsWith("|") || domainPart.startsWith(".")) return

        val validated = if (isCompatibleSelector(selector)) selector else return

        domainPart.split(",").map { it.trim() }.filter { it.isNotEmpty() && !it.contains("~") }.forEach { domain ->
            if (isException) {
                exceptions.getOrPut(domain) { mutableListOf() }.add(validated)
            } else {
                cosmetic.getOrPut(domain) { mutableListOf() }.add(validated)
            }
        }
    }

    /** Only standard-CSS selectors Chromium can apply; reject ABP/uBO procedural extensions. */
    private fun isCompatibleSelector(selector: String): Boolean {
        if (selector.length > 160) return false
        if (unsupportedProcedural.any { selector.contains(it) }) return false
        // Snippet injections (##^...) and uBO scriptlets
        if (selector.startsWith("^") || selector.startsWith("+js(")) return false
        // Guard against style/CSS injection — only plain selectors allowed
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
