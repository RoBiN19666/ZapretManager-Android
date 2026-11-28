package io.github.robin19666.zapretmanager.core

import java.util.Calendar
import java.util.TimeZone

/**
 * Новогоднее оформление включается само по календарю — с 1 декабря по 1 марта
 * (1 марта уже обычный день) и выключить его вручную нельзя.
 *
 * Отдельный объект, а не проверка в UI: границы периодов покрыты тестами,
 * чтобы праздник не включался в ноябре и не исчезал 31 декабря.
 */
object HolidayTheme {
    /** Месяцы, когда действует оформление: декабрь, январь, февраль. */
    private val WINTER_MONTHS = setOf(Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY)

    fun isNewYear(
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
    ): Boolean {
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = now }
        return calendar.get(Calendar.MONTH) in WINTER_MONTHS
    }
}
