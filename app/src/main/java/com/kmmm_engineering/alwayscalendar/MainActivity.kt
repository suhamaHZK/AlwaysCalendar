package com.kmmm_engineering.alwayscalendar

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmmm_engineering.alwayscalendar.battery.BatteryMonitor
import com.kmmm_engineering.alwayscalendar.battery.BatteryStatus
import com.kmmm_engineering.alwayscalendar.data.LandscapeMode
import com.kmmm_engineering.alwayscalendar.data.UserSettings
import com.kmmm_engineering.alwayscalendar.discord.ThresholdAlertEvaluator
import com.kmmm_engineering.alwayscalendar.network.ConnectivityMonitor
import com.kmmm_engineering.alwayscalendar.network.NetworkStatus
import com.kmmm_engineering.alwayscalendar.ui.CalendarScreen
import com.kmmm_engineering.alwayscalendar.ui.SettingsScreen
import com.kmmm_engineering.alwayscalendar.ui.theme.AlwaysCalendarTheme
import kotlinx.coroutines.delay
import java.time.YearMonth

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        val app = application as AlwaysCalendarApp
        val repo = app.settingsRepository
        val holidays = app.holidayRepository

        setContent {
            val settingsState = repo.settingsFlow.collectAsStateWithLifecycle(
                initialValue = null,
            )
            val settings = settingsState.value ?: UserSettings()

            val batteryFlow = remember { BatteryMonitor.observe(applicationContext) }
            val batteryState = batteryFlow.collectAsStateWithLifecycle(initialValue = null)
            val battery = batteryState.value ?: BatteryStatus(0, false, false)

            val networkFlow = remember { ConnectivityMonitor.observe(applicationContext) }
            val networkState = networkFlow.collectAsStateWithLifecycle(
                initialValue = NetworkStatus(com.kmmm_engineering.alwayscalendar.network.NetworkKind.NONE),
            )
            val network = networkState.value

            val holidayMap = holidays.holidays.collectAsStateWithLifecycle().value
            val holidayDates = remember(holidayMap) { holidayMap.keys }

            LaunchedEffect(Unit) {
                holidays.loadCached()
                val year = YearMonth.now().year
                val ok = holidays.ensureYearsAround(year)
                if (!ok) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.holiday_fetch_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }

            LaunchedEffect(settings.landscapeMode) {
                requestedOrientation = when (settings.landscapeMode) {
                    LandscapeMode.OFF -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    LandscapeMode.RIGHT -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    LandscapeMode.LEFT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                }
            }

            LaunchedEffect(settings.allowAutoSleep) {
                if (settings.allowAutoSleep) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            var brightUntil by remember { mutableStateOf(0L) }
            var lastInteractionAt by remember { mutableStateOf(System.currentTimeMillis()) }
            var settingsBrightnessPreview by remember { mutableStateOf<Float?>(null) }
            var showSettings by rememberSaveable { mutableStateOf(false) }
            var unlockSliderVisible by remember { mutableStateOf(false) }
            var activityResumed by remember { mutableStateOf(true) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> activityResumed = true
                        Lifecycle.Event.ON_PAUSE -> activityResumed = false
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            fun markInteraction(brightenMs: Long = 0L) {
                val now = System.currentTimeMillis()
                lastInteractionAt = now
                if (brightenMs > 0L) {
                    brightUntil = now + brightenMs
                }
            }

            LaunchedEffect(
                showSettings,
                settingsBrightnessPreview,
                settings.idleBrightness,
                brightUntil,
                unlockSliderVisible,
            ) {
                when {
                    showSettings -> {
                        val preview = settingsBrightnessPreview
                        if (preview != null) {
                            applyWindowBrightness(preview)
                        } else {
                            applyWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                        }
                    }
                    unlockSliderVisible -> {
                        applyWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                    }
                    else -> {
                        val now = System.currentTimeMillis()
                        if (brightUntil > now) {
                            applyWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                            val remaining = brightUntil - now
                            delay(remaining)
                            if (!showSettings && !unlockSliderVisible) {
                                applyWindowBrightness(settings.idleBrightness)
                            }
                        } else {
                            applyWindowBrightness(settings.idleBrightness)
                        }
                    }
                }
            }

            LaunchedEffect(activityResumed, settings.idleBrightness) {
                if (!activityResumed) return@LaunchedEffect
                while (true) {
                    if (!showSettings && !unlockSliderVisible) {
                        val now = System.currentTimeMillis()
                        val idleDeadline = maxOf(
                            lastInteractionAt + UserSettings.BRIGHTEN_DURATION_MS,
                            brightUntil,
                        )
                        if (now >= idleDeadline) {
                            val current = window.attributes.screenBrightness
                            val idle = settings.idleBrightness
                            val needsDim =
                                current == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE ||
                                    current < 0f ||
                                    current > idle + 0.005f
                            if (needsDim) {
                                applyWindowBrightness(idle)
                            }
                        }
                    }
                    delay(1_000L)
                }
            }

            // Exit on unplug — falling edge only
            var previousPlugged by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(battery.isPlugged, settings.exitOnUnplug) {
                val plugged = battery.isPlugged
                val prev = previousPlugged
                previousPlugged = plugged
                if (settings.exitOnUnplug && prev == true && !plugged) {
                    finish()
                }
            }

            // Discord thresholds
            LaunchedEffect(
                batteryState.value,
                settingsState.value,
                battery.percent,
                battery.isCharging,
                settings.discordDischargeThreshold,
                settings.discordChargeThreshold,
                settings.discordWebhookUrl,
            ) {
                if (settingsState.value == null) return@LaunchedEffect
                if (batteryState.value == null) return@LaunchedEffect
                ThresholdAlertEvaluator.evaluateAndNotify(
                    context = applicationContext,
                    percent = battery.percent,
                    isCharging = battery.isCharging,
                    settings = settings,
                )
            }

            // Observe system status-bar visibility (incl. transient swipe bars).
            // Some devices leave Compose areStatusBarsVisible / statusBars height at
            // false/0 during BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE; WindowInsetsCompat
            // isVisible is the reliable path recommended by the immersive guide.
            // Spy view: do not replace Compose's OnApplyWindowInsetsListener on content.
            var statusBarsVisibleCompat by remember { mutableStateOf(false) }
            DisposableEffect(Unit) {
                val content = findViewById<ViewGroup>(android.R.id.content)
                val spy = View(this@MainActivity).apply {
                    layoutParams = ViewGroup.LayoutParams(0, 0)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                content.addView(spy)
                ViewCompat.setOnApplyWindowInsetsListener(spy) { _, insets ->
                    statusBarsVisibleCompat =
                        insets.isVisible(WindowInsetsCompat.Type.statusBars())
                    insets
                }
                ViewCompat.getRootWindowInsets(content)?.let { root ->
                    statusBarsVisibleCompat =
                        root.isVisible(WindowInsetsCompat.Type.statusBars())
                }
                ViewCompat.requestApplyInsets(spy)
                onDispose {
                    ViewCompat.setOnApplyWindowInsetsListener(spy, null)
                    content.removeView(spy)
                }
            }

            AlwaysCalendarTheme {
                val closeSettings: () -> Unit = {
                    settingsBrightnessPreview = null
                    showSettings = false
                    markInteraction()
                    hideSystemBars()
                }
                BackHandler(enabled = showSettings) {
                    closeSettings()
                }

                when {
                    showSettings -> {
                        LaunchedEffect(Unit) {
                            if (!settings.firstRunHintSeen) {
                                repo.markFirstRunHintSeen()
                            }
                        }
                        SettingsScreen(
                            settings = settings,
                            repository = repo,
                            onBack = closeSettings,
                            onIdleBrightnessPreview = { preview ->
                                settingsBrightnessPreview = preview
                            },
                        )
                    }
                    else -> {
                        CalendarScreen(
                            settings = settings,
                            batteryPercent = battery.percent,
                            isCharging = battery.isCharging,
                            networkStatus = network,
                            holidayDates = holidayDates,
                            statusBarsVisibleCompat = statusBarsVisibleCompat,
                            onEnsureMonth = { ym ->
                                holidays.ensureMonth(ym.year, ym.monthValue)
                            },
                            onHolidayFetchFailed = {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.holiday_fetch_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onSingleTap = {
                                markInteraction(brightenMs = UserSettings.BRIGHTEN_DURATION_MS)
                            },
                            onOpenSettings = {
                                markInteraction()
                                unlockSliderVisible = false
                                settingsBrightnessPreview = null
                                showSettings = true
                            },
                            onUnlockSliderVisibilityChange = { visible ->
                                if (visible) markInteraction()
                                unlockSliderVisible = visible
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onPause() {
        applyWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        super.onPause()
    }

    private fun applyWindowBrightness(value: Float) {
        val lp = window.attributes
        lp.screenBrightness = value
        window.attributes = lp
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
