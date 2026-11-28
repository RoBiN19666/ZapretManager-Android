package io.github.robin19666.zapretmanager.core

import android.content.Context

/**
 * Память о том, должны ли движки работать.
 *
 * Сервисы живут в отдельных процессах, и система может убить их (HyperOS, Doze,
 * нехватка памяти). С флагом «должен работать» сервис при перезапуске системой
 * поднимает движок заново, а не остаётся выключенным. Одновременно ограничиваем
 * число автоперезапусков, чтобы не уйти в цикл, если сеть/движок сломаны.
 */
// commit() намеренно: флаг читает другой процесс сразу после записи.
@Suppress("ApplySharedPref")
object EngineMemory {
    const val KIND_DPI = "dpi"
    const val KIND_TG = "tg"

    private const val PREFS = "zm_engine_state"
    private const val KEY_DPI_WANTED = "dpi_wanted"
    private const val KEY_TG_WANTED = "tg_wanted"
    private const val KEY_RESTARTS = "restarts_"
    private const val KEY_WINDOW = "window_"
private const val KEY_STARTED_AT = "started_at_"
private const val KEY_SCHEDULE_PAUSED = "schedule_paused"

    private const val MAX_RESTARTS = 3
    private const val WINDOW_MS = 10 * 60 * 1000L

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setDpiWanted(context: Context, wanted: Boolean) {
        prefs(context).edit().putBoolean(KEY_DPI_WANTED, wanted).commit()
    }

    fun dpiWanted(context: Context): Boolean = prefs(context).getBoolean(KEY_DPI_WANTED, false)

/**
 * Время фактического запуска движка. Живёт в настройках, потому что состояние в
 * UI-процессе пропадает при его перезапуске: без этого таймер работы начинал
 * отсчёт заново при каждом открытии приложения.
 */
fun setStartedAt(context: Context, kind: String, ts: Long) {
    prefs(context).edit().putLong(KEY_STARTED_AT + kind, ts).apply()
}

fun startedAt(context: Context, kind: String): Long =
    prefs(context).getLong(KEY_STARTED_AT + kind, 0L)

fun clearStartedAt(context: Context, kind: String) {
    prefs(context).edit().remove(KEY_STARTED_AT + kind).apply()
}

    fun setTgWanted(context: Context, wanted: Boolean) {
        prefs(context).edit().putBoolean(KEY_TG_WANTED, wanted).commit()
    }

/** true, если движок выключило расписание (а не пользователь). */
fun setSchedulePaused(context: Context, paused: Boolean) {
    prefs(context).edit().putBoolean(KEY_SCHEDULE_PAUSED, paused).apply()
}

fun schedulePaused(context: Context): Boolean = prefs(context).getBoolean(KEY_SCHEDULE_PAUSED, false)

    fun tgWanted(context: Context): Boolean = prefs(context).getBoolean(KEY_TG_WANTED, false)

    /**
     * Разрешает ещё один автоперезапуск: не больше [MAX_RESTARTS] за [WINDOW_MS].
     * Возвращает false, если лимит исчерпан — тогда движок лучше оставить
     * выключенным и показать ошибку, чем перезапускать его бесконечно.
     */
    fun allowRestart(context: Context, kind: String): Boolean {
        val store = prefs(context)
        val now = System.currentTimeMillis()
        val windowStart = store.getLong(KEY_WINDOW + kind, 0L)
        var count = store.getInt(KEY_RESTARTS + kind, 0)
        var nextStart = windowStart
        if (windowStart == 0L || now - windowStart > WINDOW_MS) {
            count = 0
            nextStart = now
        }
        if (count >= MAX_RESTARTS) {
            LogBus.w(
                if (kind == KIND_DPI) "Ускорение" else "Telegram",
                "Автоперезапусков слишком много за 10 минут — останавливаю восстановление",
            )
            return false
        }
        store.edit()
            .putInt(KEY_RESTARTS + kind, count + 1)
            .putLong(KEY_WINDOW + kind, nextStart)
            .commit()
        return true
    }

    /** Пользователь сам остановил движок — счётчик автоперезапусков больше не нужен. */
    fun resetRestarts(context: Context, kind: String) {
        prefs(context).edit()
            .remove(KEY_RESTARTS + kind)
            .remove(KEY_WINDOW + kind)
            .commit()
    }
}
