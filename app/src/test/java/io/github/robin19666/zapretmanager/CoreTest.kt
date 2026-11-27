package io.github.robin19666.zapretmanager

import io.github.robin19666.zapretmanager.byedpi.Strategies
import io.github.robin19666.zapretmanager.core.Versions
import io.github.robin19666.zapretmanager.tgproxy.TgConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutotunerTest {
    @Test
    fun baseCandidatesAreValidArgLines() {
        val bases = io.github.robin19666.zapretmanager.byedpi.Autotuner.baseCandidates()
        assertTrue(bases.size >= 20)
        assertTrue(bases.all { it.args.startsWith("-") })
        assertTrue(bases.map { it.args }.distinct().size == bases.size)
    }

    @Test
    fun combosExtendBase() {
        val base = io.github.robin19666.zapretmanager.byedpi.AutoResult(
            io.github.robin19666.zapretmanager.byedpi.AutoCandidate("base", "-s1"), 1, 3, 100,
        )
        val combos = io.github.robin19666.zapretmanager.byedpi.Autotuner.combos(base)
        assertTrue(combos.size >= 5)
        assertTrue(combos.all { it.args.startsWith("-s1 ") })
    }

    @Test
    fun bestPrefersCoverageThenSpeed() {
        val state = io.github.robin19666.zapretmanager.byedpi.AutoState(
            results = listOf(
                io.github.robin19666.zapretmanager.byedpi.AutoResult(
                    io.github.robin19666.zapretmanager.byedpi.AutoCandidate("a", "-s1"), 2, 3, 500,
                ),
                io.github.robin19666.zapretmanager.byedpi.AutoResult(
                    io.github.robin19666.zapretmanager.byedpi.AutoCandidate("b", "-d1"), 3, 3, 900,
                ),
                io.github.robin19666.zapretmanager.byedpi.AutoResult(
                    io.github.robin19666.zapretmanager.byedpi.AutoCandidate("c", "-o1"), 3, 3, 700,
                ),
            ),
        )
        assertEquals("c", state.best?.candidate?.label)
    }
}

class SiteListsTest {
    @Test
    fun normalizesDomains() {
        val parsed = io.github.robin19666.zapretmanager.core.SiteLists.normalize(
            """
            # комментарий
            HTTPS://WWW.Example.COM/path?q=1
            example.com
            sub.example.org
            not a domain
            """.trimIndent()
        )
        assertEquals(listOf("example.com", "sub.example.org"), parsed.domains)
        assertEquals(1, parsed.duplicates)
        assertEquals(1, parsed.invalid)
        assertEquals(4, parsed.total)
    }

    @Test
    fun keepsWildcardsAndHyphens() {
        val parsed = io.github.robin19666.zapretmanager.core.SiteLists.normalize("*.youtube.com\ni.ytimg.com")
        assertEquals(listOf("*.youtube.com", "i.ytimg.com"), parsed.domains)
    }
}

class StrategyTestStateTest {
    @Test
    fun bestPrefersCoverageThenSpeed() {
        val state = io.github.robin19666.zapretmanager.byedpi.StrategyTestState(
            results = listOf(
                io.github.robin19666.zapretmanager.byedpi.StrategyTestResult("a", "A", 3, 4, 900),
                io.github.robin19666.zapretmanager.byedpi.StrategyTestResult("b", "B", 4, 4, 1500),
                io.github.robin19666.zapretmanager.byedpi.StrategyTestResult("c", "C", 4, 4, 800),
                io.github.robin19666.zapretmanager.byedpi.StrategyTestResult("d", "D", 0, 0, 0),
            ),
        )
        assertEquals("c", state.best?.strategyId)
    }

    @Test
    fun noBestWhenNothingPassed() {
        val state = io.github.robin19666.zapretmanager.byedpi.StrategyTestState(
            results = listOf(
                io.github.robin19666.zapretmanager.byedpi.StrategyTestResult("x", "X", 0, 4, 0),
            ),
        )
        assertEquals(null, state.best)
    }
}

class VersionsTest {
    @Test
    fun comparesNumericParts() {
        assertTrue(Versions.isNewer("1.10.0", "1.9.9"))
        assertTrue(Versions.isNewer("v2.0.1", "2.0.0"))
        assertFalse(Versions.isNewer("1.0.0", "1.0.0"))
        assertFalse(Versions.isNewer("1.0", "1.0.1"))
        assertEquals(0, Versions.compare("v1.0.0", "1.0"))
    }

