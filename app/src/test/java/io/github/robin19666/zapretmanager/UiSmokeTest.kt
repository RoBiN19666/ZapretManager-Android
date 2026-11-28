package io.github.robin19666.zapretmanager

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.robin19666.zapretmanager.ui.GhostButton
import io.github.robin19666.zapretmanager.ui.PrimaryButton
import io.github.robin19666.zapretmanager.ui.ZmCard
import io.github.robin19666.zapretmanager.ui.ZmBottomBar
import io.github.robin19666.zapretmanager.ui.ZmDestination
import io.github.robin19666.zapretmanager.ui.NavGlyph
import io.github.robin19666.zapretmanager.ui.theme.ZapretManagerTheme
import androidx.compose.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UI-тесты Compose, которые идут в обычных JVM-тестах через Robolectric:
 * эмулятор не нужен, гейт в CI занимает секунды.
 *
 * Ловят именно те регрессии, которые уже случались: не нажимающиеся кнопки,
 * пропавший клик у вторичной кнопки, поломанное нижнее меню.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiSmokeTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun primaryButtonCallsOnClick() {
        var clicks = 0
        rule.setContent {
            ZapretManagerTheme(darkTheme = true, newYear = false) {
                PrimaryButton("Проверить") { clicks++ }
            }
        }
        rule.onNodeWithText("Проверить").assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun disabledPrimaryButtonIgnoresClick() {
        var clicks = 0
        rule.setContent {
            ZapretManagerTheme(darkTheme = true, newYear = false) {
                PrimaryButton("Проверить", enabled = false) { clicks++ }
            }
        }
        rule.onNodeWithText("Проверить").performClick()
        rule.waitForIdle()
        assertEquals(0, clicks)
    }

    @Test
    fun ghostButtonCallsOnClick() {
        var clicks = 0
        rule.setContent {
            ZapretManagerTheme(darkTheme = true, newYear = false) {
                GhostButton("Копировать") { clicks++ }
            }
        }
        rule.onNodeWithText("Копировать").performClick()
        rule.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun cardShowsContent() {
        rule.setContent {
            ZapretManagerTheme(darkTheme = true, newYear = false) {
                ZmCard {
                    Text("Статус рантайма")
                    Text("LISTENING_ROUTE_READY")
                }
            }
        }
        rule.onNodeWithText("Статус рантайма").assertIsDisplayed()
        rule.onNodeWithText("LISTENING_ROUTE_READY").assertIsDisplayed()
    }

    @Test
    fun bottomBarShowsAllTabsAndSelects() {
        val selected = mutableListOf<String>()
        rule.setContent {
            ZapretManagerTheme(darkTheme = true, newYear = false) {
                Column {
                    ZmBottomBar(
                        items = listOf(
                            ZmDestination("dash", "Обзор", NavGlyph.HOME),
                            ZmDestination("strategies", "Стратегии", NavGlyph.SLIDERS),
                            ZmDestination("telegram", "Прокси", NavGlyph.PLANE),
                            ZmDestination("settings", "Ещё", NavGlyph.GEAR),
                        ),
                        current = "dash",
                        updateBadge = true,
                        onSelect = { selected += it },
                    )
                }
            }
        }
        rule.onNodeWithText("Обзор").assertIsDisplayed()
        rule.onNodeWithText("Стратегии").assertIsDisplayed()
        rule.onNodeWithText("Ещё").assertIsDisplayed()
        rule.onNodeWithText("Прокси").performClick()
        rule.waitForIdle()
        assertEquals(listOf("telegram"), selected)
    }

    @Test
    fun holidaysDoNotChangeClickability() {
        // Зимой на кнопках рисуется снег в том же Canvas — клик обязан работать.
        var clicks = 0
        rule.setContent {
            ZapretManagerTheme(darkTheme = true, newYear = true) {
                PrimaryButton("Скачать и установить") { clicks++ }
            }
        }
        rule.onNodeWithText("Скачать и установить").performClick()
        rule.waitForIdle()
        assertEquals(1, clicks)
    }
}
