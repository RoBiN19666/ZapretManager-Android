package io.github.robin19666.zapretmanager

import io.github.robin19666.zapretmanager.core.AppSettings
import io.github.robin19666.zapretmanager.core.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Расписание работы обхода: обычное окно, окно через полночь, выключенное. */
class ScheduleTest {

    private fun settings(enabled: Boolean, from: Int, to: Int) = AppSettings(
        scheduleEnabled = enabled,
        scheduleFrom = from,
        scheduleTo = to,
    )

    @Test
    fun disabledMeansAlwaysActive() {
        val s = settings(enabled = false, from = 9 * 60, to = 18 * 60)
        assertTrue(Schedule.isActive(s, 3 * 60))
        assertTrue(Schedule.isActive(s, 23 * 60))
        assertTrue(Schedule.isActive(s, 12 * 60))
    }

    @Test
    fun regularWindow() {
        val s = settings(enabled = true, from = 9 * 60, to = 18 * 60)
        assertFalse(Schedule.isActive(s, 8 * 60 + 59))
        assertTrue(Schedule.isActive(s, 9 * 60))
        assertTrue(Schedule.isActive(s, 12 * 60))
        assertTrue(Schedule.isActive(s, 17 * 60 + 59))
        assertFalse(Schedule.isActive(s, 18 * 60))
        assertFalse(Schedule.isActive(s, 23 * 60))
    }

    @Test
    fun windowAcrossMidnight() {
        val s = settings(enabled = true, from = 23 * 60, to = 7 * 60)
        assertTrue(Schedule.isActive(s, 23 * 60))
        assertTrue(Schedule.isActive(s, 0))
        assertTrue(Schedule.isActive(s, 6 * 60 + 59))
        assertFalse(Schedule.isActive(s, 7 * 60))
        assertFalse(Schedule.isActive(s, 12 * 60))
    }

    @Test
    fun emptyWindowMeansAlwaysActive() {
        val s = settings(enabled = true, from = 10 * 60, to = 10 * 60)
        assertTrue(Schedule.isActive(s, 0))
        assertTrue(Schedule.isActive(s, 10 * 60))
        assertTrue(Schedule.isActive(s, 23 * 60 + 59))
    }

    @Test
    fun formattingAndParsing() {
        assertEquals("08:05", Schedule.format(8 * 60 + 5))
        assertEquals("23:59", Schedule.format(23 * 60 + 59))
        assertEquals(8 * 60 + 5, Schedule.of(8, 5))
        assertEquals(23 * 60 + 59, Schedule.of(23, 59))
        // Некорректные значения зажимаются в диапазон суток.
        assertEquals(23 * 60 + 59, Schedule.of(30, 90))
    }
}
