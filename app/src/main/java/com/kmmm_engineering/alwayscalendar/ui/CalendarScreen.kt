@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.kmmm_engineering.alwayscalendar.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmmm_engineering.alwayscalendar.R
import com.kmmm_engineering.alwayscalendar.data.UserSettings
import com.kmmm_engineering.alwayscalendar.data.WeekStart
import com.kmmm_engineering.alwayscalendar.network.NetworkKind
import com.kmmm_engineering.alwayscalendar.network.NetworkStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** Usable-area insets: status bar (even when hidden) + display cutout / punch-hole. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun usableWindowInsets(): WindowInsets =
    WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout)

@Composable
fun CalendarScreen(
    settings: UserSettings,
    batteryPercent: Int,
    isCharging: Boolean,
    networkStatus: NetworkStatus,
    holidayDates: Set<String>,
    onEnsureMonth: suspend (YearMonth) -> Boolean,
    onHolidayFetchFailed: () -> Unit,
    onSingleTap: () -> Unit,
    onOpenSettings: () -> Unit,
    onUnlockSliderVisibilityChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var centerMonth by remember { mutableStateOf(YearMonth.now()) }
    var showSlider by remember { mutableStateOf(false) }
    val recentTapAts = remember { ArrayDeque<Long>() }
    var lostUserHintUntil by remember { mutableLongStateOf(0L) }
    var shiftX by remember { mutableIntStateOf(0) }
    var shiftY by remember { mutableIntStateOf(0) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val today = remember(nowMs) { LocalDate.now() }
    val wallTime = remember(nowMs) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
        "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }
    val scope = rememberCoroutineScope()

    // Finger-follow scroll offset in "pages" (-1..1 during gesture; 0 settled).
    val scrollPage = remember { Animatable(0f) }
    var dragPage by remember { mutableFloatStateOf(0f) }
    val liveScroll = scrollPage.value + dragPage

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            val rem = nowMs % 60_000L
            val wait = if (rem == 0L) 60_000L else 60_000L - rem
            delay(wait.coerceAtLeast(1L))
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(180_000L)
            shiftX = Random.nextInt(-12, 13)
            shiftY = Random.nextInt(-12, 13)
        }
    }

    LaunchedEffect(showSlider) {
        onUnlockSliderVisibilityChange(showSlider)
        if (showSlider) {
            delay(8_000L)
            showSlider = false
        }
    }

    LaunchedEffect(lostUserHintUntil) {
        val until = lostUserHintUntil
        if (until <= 0L) return@LaunchedEffect
        val remaining = until - System.currentTimeMillis()
        if (remaining > 0L) delay(remaining)
        if (lostUserHintUntil == until) lostUserHintUntil = 0L
    }

    LaunchedEffect(centerMonth) {
        val ok = onEnsureMonth(centerMonth)
        if (!ok) onHolidayFetchFailed()
    }

    val isHoliday: (LocalDate) -> Boolean = remember(holidayDates) {
        { d -> holidayDates.contains("%04d-%02d-%02d".format(d.year, d.monthValue, d.dayOfMonth)) }
    }

    fun commitScrollToNearest() {
        scope.launch {
            val current = scrollPage.value + dragPage
            val nearest = current.roundToInt().coerceIn(-1, 1)
            // Snap: animate remaining drag into Animatable, then apply month delta.
            scrollPage.snapTo(current)
            dragPage = 0f
            scrollPage.animateTo(
                nearest.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            if (nearest != 0) {
                centerMonth = centerMonth.plusMonths(nearest.toLong())
                scrollPage.snapTo(0f)
            }
        }
    }

    fun animateToMonthDelta(delta: Long) {
        if (settings.misoperationLock) return
        if (delta == 0L) return
        scope.launch {
            dragPage = 0f
            scrollPage.snapTo(0f)
            val target = delta.toFloat().coerceIn(-1f, 1f)
            scrollPage.animateTo(
                target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            centerMonth = centerMonth.plusMonths(delta)
            scrollPage.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(settings.misoperationLock) {
                detectTapGestures(
                    onTap = {
                        if (showSlider) {
                            showSlider = false
                        } else {
                            onSingleTap()
                            val tapAt = System.currentTimeMillis()
                            recentTapAts.addLast(tapAt)
                            while (recentTapAts.isNotEmpty() && tapAt - recentTapAts.first() > 60_000L) {
                                recentTapAts.removeFirst()
                            }
                            if (recentTapAts.size >= 5) {
                                lostUserHintUntil = tapAt + 5_000L
                            }
                        }
                    },
                    onDoubleTap = { showSlider = true },
                )
            },
    ) {
        // Status (top-left) — inside usable insets so punch-hole / status bar are avoided
        StatusCluster(
            timeText = wallTime,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            networkStatus = networkStatus,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(usableWindowInsets())
                .padding(start = 12.dp, top = 6.dp),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(usableWindowInsets())
                .offset { IntOffset(shiftX, shiftY) }
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .then(
                    if (settings.misoperationLock) {
                        Modifier
                    } else if (isLandscape) {
                        Modifier.pointerInput(centerMonth) {
                            val step = size.width * 0.42f
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    // Cancel in-flight snap by folding into drag
                                    dragPage += scrollPage.value
                                    scope.launch { scrollPage.snapTo(0f) }
                                },
                                onDragEnd = { commitScrollToNearest() },
                                onDragCancel = { commitScrollToNearest() },
                                onHorizontalDrag = { change, amount ->
                                    change.consume()
                                    // amount>0 = finger right → content right → previous month (+scroll means next)
                                    // so invert: drag right decreases scroll page
                                    dragPage += -amount / step
                                    dragPage = dragPage.coerceIn(-1.25f, 1.25f)
                                },
                            )
                        }
                    } else {
                        Modifier.pointerInput(centerMonth) {
                            val step = size.height * 0.38f
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragPage += scrollPage.value
                                    scope.launch { scrollPage.snapTo(0f) }
                                },
                                onDragEnd = { commitScrollToNearest() },
                                onDragCancel = { commitScrollToNearest() },
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    // Finger down → previous month (content follows down)
                                    dragPage += -amount / step
                                    // Convention: positive liveScroll → next month toward center
                                    // swipe up (amount < 0) → next; so dragPage += -amount/step
                                    dragPage = dragPage.coerceIn(-1.25f, 1.25f)
                                },
                            )
                        }
                    },
                ),
        ) {
            // Center-first: maximize current month, then size prev/next from remaining margins.
            val (centerScale, sideScale) = if (isLandscape) {
                fitMonthScalesLandscape(maxWidth, maxHeight)
            } else {
                fitMonthScalesPortrait(maxWidth, maxHeight)
            }

            if (isLandscape) {
                MonthStripLandscape(
                    centerMonth = centerMonth,
                    scroll = liveScroll,
                    centerScale = centerScale,
                    sideScale = sideScale,
                    weekStart = settings.weekStart,
                    today = today,
                    isHoliday = isHoliday,
                    misoperationLock = settings.misoperationLock,
                    onSelectDelta = { animateToMonthDelta(it) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                MonthStripPortrait(
                    centerMonth = centerMonth,
                    scroll = liveScroll,
                    centerScale = centerScale,
                    sideScale = sideScale,
                    weekStart = settings.weekStart,
                    today = today,
                    isHoliday = isHoliday,
                    misoperationLock = settings.misoperationLock,
                    onSelectDelta = { animateToMonthDelta(it) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val showPersistentHint = !settings.firstRunHintSeen && !showSlider
        val showLostUserHint = !showSlider && lostUserHintUntil > System.currentTimeMillis()
        if (showPersistentHint || showLostUserHint) {
            Text(
                text = stringResource(R.string.double_tap_hint),
                color = Color(0xFFAAAAAA),
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(usableWindowInsets())
                    .padding(bottom = 48.dp),
            )
        }

        if (showSlider) {
            UnlockSlider(
                onUnlocked = {
                    showSlider = false
                    onOpenSettings()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(usableWindowInsets())
                    .padding(horizontal = 32.dp, vertical = 40.dp)
                    .fillMaxWidth(if (isLandscape) 0.5f else 1f),
            )
        }
    }
}

/**
 * Portrait strip: prev / center / next; center maximized, sides fill leftover margins.
 * Each month is the same calendar structure, uniformly scaled (no row crush).
 * [scroll] in pages: +1 means next month is moving into center (content shifted up).
 */
@Composable
private fun MonthStripPortrait(
    centerMonth: YearMonth,
    scroll: Float,
    centerScale: Float,
    sideScale: Float,
    weekStart: WeekStart,
    today: LocalDate,
    isHoliday: (LocalDate) -> Boolean,
    misoperationLock: Boolean,
    onSelectDelta: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        val baseW = maxWidth * 0.92f
        val baseH = estimateMonthHeight(baseW)
        val sideH = baseH * sideScale
        val centerH = baseH * centerScale
        val packH = sideH * 2f + centerH
        // Center the 3-month pack in usable height (letterbox when design aspect < raw).
        val originY = (maxHeight - packH) / 2f
        // Travel distance for one month page ≈ distance between month slot centers
        val stepPx = with(density) { ((sideH + centerH) / 2f).toPx() }
        val scrollPx = -scroll * stepPx // +scroll (next) moves content up

        val months = listOf(
            -2 to centerMonth.plusMonths(-2),
            -1 to centerMonth.plusMonths(-1),
            0 to centerMonth,
            1 to centerMonth.plusMonths(1),
            2 to centerMonth.plusMonths(2),
        )

        Box(Modifier.fillMaxSize()) {
            months.forEach { (index, ym) ->
                val slotCenterY = originY + when (index) {
                    -2 -> -(sideH + centerH / 2f)
                    -1 -> sideH / 2f
                    0 -> sideH + centerH / 2f
                    1 -> sideH + centerH + sideH / 2f
                    else -> sideH + centerH + sideH + centerH / 2f
                }
                val y = slotCenterY + with(density) { scrollPx.toDp() }
                val dist = abs(index.toFloat() - scroll)
                val scale = monthScaleForDistance(dist, centerScale, sideScale)
                val visualH = baseH * scale
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset {
                            IntOffset(
                                0,
                                with(density) { (y - visualH / 2f).toPx() }.roundToInt(),
                            )
                        }
                        // uniformScale must wrap the natural-size calendar (not sit under width())
                        .uniformScale(scale)
                        .graphicsLayer {
                            alpha = (1.25f - dist * 0.35f).coerceIn(0.35f, 1f)
                        }
                        .then(
                            if (!misoperationLock && index == -1) {
                                Modifier.clickable { onSelectDelta(-1) }
                            } else if (!misoperationLock && index == 1) {
                                Modifier.clickable { onSelectDelta(1) }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    MonthCalendar(
                        yearMonth = ym,
                        weekStart = weekStart,
                        today = today,
                        isHoliday = isHoliday,
                        modifier = Modifier.width(baseW),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthStripLandscape(
    centerMonth: YearMonth,
    scroll: Float,
    centerScale: Float,
    sideScale: Float,
    weekStart: WeekStart,
    today: LocalDate,
    isHoliday: (LocalDate) -> Boolean,
    misoperationLock: Boolean,
    onSelectDelta: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        // Base = height-maximized intrinsic width; scales shrink uniformly into usable width.
        val baseH = maxHeight * 0.92f
        // Invert estimateMonthHeight: H ≈ 48.dp + W/7/1.15*6 → W ≈ (H - 48) * 7 * 1.15 / 6
        val baseW = run {
            val gridH = (baseH - 48.dp).coerceAtLeast(1.dp)
            gridH * 7f * 1.15f / 6f
        }
        val sideW = baseW * sideScale
        val centerW = baseW * centerScale
        val packW = sideW * 2f + centerW
        val originX = (maxWidth - packW) / 2f
        val stepPx = with(density) { ((sideW + centerW) / 2f).toPx() }
        val scrollPx = -scroll * stepPx

        val months = listOf(
            -2 to centerMonth.plusMonths(-2),
            -1 to centerMonth.plusMonths(-1),
            0 to centerMonth,
            1 to centerMonth.plusMonths(1),
            2 to centerMonth.plusMonths(2),
        )

        Box(Modifier.fillMaxSize()) {
            months.forEach { (index, ym) ->
                val slotCenterX = originX + when (index) {
                    -2 -> -(sideW + centerW / 2f)
                    -1 -> sideW / 2f
                    0 -> sideW + centerW / 2f
                    1 -> sideW + centerW + sideW / 2f
                    else -> sideW + centerW + sideW + centerW / 2f
                }
                val x = slotCenterX + with(density) { scrollPx.toDp() }
                val dist = abs(index.toFloat() - scroll)
                val scale = monthScaleForDistance(dist, centerScale, sideScale)
                val visualW = baseW * scale
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset {
                            IntOffset(
                                with(density) { (x - visualW / 2f).toPx() }.roundToInt(),
                                0,
                            )
                        }
                        .uniformScale(scale)
                        .graphicsLayer {
                            alpha = (1.25f - dist * 0.35f).coerceIn(0.35f, 1f)
                        }
                        .then(
                            if (!misoperationLock && index == -1) {
                                Modifier.clickable { onSelectDelta(-1) }
                            } else if (!misoperationLock && index == 1) {
                                Modifier.clickable { onSelectDelta(1) }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    MonthCalendar(
                        yearMonth = ym,
                        weekStart = weekStart,
                        today = today,
                        isHoliday = isHoliday,
                        modifier = Modifier.width(baseW),
                    )
                }
            }
        }
    }
}

/**
 * Portrait: (1) maximize center month in the design-aspect usable rect,
 * (2) size prev/next uniformly from the leftover top/bottom margins.
 * Never shrinks center to enlarge sides.
 */
private fun fitMonthScalesPortrait(
    totalW: androidx.compose.ui.unit.Dp,
    totalH: androidx.compose.ui.unit.Dp,
): Pair<Float, Float> {
    val rawAspect = totalH / totalW
    val designAspect = designAspectHeightOverWidth(rawAspect)
    // Effective layout rect (letterboxed when raw is outside 4:3…21:9).
    val layoutH = minOf(totalH, totalW * designAspect)
    val layoutW = minOf(totalW, totalH / designAspect)

    val fullBaseW = totalW * 0.92f
    val fullBaseH = estimateMonthHeight(fullBaseW)
    if (fullBaseH.value <= 0f) return 1f to 0.35f

    // Scale so base width matches layout width (square screens → narrower content).
    var centerScale = (layoutW * 0.92f) / fullBaseW
    var centerH = fullBaseH * centerScale
    if (centerH > layoutH) {
        centerScale *= (layoutH / centerH)
        centerH = fullBaseH * centerScale
    }

    val remainEach = ((layoutH - centerH) / 2f).coerceAtLeast(0.dp)
    // Nearly fill each margin; slight inset so months don't kiss the cutout edge.
    var sideScale = (remainEach / fullBaseH) * 0.96f
    sideScale = sideScale.coerceAtMost(centerScale).coerceAtLeast(0.01f)
    return centerScale to sideScale
}

/**
 * Landscape: (1) maximize center by height (width follows intrinsic aspect),
 * (2) size prev/next uniformly from leftover left/right margins.
 * Never shrinks center to enlarge sides.
 */
private fun fitMonthScalesLandscape(
    totalW: androidx.compose.ui.unit.Dp,
    totalH: androidx.compose.ui.unit.Dp,
): Pair<Float, Float> {
    val baseH = totalH * 0.92f
    val gridH = (baseH - 48.dp).coerceAtLeast(1.dp)
    val fullBaseW = gridH * 7f * 1.15f / 6f
    if (fullBaseW.value <= 0f) return 1f to 0.35f

    var centerScale = 1f
    if (fullBaseW > totalW) {
        centerScale = totalW / fullBaseW
    }
    val centerW = fullBaseW * centerScale
    val remainEach = ((totalW - centerW) / 2f).coerceAtLeast(0.dp)
    var sideScale = (remainEach / fullBaseW) * 0.96f
    sideScale = sideScale.coerceAtMost(centerScale).coerceAtLeast(0.01f)
    return centerScale to sideScale
}

@Composable
private fun StatusCluster(
    timeText: String,
    batteryPercent: Int,
    isCharging: Boolean,
    networkStatus: NetworkStatus,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = timeText,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isCharging) {
                LightningBoltIcon(
                    color = Color.White,
                    modifier = Modifier
                        .size(12.dp)
                        .semantics { contentDescription = "charging" },
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(
                text = "$batteryPercent%",
                color = Color.White,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(2.dp))
        NetworkSignalIcon(networkStatus)
    }
}

@Composable
private fun NetworkSignalIcon(status: NetworkStatus) {
    val level = status.level.coerceIn(0, 3)
    when (status.kind) {
        NetworkKind.WIFI -> WifiLevelIcon(
            level = level,
            color = Color.White,
            modifier = Modifier
                .size(16.dp)
                .semantics { contentDescription = "Wi-Fi level $level" },
        )
        NetworkKind.CELLULAR -> CellularLevelIcon(
            level = level,
            color = Color.White,
            modifier = Modifier
                .size(16.dp)
                .semantics { contentDescription = "Mobile level $level" },
        )
        NetworkKind.NONE -> WifiOffIcon(
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier
                .size(16.dp)
                .semantics { contentDescription = "Offline" },
        )
    }
}

/** 4-stage Wi-Fi arcs (level 0..3). */
@Composable
private fun WifiLevelIcon(level: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.78f
        val stroke = Stroke(width = w * 0.12f, cap = StrokeCap.Round)
        // Dot (always on when connected)
        drawCircle(color, radius = w * 0.08f, center = Offset(cx, cy))
        // 3 arcs → with dot = 4 stages; light arcs up to level
        val radii = listOf(w * 0.28f, w * 0.48f, w * 0.68f)
        radii.forEachIndexed { i, r ->
            val on = level >= (i + 1)
            val c = if (on) color else color.copy(alpha = 0.22f)
            drawArc(
                color = c,
                startAngle = 220f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = stroke,
            )
        }
    }
}

/** 4-stage cellular bars (level 0..3). */
@Composable
private fun CellularLevelIcon(level: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barW = w * 0.16f
        val gap = w * 0.08f
        val heights = listOf(0.35f, 0.55f, 0.75f, 0.95f)
        val totalBarsW = barW * 4f + gap * 3f
        val startX = (w - totalBarsW) / 2f
        heights.forEachIndexed { i, frac ->
            val on = level >= i
            val c = if (on) color else color.copy(alpha = 0.22f)
            val bh = h * frac
            drawRoundRect(
                color = c,
                topLeft = Offset(startX + i * (barW + gap), h - bh),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(barW * 0.25f, barW * 0.25f),
            )
        }
    }
}

@Composable
private fun WifiOffIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.78f
        val stroke = Stroke(width = w * 0.12f, cap = StrokeCap.Round)
        drawCircle(color, radius = w * 0.08f, center = Offset(cx, cy))
        val r = w * 0.48f
        drawArc(
            color = color,
            startAngle = 220f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2f, r * 2f),
            style = stroke,
        )
        // Slash
        drawLine(
            color = color,
            start = Offset(w * 0.2f, h * 0.15f),
            end = Offset(w * 0.8f, h * 0.85f),
            strokeWidth = w * 0.12f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun LightningBoltIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val bolt = Path().apply {
            moveTo(w * 0.58f, 0f)
            lineTo(w * 0.18f, h * 0.55f)
            lineTo(w * 0.46f, h * 0.55f)
            lineTo(w * 0.38f, h)
            lineTo(w * 0.86f, h * 0.40f)
            lineTo(w * 0.52f, h * 0.40f)
            close()
        }
        drawPath(bolt, color)
    }
}
