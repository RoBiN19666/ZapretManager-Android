package io.github.robin19666.zapretmanager.tgproxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.github.robin19666.zapretmanager.core.EnginePhase
import io.github.robin19666.zapretmanager.core.LogBus
import io.github.robin19666.zapretmanager.core.Ports
import io.github.robin19666.zapretmanager.core.ZmScope
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
    /** Порт, с которым реально запущен прокси (0 — неизвестен). */
    val port: Int = 0,
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

    /** Отпечаток секрета, с которым реально слушает рантайм (12 hex). */
    val secretFingerprint: String get() = parsed["secret_fingerprint"].orEmpty()
}

object TgProxyController {
    private const val LIVENESS_INTERVAL_MS = 10_000L

    private lateinit var appContext: Context
    private val _state = MutableStateFlow(TgStatus())
    val state: StateFlow<TgStatus> = _state.asStateFlow()
    private var stopWatchdog: Job? = null
    @Volatile
    private var lastEventAt: Long = 0L
    @Volatile
    private var lastSettingsReload: Long = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != TgProxyService.ACTION_STATE) return
            val phase = runCatching {
                EnginePhase.valueOf(intent.getStringExtra(TgProxyService.EXTRA_PHASE) ?: "ERROR")
            }.getOrDefault(EnginePhase.ERROR)
            val message = intent.getStringExtra(TgProxyService.EXTRA_MESSAGE).orEmpty()
            val statusText = intent.getStringExtra(TgProxyService.EXTRA_STATUS).orEmpty()
            val port = intent.getIntExtra(TgProxyService.EXTRA_PORT, 0)
            lastEventAt = System.currentTimeMillis()
            val since = if (_state.value.phase == phase) _state.value.since else System.currentTimeMillis()
            _state.value = TgStatus(phase, message, statusText, TgConfig.parseStatus(statusText), since, port)
            // Сервис в процессе :tg может сам поправить настройки (например, вернуть
            // рабочий секрет прокси) — подхватываем изменения, но не чаще раза в
            // 15 секунд: статус приходит каждые 2–5 секунд, и читать настройки на
            // каждое сообщение незачем.
            val now = System.currentTimeMillis()
            if (::appContext.isInitialized &&
                (now - lastSettingsReload > 15_000 || phase != _state.value.phase)
            ) {
                lastSettingsReload = now
                runCatching { io.github.robin19666.zapretmanager.core.Settings.reload(appContext) }
            }
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
        startLiveness()
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

    /**
     * Процесс `:tg` может умереть молча — тогда UI-процесс продолжит показывать
     * RUNNING по последнему широковещательному сообщению. Проверяем локальный
     * порт: если он закрыт два замера подряд, честно показываем ошибку.
     */
    private fun startLiveness() {
        ZmScope.launch {
            var misses = 0
            while (true) {
                val interval = if (::appContext.isInitialized &&
                    io.github.robin19666.zapretmanager.core.Power.isInteractive(appContext)
                ) {
                    LIVENESS_INTERVAL_MS
                } else {
                    60_000L
                }
                kotlinx.coroutines.delay(interval)
                if (_state.value.phase != EnginePhase.RUNNING) {
                    misses = 0
                    continue
                }
                val port = _state.value.port.takeIf { it > 0 }
                    ?: io.github.robin19666.zapretmanager.core.Settings.current().tgPort
                // Проверка «порт занят» без подключения: не шумит в статистике
                // рантайма отклонёнными рукопожатиями и дешевле сокета.
                if (Ports.isListening(port)) {
                    misses = 0
                    continue
                }
                if (++misses < 2) continue
                misses = 0
                LogBus.w("Telegram", "Порт $port не отвечает — рантайм остановился")
                _state.value = TgStatus(
                    phase = EnginePhase.ERROR,
                    message = "Прокси остановился без сообщения. Нажмите «Старт» или «Подобрать маршрут».",
                    port = port,
                )
            }
        }
    }

    fun startNow() {
        if (!::appContext.isInitialized) return
        if (_state.value.running || _state.value.busy) return
        // Сервис мог остаться живым после закрытия UI: порт уже слушает, повторный
        // запуск только мешает (второй рантайм упирается в занятый порт).
        // Порт берём у работающего прокси: настройку могли изменить после запуска.
        val port = _state.value.port.takeIf { it > 0 }
            ?: io.github.robin19666.zapretmanager.core.Settings.current().tgPort
        if (Ports.isListening(port)) {
            LogBus.i("Telegram", "Прокси уже работает на порту  — повторный запуск не нужен")
            TgProxyService.requestState(appContext)
            return
        }
        _state.value = TgStatus(EnginePhase.STARTING, "Запуск…", since = System.currentTimeMillis())
        lastEventAt = System.currentTimeMillis()
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
        try {
            TgProxyService.start(appContext)
        } catch (t: Throwable) {
            stopWatchdog?.cancel()
            stopWatchdog = null
            LogBus.e("Telegram", "Не удалось запустить foreground-сервис: ${t.message}")
            _state.value = TgStatus(
                EnginePhase.ERROR,
                "Не удалось запустить сервис: ${t.message ?: t.javaClass.simpleName}. " +
                    "Откройте приложение и повторите.",
            )
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
        stopWatchdog = null
        // Отдельная корутина, а не поле stopWatchdog: startNow() сам пишет в это
        // поле, и отмена «изнутри» мешала бы перезапуску.
        ZmScope.launch {
            val deadline = System.currentTimeMillis() + 8_000
            while (System.currentTimeMillis() < deadline && _state.value.phase != EnginePhase.STOPPED) {
                kotlinx.coroutines.delay(200)
            }
            if (_state.value.phase != EnginePhase.STOPPED) {
                // Сервис не подтвердил остановку: без сброса состояния startNow()
                // увидел бы busy и молча ничего не сделал бы.
                LogBus.w("Telegram", "Остановка не подтвердилась — перезапускаю принудительно")
                _state.value = TgStatus(EnginePhase.STOPPED, "Telegram-прокси выключен")
            }
            startNow()
        }
    }

    fun link(): String {
        val settings = io.github.robin19666.zapretmanager.core.Settings.current()
        return TgConfig.tgLink(settings.tgPort, secret(), settings.tgMaskingDomain)
    }

    fun applyUrl(): String {
        val settings = io.github.robin19666.zapretmanager.core.Settings.current()
        return TgConfig.tmeLink(settings.tgPort, secret(), settings.tgMaskingDomain)
    }

    /** Секрет для ссылки: тот же файл, что читает сервис в процессе `:tg`. */
    fun secret(): String {
        check(::appContext.isInitialized) { "TgProxyController ещё не инициализирован" }
        return TgSecret.ensure(appContext)
    }
}
