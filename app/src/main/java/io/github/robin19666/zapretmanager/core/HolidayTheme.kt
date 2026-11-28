package io.github.robin19666.zapretmanager.core

import java.util.Calendar
import java.util.TimeZone

/**
 * Новогоднее оформление включается само по календарю — **с 15 декабря по 15 января
 * включительно**; 14 декабря ещё обычный день, 16 января — уже тоже.
 * Выключить его вручную нельзя.
 *
 * Отдельный объект, а не проверка в UI: границы периода покрыты тестами,
 * чтобы праздник не включался раньше срока и не исчезал 31 декабря.
 */
object HolidayTheme {
    private const val START_DAY = 15
    private const val END_DAY = 15

    fun isNewYear(
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
    ): Boolean {
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = now }
        return when (calendar.get(Calendar.MONTH)) {
            Calendar.DECEMBER -> calendar.get(Calendar.DAY_OF_MONTH) >= START_DAY
            Calendar.JANUARY -> calendar.get(Calendar.DAY_OF_MONTH) <= END_DAY
            else -> false
        }
    }
}
