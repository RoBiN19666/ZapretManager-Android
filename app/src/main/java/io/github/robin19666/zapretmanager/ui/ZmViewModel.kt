package io.github.robin19666.zapretmanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.robin19666.zapretmanager.byedpi.DpiEngine
import io.github.robin19666.zapretmanager.byedpi.Strategies
import io.github.robin19666.zapretmanager.byedpi.Strategy
import io.github.robin19666.zapretmanager.core.LogBus
import io.github.robin19666.zapretmanager.core.Settings
import io.github.robin19666.zapretmanager.core.UpdateManager
import io.github.robin19666.zapretmanager.tgproxy.TgProxyController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ZmViewModel(application: Application) : AndroidViewModel(application) {

    val settings = Settings.state
    val dpi = DpiEngine.state
    val tg = TgProxyController.state
    val logs = LogBus.lines
    val updates = UpdateManager.state

    private val _strategies = MutableStateFlow<List<Strategy>>(emptyList())
    val strategies = _strategies.asStateFlow()

    init {
        refreshStrategies()
    }

    fun refreshStrategies() {
        _strategies.value = Strategies.all(getApplication())
    }

    fun selectStrategy(id: String) {
        Settings.update { it.copy(strategyId = id) }
        if (dpi.value.running) {
            // Стратегия применяется только перезапуском туннеля.
            DpiEngine.stop()
        }
    }

    fun setCustomArgs(args: String) {
        Settings.update { it.copy(customArgs = args) }
    }

    fun updateSettings(transform: (io.github.robin19666.zapretmanager.core.AppSettings) -> io.github.robin19666.zapretmanager.core.AppSettings) {
        Settings.update(transform)
    }

    fun refreshUpdates() = UpdateManager.refresh(getApplication(), force = true)

    fun updateComponent(id: String) = UpdateManager.updateComponent(getApplication(), id)

    fun updateAllComponents() = UpdateManager.updateAllComponents(getApplication())

    fun downloadAppUpdate() = UpdateManager.downloadAppUpdate(getApplication()) { _, _ -> }

    fun clearLog() = LogBus.clear()
}
