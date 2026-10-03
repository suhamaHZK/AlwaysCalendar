package com.kmmm_engineering.alwayscalendar.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmmm_engineering.alwayscalendar.data.CustomDayRule
import com.kmmm_engineering.alwayscalendar.data.WeekStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
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
 * Ratios from the layout sheet (date-column width = 1). Sum is the 7×7 square:
 * header 1.1, weekday row 0.5, six date rows × 0.9.
 * Widths: year 2.9 + month numeral 1.2 + English month 2.9 = 7; each weekday/date column 1.
 * Stored as integer parts of 70 so header splits and the 7 columns share one edge.
 */
private const val RatioTotal = 70
private const val RatioHeaderH = 11
private const val RatioWeekdayH = 5
private const val RatioDateRowH = 9
private const val RatioColW = 10
private const val RatioYearW = 29
private const val RatioMonthNumW = 12
private const val RatioMonthNameW = 29

private fun Dp.part(numerator: Int): Dp = this * (numerator.toFloat() / RatioTotal.toFloat())

/**
 * Single-month calendar block: header (year / month# / English name), weekday row, date grid.
 * Only days of [yearMonth] are shown; out-of-month cells are blank.
 *
 * The block is a **1:1 square** maximized inside the incoming constraints (the bounded
 * axis when only one is bounded, otherwise the shorter side). Regions are fractions of
 * that square, not sp/dp content sizes, so dpi and resolution do not change the ratios.
 * Glyphs are fitted to their cells the same way. Parent may still apply [Modifier.uniformScale].
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
    val labels = if (weekStart == WeekStart.SUNDAY) WeekdayLabelsSunStart else WeekdayLabelsMonStart
    val englishMonth = remember(yearMonth) {
        yearMonth.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
    }
    val days = remember(yearMonth, weekStart) {
        buildMonthCells(yearMonth, weekStart)
    }
    val yearText = yearMonth.year.toString()
    val monthNumText = yearMonth.monthValue.toString()

    BoxWithConstraints(modifier = modifier) {
        val side = when {
            constraints.hasBoundedWidth && constraints.hasBoundedHeight -> minOf(maxWidth, maxHeight)
            constraints.hasBoundedWidth -> maxWidth
            constraints.hasBoundedHeight -> maxHeight
            else -> 0.dp
        }
        val frameModifier = if (constraints.hasBoundedWidth && constraints.hasBoundedHeight) {
            Modifier.fillMaxSize()
        } else {
            Modifier.size(side)
        }
        Box(modifier = frameModifier, contentAlignment = Alignment.Center) {
            MonthCalendarSquare(
                side = side,
                yearText = yearText,
                monthNumText = monthNumText,
                englishMonth = englishMonth,
                labels = labels,
                days = days,
                weekStart = weekStart,
                today = today,
                isHoliday = isHoliday,
                colorOptions = colorOptions,
            )
        }
    }
}

@Composable
private fun MonthCalendarSquare(
    side: Dp,
    yearText: String,
    monthNumText: String,
    englishMonth: String,
    labels: List<String>,
    days: List<MonthCell>,
    weekStart: WeekStart,
    today: LocalDate,
    isHoliday: (LocalDate) -> Boolean,
    colorOptions: DayColorOptions,
) {
    val headerH = side.part(RatioHeaderH)
    val weekdayH = side.part(RatioWeekdayH)
    val dateRowH = side.part(RatioDateRowH)
    val colW = side.part(RatioColW)
    val yearW = side.part(RatioYearW)
    val monthNumW = side.part(RatioMonthNumW)
    val monthNameW = side.part(RatioMonthNameW)
    val yearSp = fitTextSize(yearW, headerH, yearText, FontWeight.Normal)
    val monthNumSp = fitTextSize(monthNumW, headerH, monthNumText, FontWeight.Bold)
    val monthNameSp = fitTextSize(monthNameW, headerH, englishMonth, FontWeight.Normal)
    val daySp = dayNumberFontSize(minOf(colW, dateRowH))
    val cellShort = minOf(colW, dateRowH)

    Column(modifier = Modifier.size(side)) {
        Row(modifier = Modifier.fillMaxWidth().weight(RatioHeaderH.toFloat())) {
            FittedLabel(
                text = yearText,
                fontSize = yearSp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(RatioYearW.toFloat()).fillMaxHeight(),
            )
            FittedLabel(
                text = monthNumText,
                fontSize = monthNumSp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(RatioMonthNumW.toFloat()).fillMaxHeight(),
            )
            FittedLabel(
                text = englishMonth,
                fontSize = monthNameSp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(RatioMonthNameW.toFloat()).fillMaxHeight(),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().weight(RatioWeekdayH.toFloat())) {
            labels.forEachIndexed { index, label ->
                val labelSp = fitTextSize(colW, weekdayH, label, FontWeight.Normal)
                FittedLabel(
                    text = label,
                    color = weekdayLabelColor(
                        index,
                        weekStart,
                        colorOptions.saturdayAsWhite,
                        colorOptions.sundayAsWhite,
                    ),
                    fontSize = labelSp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(RatioColW.toFloat()).fillMaxHeight(),
                )
            }
        }
        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().weight(RatioDateRowH.toFloat())) {
                week.forEach { cell ->
                    DayCell(
                        cell = cell,
                        today = today,
                        isHoliday = isHoliday,
                        colorOptions = colorOptions,
                        daySp = daySp,
                        cellShortSide = cellShort,
                        modifier = Modifier.weight(RatioColW.toFloat()).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FittedLabel(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    val alignment = when (textAlign) {
        TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
        TextAlign.Center -> Alignment.Center
        else -> Alignment.CenterStart
    }
    Box(modifier = modifier, contentAlignment = alignment) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/**
 * Fit [sample] inside [cellWidth]×[cellHeight] at [fill] of each axis.
 * Resulting pixel size follows the cell, not density or font scale.
 */
