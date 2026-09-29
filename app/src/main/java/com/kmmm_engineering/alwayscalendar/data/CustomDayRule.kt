package com.kmmm_engineering.alwayscalendar.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Recurring / one-shot day rule for custom holidays (赤) or custom weekdays (白).
 *
 * [dayOfWeek]: ISO-8601 Monday=1 … Sunday=7 (same as java.time.DayOfWeek.value).
 * [nth]: 1..5 for 「第N曜日」 kinds (single occurrence only; no ranges).
 * [endYear]/[endMonth]/[endDay]: inclusive end for [CustomDayKind.DATE_RANGE]
 *   (start is [year]/[month]/[day]).
 */
enum class CustomDayKind {
    /** 1日のみ — year/month/day */
    ONCE,
    /** 期間指定 — year/month/day ～ endYear/endMonth/endDay (inclusive) */
    DATE_RANGE,
    /** 毎年月日 — month/day */
    YEARLY_MD,
    /** 毎年M月第N曜日 — month/nth/dayOfWeek */
    YEARLY_NTH_DOW,
    /** 毎年月末 — month */
    YEARLY_MONTH_END,
    /** 毎月D日 — day */
    MONTHLY_D,
    /** 毎月第N曜日 — nth/dayOfWeek */
    MONTHLY_NTH_DOW,
    /** 毎月末 */
    MONTHLY_END,
    /** 毎週曜日 — dayOfWeek */
    WEEKLY_DOW,
}

