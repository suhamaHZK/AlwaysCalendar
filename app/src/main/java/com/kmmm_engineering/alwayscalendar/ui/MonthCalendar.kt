package com.kmmm_engineering.alwayscalendar.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmmm_engineering.alwayscalendar.data.WeekStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val ColorSunday = Color(0xFFFF5555)
private val ColorSaturday = Color(0xFF5599FF)
private val ColorWeekday = Color.White
private val ColorTodayBorder = Color.White

private val WeekdayLabelsSunStart = listOf("日", "月", "火", "水", "木", "金", "土")
private val WeekdayLabelsMonStart = listOf("月", "火", "水", "木", "金", "土", "日")

/**
 * Single-month calendar block: header (year / month# / English name), weekday row, date grid.
 * Only days of [yearMonth] are shown; out-of-month cells are blank.
 */
@Composable
fun MonthCalendar(
    yearMonth: YearMonth,
    weekStart: WeekStart,
    today: LocalDate,
    isHoliday: (LocalDate) -> Boolean,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val yearSp = maxOf(11f * scale, 8f).sp
    val monthNumSp = maxOf(28f * scale, 12f).sp
    val monthNameSp = maxOf(13f * scale, 8f).sp
    val weekdaySp = maxOf(11f * scale, 7f).sp
    val daySp = maxOf(16f * scale, 9f).sp
    val headerGap = (4f * scale).dp
    val gridGap = (1f * scale).dp

    val labels = if (weekStart == WeekStart.SUNDAY) WeekdayLabelsSunStart else WeekdayLabelsMonStart
    val englishMonth = remember(yearMonth) {
        yearMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    }
    val days = remember(yearMonth, weekStart) {
        buildMonthCells(yearMonth, weekStart)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header: year | month number | English name
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = yearMonth.year.toString(),
                color = Color.White,
                fontSize = yearSp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
            )
            Text(
                text = yearMonth.monthValue.toString(),
                color = Color.White,
                fontSize = monthNumSp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = englishMonth,
                color = Color.White,
                fontSize = monthNameSp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(headerGap))

        // Weekday labels
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val color = weekdayLabelColor(index, weekStart)
                Text(
                    text = label,
                    color = color,
                    fontSize = weekdaySp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(gridGap))

        // Date grid (6 rows × 7 cols)
        Column(modifier = Modifier.fillMaxWidth()) {
            days.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { cell ->
                        DayCell(
                            cell = cell,
                            today = today,
                            isHoliday = isHoliday,
                            daySp = daySp,
                            scale = scale,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    cell: MonthCell,
    today: LocalDate,
    isHoliday: (LocalDate) -> Boolean,
    daySp: TextUnit,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.aspectRatio(1.15f),
        contentAlignment = Alignment.Center,
    ) {
        val date = cell.date ?: return@Box
        val holiday = isHoliday(date)
        val color = dayColor(date.dayOfWeek, holiday)
        val isToday = date == today
        val borderPad = (2f * scale).dp.coerceAtLeast(1.dp)
        Text(
            text = date.dayOfMonth.toString(),
            color = color,
            fontSize = daySp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = if (isToday) {
                Modifier
                    .border(
                        width = (1.5f * scale).dp.coerceAtLeast(1.dp),
                        color = ColorTodayBorder,
                        shape = RoundedCornerShape((4f * scale).dp.coerceAtLeast(2.dp)),
                    )
                    .padding(horizontal = borderPad, vertical = borderPad / 2)
            } else {
                Modifier
            },
        )
    }
}

private fun weekdayLabelColor(index: Int, weekStart: WeekStart): Color = when (weekStart) {
    WeekStart.SUNDAY -> when (index) {
        0 -> ColorSunday
        6 -> ColorSaturday
        else -> ColorWeekday
    }
    WeekStart.MONDAY -> when (index) {
        5 -> ColorSaturday
        6 -> ColorSunday
        else -> ColorWeekday
    }
}

private fun dayColor(dow: DayOfWeek, holiday: Boolean): Color = when {
    holiday || dow == DayOfWeek.SUNDAY -> ColorSunday
    dow == DayOfWeek.SATURDAY -> ColorSaturday
    else -> ColorWeekday
}

private data class MonthCell(val date: LocalDate?)

private fun buildMonthCells(yearMonth: YearMonth, weekStart: WeekStart): List<MonthCell> {
    val first = yearMonth.atDay(1)
    val length = yearMonth.lengthOfMonth()
    val firstDow = first.dayOfWeek
    val offset = when (weekStart) {
        WeekStart.SUNDAY -> firstDow.value % 7 // Sun=0 … Sat=6
        WeekStart.MONDAY -> (firstDow.value + 6) % 7 // Mon=0 … Sun=6
    }
    val cells = mutableListOf<MonthCell>()
    repeat(offset) { cells.add(MonthCell(null)) }
    for (d in 1..length) {
        cells.add(MonthCell(yearMonth.atDay(d)))
    }
    // Pad to full weeks (up to 6 rows)
    while (cells.size % 7 != 0) cells.add(MonthCell(null))
    while (cells.size < 42) cells.add(MonthCell(null))
    return cells
}

/** Prefer 4:3 when nearly square; 21:9 when very tall. Returns side-scale factor for side months. */
fun sideMonthScale(aspectHeightOverWidth: Float): Float {
    // aspect = h/w of usable area
    return when {
        aspectHeightOverWidth <= 1.4f -> 0.42f // near 4:3 / square → side months a bit larger
        aspectHeightOverWidth >= 2.1f -> 0.32f // 21:9-ish → smaller sides
        else -> 0.36f
    }
}
