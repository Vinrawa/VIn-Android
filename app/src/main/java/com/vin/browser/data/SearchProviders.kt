package com.vin.browser.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /**
     * Live search suggestions. Privacy engines route through DuckDuckGo's ac endpoint,
     * everything else through Google's firefox-client endpoint. Both return the JSON
     * shape [query, [s1, s2, ...]]. Fails silent (empty list) on any error.
     */
    suspend fun suggest(query: String, isPrivacyEngine: Boolean): List<String> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()

        var conn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val apiUrl = if (isPrivacyEngine) {
                "https://duckduckgo.com/ac/?q=$encoded&type=list"
            } else {
                "https://suggestqueries.google.com/complete/search?client=firefox&q=$encoded"
            }
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
        "medium" to Pair("Medium – Where good ideas find you", "https://medium.com"),
        "linkedin" to Pair("LinkedIn: Log In or Sign Up", "https://www.linkedin.com"),
        "pinterest" to Pair("Pinterest - Discover recipes, home ideas, style", "https://www.pinterest.com")
    )

    suspend fun executeSearch(query: String, engineId: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val engine = getEngine(engineId)
        val q = query.trim()
        val qLower = q.lowercase()
        val results = mutableListOf<SearchResult>()

        // 1. Direct Official Site Match
        val directMatch = officialSites[qLower] ?: officialSites[qLower.replace(" ", "")]
        if (directMatch != null) {
            val domain = try { java.net.URI(directMatch.second).host?.removePrefix("www.") ?: "website" } catch (_: Exception) { "website" }
            results.add(
                SearchResult(
                    id = "official-top",
                    title = directMatch.first,
                    url = directMatch.second,
                    domain = domain,
                    snippet = "Official website. Access ${directMatch.first} directly.",
                    engineName = engine.name,
                    isOfficial = true,
                    score = 1000
                )
            )
        }

        // 2. Real Reddit Search API
        if (engineId == "reddit") {
            var conn: HttpURLConnection? = null
            try {
                val encoded = URLEncoder.encode(q, "UTF-8")
                val apiUrl = "https://www.reddit.com/search.json?q=$encoded&limit=15&sort=relevance"
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
                            results.add(
                                SearchResult(
                                    id = "rdt-$i",
                                    title = "$title • $sub",
                                    url = "https://www.reddit.com$permalink",
                                    domain = "reddit.com/$sub",
                                    snippet = if (selftext.isNotBlank()) selftext else "Reddit thread with $ups upvotes and $numComments comments discussion on $sub.",
                                    engineName = "Reddit",
                                    score = 500 - i
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) { } finally {
                conn?.disconnect()
            }

            if (results.isEmpty()) {
                val enc = URLEncoder.encode(q, "UTF-8")
                results.addAll(listOf(
                    SearchResult("rdt-f1", "r/all discussions on $q", "https://www.reddit.com/search/?q=$enc", "reddit.com", "Top rated community discussions, reviews, and threads about $q.", "Reddit", score = 99),
                    SearchResult("rdt-f2", "Best recommendations for $q? : r/AskReddit", "https://www.reddit.com/r/AskReddit/search/?q=$enc", "reddit.com/r/AskReddit", "Community recommendations, honest opinions, and direct answers regarding $q.", "Reddit", score = 95)
                ))
            }
            return@withContext results
        }

        // 3. DuckDuckGo Instant Answer API
        var ddgConn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val apiUrl = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            ddgConn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
            }
            if (ddgConn.responseCode == 200) {
                val jsonStr = ddgConn.inputStream.bufferedReader().readText()
                val json = JSONObject(jsonStr)
                val heading = json.optString("Heading", "")
                val abstractText = json.optString("AbstractText", "")
                val abstractUrl = json.optString("AbstractURL", "")
                val abstractSource = json.optString("AbstractSource", "Web")
                
                if (abstractText.isNotBlank() && abstractUrl.isNotBlank()) {
                    val domain = try { java.net.URI(abstractUrl).host?.removePrefix("www.") ?: abstractSource } catch (_: Exception) { abstractSource }
                    results.add(
                        SearchResult(
                            id = "ddg-instant",
                            title = if (heading.isNotBlank()) heading else "$q Overview",
                            url = abstractUrl,
                            domain = domain,
                            snippet = abstractText,
                            engineName = engine.name,
                            score = 800
                        )
                    )
                }
            }
        } catch (_: Exception) { } finally {
            ddgConn?.disconnect()
        }

        // 4. Wikipedia OpenSearch API
        var wikiConn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val apiUrl = "https://en.wikipedia.org/w/api.php?action=opensearch&search=$encoded&limit=3&namespace=0&format=json&origin=*"
            wikiConn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
            }
            if (wikiConn.responseCode == 200) {
                val json = wikiConn.inputStream.bufferedReader().readText()
                val arr = JSONArray(json)
                val titles = arr.getJSONArray(1)
                val snippets = arr.getJSONArray(2)
                val links = arr.getJSONArray(3)
                for (i in 0 until titles.length()) {
                    val title = titles.getString(i)
                    results.add(
                        SearchResult(
                            id = "wiki-$i",
                            title = "$title - Wikipedia",
                            url = links.getString(i),
                            domain = "wikipedia.org",
                            snippet = if (snippets.getString(i).isNotBlank()) snippets.getString(i) else "Encyclopedia article overview for $title.",
                            engineName = engine.name,
                            score = 400 - i
                        )
                    )
                }
            }
        } catch (_: Exception) { } finally {
            wikiConn?.disconnect()
        }

        // 5. Query word-boundary token matching (prevents "everything" matching "yt")
        val words = qLower.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        val enc = URLEncoder.encode(q, "UTF-8")
        val encDash = URLEncoder.encode(qLower.replace(" ", "-"), "UTF-8")

        if (words.contains("chess")) {
            results.addAll(listOf(
                SearchResult("ch-puzzles", "Chess Tactics & Puzzles - Train Calculation", "https://www.chess.com/puzzles", "chess.com", "Improve your tactical vision with thousands of interactive puzzles and Puzzle Rush.", engine.name, score = 300),
                SearchResult("ch-lichess", "Lichess.org - Free Open Source Chess", "https://lichess.org", "lichess.org", "100% free, open-source chess server. No ads, no tracking, unlimited computer analysis.", "Marginalia", isSmallWeb = true, score = 290),
                SearchResult("ch-reddit", "r/chess - Reddit Community & Tournaments", "https://www.reddit.com/r/chess", "reddit.com", "800k+ chess enthusiasts discussing games, grandmasters, openings, and tactics.", "Reddit", score = 280)
            ))
        } else if (words.contains("yt") || words.contains("youtube")) {
            results.addAll(listOf(
                SearchResult("yt-trending", "YouTube Trending Videos", "https://www.youtube.com/feed/trending", "youtube.com", "See what the world is watching - from the hottest music videos to what’s popular in gaming, fitness, and more.", engine.name, score = 300),
                SearchResult("yt-music", "YouTube Music", "https://music.youtube.com", "music.youtube.com", "A new music service with official albums, singles, videos, remixes, live performances and more.", engine.name, score = 290)
            ))
        } else if (engineId == "marginalia") {
            results.addAll(listOf(
                SearchResult("mgn-1", "$q - Personal Essays & Reflections", "https://solar.lowtechmagazine.com/archive/", "solar.lowtechmagazine.com", "Self-hosted, solar-powered exploration of $q. Zero tracking, pure text and minimal web design.", "Marginalia", isSmallWeb = true, score = 300),
                SearchResult("mgn-2", "Engineering Notes & Principles of $q", "https://danluu.com/", "danluu.com", "Detailed benchmarks and real data analysis on $q without commercial web clutter.", "Marginalia", isSmallWeb = true, score = 280)
            ))
        } else {
            results.addAll(listOf(
                SearchResult("g-github", "$q on GitHub: Repositories & Code", "https://github.com/search?q=$enc", "github.com", "Explore top open source tools, repositories, and source code related to $q.", engine.name, score = 250),
                SearchResult("g-reddit", "Discussions & Reviews for $q on Reddit", "https://www.reddit.com/search/?q=$enc", "reddit.com", "Community discussions, authentic feedback, questions, and answers regarding $q.", "Reddit", score = 240),
                SearchResult("g-dev", "MDN Web Docs Search: $q", "https://developer.mozilla.org/en-US/search?q=$enc", "developer.mozilla.org", "Technical reference documentation, tutorials, and web technology guides for $q.", engine.name, score = 230)
            ))
        }

        val seen = mutableSetOf<String>()
        results.filter {
            val norm = it.url.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
            seen.add(norm)
        }.sortedByDescending { it.score }
    }
}
