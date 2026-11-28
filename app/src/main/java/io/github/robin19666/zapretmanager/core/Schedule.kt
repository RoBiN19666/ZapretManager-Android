package io.github.robin19666.zapretmanager.core

import java.util.Calendar

/**
 * Расписание работы обхода DPI: окно времени в минутах от полуночи.
 *
 * Логика вынесена отдельно от UI и покрыта тестами: окно может быть обычным
 * (09:00–18:00) и «через полночь» (23:00–07:00), когда конец меньше начала.
 */
object Schedule {

    const val DAY_MINUTES = 24 * 60

    /** Текущие минуты от полуночи в локальной зоне. */
    fun nowMinutes(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /** Применяется ли обход в это время суток. */
    fun isActive(settings: AppSettings, minutes: Int): Boolean {
        if (!settings.scheduleEnabled) return true
        val from = settings.scheduleFrom.coerceIn(0, DAY_MINUTES - 1)
        val to = settings.scheduleTo.coerceIn(0, DAY_MINUTES - 1)
        val now = minutes.coerceIn(0, DAY_MINUTES - 1)
        return when {
            from == to -> true
            from < to -> now in from until to
            // Через полночь: 23:00–07:00.
            else -> now >= from || now < to
        }
    }

    fun format(minutes: Int): String {
        val clamped = minutes.coerceIn(0, DAY_MINUTES - 1)
        return "%02d:%02d".format(clamped / 60, clamped % 60)
    }

    fun of(hour: Int, minute: Int): Int = (hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59))
}