@Composable
private fun fitTextSize(
    cellWidth: Dp,
    cellHeight: Dp,
    sample: String,
    fontWeight: FontWeight,
    fill: Float = 0.9f,
): TextUnit {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    return remember(cellWidth, cellHeight, sample, fontWeight, fill, density.density, density.fontScale, textMeasurer) {
        val targetW = with(density) { cellWidth.toPx() } * fill
        val targetH = with(density) { cellHeight.toPx() } * fill
        if (targetW <= 0f || targetH <= 0f || sample.isEmpty()) return@remember 1.sp
        val probeSp = 100.sp
        val layout = textMeasurer.measure(
            text = sample,
            style = TextStyle(fontSize = probeSp, fontWeight = fontWeight),
            softWrap = false,
            maxLines = 1,
        )
        val w = layout.size.width.toFloat()
        val h = layout.size.height.toFloat()
        if (w <= 0f || h <= 0f) return@remember 1.sp
        val scale = minOf(targetW / w, targetH / h)
        val probePx = with(density) { probeSp.toPx() }
        with(density) { (probePx * scale).toSp() }
    }
}

@Composable
private fun dayNumberFontSize(cellShortSide: Dp): TextUnit {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    return remember(cellShortSide, density.density, density.fontScale, textMeasurer) {
        val targetPx = with(density) { cellShortSide.toPx() } * 0.9f
        if (targetPx <= 0f) return@remember 1.sp
        val probeSp = 100.sp
        val layout = textMeasurer.measure(
            text = "00",
            style = TextStyle(fontSize = probeSp, fontWeight = FontWeight.Normal),
            softWrap = false,
            maxLines = 1,
        )
        val measured = max(layout.size.width, layout.size.height).toFloat()
        if (measured <= 0f) return@remember 1.sp
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
    cellShortSide: Dp,
    modifier: Modifier = Modifier,
) {
    val date = cell.date
    val isToday = date != null && date == today
    // Former 1.5.dp stroke / 6.dp corner, as fractions of the date-cell short side.
    val todayStroke = cellShortSide * 0.04f
    val todayCorner = cellShortSide * 0.15f
    Box(
        modifier = modifier.then(
            if (isToday) {
                Modifier.border(
                    width = todayStroke,
                    color = ColorTodayBorder,
                    shape = RoundedCornerShape(todayCorner),
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
