package io.github.robin19666.zapretmanager.byedpi

import android.content.Context
import io.github.robin19666.zapretmanager.core.EnginePhase
import io.github.robin19666.zapretmanager.core.LogBus
import io.github.robin19666.zapretmanager.core.NetCheck
import io.github.robin19666.zapretmanager.core.Settings
import io.github.robin19666.zapretmanager.core.SiteCheck
import io.github.robin19666.zapretmanager.core.TuningGuard
import io.github.robin19666.zapretmanager.core.TuningJournal
import io.github.robin19666.zapretmanager.core.ZmScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class StrategyTestResult(
    val strategyId: String,
    val title: String,
    val passed: Int,
    val total: Int,
    val averageMs: Long,
    val note: String = "",
    val checks: List<SiteCheck> = emptyList(),
) {
    val score: Float get() = if (total == 0) 0f else passed.toFloat() / total
    val summary: String get() = "$passed/$total" + if (averageMs > 0) " · ${averageMs} мс" else ""
}

data class StrategyTestState(
    val running: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val currentTitle: String = "",
    val results: List<StrategyTestResult> = emptyList(),
    val message: String = "",
    val finished: Boolean = false,
) {
    val progress: Float get() = if (total == 0) 0f else current.toFloat() / total
    val best: StrategyTestResult? get() = results
        .filter { it.total > 0 && it.passed > 0 }
        .sortedWith(
            compareByDescending<StrategyTestResult> { it.score }
                .thenBy { if (it.averageMs <= 0) Long.MAX_VALUE else it.averageMs }
        )
        .firstOrNull()
}

/**
 * Перебор стратегий «на живом туннеле»: для каждой стратегии поднимается
 * VpnService-движок, проверяются сервисы через SOCKS-порт byedpi, результат
 * сохраняется. Так же, как «Тест стратегий» в десктопной версии.
 *
 * Требует уже выданного VPN-разрешения: запускать из UI после VpnService.prepare().
 */
object StrategyTester {
    private val _state = MutableStateFlow(StrategyTestState())
    val state: StateFlow<StrategyTestState> = _state.asStateFlow()
    private var job: Job? = null
    private val cancelled = AtomicBoolean(false)
    private var originalStrategyId: String = "general"
    private var originalCustomArgs: String = ""

    fun start(context: Context, strategies: List<Strategy>) {
        if (_state.value.running) return
        if (strategies.isEmpty()) return
        if (!TuningGuard.tryAcquire()) {
            _state.value = _state.value.copy(message = "Сначала завершите автоподбор параметров")
            return
        }
        val current = Settings.current()
        originalStrategyId = current.strategyId
        originalCustomArgs = current.customArgs
        // Страховка на случай, если процесс убьют посреди перебора: значения
        // вернёт TuningJournal при следующем запуске приложения.
        TuningJournal.save(context, originalStrategyId, originalCustomArgs)
        cancelled.set(false)
        _state.value = StrategyTestState(running = true, total = strategies.size)
        job = ZmScope.scope.launch { run(context, strategies) }
    }

    fun cancel() {
        cancelled.set(true)
        job?.cancel()
        job = null
        DpiEngine.stop()
        // TuningGuard освобождает finally корутины: отмена не должна
        // разрешать второй перебор, пока старый ещё не завершился.
        _state.value = _state.value.copy(running = false, message = "Тест отменён")
    }

    fun applyBest(): StrategyTestResult? {
        val best = _state.value.best ?: return null
        Settings.update { it.copy(strategyId = best.strategyId, customArgs = "") }
        LogBus.i("Тест", "Выбрана стратегия «${best.title}» (${best.summary})")
        return best
    }

    private suspend fun run(context: Context, strategies: List<Strategy>) {
        val results = ArrayList<StrategyTestResult>()
        try {
            // Перед тестом обязательно останавливаем текущий движок.
            DpiEngine.stop()
            awaitPhase(12_000) { it == EnginePhase.STOPPED || it == EnginePhase.ERROR }

            strategies.forEachIndexed { index, strategy ->
                if (cancelled.get()) return@forEachIndexed
                _state.value = _state.value.copy(
                    current = index + 1,
                    currentTitle = strategy.title,
                    message = "Проверка ${index + 1} из ${strategies.size}",
                )
                Settings.update { it.copy(strategyId = strategy.id, customArgs = "") }
                DpiEngine.startNow()
                val started = awaitPhase(35_000) { it == EnginePhase.RUNNING || it == EnginePhase.ERROR }
                val state = DpiEngine.state.value
                if (!started || state.phase != EnginePhase.RUNNING) {
                    val note = state.message.ifBlank { "движок не запустился" }
                    results += StrategyTestResult(strategy.id, strategy.title, 0, 0, 0, note)
                    _state.value = _state.value.copy(results = results.toList())
                    DpiEngine.stop()
                    awaitPhase(10_000) { it == EnginePhase.STOPPED || it == EnginePhase.ERROR }
                    delay(600)
                    return@forEachIndexed
                }

                // Состояние RUNNING могло прийти от предыдущего запуска: проверяем сам порт.
                if (!TunnelProbe.awaitOpen(ByeDpiVpnService.SOCKS_PORT, 8_000)) {
                    results += StrategyTestResult(
                        strategy.id, strategy.title, 0, 0, 0,
                        "SOCKS-порт byedpi не открылся",
                    )
                    _state.value = _state.value.copy(results = results.toList())
                    DpiEngine.stop()
                    awaitPhase(10_000) { it == EnginePhase.STOPPED || it == EnginePhase.ERROR }
                    delay(600)
                    return@forEachIndexed
                }

                delay(400)
                val checks = NetCheck.run(socksPort = ByeDpiVpnService.SOCKS_PORT, timeoutMs = 4_500)
                val passed = checks.count { it.ok }
                val average = checks.filter { it.ok }.map { it.millis }.average().toLong()
                results += StrategyTestResult(
                    strategyId = strategy.id,
                    title = strategy.title,
                    passed = passed,
                    total = checks.size,
                    averageMs = average,
                    note = checks.firstOrNull { !it.ok }?.let { "${it.name}: ${it.detail}" }.orEmpty(),
                    checks = checks,
                )
                LogBus.i("Тест", "«${strategy.title}»: $passed/${checks.size}, ${average} мс")
                _state.value = _state.value.copy(results = results.toList())

                DpiEngine.stop()
                awaitPhase(10_000) { it == EnginePhase.STOPPED || it == EnginePhase.ERROR }
                delay(600)
            }
        } catch (t: Throwable) {
            LogBus.e("Тест", "Ошибка теста стратегий: ${t.message}")
            _state.value = _state.value.copy(message = "Ошибка: ${t.message}")
        } finally {
            DpiEngine.stop()
            // Тест — временная операция: возвращаем стратегию и аргументы,
            // которые были у пользователя до его запуска.
            Settings.update {
                it.copy(strategyId = originalStrategyId, customArgs = originalCustomArgs)
            }
            TuningJournal.clear(context)
            TuningGuard.release()
            _state.value = _state.value.copy(
                running = false,
                finished = true,
                currentTitle = "",
                message = if (cancelled.get()) "Тест отменён" else "Тест завершён",
            )
        }
    }

    private suspend fun awaitPhase(timeoutMs: Long, predicate: (EnginePhase) -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate(DpiEngine.state.value.phase)) return true
            delay(250)
        }
        return false
    }
}
