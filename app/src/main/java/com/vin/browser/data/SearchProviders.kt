package com.vin.browser.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

object SearchProviders {

    val engines = listOf(
        SearchEngine("all", "All (Unified)", "ALL", 0xFF3B82F6, "https://www.google.com/search?q=%s", "https://www.google.com", isPrivacyFocused = true),
        SearchEngine("google", "Google", "G", 0xFF4285F4, "https://www.google.com/search?q=%s", "https://www.google.com"),
        SearchEngine("reddit", "Reddit", "R", 0xFFFF4500, "https://www.reddit.com/search/?q=%s", "https://www.reddit.com"),
        SearchEngine("duckduckgo", "DuckDuckGo", "DDG", 0xFFDE5833, "https://duckduckgo.com/?q=%s", "https://duckduckgo.com", isPrivacyFocused = true),
        SearchEngine("brave", "Brave Search", "BRV", 0xFFFB542B, "https://search.brave.com/search?q=%s", "https://search.brave.com", isPrivacyFocused = true),
        SearchEngine("marginalia", "Marginalia", "MGN", 0xFF10B981, "https://marginalia-search.com/search?query=%s", "https://marginalia-search.com", isPrivacyFocused = true, isIndie = true),
        SearchEngine("bing", "Microsoft Bing", "B", 0xFF008394, "https://www.bing.com/search?q=%s", "https://www.bing.com"),
        SearchEngine("searxng", "SearXNG", "SRX", 0xFF3B82F6, "https://searx.be/search?q=%s", "https://searx.space", isPrivacyFocused = true),
        SearchEngine("startpage", "Startpage", "STP", 0xFF6366F1, "https://www.startpage.com/sp/search?query=%s", "https://www.startpage.com", isPrivacyFocused = true),
        SearchEngine("yahoo", "Yahoo", "Y", 0xFF7E22CE, "https://search.yahoo.com/search?p=%s", "https://search.yahoo.com"),
        SearchEngine("qwant", "Qwant", "QWT", 0xFF06B6D4, "https://www.qwant.com/?q=%s", "https://www.qwant.com", isPrivacyFocused = true)
    )

    fun getEngine(id: String): SearchEngine = engines.find { it.id == id } ?: engines[0]

    /** Per-provider timeout for the unified fan-out. One slow API never blocks the rest. */
    private const val PROVIDER_TIMEOUT_MS = 5_000L

    /**
     * Live search suggestions. Queries go to DuckDuckGo's autocomplete endpoint ONLY
     * -- never to Google -- so omnibox keystrokes are not handed to an ad company.
     * Returns the JSON shape [query, [s1, s2, ...]]. Fails silent (empty list).
     */
    suspend fun suggest(query: String): List<String> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()

