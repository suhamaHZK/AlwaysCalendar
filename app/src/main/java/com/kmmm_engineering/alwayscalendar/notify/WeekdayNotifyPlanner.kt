package com.kmmm_engineering.alwayscalendar.notify

import com.kmmm_engineering.alwayscalendar.data.DayColorOptions
import com.kmmm_engineering.alwayscalendar.data.firstWeekdayOf
import com.kmmm_engineering.alwayscalendar.data.lastWeekdayOf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/** その月最初の平日 / 最後の平日. */
enum class WeekdayNotifyKind { FIRST, LAST }

/** Pure planning logic (no Android) for the 平日通知. */
object WeekdayNotifyPlanner {

    sealed interface Plan {
        /** Today is the target day, the time has passed and it is not sent yet → send now. */
        data class SendNow(val target: LocalDate) : Plan
        /** Next alarm at [at] for [target]. */
        data class ScheduleAt(val target: LocalDate, val at: LocalDateTime) : Plan
        /** Nothing within the search horizon. */
        data object None : Plan
    }

    fun targetOf(
        kind: WeekdayNotifyKind,
        ym: YearMonth,
        isApiHoliday: (LocalDate) -> Boolean,
        options: DayColorOptions,
    ): LocalDate? = when (kind) {
        WeekdayNotifyKind.FIRST -> firstWeekdayOf(ym, isApiHoliday, options)
        WeekdayNotifyKind.LAST -> lastWeekdayOf(ym, isApiHoliday, options)
    }

    /**
     * @param sentMonth "yyyy-MM" already sent for this kind (or null).
     * Same-day catch-up: if today is the target and [time] has passed, [Plan.SendNow].
     * A target day already in the past is never sent (翌日以降は送らない).
     */
    fun plan(
        kind: WeekdayNotifyKind,
        now: LocalDateTime,
        time: LocalTime,
        sentMonth: String?,
        isApiHoliday: (LocalDate) -> Boolean,
        options: DayColorOptions,
    ): Plan {
        val today = now.toLocalDate()
        var ym = YearMonth.from(today)
        repeat(HORIZON_MONTHS) {
            val key = monthKey(ym)
            if (key != sentMonth) {
                val target = targetOf(kind, ym, isApiHoliday, options)
                if (target != null && !target.isBefore(today)) {
                    val at = target.atTime(time)
                    if (at.isAfter(now)) return Plan.ScheduleAt(target, at)
                    if (target == today) return Plan.SendNow(target)
                }
            }
            ym = ym.plusMonths(1)
        }
        return Plan.None
    }

    fun monthKey(ym: YearMonth): String = "%04d-%02d".format(ym.year, ym.monthValue)

    /** 「今日は2026年11月の最初の平日です（11/2）」 */
    fun message(kind: WeekdayNotifyKind, target: LocalDate): String {
        val which = if (kind == WeekdayNotifyKind.FIRST) "最初" else "最後"
        return "今日は${target.year}年${target.monthValue}月の${which}の平日です" +
            "（${target.monthValue}/${target.dayOfMonth}）"
    }

    private const val HORIZON_MONTHS = 14
}
