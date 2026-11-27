package io.github.robin19666.zapretmanager.core

import android.content.Context
import io.github.robin19666.zapretmanager.byedpi.ByeDpiVpnService
import io.github.robin19666.zapretmanager.byedpi.DpiEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * «Сторож»: пока ускорение включён, раз в минуту проверяет туннель через SOCKS-порт
 * и при двух неудачах подряд перезапускает движок. Аналог сторожа в десктопной версии.
 */
object Watchdog {
    private var job: Job? = null
    private var failures = 0

    fun start(context: Context) {
        if (job != null) return
        val app = context.applicationContext
        job = ZmScope.scope.launch {
            LogBus.i("Сторож", "Режим сторожа включён: проверка раз в 2 минуты")
            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                if (!Settings.current().watchdogEnabled) break
                if (!DpiEngine.state.value.running) {
                    failures = 0
                    continue
                }
                if (TuningGuard.active.value) {
                    // Во время теста стратегий/автоподбора движок намеренно
                    // перезапускается — сторож не должен вмешиваться.
                    failures = 0
                    continue
                }
                val checks = runCatching {
                    NetCheck.run(
                        socksPort = ByeDpiVpnService.SOCKS_PORT,
                        timeoutMs = 6_000,
                        quick = true,
                    )
                }.getOrDefault(emptyList())
                val ok = checks.any { it.ok }
                if (ok) {
                    if (failures > 0) LogBus.i("Сторож", "Туннель восстановился")
                    failures = 0
                } else {
                    failures++
                    LogBus.w("Сторож", "Проверка не прошла ($failures/2)")
                    if (failures >= 2) {
                        LogBus.w("Сторож", "Туннель не отвечает — перезапускаю ускорение соединения")
                        failures = 0
                        DpiEngine.stop()
                        // Ждём именно STOPPED/ERROR, иначе startNow() увидит
                        // STOPPING (busy) и молча ничего не сделает.
                        val deadline = System.currentTimeMillis() + 8_000
                        while (System.currentTimeMillis() < deadline &&
                            DpiEngine.state.value.busy
                        ) {
                            delay(200)
                        }
                        if (!DpiEngine.state.value.running && !DpiEngine.state.value.busy) {
                            DpiEngine.startNow()
                        }
                    }
                }
            }
            LogBus.i("Сторож", "Режим сторожа остановлен")
            job = null
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        failures = 0
    }

    private const val CHECK_INTERVAL_MS = 120_000L
}
