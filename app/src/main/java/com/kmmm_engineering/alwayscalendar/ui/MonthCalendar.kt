package com.kmmm_engineering.alwayscalendar.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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

/** Date-number size (fraction of the cell short side) when extra lines are shown. Tune on device. */
internal const val DayNumberFillOneExtra = 0.65f
internal const val DayNumberFillBothExtras = 0.50f
/** Longest holiday name (勤労感謝の日 etc.) in characters; sets the shared name font size. */
private const val HolidayNameMaxChars = 6
/** Rokuyo text is the date colour at this alpha. */
private const val RokuyoAlpha = 0.65f

/** Rokuyo / holiday-name display for the center month only (null extras = plain). */
data class DayExtras(
    val showRokuyo: Boolean,
    val showHolidayName: Boolean,
    val rokuyoOf: (LocalDate) -> String?,
    val holidayNameOf: (LocalDate) -> String?,
)

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

/** Date numerals, month numeral, weekday labels: fraction of the cell on each axis. */
private const val GlyphFill = 0.9f

/**
 * Year fits its own cell at this fraction. English month names share one size:
 * the smallest of the 12 full names that still fits this fraction of the name cell.
 */
private const val HeaderWordFill = 0.8f

private fun Dp.part(numerator: Int): Dp = this * (numerator.toFloat() / RatioTotal.toFloat())

/**
 * Single-month calendar block: header (year / month# / English name), weekday row, date grid.
 * Only days of [yearMonth] are shown; out-of-month cells are blank.
 *
 * The block is a **1:1 square** maximized to the bounded axis (or the shorter side when
 * both are bounded). Measured size is that square — never the incoming max box. The
 * portrait/landscape strip places prev/center/next from this natural square (center
 * maximized in the usable rect, neighbors scaled into the leftover). Filling max
 * constraints would make every month as tall as the strip and they overlap.
 * Regions are fractions of the square, not sp/dp content sizes, so dpi and resolution
 * do not change the ratios. Glyphs fit their cells (year at 80% of its cell;
 * English month names share the smallest 80% fit among the 12 names; everything
 * else at 90%). Parent may still apply [Modifier.uniformScale].
 */
@Composable
fun MonthCalendar(
    yearMonth: YearMonth,
    weekStart: WeekStart,
    today: LocalDate,
    isHoliday: (LocalDate) -> Boolean,
    colorOptions: DayColorOptions = DayColorOptions(),
    extras: DayExtras? = null,
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
        // size(side), not fillMaxSize(): a loose max (the strip) must not become the
        // measured height. The parent slots months using this square.
        Box(modifier = Modifier.size(side), contentAlignment = Alignment.Center) {
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
                extras = extras,
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
    extras: DayExtras?,
) {
    val headerH = side.part(RatioHeaderH)
    val weekdayH = side.part(RatioWeekdayH)
    val dateRowH = side.part(RatioDateRowH)
    val colW = side.part(RatioColW)
    val yearW = side.part(RatioYearW)
    val monthNumW = side.part(RatioMonthNumW)
    val monthNameW = side.part(RatioMonthNameW)
    val yearSp = fitTextSize(yearW, headerH, yearText, FontWeight.Normal, fill = HeaderWordFill)
    val monthNumSp = fitTextSize(monthNumW, headerH, monthNumText, FontWeight.Bold, fill = GlyphFill)
    val monthNameSp = sharedEnglishMonthNameSize(monthNameW, headerH)
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
                val labelSp = fitTextSize(colW, weekdayH, label, FontWeight.Normal, fill = GlyphFill)
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
                        cellWidth = colW,
                        extras = extras,
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
 * Year uses [HeaderWordFill] (0.8) on its own string; other glyphs use [GlyphFill] (0.9).
 * English month names do not use this per string — see [sharedEnglishMonthNameSize].
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

/** Full English month names, January–December, in the same font as the header label. */
private val EnglishFullMonthNames: List<String> = (1..12).map { month ->
    java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
}

/**
 * One size for every English month name: probe each of the 12 names with the same
 * font ([FontWeight.Normal], no family override) and keep the smallest scale that
 * fits [HeaderWordFill] of [cellWidth]×[cellHeight] on both axes. Usually September.
 */
@Composable
private fun sharedEnglishMonthNameSize(
    cellWidth: Dp,
    cellHeight: Dp,
): TextUnit {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    return remember(cellWidth, cellHeight, density.density, density.fontScale, textMeasurer) {
        val targetW = with(density) { cellWidth.toPx() } * HeaderWordFill
        val targetH = with(density) { cellHeight.toPx() } * HeaderWordFill
        if (targetW <= 0f || targetH <= 0f) return@remember 1.sp
        val probeSp = 100.sp
        var minScale = Float.POSITIVE_INFINITY
        for (name in EnglishFullMonthNames) {
            val layout = textMeasurer.measure(
                text = name,
                style = TextStyle(fontSize = probeSp, fontWeight = FontWeight.Normal),
                softWrap = false,
                maxLines = 1,
            )
            val w = layout.size.width.toFloat()
            val h = layout.size.height.toFloat()
            if (w <= 0f || h <= 0f) continue
            val scale = minOf(targetW / w, targetH / h)
            if (scale < minScale) minScale = scale
        }
        if (!minScale.isFinite()) return@remember 1.sp
        val probePx = with(density) { probeSp.toPx() }
        with(density) { (probePx * minScale).toSp() }
    }
}

@Composable
private fun dayNumberFontSize(cellShortSide: Dp): TextUnit {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    return remember(cellShortSide, density.density, density.fontScale, textMeasurer) {
        val targetPx = with(density) { cellShortSide.toPx() } * GlyphFill
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
    cellWidth: Dp,
    extras: DayExtras?,
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
        val showR = extras?.showRokuyo == true
        val showH = extras?.showHolidayName == true
        val weight = if (isToday) FontWeight.Bold else FontWeight.Normal
        if (extras == null || (!showR && !showH)) {
            Text(
                text = date.dayOfMonth.toString(),
                color = color,
                fontSize = daySp,
                fontWeight = weight,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
            return@Box
        }
        val density = LocalDensity.current
        val numberFill = if (showR && showH) DayNumberFillBothExtras else DayNumberFillOneExtra
        val numberSp = daySp * (numberFill / GlyphFill)
        // Same size for every day: longest holiday name fits the cell width; rokuyo is 2 chars.
        val nameSp = with(density) { (cellWidth * 0.95f / HolidayNameMaxChars).toSp() }
        val rokuyoSp = with(density) {
            minOf(cellWidth * 0.9f / 2f, cellWidth * 0.24f).toSp()
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                color = color,
                fontSize = numberSp,
                fontWeight = weight,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                lineHeight = numberSp,
            )
            if (showR) {
                Text(
                    text = extras.rokuyoOf(date).orEmpty(),
                    color = color.copy(alpha = RokuyoAlpha),
                    fontSize = rokuyoSp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    lineHeight = rokuyoSp,
                )
            }
            if (showH) {
                Text(
                    text = extras.holidayNameOf(date).orEmpty(),
                    color = color,
                    fontSize = nameSp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    lineHeight = nameSp,
                )
            }
        }
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
