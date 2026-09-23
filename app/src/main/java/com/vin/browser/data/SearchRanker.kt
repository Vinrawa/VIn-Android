package com.vin.browser.data

import kotlin.math.log10

/**
 * Pure (JVM-testable) ranking for the unified "All" search.
 *
 * Every result carries a provider base score; the ranker adds query relevance
 * (token + phrase coverage), engagement (upvotes / stars / SE score, log-scaled)
 * and position decay, merges duplicates, then applies a provider-diversity cap so
 * a fast API cannot flood the top of the list over slower providers.
 */
object SearchRanker {

    private const val W_TITLE_TOKEN = 55.0
    private const val W_SNIPPET_TOKEN = 22.0
    private const val PHRASE_TITLE_BONUS = 45.0
    private const val PHRASE_SNIPPET_BONUS = 18.0
    private const val ENGAGEMENT_UNIT = 14.0
    private const val POSITION_DECAY = 8.0

    fun rank(
        query: String,
        results: List<SearchResult>,
        maxPerProviderInTop: Int = 4,
        topWindow: Int = 10
    ): List<SearchResult> {
        if (results.isEmpty()) return results
        val tokens = tokenize(query)
        val phrase = query.trim().lowercase()

        val scored = results.map { r ->
            val title = r.title.lowercase()
            val snippet = (r.snippet + " " + r.domain).lowercase()

            var score = r.score.toDouble()
            if (tokens.isNotEmpty()) {
                val inTitle = tokens.count { title.contains(it) }
                val inSnippet = tokens.count { snippet.contains(it) }
                score += (inTitle.toDouble() / tokens.size) * W_TITLE_TOKEN
                score += (inSnippet.toDouble() / tokens.size) * W_SNIPPET_TOKEN
            }
            if (phrase.length >= 3) {
                if (title.contains(phrase)) score += PHRASE_TITLE_BONUS
                else if (snippet.contains(phrase)) score += PHRASE_SNIPPET_BONUS
            }
            if (r.engagement > 0) score += log10(r.engagement + 1.0) * ENGAGEMENT_UNIT
            score -= r.providerPosition * POSITION_DECAY

            r.copy(score = score.toInt().coerceIn(0, 3000))
        }

        // De-duplicate by normalized URL; keep the highest score, join provider labels.
        val merged = LinkedHashMap<String, SearchResult>()
        for (r in scored.sortedByDescending { it.score }) {
            val key = normalizeUrl(r.url)
            val existing = merged[key]
            if (existing == null) {
                merged[key] = r
            } else if (existing.engineName != r.engineName) {
                merged[key] = existing.copy(engineName = "${existing.engineName} · ${r.engineName}")
            }
        }

        val sorted = merged.values.sortedByDescending { it.score }

        // Provider diversity: cap how many results one provider can occupy in the
        // top window; pushed-down items rejoin the tail so nothing is lost.
        val kept = mutableListOf<SearchResult>()
        val overflow = mutableListOf<SearchResult>()
        val perProvider = HashMap<String, Int>()
        for (r in sorted) {
            val idx = kept.size
            val count = perProvider.getOrDefault(r.providerKey, 0)
            if (idx < topWindow && count >= maxPerProviderInTop) {
                overflow.add(r)
            } else {
                kept.add(r)
                perProvider[r.providerKey] = count + 1
            }
        }
        return kept + overflow
    }

    /** Splits on non-alphanumerics; single-char tokens (e.g. "c") are ignored later. */
    fun tokenize(query: String): List<String> =
        query.lowercase().split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
            .distinct()

    /** Normalized identity of a result URL for de-duplication. */
    fun normalizeUrl(url: String): String = url
        .trim()
        .lowercase()
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .trimEnd('/')
}
