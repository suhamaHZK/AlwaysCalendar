package com.kmmm_engineering.alwayscalendar.data

enum class LandscapeMode { OFF, RIGHT, LEFT }

/** Week starts on Sunday (default) or Monday. */
enum class WeekStart { SUNDAY, MONDAY }

/**
 * App preferences (DataStore-backed).
 *
 * Discord threshold: -1 = OFF, otherwise 5..100 step 5.
 * [firstRunHintSeen] is true after Settings has been opened at least once
 * (DataStore key: first_run_hint_seen).
 *
 * Temporary status-bar scrim tunables (debug013/014):
 * - [debugScrimOpacityPercent] 0..100 (%), default 85 → alpha = value/100f
 * - [debugScrimBlurDp] 0..50 (dp), default 0
 * - [debugScrimAlwaysShow] when true, draw scrim even if status bars appear hidden
 */
data class UserSettings(
    val idleBrightness: Float = 0.10f,
    val discordWebhookUrl: String = "",
    val discordDischargeThreshold: Int = -1,
    val discordChargeThreshold: Int = -1,
    val landscapeMode: LandscapeMode = LandscapeMode.OFF,
    val exitOnUnplug: Boolean = false,
    val allowAutoSleep: Boolean = false,
    val firstRunHintSeen: Boolean = false,
    val weekStart: WeekStart = WeekStart.SUNDAY,
    val misoperationLock: Boolean = false,
    val debugScrimOpacityPercent: Int = DEBUG_SCRIM_OPACITY_DEFAULT,
    val debugScrimBlurDp: Int = DEBUG_SCRIM_BLUR_DEFAULT,
    val debugScrimAlwaysShow: Boolean = false,
) {
    companion object {
        /** Tap-to-brighten duration on the calendar screen (Always Calendar: 60s). */
        const val BRIGHTEN_DURATION_MS: Long = 60_000L

        const val IDLE_BRIGHTNESS_MIN = 0.01f
        const val IDLE_BRIGHTNESS_MAX = 0.35f
        const val IDLE_BRIGHTNESS_DEFAULT = 0.10f

        /** debug013: status-bar scrim opacity (%). */
        const val DEBUG_SCRIM_OPACITY_MIN = 0
        const val DEBUG_SCRIM_OPACITY_MAX = 100
        const val DEBUG_SCRIM_OPACITY_DEFAULT = 85

        /** debug013: status-bar scrim blur radius (dp). */
        const val DEBUG_SCRIM_BLUR_MIN = 0
        const val DEBUG_SCRIM_BLUR_MAX = 50
        const val DEBUG_SCRIM_BLUR_DEFAULT = 0

        fun clampScrimOpacity(v: Int): Int =
            v.coerceIn(DEBUG_SCRIM_OPACITY_MIN, DEBUG_SCRIM_OPACITY_MAX)

        fun clampScrimBlur(v: Int): Int =
            v.coerceIn(DEBUG_SCRIM_BLUR_MIN, DEBUG_SCRIM_BLUR_MAX)
    }
}
