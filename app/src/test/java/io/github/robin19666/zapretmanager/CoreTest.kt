package io.github.robin19666.zapretmanager

import io.github.robin19666.zapretmanager.byedpi.Strategies
import io.github.robin19666.zapretmanager.core.UpdateManager
import io.github.robin19666.zapretmanager.core.Versions
import io.github.robin19666.zapretmanager.tgproxy.TgConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    @Test
    fun presetsParticipateInTuning() {
        val presets = io.github.robin19666.zapretmanager.byedpi.Autotuner.presetCandidates()
        assertTrue("пресеты должны участвовать в отборе", presets.size >= 10)
        assertTrue(presets.all { it.args.startsWith("-") && it.label.startsWith("пресет:") })
    }

    @Test
    fun combosSkipTechniqueAlreadyInBase() {
        val base = io.github.robin19666.zapretmanager.byedpi.AutoResult(
            io.github.robin19666.zapretmanager.byedpi.AutoCandidate("base", "-d1 -s1"), 3, 3, 100,
        )
        val combos = io.github.robin19666.zapretmanager.byedpi.Autotuner.combos(base)
        assertTrue(combos.none { it.args == "-d1 -s1 -d1" })
        assertTrue(combos.none { it.label.endsWith("+ disorder 1") })
    }

    @Test
    fun modifiersDoNotDuplicateFlags() {
        val base = io.github.robin19666.zapretmanager.byedpi.AutoResult(
            io.github.robin19666.zapretmanager.byedpi.AutoCandidate("base", "-s1 -t8"), 3, 3, 100,
        )
        val modifiers = io.github.robin19666.zapretmanager.byedpi.Autotuner.modifiers(base)
        assertTrue(modifiers.none { it.label.endsWith("+ ttl 8") })
        assertTrue(modifiers.isNotEmpty())
    }
}

class NetworkProfilesTest {
    private fun wifi(name: String, enabled: Boolean = true) =
        io.github.robin19666.zapretmanager.core.NetworkSnapshot(
            io.github.robin19666.zapretmanager.core.NetworkSnapshot.KIND_WIFI,
            "wifi:$name",
            name,
            enabled,
        )

    private fun mobile(operator: String = "MTS", wifiEnabled: Boolean = false) =
        io.github.robin19666.zapretmanager.core.NetworkSnapshot(
            io.github.robin19666.zapretmanager.core.NetworkSnapshot.KIND_MOBILE,
            "mobile:$operator",
            "Мобильная сеть ($operator)",
            wifiEnabled,
        )

    private fun profile(key: String, kind: String, label: String = key) =
        io.github.robin19666.zapretmanager.core.NetworkProfile(
            id = "id-$key",
            label = label,
            matchKey = key,
            kind = kind,
            strategyId = "general",
        )

    @Test
    fun matchesExactThenKindThenFallback() {
        val profiles = listOf(
            profile("wifi:Home", "wifi", "Дом"),
            profile("wifi:Office", "wifi", "Офис"),
            profile("mobile:MTS", "mobile", "МТС"),
            profile("*", "default", "Запасной"),
        )
        val match = io.github.robin19666.zapretmanager.core.NetworkProfiles
        assertEquals("Дом", match.match(profiles, wifi("Home"))?.label)
        assertEquals("МТС", match.match(profiles, mobile())?.label)
        assertEquals("Запасной", match.match(profiles, wifi("Cafe"))?.label)
        assertEquals(null, match.match(emptyList(), wifi("Home")))
    }

    @Test
    fun wifiLossIsDelayedWhileWifiEnabled() {
        val action = io.github.robin19666.zapretmanager.core.NetworkSwitchPolicy.decide(
            previousKey = "wifi:Home",
            previousKind = "wifi",
            next = mobile(wifiEnabled = true),
        )
        assertEquals(io.github.robin19666.zapretmanager.core.SwitchAction.DELAYED, action)
    }

    @Test
    fun wifiOffSwitchesImmediately() {
        val action = io.github.robin19666.zapretmanager.core.NetworkSwitchPolicy.decide(
            previousKey = "wifi:Home",
            previousKind = "wifi",
            next = mobile(wifiEnabled = false),
        )
        assertEquals(io.github.robin19666.zapretmanager.core.SwitchAction.IMMEDIATE, action)
    }

