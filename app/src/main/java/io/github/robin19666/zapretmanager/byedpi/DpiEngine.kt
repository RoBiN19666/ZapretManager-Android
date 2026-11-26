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
import io.github.robin19666.zapretmanager.core.ZmScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Управление обходом DPI из UI-процесса: разрешение VPN, запуск сервиса,
 * приём состояния из процесса :native и сторожевой таймер старта.
 */
object DpiEngine {
    private lateinit var appContext: Context
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()
    private var watchdog: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ByeDpiVpnService.ACTION_STATE) return
            val phase = runCatching {
                EnginePhase.valueOf(intent.getStringExtra(ByeDpiVpnService.EXTRA_PHASE) ?: "ERROR")
            }.getOrDefault(EnginePhase.ERROR)
            val message = intent.getStringExtra(ByeDpiVpnService.EXTRA_MESSAGE).orEmpty()
            val extra = intent.getStringExtra(ByeDpiVpnService.EXTRA_EXTRA).orEmpty()
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
            LogBus.w("Обход", "Не удалось подписаться на состояние: ${t.message}")
        }
    }

    private fun apply(phase: EnginePhase, message: String, extra: String) {
        val previous = _state.value
        val since = if (previous.phase == phase) previous.since else System.currentTimeMillis()
        _state.value = EngineState(phase, message, since, extra)
        when (phase) {
            EnginePhase.RUNNING -> {
                watchdog?.cancel()
                LogBus.i("Обход", message.ifBlank { "Туннель активен" })
            }
            EnginePhase.ERROR -> {
                watchdog?.cancel()
                LogBus.e("Обход", message.ifBlank { "Ошибка движка" })
            }
            EnginePhase.STOPPED -> watchdog?.cancel()
            else -> Unit
        }
    }

    /** Возвращает intent запроса VPN-разрешения либо null, если разрешение уже есть. */
    fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)

    fun startNow() {
        if (!::appContext.isInitialized) return
        if (_state.value.running || _state.value.busy) return
        _state.value = EngineState(EnginePhase.STARTING, "Запуск…", System.currentTimeMillis())
        logStart()
        ByeDpiVpnService.start(appContext)
        watchdog?.cancel()
        watchdog = ZmScope.scope.launch {
            delay(20_000)
            if (_state.value.phase == EnginePhase.STARTING) {
                apply(EnginePhase.ERROR, "Движок не ответил за 20 секунд", "")
            }
        }
    }

    private fun logStart() {
        val strategy = Strategies.resolve(appContext, io.github.robin19666.zapretmanager.core.Settings.current().strategyId)
        LogBus.i("Обход", "Запуск: «${strategy.title}»")
    }

    fun stop() {
        if (!::appContext.isInitialized) return
        _state.value = EngineState(EnginePhase.STOPPING, "Остановка…", System.currentTimeMillis())
        ByeDpiVpnService.stop(appContext)
    }

    fun toggle() {
        if (_state.value.running || _state.value.phase == EnginePhase.STARTING) stop() else startNow()
    }
}
