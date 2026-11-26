package io.github.robin19666.zapretmanager.byedpi

import android.content.Context
import io.github.robin19666.zapretmanager.core.EnginePhase
import io.github.robin19666.zapretmanager.core.LogBus
import io.github.robin19666.zapretmanager.core.NetCheck
import io.github.robin19666.zapretmanager.core.Settings
import io.github.robin19666.zapretmanager.core.ZmScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class AutoCandidate(val label: String, val args: String)

data class AutoResult(
    val candidate: AutoCandidate,
    val passed: Int,
    val total: Int,
    val averageMs: Long,
    val note: String = "",
) {
    val score: Float get() = if (total == 0) 0f else passed.toFloat() / total
    val summary: String get() = "$passed/$total" + if (averageMs > 0) " · ${averageMs} мс" else ""
}

data class AutoState(
    val running: Boolean = false,
    val stage: String = "",
    val index: Int = 0,
    val total: Int = 0,
    val current: AutoCandidate? = null,
    val results: List<AutoResult> = emptyList(),
    val message: String = "",
    val finished: Boolean = false,
) {
    val progress: Float get() = if (total == 0) 0f else index.toFloat() / total
    val best: AutoResult? get() = results
        .filter { it.total > 0 && it.passed > 0 }
        .sortedWith(
            compareByDescending<AutoResult> { it.passed }
                .thenBy { if (it.averageMs <= 0) Long.MAX_VALUE else it.averageMs }
        )
        .firstOrNull()
}

/**
 * Глубокий автоподбор параметров byedpi.
 *
 * В отличие от перебора готовых стратегий, здесь строится собственная команда:
 *  1. базовые техники (split / disorder / oob / fake TLS / tlsrec / mod-http) с разными позициями;
 *  2. лучшая база + вторая техника (комбинации);
 *  3. модификаторы лучшей комбинации (TTL, повторы, OOB-символ).
 *
 * Каждый кандидат проверяется на живом туннеле через SOCKS-порт byedpi.
 * Итог — готовая строка аргументов, которую можно применить и сохранить.
 */
object Autotuner {
    private const val CANDIDATE_TIMEOUT_MS = 3_500

    private val _state = MutableStateFlow(AutoState())
    val state: StateFlow<AutoState> = _state.asStateFlow()
    private var job: Job? = null
    private val cancelled = AtomicBoolean(false)
    private var originalArgs: String = ""

    fun start() {
        if (_state.value.running) return
        cancelled.set(false)
        originalArgs = Settings.current().customArgs
        _state.value = AutoState(running = true, message = "Подготовка")
        job = ZmScope.scope.launch { run() }
    }

    fun cancel() {
        cancelled.set(true)
        job?.cancel()
        job = null
        DpiEngine.stop()
        restoreOriginal()
        _state.value = _state.value.copy(running = false, message = "Автоподбор отменён")
    }

    fun apply(best: AutoResult) {
        Settings.update { it.copy(customArgs = best.candidate.args, strategyId = "general") }
        LogBus.i("Автоподбор", "Применена команда: ${best.candidate.args} (${best.summary})")
    }

    /** Сохранённый результат последнего удачного автоподбора. */
    fun savedBestFile(context: Context): File = File(context.filesDir, "autotune-best.txt")

    fun saveBest(context: Context, best: AutoResult) {
        runCatching {
            val file = savedBestFile(context)
            file.parentFile?.mkdirs()
            file.writeText(
                buildString {
                    appendLine("# Zapret Manager: автоподбор byedpi")
                    appendLine("# результат: ${best.summary}")
                    appendLine(best.candidate.args)
                }
            )
        }
    }

    private fun restoreOriginal() {
        Settings.update { it.copy(customArgs = originalArgs) }
    }

    private suspend fun run() {
        val results = ArrayList<AutoResult>()
        try {
            DpiEngine.stop()
            awaitPhase(12_000) { it == EnginePhase.STOPPED || it == EnginePhase.ERROR }

            scoreStage("Базовые техники", baseCandidates(), results)
            val bestBase = _state.value.best ?: run {
                _state.value = _state.value.copy(message = "Ни одна база не сработала — попробуйте другую сеть")
                return
            }
            scoreStage("Комбинации", combos(bestBase), results)
            val bestCombo = _state.value.best ?: bestBase
            scoreStage("Модификаторы", modifiers(bestCombo), results)

            val best = _state.value.best
            _state.value = _state.value.copy(
                message = if (best != null) {
                    "Лучший результат: ${best.candidate.args} (${best.summary})"
                } else {
                    "Рабочая комбинация не найдена"
                },
            )
        } catch (t: Throwable) {
            LogBus.e("Автоподбор", "Ошибка: ${t.message}")
            _state.value = _state.value.copy(message = "Ошибка: ${t.message}")
        } finally {
            DpiEngine.stop()
            restoreOriginal()
            _state.value = _state.value.copy(
                running = false,
                finished = true,
                current = null,
                stage = "",
            )
        }
    }

