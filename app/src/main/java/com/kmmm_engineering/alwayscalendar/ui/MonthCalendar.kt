package com.kmmm_engineering.alwayscalendar.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmmm_engineering.alwayscalendar.data.WeekStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val ColorSunday = Color(0xFFFF5555)
private val ColorSaturday = Color(0xFF5599FF)
private val ColorWeekday = Color.White
private val ColorTodayBorder = Color.White

private val WeekdayLabelsSunStart = listOf("日", "月", "火", "水", "木", "金", "土")
private val WeekdayLabelsMonStart = listOf("月", "火", "水", "木", "金", "土", "日")

/**
 * Single-month calendar block: header (year / month# / English name), weekday row, date grid.
 * Only days of [yearMonth] are shown; out-of-month cells are blank.
 *
 * Lays out in a **1:1 square** container (width = height). Header + weekday + 6-row
 * grid are packed tightly inside the square (see docs/reference/8月.png).
 * Overall size is controlled by the parent via [Modifier.uniformScale].
 */
@Composable
fun MonthCalendar(
    yearMonth: YearMonth,
    weekStart: WeekStart,
    today: LocalDate,
    isHoliday: (LocalDate) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val yearSp = 11.sp
    val monthNumSp = 28.sp
    val monthNameSp = 13.sp
    val weekdaySp = 11.sp
    val daySp = 16.sp
    val headerGap = 2.dp
    val gridGap = 1.dp

    val labels = if (weekStart == WeekStart.SUNDAY) WeekdayLabelsSunStart else WeekdayLabelsMonStart
    val englishMonth = remember(yearMonth) {
        yearMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    }
    val days = remember(yearMonth, weekStart) {
        buildMonthCells(yearMonth, weekStart)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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

        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    color = weekdayLabelColor(index, weekStart),
                    fontSize = weekdaySp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(gridGap))

        // 6 equal rows fill the remaining square area (no fixed cell aspect).
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            days.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    week.forEach { cell ->
                        DayCell(
                            cell = cell,
                            today = today,
                            isHoliday = isHoliday,
                            daySp = daySp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val date = cell.date ?: return@Box
        val holiday = isHoliday(date)
        val color = dayColor(date.dayOfWeek, holiday)
        val isToday = date == today
        Text(
            text = date.dayOfMonth.toString(),
            color = color,
            fontSize = daySp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = if (isToday) {
                Modifier
                    .border(
                        width = 1.5.dp,
                        color = ColorTodayBorder,
                        shape = RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 2.dp, vertical = 1.dp)
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
    while (cells.size % 7 != 0) cells.add(MonthCell(null))
    while (cells.size < 42) cells.add(MonthCell(null))
    return cells
}

/**
 * Uniform visual scale that also shrinks layout bounds (packing uses visual size).
 * Never applies non-uniform scaleY alone.
 */
fun Modifier.uniformScale(scale: Float): Modifier {
    if (scale == 1f) return this
    val s = scale.coerceAtLeast(0.01f)
    return this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val w = (placeable.width * s).roundToInt().coerceAtLeast(0)
        val h = (placeable.height * s).roundToInt().coerceAtLeast(0)
        layout(w, h) {
            placeable.placeWithLayer(0, 0) {
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/**
 * Intrinsic height of [MonthCalendar] at [width]: always equal to [width] (1:1 square).
 */
fun estimateMonthHeight(width: Dp): Dp = width

/**
 * Clamp usable aspect (height/width) into the design range for **screen letterboxing**:
 * more square than 4:3 → treat as 4:3; taller than 21:9 → treat as 21:9.
 * Individual month blocks themselves are always 1:1 regardless of this clamp.
 */
fun designAspectHeightOverWidth(rawHeightOverWidth: Float): Float =
    rawHeightOverWidth.coerceIn(4f / 3f, 21f / 9f)

/**
 * Interpolate uniform scale by distance from focused month (0 = center).
 * At rest (distance == 1) scale must equal [sideScale] — packing allocates side
 * slots with that height. Beyond 1 (peek of ±2 during fling) ease down to 50%.
 */
fun monthScaleForDistance(distance: Float, centerScale: Float, sideScale: Float): Float {
    val d = distance.coerceIn(0f, 1.5f)
    return when {
        d <= 0f -> centerScale
        d <= 1f -> centerScale + (sideScale - centerScale) * d
        else -> {
            // d in (1, 1.5]: sideScale → sideScale * 0.5
            val far = ((d - 1f) / 0.5f).coerceIn(0f, 1f)
            sideScale * (1f - 0.5f * far)
        }
    }
}