    @Test
    fun returningToWifiAndSameNetworkAreHandled() {
        val watcher = io.github.robin19666.zapretmanager.core.NetworkSwitchPolicy
        assertEquals(
            io.github.robin19666.zapretmanager.core.SwitchAction.IMMEDIATE,
            watcher.decide("mobile:MTS", "mobile", wifi("Home")),
        )
        assertEquals(
            io.github.robin19666.zapretmanager.core.SwitchAction.NONE,
            watcher.decide("wifi:Home", "wifi", wifi("Home")),
        )
        assertEquals(
            io.github.robin19666.zapretmanager.core.SwitchAction.NONE,
            watcher.decide(null, null, wifi("Home")),
        )
    }

    @Test
    fun tuningCreatesProfileForNewNetwork() {
        val profile = io.github.robin19666.zapretmanager.core.NetworkProfiles.tunedProfile(
            existing = null,
            snapshot = wifi("Cafe"),
            strategyId = "general",
            customArgs = "-o1 -a1 -s1",
            settings = io.github.robin19666.zapretmanager.core.AppSettings(tgPort = 2443, tgRoute = "direct_ws"),
        )
        assertTrue("профиль должен создаться", profile != null)
        assertEquals("wifi:Cafe", profile!!.matchKey)
        assertEquals("-o1 -a1 -s1", profile.customArgs)
        assertEquals(2443, profile.tgPort)
        assertEquals("direct_ws", profile.tgRoute)
    }

    @Test
    fun tuningUpdatesExistingProfileAndKeepsTelegram() {
        val existing = io.github.robin19666.zapretmanager.core.NetworkProfile(
            id = "p1",
            label = "Дом",
            matchKey = "wifi:Home",
            kind = io.github.robin19666.zapretmanager.core.NetworkSnapshot.KIND_WIFI,
            strategyId = "old",
            customArgs = "",
        )
        val profile = io.github.robin19666.zapretmanager.core.NetworkProfiles.tunedProfile(
            existing = existing,
            snapshot = wifi("Home"),
            strategyId = "general",
            customArgs = "-d1",
            settings = io.github.robin19666.zapretmanager.core.AppSettings(tgPort = 9999),
        )
        assertEquals("p1", profile?.id)
        assertEquals("-d1", profile?.customArgs)
        // Telegram-параметры существующего профиля не перезаписываются.
        assertEquals(1443, profile?.tgPort)
    }

    @Test
    fun tuningWithoutNetworkCreatesNothing() {
        val profile = io.github.robin19666.zapretmanager.core.NetworkProfiles.tunedProfile(
            existing = null,
            snapshot = io.github.robin19666.zapretmanager.core.NetworkSnapshot.NONE,
            strategyId = "general",
            customArgs = "-d1",
            settings = io.github.robin19666.zapretmanager.core.AppSettings(),
        )
        assertEquals(null, profile)
    }
}

class HostsTest {
    @Test
    fun normalizesDomainInput() {
        val hosts = io.github.robin19666.zapretmanager.core.Hosts
        assertEquals("example.com", hosts.normalize(" HTTPS://Example.com/path?q=1 "))
        assertEquals("sub.example.org", hosts.normalize("sub.example.org:443"))
        assertEquals("", hosts.normalize("   "))
    }
}

class MarkdownTest {
    private val markdown = io.github.robin19666.zapretmanager.ui.Markdown

    @Test
    fun parsesHeadingsBulletsAndCode() {
        val blocks = markdown.parse(
            """
            ## Что нового
            * первое **важное** изменение
            * второе `код`
            ```
            adb shell
            ```
            ---
            """.trimIndent(),
        )
        assertEquals(1, blocks.filterIsInstance<io.github.robin19666.zapretmanager.ui.MdBlock.Heading>().size)
        assertEquals(2, blocks.filterIsInstance<io.github.robin19666.zapretmanager.ui.MdBlock.Bullet>().size)
        assertEquals(1, blocks.filterIsInstance<io.github.robin19666.zapretmanager.ui.MdBlock.Code>().size)
        assertEquals(1, blocks.filterIsInstance<io.github.robin19666.zapretmanager.ui.MdBlock.Rule>().size)
    }

    @Test
    fun joinsWrappedParagraphLines() {
        val blocks = markdown.parse("Первая строка\nвторая строка\n\nНовый абзац")
        val paragraphs = blocks.filterIsInstance<io.github.robin19666.zapretmanager.ui.MdBlock.Paragraph>()
        assertEquals(2, paragraphs.size)
        assertEquals("Первая строка вторая строка", paragraphs[0].text)
    }

    @Test
    fun numberedListKeepsNumber() {
        val block = markdown.parse("1. первый\n2. второй")
            .filterIsInstance<io.github.robin19666.zapretmanager.ui.MdBlock.Bullet>()
        assertEquals(1, block[0].number)
        assertEquals("первый", block[0].text)
    }
}

