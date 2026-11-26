package io.github.robin19666.zapretmanager.tgproxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.github.robin19666.zapretmanager.core.EnginePhase
import io.github.robin19666.zapretmanager.core.LogBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TgStatus(
    val phase: EnginePhase = EnginePhase.STOPPED,
    val message: String = "",
    val statusText: String = "",
    val parsed: Map<String, String> = emptyMap(),
    val since: Long = 0L,
) {
    val running: Boolean get() = phase == EnginePhase.RUNNING
    val busy: Boolean get() = phase == EnginePhase.STARTING || phase == EnginePhase.STOPPING
    val outbound: String get() = parsed["outbound"].orEmpty()
    val routeReady: Boolean get() = parsed["status"] == "LISTENING_ROUTE_READY"
    val runtimeStatus: String get() = parsed["status"].orEmpty()
    val routeReason: String get() = parsed["route_reason"].orEmpty()
    val lastError: String get() = parsed["last_error"].orEmpty().takeIf { it != "<nil>" }.orEmpty()
    val bytesUp: Long get() = parsed["bytes_up"]?.toLongOrNull() ?: 0L
    val bytesDown: Long get() = parsed["bytes_down"]?.toLongOrNull() ?: 0L
}

object TgProxyController {
    private lateinit var appContext: Context
    private val _state = MutableStateFlow(TgStatus())
    val state: StateFlow<TgStatus> = _state.asStateFlow()
    private var stopWatchdog: Job? = null
    @Volatile
    private var lastEventAt: Long = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != TgProxyService.ACTION_STATE) return
            val phase = runCatching {
                EnginePhase.valueOf(intent.getStringExtra(TgProxyService.EXTRA_PHASE) ?: "ERROR")
            }.getOrDefault(EnginePhase.ERROR)
            val message = intent.getStringExtra(TgProxyService.EXTRA_MESSAGE).orEmpty()
            val statusText = intent.getStringExtra(TgProxyService.EXTRA_STATUS).orEmpty()
            lastEventAt = System.currentTimeMillis()
            val since = if (_state.value.phase == phase) _state.value.since else System.currentTimeMillis()
            _state.value = TgStatus(phase, message, statusText, TgConfig.parseStatus(statusText), since)
            if (phase == EnginePhase.ERROR) LogBus.e("Telegram", message)
        }
    }

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(TgProxyService.ACTION_STATE),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure { LogBus.w("Telegram", "Не удалось подписаться на состояние: ${it.message}") }
        TgProxyService.requestState(appContext)
        // Если сервис не ответил, а маркер сессии остался — прошлый запуск упал.
        io.github.robin19666.zapretmanager.core.ZmScope.scope.launch {
            kotlinx.coroutines.delay(1_500)
            val marker = java.io.File(appContext.filesDir, "tg_session.marker")
            if (marker.exists() && _state.value.phase == EnginePhase.STOPPED) {
                marker.delete()
                LogBus.w("Telegram", "Прошлый запуск прокси завершился аварийно (процесс :tg остановился)")
                _state.value = TgStatus(
                    EnginePhase.ERROR,
                    "Прошлый запуск завершился аварийно — нажмите «Подобрать маршрут» и пришлите отчёт",
                )
            }
        }
    }

    fun startNow() {
        if (!::appContext.isInitialized) return
        if (_state.value.running || _state.value.busy) return
        _state.value = TgStatus(EnginePhase.STARTING, "Запуск…", since = System.currentTimeMillis())
        lastEventAt = System.currentTimeMillis()
        TgProxyService.start(appContext)
        stopWatchdog?.cancel()
        // Сторожевой таймер следит за «тишиной»: сервис присылает прогресс на каждом этапе,
        // поэтому 45 секунд без сообщений означают, что процесс :tg упал или завис.
        stopWatchdog = io.github.robin19666.zapretmanager.core.ZmScope.scope.launch {
            while (_state.value.phase == EnginePhase.STARTING) {
                kotlinx.coroutines.delay(5_000)
                if (_state.value.phase != EnginePhase.STARTING) break
                if (System.currentTimeMillis() - lastEventAt > 45_000) {
                    val crash = io.github.robin19666.zapretmanager.core.CrashLog.summary(appContext)
                    _state.value = TgStatus(
                        EnginePhase.ERROR,
                        buildString {
                            append("Прокси не отвечает: процесс :tg упал или завис.")
                            if (!crash.isNullOrBlank()) append(" Сбой: $crash")
                            append(" Откройте «Ещё → Полная диагностика» и скопируйте отчёт.")
                        },
                    )
                    break
                }
            }
        }
    }

    fun stop() {
        if (!::appContext.isInitialized) return
        _state.value = _state.value.copy(phase = EnginePhase.STOPPING, message = "Остановка…")
        TgProxyService.stop(appContext)
        stopWatchdog?.cancel()
        stopWatchdog = io.github.robin19666.zapretmanager.core.ZmScope.scope.launch {
            kotlinx.coroutines.delay(8_000)
            if (_state.value.phase == EnginePhase.STOPPING) {
                _state.value = TgStatus(EnginePhase.STOPPED, "Telegram-прокси выключен")
            }
        }
    }

    fun toggle() {
        if (_state.value.running || _state.value.phase == EnginePhase.STARTING) stop() else startNow()
    }

    /** Останавливает прокси и запускает заново — так подбирается рабочий маршрут. */
    fun restart() {
        if (!::appContext.isInitialized) return
        TgProxyService.stop(appContext)
        stopWatchdog?.cancel()
        stopWatchdog = io.github.robin19666.zapretmanager.core.ZmScope.scope.launch {
            var waited = 0
            while (waited < 6_000 && _state.value.phase != EnginePhase.STOPPED) {
                kotlinx.coroutines.delay(200)
                waited += 200
            }
            startNow()
        }
    }

    fun link(): String {
        val settings = io.github.robin19666.zapretmanager.core.Settings.current()
        return TgConfig.tgLink(settings.tgPort, TgConfig.ensureSecret(), settings.tgMaskingDomain)
    }

    fun applyUrl(): String {
        val settings = io.github.robin19666.zapretmanager.core.Settings.current()
        return TgConfig.tmeLink(settings.tgPort, TgConfig.ensureSecret(), settings.tgMaskingDomain)
    }
}