    @Test
    fun humanBytes() {
        assertEquals("512 Б", Versions.humanBytes(512))
        assertEquals("1.0 КБ", Versions.humanBytes(1024))
        assertEquals("1.5 МБ", Versions.humanBytes(1024L * 1024L * 3 / 2))
    }

    @Test
    fun humanDuration() {
        assertEquals("0:05", Versions.humanDuration(5_000))
        assertEquals("1:05", Versions.humanDuration(65_000))
        assertEquals("1:01:01", Versions.humanDuration(3_661_000))
    }
}

class StrategiesTest {
    @Test
    fun parsesUpstreamList() {
        val text = """
            # comment
            -d1 -d3:5:3+s -r1+s -a1

            -o1 -a1 -r-5+se
            <html> not a strategy
        """.trimIndent()
        val parsed = Strategies.parse(text)
        assertEquals(2, parsed.size)
        assertTrue(parsed[0].id.startsWith("s1-"))
        assertEquals("-o1 -a1 -r-5+se", parsed[1].args)
        assertEquals("upstream", parsed[0].origin)
        assertEquals(2, parsed.map { it.title }.distinct().size)
        assertTrue(parsed[0].title.startsWith("1."))
    }

    @Test
    fun replacesSniPlaceholder() {
        val resolved = Strategies.FAKE_SNI
        assertEquals("www.google.com", resolved)
    }

    @Test
    fun builtinListIsNotEmpty() {
        assertTrue(Strategies.builtin.size >= 5)
        assertTrue(Strategies.builtin.all { it.args.isNotBlank() })
    }

    @Test
    fun authorPresetsAreValid() {
        val presets = Strategies.authorPresets
        assertTrue(presets.size >= 4)
        assertTrue(presets.all { it.args.startsWith("-") })
        assertTrue(presets.all { it.origin == "author" })
        assertTrue(presets.map { it.id }.distinct().size == presets.size)
    }

    @Test
    fun originLabelsForBadges() {
        assertEquals("встроенная", Strategies.originLabel("builtin"))
        assertEquals("byedpi", Strategies.originLabel("author"))
        assertEquals("свои", Strategies.originLabel("custom"))
        assertEquals("ByeByeDPI", Strategies.originLabel("upstream"))
    }

    @Test
    fun importAcceptsOnlyArgLines() {
        val parsed = Strategies.parse("-o1 -a1\n< html >\n-d1", "custom", "c")
        assertEquals(2, parsed.size)
        assertTrue(parsed.all { it.id.startsWith("c") && it.origin == "custom" })
    }
}

class TgConfigTest {
    @Test
    fun buildsDdSecretWithoutDomain() {
        val secret = "0123456789abcdef0123456789abcdef"
        assertEquals("dd$secret", TgConfig.linkSecret(secret, ""))
    }

    @Test
    fun buildsEeSecretWithDomain() {
        val secret = "0123456789abcdef0123456789abcdef"
        val link = TgConfig.linkSecret(secret, "example.com")
        assertTrue(link.startsWith("ee$secret"))
        assertEquals("example.com".toByteArray().size * 2 + 2 + 32, link.length)
    }

    @Test
    fun runtimeTokensEnableCfProxy() {
        val tokens = TgConfig.runtimeTokens("cf_proxy_ws", "")
        assertTrue(tokens.contains("@route_cf_proxy_ws=1"))
        assertTrue(tokens.contains("@cfproxy=1"))
        assertTrue(tokens.contains("@cfproxy_no_tls=1"))
        assertTrue(tokens.contains("@connection_mode=cf_first"))
        assertFalse(tokens.contains(" "))
    }

    @Test
    fun runtimeTokensCanSwitchToTls() {
        val tokens = TgConfig.runtimeTokens("cf_proxy_ws", "", cfNoTls = false)
        assertFalse(tokens.contains("@cfproxy_no_tls=1"))
        assertTrue(tokens.contains("@route_cf_proxy_ws=1"))
    }

    @Test
    fun minimalTokensStayEmptyWithoutFakeTls() {
        assertEquals("", TgConfig.minimalTokens(""))
        assertEquals(
            "@mtproto_fake_tls_domain=www.google.com",
            TgConfig.minimalTokens("www.google.com"),
        )
    }

    @Test
    fun parsesStatusLine() {
        val parsed = TgConfig.parseStatus("status=LISTENING_ROUTE_READY;outbound=cf_proxy_ws;bytes_up=10")
        assertEquals("LISTENING_ROUTE_READY", parsed["status"])
        assertEquals("cf_proxy_ws", parsed["outbound"])
        assertEquals("10", parsed["bytes_up"])
    }
}