class BackupTest {
    @Test
    fun settingsRoundTrip() {
        val source = io.github.robin19666.zapretmanager.core.AppSettings(
            strategyId = "custom-1",
            customArgs = "-o1 -a1",
            tgPort = 2443,
            themeMode = "light",
            splitApps = setOf("org.telegram.messenger", "com.discord"),
        )
        val json = io.github.robin19666.zapretmanager.core.Backup.settingsToJson(source)
        val restored = io.github.robin19666.zapretmanager.core.Backup.settingsFromJson(
            json,
            io.github.robin19666.zapretmanager.core.AppSettings(),
        )
        assertEquals("custom-1", restored.strategyId)
        assertEquals("-o1 -a1", restored.customArgs)
        assertEquals(2443, restored.tgPort)
        assertEquals("light", restored.themeMode)
        assertEquals(setOf("org.telegram.messenger", "com.discord"), restored.splitApps)
    }

    @Test
    fun missingFieldsFallBack() {
        val fallback = io.github.robin19666.zapretmanager.core.AppSettings(themeMode = "dark")
        val restored = io.github.robin19666.zapretmanager.core.Backup.settingsFromJson(
            org.json.JSONObject(),
            fallback,
        )
        assertEquals("dark", restored.themeMode)
        assertEquals(fallback.tgPort, restored.tgPort)
    }

    @Test
    fun secretIsNotExported() {
        val json = io.github.robin19666.zapretmanager.core.Backup.settingsToJson(
            io.github.robin19666.zapretmanager.core.AppSettings(
                tgSecret = "0123456789abcdef0123456789abcdef",
            ),
        )
        assertFalse("секрет не должен попадать в архив", json.has("tgSecret"))
    }

    @Test
    fun importKeepsCurrentSecret() {
        val current = io.github.robin19666.zapretmanager.core.AppSettings(
            tgSecret = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        )
        val json = org.json.JSONObject().put("format", 2).put("tgSecret", "bbbb")
        val restored = io.github.robin19666.zapretmanager.core.Backup.settingsFromJson(json, current)
        assertEquals(current.tgSecret, restored.tgSecret)
    }

    @Test
    fun rejectsArchiveFromNewerVersion() {
        try {
            io.github.robin19666.zapretmanager.core.Backup.checkFormat(
                org.json.JSONObject().put("format", 99),
            )
            org.junit.Assert.fail("архив нового формата должен отклоняться")
        } catch (e: java.io.IOException) {
            assertTrue(e.message.orEmpty().contains("более новой"))
        }
    }
}

class SiteCheckEngineTest {
    @Test
    fun verdictFollowsFirstBrokenStep() {
        val e = io.github.robin19666.zapretmanager.core.SiteCheckEngine
        assertEquals(io.github.robin19666.zapretmanager.core.SiteVerdict.DNS, e.classify(false, false, false, false))
        assertEquals(io.github.robin19666.zapretmanager.core.SiteVerdict.TCP, e.classify(true, false, false, false))
        assertEquals(io.github.robin19666.zapretmanager.core.SiteVerdict.TLS, e.classify(true, true, false, false))
        assertEquals(io.github.robin19666.zapretmanager.core.SiteVerdict.HTTP, e.classify(true, true, true, false))
        assertEquals(io.github.robin19666.zapretmanager.core.SiteVerdict.OK, e.classify(true, true, true, true))
    }

