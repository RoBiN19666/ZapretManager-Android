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
        // Выбор готовой стратегии должен выключать свои аргументы: иначе они
        // остаются приоритетнее (так строится командная строка для byedpi), и
        // выбранный пресет молча перестаёт применяться.
        Settings.update { it.copy(strategyId = id, customArgs = "") }
        restartIfRunning()
    }

    fun setCustomArgs(args: String) {
        Settings.update { it.copy(customArgs = args) }
        restartIfRunning()
    }

    /** Новые аргументы применяются перезапуском туннеля, а не полным выключением. */
    private fun restartIfRunning() {
        if (dpi.value.running || dpi.value.busy) {
            DpiEngine.restart()
        }
    }

    fun updateSettings(transform: (io.github.robin19666.zapretmanager.core.AppSettings) -> io.github.robin19666.zapretmanager.core.AppSettings) {
        val before = io.github.robin19666.zapretmanager.core.Settings.current()
        io.github.robin19666.zapretmanager.core.Settings.update(transform)
        val after = io.github.robin19666.zapretmanager.core.Settings.current()
        // Выключили автозапуск — снимаем и «движок должен работать», иначе
        // следующий запуск приложения всё равно поднимет движки.
        if (before.autostartOnLaunch && !after.autostartOnLaunch) {
            io.github.robin19666.zapretmanager.core.EngineMemory.setDpiWanted(getApplication(), false)
        }
        if (before.autostartTgOnLaunch && !after.autostartTgOnLaunch) {
            io.github.robin19666.zapretmanager.core.EngineMemory.setTgWanted(getApplication(), false)
        }
        if (before.tgAutoApply && !after.tgAutoApply) {
            io.github.robin19666.zapretmanager.core.LogBus.i("Telegram", "Автоприменение ссылки выключено")
        }
    }

    fun refreshUpdates() = UpdateManager.refresh(getApplication(), force = true)

    /** Проверка при запуске приложения — не чаще раза в 12 часов. */
    fun refreshUpdatesOnLaunch() = UpdateManager.refreshOnLaunch(getApplication())

    fun updateComponent(id: String) = UpdateManager.updateComponent(getApplication(), id)

    fun updateAllComponents() = UpdateManager.updateAllComponents(getApplication())

    /** «Обновить всё»: компоненты + скачивание и установка APK. */
    fun updateEverything() = UpdateManager.updateEverything(getApplication())

    fun downloadAppUpdate() = UpdateManager.downloadAppUpdate(getApplication()) { _, _ -> }

    fun dismissInstallerNotice() = UpdateManager.dismissInstallerNotice()

    fun clearLog() = LogBus.clear()
}