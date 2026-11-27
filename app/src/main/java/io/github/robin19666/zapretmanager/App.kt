package io.github.robin19666.zapretmanager

import android.app.Application
import io.github.robin19666.zapretmanager.core.CrashLog
import io.github.robin19666.zapretmanager.core.Cleanup
import io.github.robin19666.zapretmanager.core.LogBus
import io.github.robin19666.zapretmanager.core.UpdateState
import io.github.robin19666.zapretmanager.core.Notifications
import io.github.robin19666.zapretmanager.core.Settings
import io.github.robin19666.zapretmanager.byedpi.DpiEngine
import io.github.robin19666.zapretmanager.tgproxy.TgProxyController

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Settings.init(this)
        Notifications.createChannels(this)
        CrashLog.install(this)
        // После штатного обновления не показываем ложные «аварийные» сообщения движков.
        UpdateState.suppressCrashNoise(this)
        if (UpdateState.isInstalling(this)) {
            Notifications.cancelInstalling(this)
            Cleanup.clearDownloads(this)
        }
        Cleanup.purgeDownloads(this)
        Cleanup.trimExports(this)
        DpiEngine.init(this)
        TgProxyController.init(this)
        if (Settings.current().networkAutoSwitch) {
            io.github.robin19666.zapretmanager.core.NetworkWatcher.start(this)
        }
        LogBus.i("App", "Zapret Manager ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) запущен")
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
