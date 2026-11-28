package io.github.robin19666.zapretmanager.byedpi

import android.content.Context
import io.github.robin19666.zapretmanager.core.EnginePhase
import io.github.robin19666.zapretmanager.core.LogBus
import io.github.robin19666.zapretmanager.core.NetCheck
import io.github.robin19666.zapretmanager.core.NetworkProfiles
import io.github.robin19666.zapretmanager.core.Settings
import io.github.robin19666.zapretmanager.core.TuningGuard
import io.github.robin19666.zapretmanager.core.TuningJournal
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

/**
 * Порядок выбора победителя: больше пройденных сервисов, затем меньше задержка,
 * затем более короткая команда (при равном результате проще конфигурация).
 */
private val AUTO_RESULT_ORDER: Comparator<AutoResult> =
    compareByDescending<AutoResult> { it.passed }
        .thenBy { if (it.averageMs <= 0) Long.MAX_VALUE else it.averageMs }
        .thenBy { it.candidate.args.length }

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
        .sortedWith(AUTO_RESULT_ORDER)
        .firstOrNull()
}

/**
 * Глубокий автоподбор параметров byedpi.
 *
 * Порядок работы:
 *  1. готовые пресеты byedpi и ByeByeDPI — на практике часто работают лучше
 *     самодельных комбинаций, поэтому они участвуют в отборе наравне с ними;
 *  2. базовые техники (split / disorder / oob / fake TLS / tlsrec / mod-http);
 *  3. лучшая база + вторая техника (комбинации);
 *  4. модификаторы лучшей комбинации (TTL, md5sig, auto, разбиение);
 *  5. финальная проверка тройки лидеров более длинными пробами — короткие пробы
 *     шумят, и победитель по ним часто оказывается случайным.
 *
 * Каждый кандидат проверяется на живом туннеле через SOCKS-порт byedpi.
 * Итог — готовая строка аргументов, которую можно применить (и запомнить для
 * текущей сети) или сохранить в файл.
 */
object Autotuner {
    private const val CANDIDATE_TIMEOUT_MS = 3_500
    private const val FINAL_TIMEOUT_MS = 6_000
    private const val FINALISTS = 3

    private val _state = MutableStateFlow(AutoState())
    val state: StateFlow<AutoState> = _state.asStateFlow()
    private var job: Job? = null
    private val cancelled = AtomicBoolean(false)
    private var originalArgs: String = ""
    private var originalStrategyId: String = "general"

    fun start(context: Context) {
        if (_state.value.running) return
        if (!TuningGuard.tryAcquire()) {
            _state.value = _state.value.copy(message = "Сначала завершите тест стратегий")
            return
        }
        val current = Settings.current()
        originalArgs = current.customArgs
        originalStrategyId = current.strategyId
        // Как и в тесте стратегий: если процесс убьют, настройки вернёт журнал.
        TuningJournal.save(context, originalStrategyId, originalArgs)
        cancelled.set(false)
        _state.value = AutoState(running = true, message = "Подготовка")
        job = ZmScope.scope.launch { run(context) }
    }

    fun cancel() {
        cancelled.set(true)
        job?.cancel()
        job = null
        DpiEngine.stop()
        restoreOriginal()
        // Guard освобождает finally корутины, иначе можно запустить второй
        // перебор, пока старый ещё выполняет блокирующие проверки.
        _state.value = _state.value.copy(running = false, message = "Автоподбор отменён")
    }

    /**
     * Применяет результат и запоминает его для текущей сети: при переключении
     * Wi-Fi ↔ мобильная сеть параметры подхватит профиль сети, а не потеряются.
     * Возвращает название профиля, если запомнить удалось.
     */
    fun apply(context: Context, best: AutoResult): String? {
        Settings.update { it.copy(customArgs = best.candidate.args) }
        LogBus.i("Автоподбор", "Применена команда: ${best.candidate.args} (${best.summary})")
        return NetworkProfiles.rememberTuning(
            context = context,
            strategyId = Settings.current().strategyId,
            customArgs = best.candidate.args,
        )?.label
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
        Settings.update { it.copy(strategyId = originalStrategyId, customArgs = originalArgs) }
    }

