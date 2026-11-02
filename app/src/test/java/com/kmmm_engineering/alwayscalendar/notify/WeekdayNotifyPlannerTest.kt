package com.kmmm_engineering.alwayscalendar.notify

import com.kmmm_engineering.alwayscalendar.data.CustomDayKind
import com.kmmm_engineering.alwayscalendar.data.CustomDayRule
import com.kmmm_engineering.alwayscalendar.data.DayColorOptions
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

class WeekdayNotifyPlannerTest {
    private val noHoliday: (LocalDate) -> Boolean = { false }
    private val nine = LocalTime.of(9, 0)
    private val opts = DayColorOptions()

    @Test fun nov2026_first_last() {
        val ym = YearMonth.of(2026, 11)
        // 11/3 文化の日 is a holiday, but 11/2 (Mon) comes first anyway.
        assertEquals(LocalDate.of(2026, 11, 2), WeekdayNotifyPlanner.targetOf(WeekdayNotifyKind.FIRST, ym, noHoliday, opts))
        assertEquals(LocalDate.of(2026, 11, 30), WeekdayNotifyPlanner.targetOf(WeekdayNotifyKind.LAST, ym, noHoliday, opts))
    }

    @Test fun jan_skips_holiday_and_saturday_white() {
        val ym = YearMonth.of(2027, 1) // 1/1 Fri holiday, 1/2 Sat
        val hol: (LocalDate) -> Boolean = { it == LocalDate.of(2027, 1, 1) }
        assertEquals(LocalDate.of(2027, 1, 4), WeekdayNotifyPlanner.targetOf(WeekdayNotifyKind.FIRST, ym, hol, opts))
        val satWhite = DayColorOptions(saturdayAsWhite = true)
        assertEquals(LocalDate.of(2027, 1, 2), WeekdayNotifyPlanner.targetOf(WeekdayNotifyKind.FIRST, ym, hol, satWhite))
        val holWhite = DayColorOptions(holidaysAsWhite = true)
        assertEquals(LocalDate.of(2027, 1, 1), WeekdayNotifyPlanner.targetOf(WeekdayNotifyKind.FIRST, ym, hol, holWhite))
    }

    @Test fun custom_priority() {
        val ym = YearMonth.of(2026, 11)
        val d2 = CustomDayRule(kind = CustomDayKind.ONCE, year = 2026, month = 11, day = 2)
        val d1 = CustomDayRule(kind = CustomDayKind.ONCE, year = 2026, month = 11, day = 1)
        // custom holiday on 11/2 → 11/4 (11/3 holiday)
        val hol: (LocalDate) -> Boolean = { it == LocalDate.of(2026, 11, 3) }
        assertEquals(LocalDate.of(2026, 11, 4), WeekdayNotifyPlanner.targetOf(WeekdayNotifyKind.FIRST, ym, hol, DayColorOptions(customHolidays = listOf(d2))))
        // custom weekday beats custom holiday; Sunday 11/1 made white
        assertEquals(LocalDate.of(2026, 11, 1), WeekdayNotifyPlanner.targetOf(WeekdayNotifyKind.FIRST, ym, hol,
            DayColorOptions(customHolidays = listOf(d1), customWeekdays = listOf(d1))))
    }

    @Test fun plan_schedule_catchup_and_skip() {
        val p1 = WeekdayNotifyPlanner.plan(WeekdayNotifyKind.FIRST, LocalDateTime.of(2026, 11, 2, 8, 0), nine, null, noHoliday, opts)
        assertEquals(WeekdayNotifyPlanner.Plan.ScheduleAt(LocalDate.of(2026, 11, 2), LocalDateTime.of(2026, 11, 2, 9, 0)), p1)
        val p2 = WeekdayNotifyPlanner.plan(WeekdayNotifyKind.FIRST, LocalDateTime.of(2026, 11, 2, 15, 0), nine, null, noHoliday, opts)
        assertEquals(WeekdayNotifyPlanner.Plan.SendNow(LocalDate.of(2026, 11, 2)), p2)
        val p3 = WeekdayNotifyPlanner.plan(WeekdayNotifyKind.FIRST, LocalDateTime.of(2026, 11, 2, 15, 0), nine, "2026-11", noHoliday, opts)
        assertEquals(WeekdayNotifyPlanner.Plan.ScheduleAt(LocalDate.of(2026, 12, 1), LocalDateTime.of(2026, 12, 1, 9, 0)), p3)
        // next day, not sent → never late-send; go to December
        val p4 = WeekdayNotifyPlanner.plan(WeekdayNotifyKind.FIRST, LocalDateTime.of(2026, 11, 3, 0, 5), nine, null, noHoliday, opts)
        assertEquals(WeekdayNotifyPlanner.Plan.ScheduleAt(LocalDate.of(2026, 12, 1), LocalDateTime.of(2026, 12, 1, 9, 0)), p4)
    }

    @Test fun message_format() {
        assertEquals("今日は2026年11月の最初の平日です（11/2）", WeekdayNotifyPlanner.message(WeekdayNotifyKind.FIRST, LocalDate.of(2026, 11, 2)))
        assertEquals("今日は2026年11月の最後の平日です（11/30）", WeekdayNotifyPlanner.message(WeekdayNotifyKind.LAST, LocalDate.of(2026, 11, 30)))
    }
}
