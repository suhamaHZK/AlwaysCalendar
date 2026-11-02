package com.kmmm_engineering.alwayscalendar.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** How a date is painted on the calendar: white (weekday), blue (Saturday) or red (holiday). */
enum class DayKind { WEEKDAY, SATURDAY, HOLIDAY }

/**
 * Day-color options from settings (priority applied inside [resolveDayKind]).
 */
data class DayColorOptions(
    val holidaysAsWhite: Boolean = false,
    val saturdayAsWhite: Boolean = false,
    val sundayAsWhite: Boolean = false,
    val customHolidays: List<CustomDayRule> = emptyList(),
    val customWeekdays: List<CustomDayRule> = emptyList(),
) {
    companion object {
        fun from(s: UserSettings) = DayColorOptions(
            holidaysAsWhite = s.holidaysAsWhite,
            saturdayAsWhite = s.saturdayAsWhite,
            sundayAsWhite = s.sundayAsWhite,
            customHolidays = s.customHolidays,
            customWeekdays = s.customWeekdays,
        )
    }
}

/**
 * Single source of truth for the screen color AND the 平日通知 weekday judgement.
 * Priority: custom weekday (白) ＞ custom holiday (赤) ＞ white-switches ＞ default
 * (祝日・日曜赤、土曜青、他白). When holidaysAsWhite, API/cache holidays are ignored.
 */
fun resolveDayKind(
    date: LocalDate,
    isApiHoliday: Boolean,
    options: DayColorOptions,
): DayKind {
    if (options.customWeekdays.any { it.matches(date) }) return DayKind.WEEKDAY
    if (options.customHolidays.any { it.matches(date) }) return DayKind.HOLIDAY
    val treatHoliday = !options.holidaysAsWhite && isApiHoliday
    val dow = date.dayOfWeek
    return when {
        treatHoliday -> DayKind.HOLIDAY
        dow == DayOfWeek.SUNDAY -> if (options.sundayAsWhite) DayKind.WEEKDAY else DayKind.HOLIDAY
        dow == DayOfWeek.SATURDAY -> if (options.saturdayAsWhite) DayKind.WEEKDAY else DayKind.SATURDAY
        else -> DayKind.WEEKDAY
    }
}

/** 白で表示される日 = 平日。 */
fun isWhiteWeekday(date: LocalDate, isApiHoliday: Boolean, options: DayColorOptions): Boolean =
    resolveDayKind(date, isApiHoliday, options) == DayKind.WEEKDAY

/** First white day of [ym], or null if the month has none. */
fun firstWeekdayOf(ym: YearMonth, isApiHoliday: (LocalDate) -> Boolean, options: DayColorOptions): LocalDate? =
    (1..ym.lengthOfMonth()).asSequence().map { ym.atDay(it) }
        .firstOrNull { isWhiteWeekday(it, isApiHoliday(it), options) }

/** Last white day of [ym], or null if the month has none. */
fun lastWeekdayOf(ym: YearMonth, isApiHoliday: (LocalDate) -> Boolean, options: DayColorOptions): LocalDate? =
    (ym.lengthOfMonth() downTo 1).asSequence().map { ym.atDay(it) }
        .firstOrNull { isWhiteWeekday(it, isApiHoliday(it), options) }
