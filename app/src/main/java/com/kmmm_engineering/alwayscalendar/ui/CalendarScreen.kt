package com.kmmm_engineering.alwayscalendar.ui

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.kmmm_engineering.alwayscalendar.network.NetworkKind
import com.kmmm_engineering.alwayscalendar.network.NetworkStatus
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

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

    // Align clock updates to next minute
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            val rem = nowMs % 60_000L
            val wait = if (rem == 0L) 60_000L else 60_000L - rem
            delay(wait.coerceAtLeast(1L))
        }
    }

    // OLED pixel shift every ~3 minutes
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

    // Fetch holidays when center month changes
    LaunchedEffect(centerMonth) {
        val ok = onEnsureMonth(centerMonth)
        if (!ok) onHolidayFetchFailed()
    }

    val isHoliday: (LocalDate) -> Boolean = remember(holidayDates) {
        { d -> holidayDates.contains("%04d-%02d-%02d".format(d.year, d.monthValue, d.dayOfMonth)) }
    }

    fun shiftMonth(delta: Long) {
        if (settings.misoperationLock) return
        centerMonth = centerMonth.plusMonths(delta)
    }

    val dragAccum = remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 64.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(settings.misoperationLock, isLandscape) {
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
            }
            .then(
                if (settings.misoperationLock) {
                    Modifier
                } else if (isLandscape) {
                    Modifier.pointerInput(centerMonth) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val a = dragAccum.floatValue
                                dragAccum.floatValue = 0f
                                when {
                                    a <= -swipeThresholdPx -> shiftMonth(1)
                                    a >= swipeThresholdPx -> shiftMonth(-1)
                                }
                            },
                            onDragCancel = { dragAccum.floatValue = 0f },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                dragAccum.floatValue += amount
                            },
                        )
                    }
                } else {
                    Modifier.pointerInput(centerMonth) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                val a = dragAccum.floatValue
                                dragAccum.floatValue = 0f
                                when {
                                    // swipe up → next month; swipe down → previous
                                    a <= -swipeThresholdPx -> shiftMonth(1)
                                    a >= swipeThresholdPx -> shiftMonth(-1)
                                }
                            },
                            onDragCancel = { dragAccum.floatValue = 0f },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                dragAccum.floatValue += amount
                            },
                        )
                    }
                },
            ),
    ) {
        // Status (top-left), outside OLED shift so it stays readable near notch
        StatusCluster(
            timeText = wallTime,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            networkStatus = networkStatus,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .offset { IntOffset(shiftX, shiftY) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val aspect = maxHeight / maxWidth
            val sideScale = sideMonthScale(aspect)
            val prev = centerMonth.plusMonths(-1)
            val next = centerMonth.plusMonths(1)

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SideMonth(
                        yearMonth = prev,
                        weekStart = settings.weekStart,
                        today = today,
                        isHoliday = isHoliday,
                        scale = sideScale,
                        enabled = !settings.misoperationLock,
                        onClick = { shiftMonth(-1) },
                        modifier = Modifier
                            .weight(0.28f)
                            .fillMaxHeight(0.85f),
                    )
                    MonthCalendar(
                        yearMonth = centerMonth,
                        weekStart = settings.weekStart,
                        today = today,
                        isHoliday = isHoliday,
                        scale = 1f,
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxHeight(),
                    )
                    SideMonth(
                        yearMonth = next,
                        weekStart = settings.weekStart,
                        today = today,
                        isHoliday = isHoliday,
                        scale = sideScale,
                        enabled = !settings.misoperationLock,
                        onClick = { shiftMonth(1) },
                        modifier = Modifier
                            .weight(0.28f)
                            .fillMaxHeight(0.85f),
                    )
                }
            } else {
                // Portrait: prev (small) / center (large) / next (small)
                val sideWeight = sideScale
                val centerWeight = 1f
                val total = sideWeight * 2f + centerWeight
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    SideMonth(
                        yearMonth = prev,
                        weekStart = settings.weekStart,
                        today = today,
                        isHoliday = isHoliday,
                        scale = sideScale,
                        enabled = !settings.misoperationLock,
                        onClick = { shiftMonth(-1) },
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .weight(sideWeight / total),
                    )
                    MonthCalendar(
                        yearMonth = centerMonth,
                        weekStart = settings.weekStart,
                        today = today,
                        isHoliday = isHoliday,
                        scale = 1f,
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .weight(centerWeight / total),
                    )
                    SideMonth(
                        yearMonth = next,
                        weekStart = settings.weekStart,
                        today = today,
                        isHoliday = isHoliday,
                        scale = sideScale,
                        enabled = !settings.misoperationLock,
                        onClick = { shiftMonth(1) },
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .weight(sideWeight / total),
                    )
                }
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
                    .padding(horizontal = 32.dp, vertical = 40.dp)
                    .fillMaxWidth(if (isLandscape) 0.5f else 1f),
            )
        }
    }
}

@Composable
private fun SideMonth(
    yearMonth: YearMonth,
    weekStart: com.kmmm_engineering.alwayscalendar.data.WeekStart,
    today: LocalDate,
    isHoliday: (LocalDate) -> Boolean,
    scale: Float,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .then(
                if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        MonthCalendar(
            yearMonth = yearMonth,
            weekStart = weekStart,
            today = today,
            isHoliday = isHoliday,
            scale = scale,
            modifier = Modifier.fillMaxWidth(),
        )
    }
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
        when (networkStatus.kind) {
            NetworkKind.WIFI -> Icon(
                imageVector = Icons.Filled.Wifi,
                contentDescription = "Wi-Fi",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            NetworkKind.CELLULAR -> Icon(
                imageVector = Icons.Filled.SignalCellularAlt,
                contentDescription = "Mobile",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            NetworkKind.NONE -> Icon(
                imageVector = Icons.Filled.WifiOff,
                contentDescription = "Offline",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp),
            )
        }
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