    private suspend fun run(context: Context) {
        val results = ArrayList<AutoResult>()
        try {
            DpiEngine.stop()
            awaitPhase(12_000) { it == EnginePhase.STOPPED || it == EnginePhase.ERROR }

            scoreStage("Готовые пресеты", presetCandidates(), results)
            scoreStage("Базовые техники", baseCandidates(), results)

            val bestBase = _state.value.best ?: run {
                _state.value = _state.value.copy(
                    message = "Ни один вариант не сработал — проверьте сеть или выберите пресет вручную",
                )
                return
            }
            scoreStage("Комбинации", combos(bestBase), results)
            val bestCombo = _state.value.best ?: bestBase
            scoreStage("Модификаторы", modifiers(bestCombo), results)

            validateFinalists(results)

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
            TuningJournal.clear(context)
            TuningGuard.release()
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
        // Одинаковые аргументы не проверяем дважды: пресеты и базы пересекаются.
        val seen = results.mapTo(HashSet()) { it.candidate.args.trim() }
        val fresh = candidates.filter { seen.add(it.args.trim()) }
        if (fresh.isEmpty()) return
        _state.value = _state.value.copy(
            stage = stage,
            total = _state.value.total + fresh.size,
        )
        fresh.forEachIndexed { position, candidate ->
            if (cancelled.get()) return
            _state.value = _state.value.copy(
                index = _state.value.index + 1,
                current = candidate,
                message = "$stage: ${position + 1} из ${fresh.size}",
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

    /**
     * Перепроверяет тройку лидеров длинными пробами (все 4 сервиса, 6 секунд):
     * на коротких пробах разница между кандидатами часто в пределах шума.
     */
    private suspend fun validateFinalists(results: MutableList<AutoResult>) {
        if (cancelled.get()) return
        val finals = results
            .filter { it.passed > 0 }
            .sortedWith(AUTO_RESULT_ORDER)
            .distinctBy { it.candidate.args.trim() }
            .take(FINALISTS)
        if (finals.isEmpty()) return

        _state.value = _state.value.copy(
            stage = "Проверка финалистов",
            total = _state.value.total + finals.size,
        )
        finals.forEachIndexed { position, leader ->
            if (cancelled.get()) return
            _state.value = _state.value.copy(
                index = _state.value.index + 1,
                current = leader.candidate,
                message = "Финальная проверка: ${position + 1} из ${finals.size}",
            )
            val verified = test(leader.candidate, timeoutMs = FINAL_TIMEOUT_MS, quick = false)
            val index = results.indexOfFirst { it.candidate.args.trim() == verified.candidate.args.trim() }
            if (index >= 0) results[index] = verified else results += verified
            _state.value = _state.value.copy(results = results.toList())
            LogBus.i("Автоподбор", "Финалист «${verified.candidate.label}»: ${verified.summary}")
        }
    }

    private suspend fun test(
        candidate: AutoCandidate,
        timeoutMs: Int = CANDIDATE_TIMEOUT_MS,
        quick: Boolean = true,
    ): AutoResult {
        Settings.update { it.copy(customArgs = candidate.args) }
        DpiEngine.startNow()
        val started = awaitPhase(35_000) { it == EnginePhase.RUNNING || it == EnginePhase.ERROR }
        if (!started || DpiEngine.state.value.phase != EnginePhase.RUNNING) {
            val note = DpiEngine.state.value.message.ifBlank { "движок не запустился" }
            DpiEngine.stop()
            awaitPhase(10_000) { it == EnginePhase.STOPPED || it == EnginePhase.ERROR }
            delay(600)
            return AutoResult(candidate, 0, 0, 0, note)
        }
        if (!TunnelProbe.awaitOpen(ByeDpiVpnService.SOCKS_PORT, 8_000)) {
            DpiEngine.stop()
            awaitPhase(10_000) { it == EnginePhase.STOPPED || it == EnginePhase.ERROR }
            delay(600)
            return AutoResult(candidate, 0, 0, 0, "SOCKS-порт byedpi не открылся")
        }
        delay(400)
        val checks = NetCheck.run(
            socksPort = ByeDpiVpnService.SOCKS_PORT,
            timeoutMs = timeoutMs,
            quick = quick,
        )
        val passed = checks.count { it.ok }
        val average = checks.filter { it.ok }.map { it.millis }.average().toLong()
        DpiEngine.stop()
        awaitPhase(10_000) { it == EnginePhase.STOPPED || it == EnginePhase.ERROR }
        delay(600)
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
    /**
     * Готовые пресеты участвуют в отборе: на практике заботливо подобранные
     * наборы из byedpi и ByeByeDPI часто выигрывают у самодельных комбинаций.
     */
    internal fun presetCandidates(): List<AutoCandidate> =
        (Strategies.builtin + Strategies.authorPresets)
            .map { AutoCandidate("пресет: ${it.title}", it.args) }

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

    /** Комбинации: не дублируем то, что уже есть в базе. */
    internal fun combos(base: AutoResult): List<AutoCandidate> = listOf(
        "tlsrec 1+s" to "-r1+s",
        "disorder 1" to "-d1",
        "split 2+s" to "-s2+s",
        "fake TLS" to "-f-1 -n www.google.com -Qr -a1",
        "oob 1" to "-o1 -a1",
        "mod-http" to "-Mh",
    ).filterNot { (_, args) -> containsFragment(base.candidate.args, args) }
        .map { (label, args) ->
            AutoCandidate("${base.candidate.label} + $label", "${base.candidate.args} $args".trim())
        }

    internal fun modifiers(base: AutoResult): List<AutoCandidate> = listOf(
        "ttl 8" to "-t8",
        "md5sig" to "-S",
        "auto torst" to "-A torst",
        "split 2+s" to "-s2+s",
    ).filterNot { (_, args) -> containsFragment(base.candidate.args, args) }
        .map { (label, args) ->
            AutoCandidate("${base.candidate.label} + $label", "${base.candidate.args} $args".trim())
        }

    /** Грубая проверка «такой флаг уже есть» — чтобы не дублировать аргументы. */
    private fun containsFragment(args: String, fragment: String): Boolean {
        val first = fragment.substringBefore(' ')
        return args.split(Regex("\\s+")).any { it == first }
    }
}
