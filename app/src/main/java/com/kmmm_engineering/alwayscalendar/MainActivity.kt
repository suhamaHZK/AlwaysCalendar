package com.kmmm_engineering.alwayscalendar

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
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
import com.kmmm_engineering.alwayscalendar.data.LandscapeMode
import com.kmmm_engineering.alwayscalendar.data.UserSettings
import com.kmmm_engineering.alwayscalendar.ui.CalendarScreen
import com.kmmm_engineering.alwayscalendar.ui.SettingsScreen
import com.kmmm_engineering.alwayscalendar.ui.theme.AlwaysCalendarTheme
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        val app = application as AlwaysCalendarApp
        val repo = app.settingsRepository

        setContent {
            val settingsState = repo.settingsFlow.collectAsStateWithLifecycle(
                initialValue = null,
            )
            val settings = settingsState.value ?: UserSettings()

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
                    else -> {
                        val now = System.currentTimeMillis()
                        if (brightUntil > now) {
                            applyWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                            val remaining = brightUntil - now
                            delay(remaining)
                            if (!showSettings) {
                                applyWindowBrightness(settings.idleBrightness)
                            }
                        } else {
                            applyWindowBrightness(settings.idleBrightness)
                        }
                    }
                }
            }

            // Periodic idle-dim; respects BRIGHTEN_DURATION_MS tap-to-brighten.
            LaunchedEffect(activityResumed, settings.idleBrightness) {
                if (!activityResumed) return@LaunchedEffect
                while (true) {
                    if (!showSettings) {
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
                            onSingleTap = {
                                markInteraction(brightenMs = UserSettings.BRIGHTEN_DURATION_MS)
                            },
                            onOpenSettings = {
                                markInteraction()
                                settingsBrightnessPreview = null
                                showSettings = true
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
