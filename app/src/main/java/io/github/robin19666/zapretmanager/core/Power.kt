package io.github.robin19666.zapretmanager.core

import android.app.usage.UsageStatsManager
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
 *
 * На прошивках вроде HyperOS/MIUI режим «без ограничений» не всегда выставляет
 * флаг `isIgnoringBatteryOptimizations`, поэтому дополнительно смотрим
 * standby-bucket: ACTIVE/WORKING_SET означают, что система не душит приложение.
 */
object Power {
    fun isIgnoring(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val manager = context.getSystemService(PowerManager::class.java)
        if (manager != null && manager.isIgnoringBatteryOptimizations(context.packageName)) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val stats = context.getSystemService(UsageStatsManager::class.java)
            val bucket = runCatching { stats?.appStandbyBucket }.getOrNull()
            if (bucket == UsageStatsManager.STANDBY_BUCKET_ACTIVE ||
                bucket == UsageStatsManager.STANDBY_BUCKET_WORKING_SET
            ) {
                return true
            }
        }
        return false
    }

    fun statusText(context: Context): String = if (isIgnoring(context)) {
        "без ограничений"
    } else {
        "оптимизируется системой"
    }

    /**
     * Системный экран выбора приложений без оптимизации батареи.
     *
     * Прямой ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS требует отдельного
     * разрешения и ограничен политикой Play, поэтому ведём пользователя в
     * общий список — там он сам выбирает приложение.
     */
    fun requestIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Общий список приложений с настройками батареи — запасной вариант. */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Экран «Приложение → ...», где есть «выгружать при неактивности» / пауза. */
    fun appSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun open(context: Context): Boolean {
        return runCatching {
            context.startActivity(requestIntent())
            true
        }.getOrElse {
            runCatching {
                context.startActivity(settingsIntent())
                true
            }.getOrDefault(false)
        }
    }

    fun openAppSettings(context: Context): Boolean = runCatching {
        context.startActivity(appSettingsIntent(context))
        true
    }.getOrDefault(false)
}

