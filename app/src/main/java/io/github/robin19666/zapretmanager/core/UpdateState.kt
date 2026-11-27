package io.github.robin19666.zapretmanager.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.io.File

/**
 * Состояние штатного обновления приложения.
 *
 * Перед запуском системного установщика записываем, какие движки работали:
 * после обновления приложение не должно ругаться «аварийно завершился» и может
 * поднять то, что было включено до обновления.
 */
object UpdateState {
    private const val FILE_NAME = "update.pending"
    private const val VALID_MS = 30 * 60 * 1000L
    private const val TG_MARKER = "tg_session.marker"
    private const val ACTION_RELAUNCH = "io.github.robin19666.zapretmanager.RELAUNCH"
    private const val RELAUNCH_REQUEST = 1001

    data class Pending(
        val dpiWasRunning: Boolean,
        val tgWasRunning: Boolean,
        val at: Long,
    )

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun markInstalling(context: Context, dpiWasRunning: Boolean, tgWasRunning: Boolean) {
        runCatching {
            file(context).writeText(
                JSONObject()
                    .put("dpi", dpiWasRunning)
                    .put("tg", tgWasRunning)
                    .put("at", System.currentTimeMillis())
                    .toString(),
            )
            LogBus.i(
                "Обновление",
                "Отмечено обновление: DPI=${dpiWasRunning}, Telegram=$tgWasRunning",
            )
        }
    }

    fun pending(context: Context): Pending? {
        val target = file(context)
        if (!target.exists()) return null
        return runCatching {
            val json = JSONObject(target.readText())
            val at = json.optLong("at", 0L)
            if (System.currentTimeMillis() - at > VALID_MS) {
                target.delete()
                return null
            }
            Pending(
                dpiWasRunning = json.optBoolean("dpi", false),
                tgWasRunning = json.optBoolean("tg", false),
                at = at,
            )
        }.getOrNull()
    }

    fun isInstalling(context: Context): Boolean = pending(context) != null

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /**
     * Ставит одноразовый будильник: через 25 секунд попробовать открыть приложение.
     * Сработает, если система разрешит запуск активности; иначе пользователь увидит уведомление.
     */
    fun scheduleRelaunch(context: Context, delayMs: Long = 25_000L) {
        runCatching {
            val alarm = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context, RestartReceiver::class.java)
                .setAction(ACTION_RELAUNCH)
            val pending = PendingIntent.getBroadcast(
                context,
                RELAUNCH_REQUEST,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val at = System.currentTimeMillis() + delayMs
            // Без SCHEDULE_EXACT_ALARM: точный будильник на Android 12+
            // требует отдельного разрешения, а нам достаточно «примерно».
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            LogBus.d("Обновление", "Автозапуск после обновления запланирован")
        }
    }

    fun cancelRelaunch(context: Context) {
        runCatching {
            val alarm = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context, RestartReceiver::class.java).setAction(ACTION_RELAUNCH)
            val pending = PendingIntent.getBroadcast(
                context,
                RELAUNCH_REQUEST,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarm.cancel(pending)
        }
    }

    /**
     * Вызывается при старте после обновления: убирает ложные следы «аварийного» завершения.
     * Срабатывает один раз — повторные запуски не трогают журнал сбоев.
     */
    fun suppressCrashNoise(context: Context) {
        val pending = pending(context) ?: return
        val target = file(context)
        val json = runCatching { JSONObject(target.readText()) }.getOrNull() ?: return
        if (json.optBoolean("suppressed", false)) return
        LogBus.i("Обновление", "Старт после обновления: предупреждения о сбоях движков подавлены")
        CrashLog.clear(context)
        TgTrace.clear(context)
        runCatching { File(context.filesDir, TG_MARKER).delete() }
        json.put("suppressed", true)
        runCatching { target.writeText(json.toString()) }
        LogBus.d("Обновление", "Было запущено: DPI=${pending.dpiWasRunning}, Telegram=${pending.tgWasRunning}")
    }
}
