package io.github.robin19666.zapretmanager

import android.app.Application
import io.github.robin19666.zapretmanager.core.CrashLog
import io.github.robin19666.zapretmanager.core.LogBus
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
        DpiEngine.init(this)
        TgProxyController.init(this)
        LogBus.i("App", "Zapret Manager ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) запущен")
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