        var conn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val apiUrl = "https://duckduckgo.com/ac/?q=$encoded&type=list"
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "ViNBrowser/1.0 (Android)")
                connectTimeout = 2500
                readTimeout = 2500
            }
            if (conn.responseCode == 200) {
                val json = JSONArray(conn.inputStream.bufferedReader().readText())
                val items = json.optJSONArray(1) ?: return@withContext emptyList()
                (0 until minOf(items.length(), 8)).mapNotNull { i ->
                    items.optString(i).takeIf { it.isNotBlank() }
                }
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    private val officialSites = mapOf(
        "yt" to Pair("YouTube: Home", "https://m.youtube.com"),
        "youtube" to Pair("YouTube: Home", "https://m.youtube.com"),
        "chess" to Pair("Chess.com - Play Chess Online - Free Games", "https://www.chess.com"),
        "chess.com" to Pair("Chess.com - Play Chess Online", "https://www.chess.com"),
        "lichess" to Pair("Lichess - Free Open Source Chess", "https://lichess.org"),
        "reddit" to Pair("Reddit - Dive into anything", "https://www.reddit.com"),
        "gh" to Pair("GitHub: Let's build from here", "https://github.com"),
        "github" to Pair("GitHub: Let's build from here", "https://github.com"),
        "insta" to Pair("Instagram", "https://www.instagram.com"),
        "instagram" to Pair("Instagram", "https://www.instagram.com"),
        "fb" to Pair("Facebook - Log In or Sign Up", "https://www.facebook.com"),
        "facebook" to Pair("Facebook - Log In or Sign Up", "https://www.facebook.com"),
        "x" to Pair("X (Formerly Twitter)", "https://x.com"),
        "twitter" to Pair("X / Twitter", "https://x.com"),
        "chatgpt" to Pair("ChatGPT - OpenAI", "https://chatgpt.com"),
        "openai" to Pair("OpenAI Official Website", "https://openai.com"),
        "spotify" to Pair("Spotify - Web Player: Music for everyone", "https://open.spotify.com"),
        "netflix" to Pair("Netflix - Watch TV Shows Online, Watch Movies", "https://www.netflix.com"),
        "amazon" to Pair("Amazon.com. Spend less. Smile more.", "https://www.amazon.com"),
        "wiki" to Pair("Wikipedia, the free encyclopedia", "https://www.wikipedia.org"),
        "wikipedia" to Pair("Wikipedia, the free encyclopedia", "https://www.wikipedia.org"),
        "google" to Pair("Google Search", "https://www.google.com"),
        "gmail" to Pair("Gmail: Private and secure email", "https://mail.google.com"),
        "discord" to Pair("Discord | Group Chat & Talk", "https://discord.com"),
        "telegram" to Pair("Telegram Messenger", "https://telegram.org"),
        "stackoverflow" to Pair("Stack Overflow - Where Developers Learn", "https://stackoverflow.com"),
        "twitch" to Pair("Twitch - Live Game Streaming", "https://www.twitch.tv"),
        "quora" to Pair("Quora - A place to share knowledge", "https://www.quora.com"),
        "medium" to Pair("Medium - Where good ideas find you", "https://medium.com"),
        "linkedin" to Pair("LinkedIn: Log In or Sign Up", "https://www.linkedin.com"),
        "pinterest" to Pair("Pinterest - Discover recipes, home ideas, style", "https://www.pinterest.com")
    )

    /**
     * Unified "All" search: fans out to every open provider API in parallel, merges
     * everything with [SearchRanker] (relevance + engagement + diversity) and returns
     * a single ranked list. Local history/bookmarks and the official-site map are
     * always included so the list is instant even offline.
     *
     * Single-engine mode (engineId != "all") keeps the original behavior: curated
     * official/direct results only -- the actual web search navigates the WebView
     * to that engine's site.
     */
    suspend fun executeSearch(
        query: String,
        engineId: String,
        localHistory: List<Pair<String, String>> = emptyList(),
        localBookmarks: List<Pair<String, String>> = emptyList()
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val engine = getEngine(engineId)
        val q = query.trim()
        val qLower = q.lowercase()
        val results = mutableListOf<SearchResult>()

        // 1. Direct Official Site Match
        val directMatch = officialSites[qLower] ?: officialSites[qLower.replace(" ", "")]
        if (directMatch != null) {
            val domain = try {
                java.net.URI(directMatch.second).host?.removePrefix("www.") ?: "website"
            } catch (_: Exception) { "website" }
            results.add(
                SearchResult(
                    id = "official-top",
                    title = directMatch.first,
                    url = directMatch.second,
                    domain = domain,
                    snippet = "Official website. Access ${directMatch.first} directly.",
                    engineName = "Official",
                    isOfficial = true,
                    providerKey = "official",
                    score = 1000
                )
            )
        }

        // 2. Local history + bookmarks (rank below official, above remote fillers)
        localBookmarks.filter { matchesQuery(it, qLower) }.take(3).forEachIndexed { i, b ->
            results.add(
                SearchResult(
                    id = "bm-$i-${b.second.hashCode()}",
                    title = b.first.ifBlank { b.second },
                    url = b.second,
                    domain = hostOf(b.second),
                    snippet = "From your bookmarks",
                    engineName = "Bookmarks",
                    providerKey = "bookmarks",
                    providerPosition = i,
                    score = 950 - i
                )
            )
        }
        localHistory.filter { matchesQuery(it, qLower) }.take(4).forEachIndexed { i, h ->
            results.add(
                SearchResult(
                    id = "his-$i-${h.second.hashCode()}",
                    title = h.first.ifBlank { h.second },
                    url = h.second,
                    domain = hostOf(h.second),
                    snippet = "Visited recently from your history",
                    engineName = "History",
                    providerKey = "history",
                    providerPosition = i,
                    score = 900 - i
                )
            )
        }

        if (engineId == "all") {
            // 3. Parallel fan-out to every open provider API.
            coroutineScope {
                val providers = listOf(
                    async { withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { ddgInstant(q) } },
                    async { withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { wikipedia(q) } },
                    async { withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { reddit(q) } },
                    async { withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { github(q) } },
                    async { withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { stackExchange(q) } }
                )
                providers.awaitAll().forEach { providerResults -> if (providerResults != null) results.addAll(providerResults) }
            }

            // Utility deep-links so the user always has a "search this elsewhere" path.
            val enc = URLEncoder.encode(q, "UTF-8")
            results.add(
                SearchResult(
                    "util-google", "Search Google for \"$q\"",
                    "https://www.google.com/search?q=$enc", "google.com",
                    "Continue this query on Google's web results.", "Web", providerKey = "web", score = 200
                )
            )
            results.add(
                SearchResult(
                    "util-mdn", "MDN Web Docs: $q",
                    "https://developer.mozilla.org/en-US/search?q=$enc", "developer.mozilla.org",
                    "Technical reference documentation and web technology guides.", "Web", providerKey = "web", score = 190
                )
            )

            SearchRanker.rank(q, results)
        } else {
            // Non-unified engine: preserve the original curated fallback behavior.
            results.addAll(curatedFallback(q, engine))
            val seen = mutableSetOf<String>()
            results.filter {
                seen.add(SearchRanker.normalizeUrl(it.url))
            }.sortedByDescending { it.score }
        }
    }

    private fun matchesQuery(item: Pair<String, String>, qLower: String): Boolean {
        if (qLower.isEmpty()) return false
        val tokens = SearchRanker.tokenize(qLower)
        if (tokens.isEmpty()) return false
        val haystack = "${item.first} ${item.second}".lowercase()
        return tokens.any { haystack.contains(it) }
    }

    private fun hostOf(raw: String): String = try {
        java.net.URI(raw).host?.removePrefix("www.") ?: raw.take(30)
    } catch (_: Exception) {
        raw.removePrefix("https://").removePrefix("http://").substringBefore('/').take(30)
    }

    // ------------------------------------------------------------------
    // Provider fetchers (each returns an EMPTY list on any failure -- one
    // dead API never breaks the unified list).
    // ------------------------------------------------------------------

    private fun SearchResult.at(pos: Int) = copy(providerPosition = pos)

    /** DuckDuckGo Instant Answer API: main topic abstract + up to 3 related topics. */
    private suspend fun ddgInstant(q: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val out = mutableListOf<SearchResult>()
        var conn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val apiUrl = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "ViNBrowser/1.0 (Android)")
            }
            if (conn.responseCode == 200) {
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val heading = json.optString("Heading", "")
                val abstractText = json.optString("AbstractText", "")
                val abstractUrl = json.optString("AbstractURL", "")
                val abstractSource = json.optString("AbstractSource", "Web")
                if (abstractText.isNotBlank() && abstractUrl.isNotBlank()) {
                    val url = if (abstractUrl.startsWith("http")) abstractUrl else "https://duckduckgo.com$abstractUrl"
                    out.add(
                        SearchResult(
                            id = "ddg-instant",
                            title = if (heading.isNotBlank()) heading else "$q Overview",
                            url = url,
                            domain = hostOf(url).ifBlank { abstractSource },
                            snippet = abstractText,
                            engineName = "DuckDuckGo",
                            providerKey = "ddg",
                            score = 800
                        )
                    )
                }
                val related = json.optJSONArray("RelatedTopics") ?: return@withContext out
                var pos = 1
                for (i in 0 until related.length()) {
                    if (pos >= 4) break
                    val topic = related.optJSONObject(i) ?: continue
                    val text = topic.optString("Text", "")
                    val firstUrl = topic.optString("FirstURL", "")
                    if (text.isNotBlank() && firstUrl.startsWith("http")) {
                        out.add(
                            SearchResult(
                                id = "ddg-rel-$i",
                                title = text.substringBefore(" - ").take(80),
                                url = firstUrl,
                                domain = hostOf(firstUrl),
                                snippet = text,
                                engineName = "DuckDuckGo",
                                providerKey = "ddg",
                                score = 620 - pos * 10
                            ).at(pos)
                        )
                        pos++
                    }
                }
            }
        } catch (_: Exception) { } finally {
            conn?.disconnect()
        }
        out
    }

    /** Wikipedia OpenSearch API: up to 4 encyclopedia results. */
    private suspend fun wikipedia(q: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val out = mutableListOf<SearchResult>()
        var conn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val apiUrl = "https://en.wikipedia.org/w/api.php?action=opensearch&search=$encoded&limit=4&namespace=0&format=json&origin=*"
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "ViNBrowser/1.0 (Android)")
            }
            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().readText()
                val arr = JSONArray(json)
                val titles = arr.getJSONArray(1)
                val snippets = arr.getJSONArray(2)
                val links = arr.getJSONArray(3)
                for (i in 0 until titles.length()) {
                    val title = titles.getString(i)
                    val url = links.getString(i)
                    out.add(
                        SearchResult(
                            id = "wiki-$i",
                            title = "$title - Wikipedia",
                            url = url,
                            domain = "wikipedia.org",
                            snippet = if (snippets.getString(i).isNotBlank()) snippets.getString(i)
                            else "Encyclopedia article overview for $title.",
                            engineName = "Wikipedia",
                            providerKey = "wikipedia",
                            score = 500 - i * 10
                        ).at(i)
                    )
                }
            }
        } catch (_: Exception) { } finally {
            conn?.disconnect()
        }
        out
    }

    /** Reddit public JSON search: real threads, engagement from upvotes. */
    private suspend fun reddit(q: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val out = mutableListOf<SearchResult>()
        var conn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val apiUrl = "https://www.reddit.com/search.json?q=$encoded&limit=10&sort=relevance"
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "ViNBrowser/1.0 (Android)")
                connectTimeout = 4000
                readTimeout = 4000
            }
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().readText()
                val dataObj = JSONObject(jsonStr).getJSONObject("data")
                val children = dataObj.getJSONArray("children")
                for (i in 0 until children.length()) {
                    val childData = children.getJSONObject(i).getJSONObject("data")
                    val title = childData.optString("title", "")
                    val sub = childData.optString("subreddit_name_prefixed", "r/reddit")
                    val permalink = childData.optString("permalink", "")
                    val selftext = childData.optString("selftext", "").take(160)
                    val numComments = childData.optInt("num_comments", 0)
                    val ups = childData.optInt("ups", 0)

                    if (title.isNotBlank() && permalink.isNotBlank()) {
                        out.add(
                            SearchResult(
                                id = "rdt-$i",
                                title = "$title - $sub",
                                url = "https://www.reddit.com$permalink",
                                domain = "reddit.com/$sub",
                                snippet = if (selftext.isNotBlank()) selftext
                                else "Reddit thread with $ups upvotes and $numComments comments on $sub.",
                                engineName = "Reddit",
                                providerKey = "reddit",
                                engagement = ups + numComments,
                                score = 440
                            ).at(i)
                        )
                    }
                }
            }
        } catch (_: Exception) { } finally {
            conn?.disconnect()
        }
        out
    }

    /** GitHub repository search API: real repos, engagement from stars. */
    private suspend fun github(q: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val out = mutableListOf<SearchResult>()
        var conn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val apiUrl = "https://api.github.com/search/repositories?q=$encoded&per_page=6&sort=best-match"
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "ViNBrowser/1.0 (Android)")
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 4000
                readTimeout = 4000
            }
            if (conn.responseCode == 200) {
                val items = JSONObject(conn.inputStream.bufferedReader().readText()).optJSONArray("items")
                    ?: return@withContext out
                for (i in 0 until minOf(items.length(), 6)) {
                    val repo = items.getJSONObject(i)
                    val fullName = repo.optString("full_name", "")
                    if (fullName.isBlank()) continue
                    val stars = repo.optInt("stargazers_count", 0)
                    val desc = repo.optString("description", "")
                    out.add(
                        SearchResult(
                            id = "gh-$i",
                            title = "$fullName - GitHub",
                            url = repo.optString("html_url", "https://github.com/$fullName"),
                            domain = "github.com/$fullName",
                            snippet = if (desc.isNotBlank()) desc else "Open-source repository on GitHub.",
                            engineName = "GitHub",
                            providerKey = "github",
                            engagement = stars,
                            score = 460
                        ).at(i)
                    )
                }
            }
        } catch (_: Exception) { } finally {
            conn?.disconnect()
        }
        out
    }

    /** Stack Exchange API: programming Q&A, engagement from question score. */
    private suspend fun stackExchange(q: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val out = mutableListOf<SearchResult>()
        var conn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val apiUrl = "https://api.stackexchange.com/2.3/search/advanced?order=desc&sort=relevance&q=$encoded&site=stackoverflow&pagesize=6&filter=default"
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "ViNBrowser/1.0 (Android)")
                connectTimeout = 4000
                readTimeout = 4000
            }
            // Stack Exchange responses are gzip-compressed; HttpURLConnection
            // transparently decompresses only when gzip is NOT requested manually.
            if (conn.responseCode == 200) {
                val items = JSONObject(conn.inputStream.bufferedReader().readText()).optJSONArray("items")
                    ?: return@withContext out
                for (i in 0 until minOf(items.length(), 6)) {
                    val item = items.getJSONObject(i)
                    val title = item.optString("title", "")
                    val link = item.optString("link", "")
                    if (title.isBlank() || link.isBlank()) continue
                    val score = item.optInt("score", 0)
                    val answers = item.optInt("answer_count", 0)
                    out.add(
                        SearchResult(
                            id = "se-$i",
                            title = android.text.Html.fromHtml(title, android.text.Html.FROM_HTML_MODE_LEGACY).toString(),
                            url = link,
                            domain = "stackoverflow.com",
                            snippet = "Stack Overflow question - $answers answer(s), score $score.",
                            engineName = "Stack Overflow",
                            providerKey = "stackexchange",
                            engagement = score + answers,
                            score = 480
                        ).at(i)
                    )
                }
            }
        } catch (_: Exception) { } finally {
            conn?.disconnect()
        }
        out
    }

    /** Curated utility results for single-engine mode (never shown in unified mode). */
    private fun curatedFallback(q: String, engine: SearchEngine): List<SearchResult> {
        val words = q.lowercase().split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        return when {
            words.contains("chess") -> listOf(
                SearchResult("ch-puzzles", "Chess Tactics & Puzzles - Train Calculation", "https://www.chess.com/puzzles", "chess.com", "Improve your tactical vision with thousands of interactive puzzles and Puzzle Rush.", engine.name, providerKey = "curated", score = 300),
                SearchResult("ch-lichess", "Lichess.org - Free Open Source Chess", "https://lichess.org", "lichess.org", "100% free, open-source chess server. No ads, no tracking, unlimited computer analysis.", "Marginalia", isSmallWeb = true, providerKey = "curated", score = 290),
                SearchResult("ch-reddit", "r/chess - Reddit Community & Tournaments", "https://www.reddit.com/r/chess", "reddit.com", "800k+ chess enthusiasts discussing games, grandmasters, openings, and tactics.", "Reddit", providerKey = "curated", score = 280)
            )
            words.contains("yt") || words.contains("youtube") -> listOf(
                SearchResult("yt-trending", "YouTube Trending Videos", "https://www.youtube.com/feed/trending", "youtube.com", "See what the world is watching - from the hottest music videos to what's popular in gaming, fitness, and more.", engine.name, providerKey = "curated", score = 300),
                SearchResult("yt-music", "YouTube Music", "https://music.youtube.com", "music.youtube.com", "A new music service with official albums, singles, videos, remixes, live performances and more.", engine.name, providerKey = "curated", score = 290)
            )
            engine.id == "marginalia" -> listOf(
                SearchResult("mgn-1", "$q - Personal Essays & Reflections", "https://solar.lowtechmagazine.com/archive/", "solar.lowtechmagazine.com", "Self-hosted, solar-powered exploration of $q. Zero tracking, pure text and minimal web design.", "Marginalia", isSmallWeb = true, providerKey = "curated", score = 300),
                SearchResult("mgn-2", "Engineering Notes & Principles of $q", "https://danluu.com/", "danluu.com", "Detailed benchmarks and real data analysis on $q without commercial web clutter.", "Marginalia", isSmallWeb = true, providerKey = "curated", score = 280)
            )
            else -> emptyList()
        }
    }
}
