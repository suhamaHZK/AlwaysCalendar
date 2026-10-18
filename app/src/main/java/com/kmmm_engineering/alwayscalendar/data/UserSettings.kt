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
 * Status-bar scrim opacity is fixed at [SCRIM_OPACITY_PERCENT] (85%);
 * legacy debug opacity/blur/always-show keys are read-discarded.
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
    /** Immersive fullscreen (hide system bars). Default OFF. */
    val fullscreenEnabled: Boolean = false,
    /** 祝日を白で表示 — stop holiday API; ignore cache for color. */
    val holidaysAsWhite: Boolean = false,
    val saturdayAsWhite: Boolean = false,
    val sundayAsWhite: Boolean = false,
    /** 六曜を表示 (中央の月のみ). */
    val showRokuyo: Boolean = false,
    /** 祝日名を表示 (中央の月のみ). */
    val showHolidayName: Boolean = false,
    val customHolidays: List<CustomDayRule> = emptyList(),
    val customWeekdays: List<CustomDayRule> = emptyList(),
) {
    companion object {
        /** Tap-to-brighten duration on the calendar screen (Always Calendar: 60s). */
        const val BRIGHTEN_DURATION_MS: Long = 60_000L

        const val IDLE_BRIGHTNESS_MIN = 0.01f
        const val IDLE_BRIGHTNESS_MAX = 0.35f
        const val IDLE_BRIGHTNESS_DEFAULT = 0.10f

        /** Fixed status-bar scrim opacity (%). */
        const val SCRIM_OPACITY_PERCENT = 85
    }
}
