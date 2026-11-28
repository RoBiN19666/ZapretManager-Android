package io.github.robin19666.zapretmanager

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import io.github.robin19666.zapretmanager.byedpi.DpiEngine
import io.github.robin19666.zapretmanager.core.Cleanup
import io.github.robin19666.zapretmanager.core.CrashLog
import io.github.robin19666.zapretmanager.core.LogBus
import io.github.robin19666.zapretmanager.core.NetworkWatcher
import io.github.robin19666.zapretmanager.core.Notifications
import io.github.robin19666.zapretmanager.core.Settings
import io.github.robin19666.zapretmanager.core.TuningJournal
import io.github.robin19666.zapretmanager.core.UpdateState
import io.github.robin19666.zapretmanager.core.Watchdog
import io.github.robin19666.zapretmanager.tgproxy.TgProxyController
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this

        // Эти объекты нужны в каждом процессе: сервисы читают настройки,
        // создают каналы уведомлений и пишут crash-логи.
        Settings.init(this)
        Notifications.createChannels(this)
        CrashLog.install(this)

        // UI-контроллеры, сторож и наблюдатель сети должны жить только в
        // основном процессе. Иначе :native и :tg получают «теневые» копии,
        // которые дублируют управление движками и портят маркер Telegram.
        if (isMainProcess(this)) {
            // После штатного обновления не показываем ложные «аварийные» сообщения движков.
            UpdateState.suppressCrashNoise(this)
            if (UpdateState.isInstalling(this)) {
                Notifications.cancelInstalling(this)
                Cleanup.clearDownloads(this)
            }
            Cleanup.purgeDownloads(this)
            Cleanup.trimExports(this)
            // Если прошлый прогон теста/автоподбора был прерван, настройки
            // могли остаться на последнем проверенном кандидате.
            TuningJournal.restoreIfNeeded(this)
            DpiEngine.init(this)
            TgProxyController.init(this)
            if (Settings.current().watchdogEnabled) {
                Watchdog.start(this)
            }
            if (Settings.current().networkAutoSwitch) {
                NetworkWatcher.start(this)
            }
        }

        LogBus.i("App", "Zapret Manager ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) запущен")
    }

    private fun isMainProcess(context: Context): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            runCatching {
                File("/proc/${Process.myPid()}/cmdline").readText().trim('\u0000', '\n')
            }.getOrNull()
        }
        return processName == context.packageName
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
