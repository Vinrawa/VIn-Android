package com.vin.browser.adblock

/** Compiles the supported ABP URL syntax once, not on every WebView request. */
internal class NetworkPattern(pattern: String) {
    private val literal = pattern.split('*', '^', '|').maxByOrNull { it.length }.orEmpty()
    private val regex: Regex
    init {
        var body = pattern
        val prefix = when {
            body.startsWith("||") -> {
                body = body.drop(2)
                "^[a-z][a-z0-9+.-]*://(?:[a-z0-9_-]+\\.)*"
            }
            body.startsWith('|') -> { body = body.drop(1); "^" }
            else -> ""
        }
        val end = body.endsWith('|')
        if (end) body = body.dropLast(1)
        val expression = buildString {
            append(prefix)
            for (c in body) append(when (c) {
                '*' -> ".*"
                '^' -> "(?:[^a-zA-Z0-9_.%-]|$)"
                else -> Regex.escape(c.toString())
            })
            if (end) append('$')
        }
        regex = Regex(expression, RegexOption.IGNORE_CASE)
    }
    fun matches(url: String): Boolean = url.contains(literal, ignoreCase = true) && regex.containsMatchIn(url)
}
