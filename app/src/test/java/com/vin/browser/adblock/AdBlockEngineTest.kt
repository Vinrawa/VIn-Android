package com.vin.browser.adblock

import org.junit.Assert.*
import org.junit.Test

class AdBlockEngineTest {
    private fun engine(rules: String) = AdBlockEngine().apply {
        loadRules(FilterListLoader.parse(rules))
    }

    private fun AdBlockEngine.block(url: String, page: String = "publisher.example") =
        shouldBlock(url, java.net.URI(url).host, page)

    @Test fun blocksPatternsWithoutHardcodedKeywords() {
        assertNotNull(engine("/sponsor-slot/").block("https://cdn.example/sponsor-slot/file.js"))
    }

    @Test fun keepsRulesBeyondFiveHundred() {
        val rules = (1..700).joinToString("\n") { "/ads/slot-$it/" }
        assertNotNull(engine(rules).block("https://cdn.example/ads/slot-700/file.js"))
    }

    @Test fun supportsWildcardSeparatorAndUrlAnchors() {
        val e = engine("|https://cdn.example/ads/*/banner^\n/banner.gif|")
        assertNotNull(e.block("https://cdn.example/ads/123/banner?x=1"))
        assertNull(e.block("https://cdn.example/ads/123/bannerish"))
        assertNull(e.block("https://other.example/https://cdn.example/ads/123/banner"))
        assertNotNull(e.block("https://cdn.example/banner.gif"))
        assertNull(e.block("https://cdn.example/banner.gif.backup"))
    }

    @Test fun domainPathRuleDoesNotBlockWholeDomainOrLookalikes() {
        val e = engine("||cdn.example/ads/*")
        assertNotNull(e.block("https://sub.cdn.example/ads/banner.js"))
        assertNull(e.block("https://cdn.example/content.js"))
        assertNull(e.block("https://notcdn.example/ads/banner.js"))
    }

    @Test fun explicitExceptionsWinOverNetworkRules() {
        val e = engine("||ads.example^\n@@||ads.example/allowed.js|")
        assertNull(e.block("https://ads.example/allowed.js"))
        assertNotNull(e.block("https://ads.example/other.js"))
    }

    @Test fun scopedRulesHonorIncludesAndExcludes() {
        val e = engine("/sponsor-slot/\$domain=publisher.example|~safe.publisher.example")
        val url = "https://cdn.example/sponsor-slot/file.js"
        assertNotNull(e.block(url))
        assertNotNull(e.block(url, "news.publisher.example"))
        assertNull(e.block(url, "safe.publisher.example"))
        assertNull(e.block(url, "other.example"))
    }

    @Test fun partyConstraintsAreRespected() {
        val e = engine("||ads.example^\$third-party\n/first-party-slot/\$~third-party")
        assertNotNull(e.block("https://ads.example/file.js"))
        assertNull(e.block("https://ads.example/file.js", "www.ads.example"))
        assertNotNull(e.block("https://publisher.example/first-party-slot/file.js"))
        assertNull(e.block("https://cdn.example/first-party-slot/file.js"))
    }

    @Test fun unsupportedOptionsDoNotBecomeBroadDomainBlocks() {
        val e = engine("||cdn.example^\$script\n||images.example^\$image\n||other.example^\$ unknown-option")
        assertNull(e.block("https://cdn.example/styles.css"))
        assertNull(e.block("https://images.example/app.js"))
        assertNull(e.block("https://other.example/content.js"))
    }

    @Test fun searchProvidersAreNotBlanketExemptFromAdRules() {
        val e = engine("||google.com/ads/banner.js")
        assertNotNull(e.block("https://google.com/ads/banner.js", "www.google.com"))
        assertNull(e.block("https://google.com/search?q=test", "www.google.com"))
    }

    @Test fun globalSiteAndExactUrlOverridesStillWork() {
        val e = engine("||ads.example^")
        val url = "https://ads.example/file.js"
        e.isGlobalAdBlockEnabled = false
        assertNull(e.block(url))
        e.isGlobalAdBlockEnabled = true
        e.disableForSite("publisher.example")
        assertNull(e.block(url))
        e.enableForSite("publisher.example")
        assertNotNull(e.block(url))
        e.allowForSite("publisher.example", url)
        assertNull(e.block(url))
    }

    @Test fun siteDisableAlsoStopsCosmeticFiltering() {
        val e = engine("publisher.example##.sponsor")
        assertEquals(listOf(".sponsor"), e.getCosmeticSelectors("publisher.example"))
        e.disableForSite("publisher.example")
        assertTrue(e.getCosmeticSelectors("publisher.example").isEmpty())
    }

    @Test fun remoteRulesReplaceRatherThanAccumulateAndKeepBasePopups() {
        val e = engine("/popup-base-slot/\$popup")
        e.replaceRemoteRules(FilterListLoader.parse("||remote.example^"))
        assertNotNull(e.block("https://remote.example/file.js"))
        e.replaceRemoteRules(FilterListLoader.parse("||newremote.example^"))
        assertNull(e.block("https://remote.example/file.js"))
        assertNotNull(e.block("https://newremote.example/file.js"))
        assertTrue(e.shouldBlockPopup("https://cdn.example/popup-base-slot/", "cdn.example", "publisher.example"))
    }

    @Test fun remoteExceptionsAreReplacedWithTheRemoteSnapshot() {
        val e = engine("||ads.example^")
        val url = "https://ads.example/allowed.js"
        e.replaceRemoteRules(FilterListLoader.parse("@@||ads.example/allowed.js|"))
        assertNull(e.block(url))
        e.replaceRemoteRules(FilterListLoader.parse(""))
        assertNotNull(e.block(url))
    }

    @Test fun bundledListsBlockRealPatternsWithoutBlankingContent() {
        val assets = java.io.File("src/main/assets")
        val parsed = FilterListLoader.merge(listOf("easylist.txt", "easyprivacy.txt").map {
            FilterListLoader.parse(java.io.File(assets, it).readText())
        })
        val e = AdBlockEngine().apply { loadRules(parsed) }
        assertNotNull(e.block("https://cdn.example/88/tag.min.js"))
        assertNotNull(e.block("https://sub.000webhost.com/images/banners/banner.png"))
        assertNotNull(e.block("https://cdn.akamaized.net/mr/popunder.js"))
        assertNull(e.block("https://cdn.akamaized.net/content/main.js"))
        assertNull(e.block("https://www.google.com/search?q=android", "www.google.com"))
        assertNull(e.block("https://discretemath.org/ads/lesson.html"))
        assertNotNull(e.block("https://ads.doubleclick.net/request.js"))
        println("Bundled lists: ${parsed.networkRules.size} supported network rules")
    }
}