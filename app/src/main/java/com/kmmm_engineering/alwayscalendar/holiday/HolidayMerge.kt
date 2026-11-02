package com.kmmm_engineering.alwayscalendar.holiday

/**
 * Holiday judgement (shared by the screen and 平日通知).
 * Rokuyo ON: Koyomi is main, holidays-jp backs up years Koyomi lacks.
 * Rokuyo OFF: holidays-jp is main, Koyomi only fills years holidays-jp lacks (fallback).
 */
fun mergeHolidayDates(
    jp: Map<String, String>,
    koyomi: Map<String, KoyomiDay>,
    rokuyoOn: Boolean,
): Set<String> {
    val koyomiDates = koyomi.filter { it.value.holiday.isNotEmpty() }.keys
    val koyomiYears = koyomi.keys.map { it.take(4) }.toSet()
    val jpYears = jp.keys.map { it.take(4) }.toSet()
    val out = HashSet<String>()
    if (rokuyoOn) {
        out.addAll(koyomiDates)
        jp.keys.filterTo(out) { it.take(4) !in koyomiYears }
    } else {
        out.addAll(jp.keys)
        koyomiDates.filterTo(out) { it.take(4) !in jpYears }
    }
    return out
}
