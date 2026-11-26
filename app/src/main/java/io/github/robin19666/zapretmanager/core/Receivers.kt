package io.github.robin19666.zapretmanager.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.robin19666.zapretmanager.byedpi.ByeDpiVpnService
import io.github.robin19666.zapretmanager.byedpi.DpiEngine
import io.github.robin19666.zapretmanager.tgproxy.TgProxyService

/** Автозапуск после перезагрузки — только если пользователь включил это в настройках. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        Settings.init(context)
        val settings = Settings.current()
        LogBus.i("Автозапуск", "Событие $action: обход=${settings.autostartDpi}, прокси=${settings.autostartTg}")

        if (settings.autostartDpi) {
            runCatching {
                val pending = DpiEngine.prepareIntent(context)
                if (pending == null) {
                    ByeDpiVpnService.start(context)
                } else {
                    LogBus.w("Автозапуск", "Нет VPN-разрешения — обход не запущен")
                }
            }.onFailure { Log.e("BootReceiver", "dpi: ${it.message}") }
        }
        if (settings.autostartTg) {
            runCatching { TgProxyService.start(context) }
                .onFailure { Log.e("BootReceiver", "tg: ${it.message}") }
        }
    }
}

/** Кнопки «Стоп» в уведомлениях и快捷-действия. */
class ServiceActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_START_DPI -> ByeDpiVpnService.start(context)
            ACTION_STOP_DPI -> ByeDpiVpnService.stop(context)
            ACTION_START_TG -> TgProxyService.start(context)
            ACTION_STOP_TG -> TgProxyService.stop(context)
        }
    }

    companion object {
        const val ACTION_START_DPI = "io.github.robin19666.zapretmanager.action.START_DPI"
        const val ACTION_STOP_DPI = "io.github.robin19666.zapretmanager.action.STOP_DPI"
        const val ACTION_START_TG = "io.github.robin19666.zapretmanager.action.START_TG"
        const val ACTION_STOP_TG = "io.github.robin19666.zapretmanager.action.STOP_TG"
    }
}

/** Результат установки APK из системного установщика. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val status = intent?.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -1) ?: -1
        val message = intent?.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                }
            }
            android.content.pm.PackageInstaller.STATUS_SUCCESS ->
                LogBus.i("Обновление", "APK установлен: ${message.orEmpty()}")
            else ->
                LogBus.e("Обновление", "Установка не удалась ($status): ${message.orEmpty()}")
        }
    }
}
