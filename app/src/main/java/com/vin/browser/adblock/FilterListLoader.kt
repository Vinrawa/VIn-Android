package com.vin.browser.adblock

/**
 * Result of parsing one raw filter list (EasyList-style) text.
 */
data class FilterParseResult(
    val networkRules: List<BlockRule>,               // request-level blocking rules
    val popupRules: List<BlockRule>,                 // $popup rules — block navigations/popups
    val cosmeticRules: Map<String, List<String>>,    // domain -> element-hiding CSS selectors
    val cosmeticExceptions: Map<String, List<String>> // domain -> exception selectors (#@#)
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
        return FilterParseResult(network, popup, cosmetic, exceptions)
    }

    // Parses a pragmatic subset of Adblock Plus filter syntax:
    //   ||domain.com^                  -> network domain rule (all requests)
    //   ||domain.com^$third-party      -> network domain rule (third-party requests only)
    //   ||domain.com^$popup            -> POPUP rule: navigation to this domain is blocked
    //   /some/path/fragment            -> substring pattern rule
    //   domain1,domain2##selector      -> cosmetic: hide element on those domains
    //   domain#@#selector              -> cosmetic exception: never hide
    //   ! comment / [header] / @@allow / ##generic -> skipped
    //
    // SAFETY RULES (learned the hard way — whole-domain breakage):
    //   1. `||domain.com^*/path$opts` (path after the ^ separator) is a PATH rule.
    //      Truncating at '^' must NEVER become a bare domain rule — that blocked
    //      entire sites like bing.com, startpage.com, cloudfront.net, akamai.net.
    //   2. `$domain=...` rules are site-scoped; skipped (we can't honor the scope).
    //   3. `$third-party` is honored via BlockRule.thirdPartyOnly.
    fun parse(rawText: String): FilterParseResult {
        val network = mutableListOf<BlockRule>()
        val popup = mutableListOf<BlockRule>()
        val cosmetic = mutableMapOf<String, MutableList<String>>()
        val exceptions = mutableMapOf<String, MutableList<String>>()

        rawText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return@forEach
            if (line.startsWith("@@")) return@forEach

            // ---- Cosmetic rules (element hiding) ----
            if (line.contains("##") || line.contains("#@#") || line.contains("#?#")) {
                parseCosmeticLine(line, cosmetic, exceptions)
                return@forEach
            }

            if (line.startsWith("||")) {
                parseDomainRule(line.removePrefix("||"))?.let { (rule, isPopup) ->
                    if (isPopup) popup.add(rule) else network.add(rule)
                }
            } else {
                parsePatternRule(line)?.let { (rule, isPopup) ->
                    if (isPopup) popup.add(rule) else network.add(rule)
                }
            }
        }
        return FilterParseResult(network, popup, cosmetic, exceptions)
    }

    /**
     * Handles: domain.com^ | domain.com^$opts | domain.com$opts | domain.com
     * Returns null when the rule carries a path (after ^ or inside the body)
     * or options this engine cannot honor.
     */
    private fun parseDomainRule(body: String): Pair<BlockRule, Boolean>? {
        val lowered = body.lowercase()
        val domainPart = lowered.substringBefore("^").substringBefore("$")

        // Reject anything with a path embedded (||foo.com/bar^, ||foo.com/sp/$ping)
        if (domainPart.isEmpty() || domainPart.contains("/")) return null

        // After a '^' separator only options ($...) may follow. Anything else
        // (including "*") means the rule targets specific paths — skip it.
        val caretIdx = lowered.indexOf('^')
        if (caretIdx != -1) {
            val afterCaret = lowered.substring(caretIdx + 1)
            if (afterCaret.isNotEmpty() && !afterCaret.startsWith("$")) return null
        }

        val options = lowered.substringAfter("$", "").split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val isPopup = options.any { it == "popup" }
        if (!isPopup) {
            // Site-scoped rules can't be honored — skipping is safer than over-blocking
            if (options.any { it.startsWith("domain=") }) return null
            // ~third-party = first-party only, can't be honored correctly
            if (options.any { it == "~third-party" }) return null
        }

        val thirdPartyOnly = options.any { it == "third-party" || it == "3p" }
        return BlockRule(domainPart, guessCategory(domainPart), isDomainRule = true, thirdPartyOnly = thirdPartyOnly) to isPopup
    }

    private fun parsePatternRule(line: String): Pair<BlockRule, Boolean>? {
        val lowered = line.lowercase()
        val pattern = lowered.substringBefore("$")
        if (pattern.isEmpty()) return null

        val options = lowered.substringAfter("$", "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val isPopup = options.any { it == "popup" }

        if (!isPopup) {
            if (options.any { it.startsWith("domain=") }) return null
            if (options.any { it == "~third-party" }) return null
        } else {
            // Popup pattern rules must look path-ish to be safely applied to
            // navigations; bare short substrings cause false positives.
            if (pattern.length < 8) return null
            if (!pattern.any { it == '/' || it == '?' || it == '=' }) return null
        }

        val thirdPartyOnly = options.any { it == "third-party" || it == "3p" }
        return BlockRule(pattern, guessCategory(pattern), isDomainRule = false, thirdPartyOnly = thirdPartyOnly) to isPopup
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