data class CustomDayRule(
    val id: String = UUID.randomUUID().toString(),
    val kind: CustomDayKind,
    val year: Int = 0,
    val month: Int = 0,
    val day: Int = 0,
    val nth: Int = 0,
    val dayOfWeek: Int = 0,
    val endYear: Int = 0,
    val endMonth: Int = 0,
    val endDay: Int = 0,
) {
    fun matches(date: LocalDate): Boolean = when (kind) {
        CustomDayKind.ONCE ->
            date.year == year && date.monthValue == month && date.dayOfMonth == day
        CustomDayKind.YEARLY_MD ->
            date.monthValue == month && date.dayOfMonth == day
        CustomDayKind.YEARLY_NTH_DOW ->
            date.monthValue == month && isNthWeekday(date, nth, dayOfWeek)
        CustomDayKind.YEARLY_MONTH_END ->
            date.monthValue == month && date.dayOfMonth == date.lengthOfMonth()
        CustomDayKind.MONTHLY_D ->
            day in 1..date.lengthOfMonth() && date.dayOfMonth == day
        CustomDayKind.MONTHLY_NTH_DOW ->
            isNthWeekday(date, nth, dayOfWeek)
        CustomDayKind.MONTHLY_END ->
            date.dayOfMonth == date.lengthOfMonth()
        CustomDayKind.WEEKLY_DOW ->
            date.dayOfWeek.value == dayOfWeek
        CustomDayKind.DATE_RANGE -> {
            val start = LocalDate.of(year, month, day)
            val end = LocalDate.of(endYear, endMonth, endDay)
            !date.isBefore(start) && !date.isAfter(end)
        }
    }

    /** Japanese display label for list UI. */
    fun displayLabel(): String {
        val dowLabel = dayOfWeekLabel(dayOfWeek)
        return when (kind) {
            CustomDayKind.ONCE ->
                "${year}年${month}月${day}日"
            CustomDayKind.YEARLY_MD ->
                "毎年${month}月${day}日"
            CustomDayKind.YEARLY_NTH_DOW ->
                "毎年${month}月第${nth}${dowLabel}曜日"
            CustomDayKind.YEARLY_MONTH_END ->
                "毎年${month}月末"
            CustomDayKind.MONTHLY_D ->
                "毎月${day}日"
            CustomDayKind.MONTHLY_NTH_DOW ->
                "毎月第${nth}${dowLabel}曜日"
            CustomDayKind.MONTHLY_END ->
                "毎月末"
            CustomDayKind.WEEKLY_DOW ->
                "毎週${dowLabel}曜日"
            CustomDayKind.DATE_RANGE ->
                "%04d/%02d/%02d～%04d/%02d/%02d".format(
                    year, month, day, endYear, endMonth, endDay,
                )
        }
    }

    companion object {
        fun dayOfWeekLabel(iso: Int): String = when (iso) {
            1 -> "月"
            2 -> "火"
            3 -> "水"
            4 -> "木"
            5 -> "金"
            6 -> "土"
            7 -> "日"
            else -> "?"
        }

        fun kindLabel(kind: CustomDayKind): String = when (kind) {
            CustomDayKind.ONCE -> "1日のみ"
            CustomDayKind.DATE_RANGE -> "期間指定"
            CustomDayKind.YEARLY_MD -> "毎年 M 月 D 日"
            CustomDayKind.YEARLY_NTH_DOW -> "毎年 M 月第 N W 曜日"
            CustomDayKind.YEARLY_MONTH_END -> "毎年 M 月末"
            CustomDayKind.MONTHLY_D -> "毎月 D 日"
            CustomDayKind.MONTHLY_NTH_DOW -> "毎月第 N W 曜日"
            CustomDayKind.MONTHLY_END -> "毎月末"
            CustomDayKind.WEEKLY_DOW -> "毎週 W 曜日"
        }

        /** True when [date] is the [nth] (1..5) occurrence of [isoDow] in its month. */
        fun isNthWeekday(date: LocalDate, nth: Int, isoDow: Int): Boolean {
            if (nth !in 1..5) return false
            if (date.dayOfWeek.value != isoDow) return false
            var count = 0
            var d = YearMonth.from(date).atDay(1)
            while (d.monthValue == date.monthValue) {
                if (d.dayOfWeek.value == isoDow) {
                    count++
                    if (d == date) return count == nth
                }
                d = d.plusDays(1)
            }
            return false
        }

        /**
         * Normalize a DATE_RANGE so start ≤ end (swap if needed). Other kinds unchanged.
         */
        fun normalized(rule: CustomDayRule): CustomDayRule {
            if (rule.kind != CustomDayKind.DATE_RANGE) return rule
            val start = LocalDate.of(rule.year, rule.month, rule.day)
            val end = LocalDate.of(rule.endYear, rule.endMonth, rule.endDay)
            if (!end.isBefore(start)) return rule
            return rule.copy(
                year = end.year,
                month = end.monthValue,
                day = end.dayOfMonth,
                endYear = start.year,
                endMonth = start.monthValue,
                endDay = start.dayOfMonth,
            )
        }

        fun encodeList(rules: List<CustomDayRule>): String {
            val arr = JSONArray()
            for (r in rules) {
                arr.put(
                    JSONObject()
                        .put("id", r.id)
                        .put("kind", r.kind.name)
                        .put("year", r.year)
                        .put("month", r.month)
                        .put("day", r.day)
                        .put("nth", r.nth)
                        .put("dayOfWeek", r.dayOfWeek)
                        .put("endYear", r.endYear)
                        .put("endMonth", r.endMonth)
                        .put("endDay", r.endDay),
                )
            }
            return arr.toString()
        }

        fun decodeList(raw: String?): List<CustomDayRule> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val kind = runCatching {
                            CustomDayKind.valueOf(o.getString("kind"))
                        }.getOrNull() ?: continue
                        add(
                            CustomDayRule(
                                id = o.optString("id", UUID.randomUUID().toString()),
                                kind = kind,
                                year = o.optInt("year", 0),
                                month = o.optInt("month", 0),
                                day = o.optInt("day", 0),
                                nth = o.optInt("nth", 0),
                                dayOfWeek = o.optInt("dayOfWeek", 0),
                                endYear = o.optInt("endYear", 0),
                                endMonth = o.optInt("endMonth", 0),
                                endDay = o.optInt("endDay", 0),
                            ),
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
