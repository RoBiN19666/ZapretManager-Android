package io.github.robin19666.zapretmanager.byedpi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.core.content.ContextCompat
import io.github.robin19666.zapretmanager.core.EnginePhase
import io.github.robin19666.zapretmanager.core.EngineState
import io.github.robin19666.zapretmanager.core.LogBus
import io.github.robin19666.zapretmanager.core.Ports
import io.github.robin19666.zapretmanager.core.TuningGuard
import io.github.robin19666.zapretmanager.core.ZmScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Управление ускорением соединения из UI-процесса: разрешение VPN, запуск сервиса,
 * приём состояния из процесса :native и сторожевой таймер старта.
 */
object DpiEngine {
    private const val LIVENESS_INTERVAL_MS = 5_000L

    private lateinit var appContext: Context
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()
    private var watchdog: Job? = null
    private var session: Long = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ByeDpiVpnService.ACTION_STATE) return
            val phase = runCatching {
                EnginePhase.valueOf(intent.getStringExtra(ByeDpiVpnService.EXTRA_PHASE) ?: "ERROR")
            }.getOrDefault(EnginePhase.ERROR)
            val message = intent.getStringExtra(ByeDpiVpnService.EXTRA_MESSAGE).orEmpty()
            val extra = intent.getStringExtra(ByeDpiVpnService.EXTRA_EXTRA).orEmpty()
            val incoming = intent.getLongExtra(ByeDpiVpnService.EXTRA_SESSION, 0L)
            // Отбрасываем запоздавшие сообщения от предыдущего запуска.
            if (incoming > 0L && session > 0L && incoming != session) return
            if (session == 0L && incoming > 0L) session = incoming
            apply(phase, message, extra)
        }
    }

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        try {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(ByeDpiVpnService.ACTION_STATE),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } catch (t: Throwable) {
            LogBus.w("Ускорение", "Не удалось подписаться на состояние: ${t.message}")
        }
        // Если сервис уже работает (UI-процесс перезапускался) — узнаём его состояние.
        ByeDpiVpnService.requestState(appContext)
        startLiveness()
    }

    /**
     * Процесс `:native` может умереть молча (система, нехватка памяти): состояние
     * в UI-процессе останется RUNNING, хотя SOCKS-порт уже закрыт. Проверяем порт
     * и честно переводим движок в ERROR, чтобы переключатель не «врал».
     */
    private fun startLiveness() {
        ZmScope.launch {
            var misses = 0
            while (true) {
                delay(LIVENESS_INTERVAL_MS)
                if (_state.value.phase != EnginePhase.RUNNING || TuningGuard.active.value) {
                    misses = 0
                    continue
                }
                if (Ports.isOpen(ByeDpiVpnService.SOCKS_PORT, timeoutMs = 500)) {
                    misses = 0
                    continue
                }
                if (++misses < 2) continue
                misses = 0
                LogBus.w("Ускорение", "SOCKS-порт ${ByeDpiVpnService.SOCKS_PORT} не отвечает")
                apply(
                    EnginePhase.ERROR,
                    "Движок остановился без сообщения (процесс :native завершился). Включите ускорение заново.",
                    "",
                )
            }
        }
    }

    private fun apply(phase: EnginePhase, message: String, extra: String) {
        val previous = _state.value
        val since = if (previous.phase == phase) previous.since else System.currentTimeMillis()
        _state.value = EngineState(phase, message, since, extra)
        if (previous.phase != phase) {
            when (phase) {
                EnginePhase.RUNNING -> {
                    watchdog?.cancel()
                    LogBus.i("Ускорение", message.ifBlank { "Туннель активен" })
                }
                EnginePhase.ERROR -> {
                    watchdog?.cancel()
                    LogBus.e("Ускорение", message.ifBlank { "Ошибка движка" })
                }
                EnginePhase.STOPPED -> watchdog?.cancel()
                else -> Unit
            }
        }
    }

    /** Возвращает intent запроса VPN-разрешения либо null, если разрешение уже есть. */
    fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)

    fun startNow() {
        if (!::appContext.isInitialized) return
        if (_state.value.running || _state.value.busy) return
        _state.value = EngineState(EnginePhase.STARTING, "Запуск…", System.currentTimeMillis())
        runCatching { logStart() }.onFailure { LogBus.w("Ускорение", "Не удалось прочитать стратегию: ${it.message}") }
        session = System.currentTimeMillis()
        // Watchdog ставим до запуска сервиса: если startForegroundService
        // выбросит исключение, состояние не должно остаться в STARTING навсегда.
        watchdog?.cancel()
        watchdog = ZmScope.scope.launch {
            delay(35_000)
            if (_state.value.phase == EnginePhase.STARTING) {
                apply(EnginePhase.ERROR, "Движок не ответил за 35 секунд", "")
            }
        }
        try {
            ByeDpiVpnService.start(appContext, session)
        } catch (t: Throwable) {
            watchdog?.cancel()
            watchdog = null
            LogBus.e("Ускорение", "Не удалось запустить foreground-сервис: ${t.message}")
            apply(
                EnginePhase.ERROR,
                "Не удалось запустить сервис: ${t.message ?: t.javaClass.simpleName}. " +
                    "Откройте приложение и повторите.",
                "",
            )
        }
    }

    private fun logStart() {
        val strategy = Strategies.resolve(appContext, io.github.robin19666.zapretmanager.core.Settings.current().strategyId)
        LogBus.i("Ускорение", "Запуск: «${strategy.title}»")
    }

    fun stop() {
        if (!::appContext.isInitialized) return
        _state.value = EngineState(EnginePhase.STOPPING, "Остановка…", System.currentTimeMillis())
        ByeDpiVpnService.stop(appContext, session)
        // Если сервис уже не отвечает (например, его процесс убит), не оставляем UI в «Остановка…».
        watchdog?.cancel()
        watchdog = ZmScope.scope.launch {
            delay(8_000)
            if (_state.value.phase == EnginePhase.STOPPING) {
                apply(EnginePhase.STOPPED, "Ускорение соединения выключен", "")
            }
        }
    }

    fun toggle() {
        if (_state.value.running || _state.value.phase == EnginePhase.STARTING) stop() else startNow()
    }
}
