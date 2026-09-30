package com.kmmm_engineering.alwayscalendar.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/**
 * Export / import package for custom holidays & weekdays.
 *
 * Wrap format:
 * ```
 * {
 *   "format": "alwayscalendar-custom-days",
 *   "formatVersion": 1,
 *   "holidays": [ ... CustomDayRule fields ... ],
 *   "weekdays": [ ... ]
 * }
 * ```
 * Also accepts a bare JSON array of rules (caller chooses holiday vs weekday).
 */
object CustomDayTransfer {
    const val FORMAT = "alwayscalendar-custom-days"
    const val FORMAT_VERSION = 1

    enum class ApplyMode { APPEND, REPLACE }

    enum class TargetCategory { HOLIDAYS, WEEKDAYS }

    data class RuleParseBatch(
        val rules: List<CustomDayRule>,
        val skipped: Int,
    )

    sealed class ParseResult {
        data class Package(
            val holidays: RuleParseBatch?,
            val weekdays: RuleParseBatch?,
        ) : ParseResult()

        data class ArrayOnly(val batch: RuleParseBatch) : ParseResult()

        data class Failure(val message: String) : ParseResult()
    }

    data class ApplyReport(
        val holidaysAdded: Int = 0,
        val holidaysReplaced: Int = 0,
        val weekdaysAdded: Int = 0,
        val weekdaysReplaced: Int = 0,
        val skipped: Int = 0,
    ) {
        fun toastMessage(): String {
            val parts = mutableListOf<String>()
            if (holidaysReplaced > 0) parts += "休日を${holidaysReplaced}件に置換"
            if (holidaysAdded > 0) parts += "休日${holidaysAdded}件追加"
            if (weekdaysReplaced > 0) parts += "平日を${weekdaysReplaced}件に置換"
            if (weekdaysAdded > 0) parts += "平日${weekdaysAdded}件追加"
            if (parts.isEmpty() && skipped == 0) parts += "適用する規則がありません"
            var msg = parts.joinToString("、")
            if (skipped > 0) {
                msg = if (msg.isEmpty()) "スキップ${skipped}件" else "$msg（スキップ${skipped}件）"
            }
            return msg
        }
    }

    fun encodePackage(
        holidays: List<CustomDayRule>,
        weekdays: List<CustomDayRule>,
    ): String {
        val obj = JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("holidays", rulesToJsonArray(holidays))
            .put("weekdays", rulesToJsonArray(weekdays))
        return obj.toString(2)
    }

    fun parse(raw: String): ParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ParseResult.Failure("テキストが空です")
        return runCatching {
            when {
                trimmed.startsWith("{") -> parseObject(JSONObject(trimmed))
                trimmed.startsWith("[") -> {
                    val batch = parseRuleArray(JSONArray(trimmed))
                    ParseResult.ArrayOnly(batch)
                }
                else -> ParseResult.Failure("JSONオブジェクトまたは配列ではありません")
            }
        }.getOrElse {
            ParseResult.Failure("JSONの解析に失敗しました")
        }
    }

    private fun parseObject(obj: JSONObject): ParseResult {
        val hasHolidays = obj.has("holidays")
        val hasWeekdays = obj.has("weekdays")
        val format = obj.optString("format", "")
        val formatOk = format.isEmpty() || format == FORMAT

        if (!hasHolidays && !hasWeekdays) {
            return ParseResult.Failure("holidays / weekdays が見つかりません")
        }
        if (!formatOk) {
            return ParseResult.Failure("未対応の format: $format")
        }

        val holidays = if (hasHolidays) {
            val v = obj.opt("holidays")
            if (v !is JSONArray) {
                return ParseResult.Failure("holidays は配列である必要があります")
            }
            parseRuleArray(v)
        } else {
            null
        }
        val weekdays = if (hasWeekdays) {
            val v = obj.opt("weekdays")
            if (v !is JSONArray) {
                return ParseResult.Failure("weekdays は配列である必要があります")
            }
            parseRuleArray(v)
        } else {
            null
        }
        return ParseResult.Package(holidays = holidays, weekdays = weekdays)
    }

    private fun parseRuleArray(arr: JSONArray): RuleParseBatch {
        val rules = mutableListOf<CustomDayRule>()
        var skipped = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) {
                skipped++
                continue
            }
            val rule = parseRule(o)
            if (rule == null) skipped++ else rules.add(rule)
        }
        return RuleParseBatch(rules = rules, skipped = skipped)
    }

    /**
     * Parse one rule object. Unknown fields ignored. Missing [id] → new UUID.
     * Invalid kind / out-of-range fields → null (caller counts as skipped).
     */
    fun parseRule(o: JSONObject): CustomDayRule? {
        val kindName = o.optString("kind", "")
        if (kindName.isBlank()) return null
        val kind = runCatching { CustomDayKind.valueOf(kindName) }.getOrNull() ?: return null
        val idRaw = o.optString("id", "")
        val id = if (idRaw.isBlank()) UUID.randomUUID().toString() else idRaw
        val rule = CustomDayRule(
            id = id,
            kind = kind,
            year = o.optInt("year", 0),
            month = o.optInt("month", 0),
            day = o.optInt("day", 0),
            nth = o.optInt("nth", 0),
            dayOfWeek = o.optInt("dayOfWeek", 0),
            endYear = o.optInt("endYear", 0),
            endMonth = o.optInt("endMonth", 0),
            endDay = o.optInt("endDay", 0),
        )
        if (!isStructurallyValid(rule)) return null
        return CustomDayRule.normalized(rule)
    }

    fun isStructurallyValid(rule: CustomDayRule): Boolean = when (rule.kind) {
        CustomDayKind.ONCE -> isValidYmd(rule.year, rule.month, rule.day)
        CustomDayKind.DATE_RANGE ->
            isValidYmd(rule.year, rule.month, rule.day) &&
                isValidYmd(rule.endYear, rule.endMonth, rule.endDay)
        CustomDayKind.YEARLY_MD -> rule.month in 1..12 && rule.day in 1..31 &&
            runCatching { LocalDate.of(2024, rule.month, rule.day) }.isSuccess
        CustomDayKind.YEARLY_NTH_DOW ->
            rule.month in 1..12 && rule.nth in 1..5 && rule.dayOfWeek in 1..7
        CustomDayKind.YEARLY_MONTH_END -> rule.month in 1..12
        CustomDayKind.MONTHLY_D -> rule.day in 1..31
        CustomDayKind.MONTHLY_NTH_DOW -> rule.nth in 1..5 && rule.dayOfWeek in 1..7
        CustomDayKind.MONTHLY_END -> true
        CustomDayKind.WEEKLY_DOW -> rule.dayOfWeek in 1..7
    }

    private fun isValidYmd(year: Int, month: Int, day: Int): Boolean =
        runCatching { LocalDate.of(year, month, day) }.isSuccess

    private fun rulesToJsonArray(rules: List<CustomDayRule>): JSONArray {
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
        return arr
    }
}