    private suspend fun scoreStage(stage: String, candidates: List<AutoCandidate>, results: MutableList<AutoResult>) {
        if (cancelled.get()) return
        _state.value = _state.value.copy(
            stage = stage,
            total = _state.value.total + candidates.size,
        )
        candidates.forEachIndexed { position, candidate ->
            if (cancelled.get()) return
            _state.value = _state.value.copy(
                index = _state.value.index + 1,
                current = candidate,
                message = "$stage: ${position + 1} из ${candidates.size}",
            )
            val result = test(candidate)
            results += result
            _state.value = _state.value.copy(results = results.toList())
            LogBus.i(
                "Автоподбор",
                "${candidate.label}: ${result.summary}${if (result.note.isBlank()) "" else " · ${result.note}"}",
            )
        }
    }

    private suspend fun test(candidate: AutoCandidate): AutoResult {
        Settings.update { it.copy(customArgs = candidate.args) }
        DpiEngine.startNow()
        val started = awaitPhase(25_000) { it == EnginePhase.RUNNING || it == EnginePhase.ERROR }
        if (!started || DpiEngine.state.value.phase != EnginePhase.RUNNING) {
            val note = DpiEngine.state.value.message.ifBlank { "движок не запустился" }
            DpiEngine.stop()
            awaitPhase(10_000) { it == EnginePhase.STOPPED || it == EnginePhase.ERROR }
            return AutoResult(candidate, 0, 0, 0, note)
        }
        delay(600)
        val checks = NetCheck.run(
            socksPort = ByeDpiVpnService.SOCKS_PORT,
            timeoutMs = CANDIDATE_TIMEOUT_MS,
            quick = true,
        )
        val passed = checks.count { it.ok }
        val average = checks.filter { it.ok }.map { it.millis }.average().toLong()
        DpiEngine.stop()
        awaitPhase(10_000) { it == EnginePhase.STOPPED || it == EnginePhase.ERROR }
        return AutoResult(
            candidate = candidate,
            passed = passed,
            total = checks.size,
            averageMs = average,
            note = checks.firstOrNull { !it.ok }?.let { "${it.name}: ${it.detail}" }.orEmpty(),
        )
    }

    private suspend fun awaitPhase(timeoutMs: Long, predicate: (EnginePhase) -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cancelled.get()) return false
            if (predicate(DpiEngine.state.value.phase)) return true
            delay(250)
        }
        return false
    }

    // ---- Генерация кандидатов ------------------------------------------------
    internal fun baseCandidates(): List<AutoCandidate> = listOf(
        AutoCandidate("split 1", "-s1"),
        AutoCandidate("split 2+s", "-s2+s"),
        AutoCandidate("split 3", "-s3"),
        AutoCandidate("disorder 1", "-d1"),
        AutoCandidate("disorder 1+s", "-d1+s"),
        AutoCandidate("disorder 3:5:3+s", "-d3:5:3+s"),
        AutoCandidate("oob 1", "-o1 -a1"),
        AutoCandidate("oob 2", "-o2 -a1"),
        AutoCandidate("disoob 1", "-q1 -a1"),
        AutoCandidate("disoob 2", "-q2 -a1"),
        AutoCandidate("fake 1 + TLS", "-f-1 -n www.google.com -Qr -a1"),
        AutoCandidate("fake 204 + TLS", "-f-204 -n www.google.com -Qr -a1"),
        AutoCandidate("tlsrec 1+s", "-r1+s"),
        AutoCandidate("tlsrec 6+s", "-r6+s"),
        AutoCandidate("split+split", "-s1 -s2+s"),
        AutoCandidate("disorder+split", "-d1 -s1+s"),
        AutoCandidate("oob+split", "-o1 -a1 -s1+s"),
        AutoCandidate("disorder+tlsrec", "-d1 -r1+s"),
        AutoCandidate("fake+disorder", "-f-1 -n www.google.com -Qr -d1 -a1"),
        AutoCandidate("mod-http+split", "-Mh -s1"),
        AutoCandidate("oob-char", "-e a -o1 -a1"),
        AutoCandidate("multi-split", "-s1 -s3 -s5+s"),
    )

    internal fun combos(base: AutoResult): List<AutoCandidate> {
        val add = listOf(
            "tlsrec 1+s" to "-r1+s",
            "disorder 1" to "-d1",
            "split 2+s" to "-s2+s",
            "fake TLS" to "-f-1 -n www.google.com -Qr -a1",
            "oob 1" to "-o1 -a1",
            "mod-http" to "-Mh",
        )
        return add.map { (label, args) ->
            AutoCandidate("${base.candidate.label} + $label", "${base.candidate.args} $args")
        }
    }

    internal fun modifiers(base: AutoResult): List<AutoCandidate> = listOf(
        AutoCandidate("${base.candidate.label} + ttl 8", "${base.candidate.args} -t8"),
        AutoCandidate("${base.candidate.label} + udp-fake 1", "${base.candidate.args} -a1"),
        AutoCandidate("${base.candidate.label} + repeat 2", "${base.candidate.args} -s2:7:2+sm"),
        AutoCandidate("${base.candidate.label} + md5sig", "${base.candidate.args} -S"),
    )
}