    @Test
    fun normalizesHostInput() {
        val e = io.github.robin19666.zapretmanager.core.SiteCheckEngine
        assertEquals("youtube.com", e.normalizeHost(" HTTPS://WWW.YouTube.com/watch?v=1 "))
        assertEquals("discord.com", e.normalizeHost("discord.com:443"))
        assertEquals("", e.normalizeHost("   "))
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

class PortsTest {
    @Test
    fun detectsListeningPort() {
        java.net.ServerSocket(0).use { server ->
            assertTrue(io.github.robin19666.zapretmanager.core.Ports.isOpen(server.localPort, timeoutMs = 1_000))
        }
    }

    @Test
    fun closedPortIsReportedAsClosed() {
        val socket = java.net.ServerSocket(0)
        val port = socket.localPort
        socket.close()
        assertFalse(io.github.robin19666.zapretmanager.core.Ports.isOpen(port, timeoutMs = 300))
    }

    @Test
    fun invalidPortIsNeverOpen() {
        assertFalse(io.github.robin19666.zapretmanager.core.Ports.isOpen(0))
        assertFalse(io.github.robin19666.zapretmanager.core.Ports.isOpen(-1))
    }
}

class PowerTest {
    @Test
    fun detectsVendorFamilies() {
        val power = io.github.robin19666.zapretmanager.core.Power
        assertEquals(
            io.github.robin19666.zapretmanager.core.VendorFamily.XIAOMI,
            power.vendorFamily("Xiaomi", "Redmi"),
        )
        assertEquals(
            io.github.robin19666.zapretmanager.core.VendorFamily.XIAOMI,
            power.vendorFamily("Xiaomi", "POCO"),
        )
        assertEquals(
            io.github.robin19666.zapretmanager.core.VendorFamily.HUAWEI,
            power.vendorFamily("HUAWEI", "HONOR"),
        )
        assertEquals(
            io.github.robin19666.zapretmanager.core.VendorFamily.SAMSUNG,
            power.vendorFamily("samsung", "samsung"),
        )
        assertEquals(
            io.github.robin19666.zapretmanager.core.VendorFamily.OPPO,
            power.vendorFamily("realme", "realme"),
        )
        assertEquals(
            io.github.robin19666.zapretmanager.core.VendorFamily.VIVO,
            power.vendorFamily("vivo", "iQOO"),
        )
        assertEquals(
            io.github.robin19666.zapretmanager.core.VendorFamily.OTHER,
            power.vendorFamily("Google", "Pixel"),
        )
    }

    @Test
    fun hintNamesRealScreensForXiaomi() {
        val power = io.github.robin19666.zapretmanager.core.Power
        val hint = power.vendorHintFor(io.github.robin19666.zapretmanager.core.VendorFamily.XIAOMI)
        assertTrue(hint.contains("Экономия заряда"))
        assertTrue(hint.contains("Автозапуск"))
        val stock = power.vendorHintFor(io.github.robin19666.zapretmanager.core.VendorFamily.OTHER)
        assertTrue(stock.contains("Без ограничений"))
    }
}

class CustomPresetsTest {
    private val presets = io.github.robin19666.zapretmanager.byedpi.CustomPresets
    private fun preset(id: String, name: String, args: String) =
        io.github.robin19666.zapretmanager.byedpi.CustomPreset(id, name, args)

    @Test
    fun serializeAndParseRoundTrip() {
        val list = listOf(preset("p-1", "Дом", "-o1 -a1"), preset("p-2", "МТС", "-d1 -s1"))
        assertEquals(list, presets.parse(presets.serialize(list)))
    }

    @Test
    fun parseSkipsBrokenEntriesAndBadJson() {
        val json = """[{"id":"a","name":"ok","args":"-d1"},{"name":"пусто","args":"   "}]"""
        val parsed = presets.parse(json)
        assertEquals(1, parsed.size)
        assertEquals("ok", parsed[0].name)
        assertTrue(presets.parse("не json").isEmpty())
        assertTrue(presets.parse("").isEmpty())
    }

    @Test
    fun upsertUpdatesExistingNameIgnoringCase() {
        val list = listOf(preset("p-1", "Дом", "-o1"))
        val updated = presets.upsert(list, " дом ", "-d1 -s1", "p-2")
        assertEquals(1, updated.size)
        assertEquals("p-1", updated[0].id)
        assertEquals("-d1 -s1", updated[0].args)
    }

    @Test
    fun upsertAddsNewAndRemoveDeletes() {
        val added = presets.upsert(emptyList(), "Вечер", "-s1", "p-9")
        assertEquals(1, added.size)
        assertEquals("p-9", added[0].id)
        assertTrue(presets.remove(added, "p-9").isEmpty())
        assertEquals(1, presets.remove(added, "другой").size)
    }

    @Test
    fun defaultNameUsesFirstFlags() {
        assertEquals("-o1 -a1 -r-5+se", presets.defaultName("-o1 -a1 -r-5+se -n www.google.com"))
        assertTrue(presets.defaultName("-a".repeat(40)).length <= 32)
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

    @Test
    fun dropsBindFlagsTogetherWithTheirValues() {
        val result = Strategies.filterTokens(
            listOf("-s1", "-i", "0.0.0.0", "-p", "9999", "-a1"),
        )
        assertEquals(listOf("-s1", "-a1"), result.tokens)
        assertEquals(listOf("-i", "0.0.0.0", "-p", "9999"), result.dropped)
    }

    @Test
    fun dropsAttachedBindValuesAndTransparentMode() {
        val result = Strategies.filterTokens(
            listOf("-i127.0.0.1", "--port=8080", "-D", "-E", "--transparent", "-d1"),
        )
        assertEquals(listOf("-d1"), result.tokens)
        assertEquals(5, result.dropped.size)
    }

    @Test
    fun dropsPidfileWithPath() {
        val result = Strategies.filterTokens(
            listOf("-w", "/data/local/tmp/a.pid", "--pidfile=/x.pid", "-s2"),
        )
        assertEquals(listOf("-s2"), result.tokens)
    }

    @Test
    fun keepsOrdinaryStrategyFlags() {
        val args = listOf("-o1", "-a1", "-s1+sm", "-f-204", "-n", "www.google.com", "-Mh,d")
        val result = Strategies.filterTokens(args)
        assertEquals(args, result.tokens)
        assertTrue(result.dropped.isEmpty())
    }

    @Test
    fun customArgsOverrideSelectedStrategy() {
        val preset = Strategies.builtin.first()
        val active = Strategies.activeFor(preset, "  -o2 -d6+s -q5+hm  ")
        assertTrue("свои аргументы должны показываться вместо пресета", active.custom)
        assertEquals(Strategies.CUSTOM_ARG_ID, active.id)
        assertEquals("-o2 -d6+s -q5+hm", active.args)
        assertEquals("Свои параметры", active.title)
    }

    @Test
    fun blankCustomArgsShowSelectedStrategy() {
        val preset = Strategies.builtin.first()
        val active = Strategies.activeFor(preset, "   ")
        assertFalse(active.custom)
        assertEquals(preset.title, active.title)
        assertEquals(preset.args, active.args)
    }

    @Test
    fun missingStrategyFallsBackToBuiltin() {
        val active = Strategies.activeFor(null, "")
        assertFalse(active.custom)
        assertEquals(Strategies.builtin.first().title, active.title)
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
    fun cachedDomainsGoIntoConfig() {
        val tokens = TgConfig.runtimeTokens(
            "cf_proxy_ws", "", cachedDomains = listOf("a.com", "b.com", "c.com"),
        )
        assertTrue(tokens.contains("@cf_cached_domains=a.com|b.com|c.com"))
        val minimal = TgConfig.minimalTokens("", listOf("a.com", "b.com"))
        assertFalse(minimal.contains("@cf_cached_domains"))
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

class UpdateCheckThrottleTest {
    private val hour = 60 * 60 * 1000L

    @Test
    fun firstLaunchAlwaysChecks() {
        assertTrue(UpdateManager.shouldAutoCheck(0L, now = 1_000L))
    }

    @Test
    fun skipsInsideTwelveHours() {
        val now = 10_000_000_000L
        assertFalse(UpdateManager.shouldAutoCheck(now - hour, now = now))
        assertFalse(UpdateManager.shouldAutoCheck(now - 11 * hour, now = now))
        // Не хватает одной миллисекунды до 12 часов — всё ещё пропуск.
        assertFalse(UpdateManager.shouldAutoCheck(now - (12 * hour - 1), now = now))
    }

    @Test
    fun checksAfterTwelveHours() {
        val now = 10_000_000_000L
        assertTrue(UpdateManager.shouldAutoCheck(now - 12 * hour, now = now))
        assertTrue(UpdateManager.shouldAutoCheck(now - 30 * hour, now = now))
    }
}

class ThemeTest {
    @Test
    fun backgroundFollowsAccent() {
        val amber = io.github.robin19666.zapretmanager.ui.theme.paletteFor(true, 0xFFFBBF24, false)
        val blue = io.github.robin19666.zapretmanager.ui.theme.paletteFor(true, 0xFF4C8DFF, false)
        assertNotEquals(amber.background, blue.background)
        assertNotEquals(amber.surface, blue.surface)
        assertNotEquals(amber.divider, blue.divider)
    }

    @Test
    fun manualBackgroundOverridesAccent() {
        val first = io.github.robin19666.zapretmanager.ui.theme.paletteFor(
            dark = true,
            accentArgb = 0xFFFBBF24,
            amoled = false,
            backgroundArgb = 0xFF4C8DFF,
        )
        val second = io.github.robin19666.zapretmanager.ui.theme.paletteFor(
            dark = true,
            accentArgb = 0xFFFF6B6B,
            amoled = false,
            backgroundArgb = 0xFF4C8DFF,
        )
        assertEquals(first.background, second.background)
        assertNotEquals(first.accent, second.accent)
    }

    @Test
    fun amoledIsPureBlack() {
        val palette = io.github.robin19666.zapretmanager.ui.theme.paletteFor(true, 0xFFFBBF24, true)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), palette.background)
    }

    @Test
    fun defaultAccentIsAmberAndBackgroundIsAuto() {
        val defaults = io.github.robin19666.zapretmanager.core.AppSettings()
        assertEquals(0xFFFBBF24, defaults.accentColor)
        assertEquals(0L, defaults.backgroundTint)
    }
}
