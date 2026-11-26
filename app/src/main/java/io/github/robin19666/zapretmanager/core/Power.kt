package io.github.robin19666.zapretmanager.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Настройки энергосбережения.
 *
 * Android усыпляет фоновые процессы (Doze, «адаптивная батарея»), из-за чего
 * VPN/прокси-сервис может быть остановлен системой. Приложению нужен режим
 * «без ограничений», иначе туннель рвётся при выключенном экране.
 */
object Power {
    fun isIgnoring(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val manager = context.getSystemService(PowerManager::class.java) ?: return true
        return manager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Системный диалог «разрешить работу без ограничений» для этого приложения. */
    fun requestIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Общий список приложений с настройками батареи — запасной вариант. */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun open(context: Context): Boolean {
        return runCatching {
            context.startActivity(requestIntent(context))
            true
        }.getOrElse {
            runCatching {
                context.startActivity(settingsIntent())
                true
            }.getOrDefault(false)
        }
    }
}
