package com.kmmm_engineering.alwayscalendar.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmmm_engineering.alwayscalendar.data.CustomDayRule
import com.kmmm_engineering.alwayscalendar.data.WeekStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

internal val ColorSunday = Color(0xFFFF5555)
internal val ColorSaturday = Color(0xFF5599FF)
internal val ColorWeekday = Color.White
private val ColorTodayBorder = Color.White

private val WeekdayLabelsSunStart = listOf("日", "月", "火", "水", "木", "金", "土")
private val WeekdayLabelsMonStart = listOf("月", "火", "水", "木", "金", "土", "日")

/**
 * Day-color options from settings (priority applied inside [resolveDayNumeralColor]).
 */
data class DayColorOptions(
    val holidaysAsWhite: Boolean = false,
    val saturdayAsWhite: Boolean = false,
    val sundayAsWhite: Boolean = false,
    val customHolidays: List<CustomDayRule> = emptyList(),
    val customWeekdays: List<CustomDayRule> = emptyList(),
)

/**
 * Priority: custom weekday (白) ＞ custom holiday (赤) ＞ white-switches ＞ default
 * (祝日・日曜赤、土曜青、他白). When holidaysAsWhite, API/cache holidays ignored.
 */
fun resolveDayNumeralColor(
    date: LocalDate,
    isApiHoliday: Boolean,
    options: DayColorOptions,
): Color {
    if (options.customWeekdays.any { it.matches(date) }) return ColorWeekday
    if (options.customHolidays.any { it.matches(date) }) return ColorSunday
    val treatHoliday = !options.holidaysAsWhite && isApiHoliday
    val dow = date.dayOfWeek
    return when {
        treatHoliday -> ColorSunday
        dow == DayOfWeek.SUNDAY -> if (options.sundayAsWhite) ColorWeekday else ColorSunday
        dow == DayOfWeek.SATURDAY -> if (options.saturdayAsWhite) ColorWeekday else ColorSaturday
        else -> ColorWeekday
    }
}

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
    colorOptions: DayColorOptions = DayColorOptions(),
    modifier: Modifier = Modifier,
) {
    val yearSp = 11.sp
    val monthNumSp = 28.sp
    val monthNameSp = 13.sp
    val weekdaySp = 11.sp
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
                    color = weekdayLabelColor(
                        index,
                        weekStart,
                        colorOptions.saturdayAsWhite,
                        colorOptions.sundayAsWhite,
                    ),
                    fontSize = weekdaySp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(gridGap))

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val cellShortSide = minOf(maxWidth / 7f, maxHeight / 6f)
            val daySp = dayNumberFontSize(cellShortSide)
            Column(modifier = Modifier.fillMaxSize()) {
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
                                colorOptions = colorOptions,
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
}

@Composable
private fun dayNumberFontSize(cellShortSide: Dp): TextUnit {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    return remember(cellShortSide, density.density, density.fontScale, textMeasurer) {
        val targetPx = with(density) { cellShortSide.toPx() } * 0.9f
        if (targetPx <= 0f) return@remember 16.sp
        val probeSp = 100.sp
        val layout = textMeasurer.measure(
            text = "00",
            style = androidx.compose.ui.text.TextStyle(fontSize = probeSp, fontWeight = FontWeight.Normal),
        )
        val measured = max(layout.size.width, layout.size.height).toFloat()
        if (measured <= 0f) return@remember 16.sp
        val probePx = with(density) { probeSp.toPx() }
        with(density) { (probePx * (targetPx / measured)).toSp() }
    }
}

@Composable
private fun DayCell(
    cell: MonthCell,
    today: LocalDate,
    isHoliday: (LocalDate) -> Boolean,
    colorOptions: DayColorOptions,
    daySp: TextUnit,
    modifier: Modifier = Modifier,
) {
    val date = cell.date
    val isToday = date != null && date == today
    Box(
        modifier = modifier.then(
            if (isToday) {
                Modifier.border(
                    width = 1.5.dp,
                    color = ColorTodayBorder,
                    shape = RoundedCornerShape(6.dp),
                )
            } else {
                Modifier
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (date == null) return@Box
        val color = resolveDayNumeralColor(date, isHoliday(date), colorOptions)
        Text(
            text = date.dayOfMonth.toString(),
            color = color,
            fontSize = daySp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun weekdayLabelColor(
    index: Int,
    weekStart: WeekStart,
    saturdayAsWhite: Boolean,
    sundayAsWhite: Boolean,
): Color {
    val sunday = if (sundayAsWhite) ColorWeekday else ColorSunday
    val saturday = if (saturdayAsWhite) ColorWeekday else ColorSaturday
    return when (weekStart) {
        WeekStart.SUNDAY -> when (index) {
            0 -> sunday
            6 -> saturday
            else -> ColorWeekday
        }
        WeekStart.MONDAY -> when (index) {
            5 -> saturday
            6 -> sunday
            else -> ColorWeekday
        }
    }
}

private data class MonthCell(val date: LocalDate?)

private fun buildMonthCells(yearMonth: YearMonth, weekStart: WeekStart): List<MonthCell> {
    val first = yearMonth.atDay(1)
    val length = yearMonth.lengthOfMonth()
    val firstDow = first.dayOfWeek
    val offset = when (weekStart) {
        WeekStart.SUNDAY -> firstDow.value % 7
        WeekStart.MONDAY -> (firstDow.value + 6) % 7
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

fun estimateMonthHeight(width: Dp): Dp = width

fun designAspectHeightOverWidth(rawHeightOverWidth: Float): Float =
    rawHeightOverWidth.coerceIn(4f / 3f, 21f / 9f)

fun designAspectWidthOverHeight(rawWidthOverHeight: Float): Float =
    rawWidthOverHeight.coerceIn(4f / 3f, 21f / 9f)

fun monthScaleForDistance(distance: Float, centerScale: Float, sideScale: Float): Float {
    val d = distance.coerceIn(0f, 1.5f)
    return when {
        d <= 0f -> centerScale
        d <= 1f -> centerScale + (sideScale - centerScale) * d
        else -> {
            val far = ((d - 1f) / 0.5f).coerceIn(0f, 1f)
            sideScale * (1f - 0.5f * far)
        }
    }
}
