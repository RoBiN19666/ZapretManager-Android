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
    val bytesUp: Long get() = parsed["bytes_up"]?.toLongOrNull() ?: 0L
    val bytesDown: Long get() = parsed["bytes_down"]?.toLongOrNull() ?: 0L
}

object TgProxyController {
    private lateinit var appContext: Context
    private val _state = MutableStateFlow(TgStatus())
    val state: StateFlow<TgStatus> = _state.asStateFlow()
    private var stopWatchdog: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != TgProxyService.ACTION_STATE) return
            val phase = runCatching {
                EnginePhase.valueOf(intent.getStringExtra(TgProxyService.EXTRA_PHASE) ?: "ERROR")
            }.getOrDefault(EnginePhase.ERROR)
            val message = intent.getStringExtra(TgProxyService.EXTRA_MESSAGE).orEmpty()
            val statusText = intent.getStringExtra(TgProxyService.EXTRA_STATUS).orEmpty()
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
    }

    fun startNow() {
        if (!::appContext.isInitialized) return
        if (_state.value.running || _state.value.busy) return
        _state.value = TgStatus(EnginePhase.STARTING, "Запуск…", since = System.currentTimeMillis())
        TgProxyService.start(appContext)
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

    fun link(): String {
        val settings = io.github.robin19666.zapretmanager.core.Settings.current()
        return TgConfig.tgLink(settings.tgPort, TgConfig.ensureSecret(), settings.tgMaskingDomain)
    }

    fun applyUrl(): String {
        val settings = io.github.robin19666.zapretmanager.core.Settings.current()
        return TgConfig.tmeLink(settings.tgPort, TgConfig.ensureSecret(), settings.tgMaskingDomain)
    }
}
