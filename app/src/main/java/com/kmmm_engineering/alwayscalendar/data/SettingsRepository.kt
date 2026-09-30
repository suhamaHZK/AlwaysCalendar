package com.kmmm_engineering.alwayscalendar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kmmm_engineering.alwayscalendar.discord.ThresholdAlertSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "always_calendar_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val idleBrightness = floatPreferencesKey("idle_brightness")
        val discordWebhookUrl = stringPreferencesKey("discord_webhook_url")
        val discordDischarge = intPreferencesKey("discord_discharge")
        val discordCharge = intPreferencesKey("discord_charge")
        val landscape = stringPreferencesKey("landscape")
        val exitOnUnplug = booleanPreferencesKey("exit_on_unplug")
        val allowAutoSleep = booleanPreferencesKey("allow_auto_sleep")
        val firstRunHintSeen = booleanPreferencesKey("first_run_hint_seen")
        val weekStart = stringPreferencesKey("week_start")
        val misoperationLock = booleanPreferencesKey("misoperation_lock")
        val fullscreenEnabled = booleanPreferencesKey("fullscreen_enabled")
        val holidaysAsWhite = booleanPreferencesKey("holidays_as_white")
        val saturdayAsWhite = booleanPreferencesKey("saturday_as_white")
        val sundayAsWhite = booleanPreferencesKey("sunday_as_white")
        val customHolidays = stringPreferencesKey("custom_holidays_json")
        val customWeekdays = stringPreferencesKey("custom_weekdays_json")
        // Legacy debug013/014/017 keys — intentionally unread (discard on migrate).
        // debug_scrim_opacity / debug_scrim_blur / debug_scrim_always_show
        // Discord threshold-cross persistence
        val alertLastPercent = intPreferencesKey("alert_last_percent")
        val alertLastCharging = booleanPreferencesKey("alert_last_charging")
        val alertDischargeArmed = booleanPreferencesKey("alert_discharge_armed")
        val alertChargeArmed = booleanPreferencesKey("alert_charge_armed")
        val alertLastDischargeThreshold = intPreferencesKey("alert_last_discharge_threshold")
        val alertLastChargeThreshold = intPreferencesKey("alert_last_charge_threshold")
        val alertSeeded = booleanPreferencesKey("alert_seeded")
    }

    private fun prefsToSettings(prefs: Preferences): UserSettings = UserSettings(
        idleBrightness = prefs[Keys.idleBrightness] ?: UserSettings.IDLE_BRIGHTNESS_DEFAULT,
        discordWebhookUrl = prefs[Keys.discordWebhookUrl] ?: "",
        discordDischargeThreshold = prefs[Keys.discordDischarge] ?: -1,
        discordChargeThreshold = prefs[Keys.discordCharge] ?: -1,
        landscapeMode = prefs[Keys.landscape]?.let {
            runCatching { LandscapeMode.valueOf(it) }.getOrDefault(LandscapeMode.OFF)
        } ?: LandscapeMode.OFF,
        exitOnUnplug = prefs[Keys.exitOnUnplug] ?: false,
        allowAutoSleep = prefs[Keys.allowAutoSleep] ?: false,
        firstRunHintSeen = prefs[Keys.firstRunHintSeen] ?: false,
        weekStart = prefs[Keys.weekStart]?.let {
            runCatching { WeekStart.valueOf(it) }.getOrDefault(WeekStart.SUNDAY)
        } ?: WeekStart.SUNDAY,
        misoperationLock = prefs[Keys.misoperationLock] ?: false,
        fullscreenEnabled = prefs[Keys.fullscreenEnabled] ?: false,
        holidaysAsWhite = prefs[Keys.holidaysAsWhite] ?: false,
        saturdayAsWhite = prefs[Keys.saturdayAsWhite] ?: false,
        sundayAsWhite = prefs[Keys.sundayAsWhite] ?: false,
        customHolidays = CustomDayRule.decodeList(prefs[Keys.customHolidays]),
        customWeekdays = CustomDayRule.decodeList(prefs[Keys.customWeekdays]),
    )

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        prefsToSettings(prefs)
    }

    suspend fun getSettingsOnce(): UserSettings = settingsFlow.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.dataStore.edit { prefs ->
            val current = prefsToSettings(prefs)
            val next = transform(current)
            prefs[Keys.idleBrightness] = next.idleBrightness.coerceIn(
                UserSettings.IDLE_BRIGHTNESS_MIN,
                UserSettings.IDLE_BRIGHTNESS_MAX,
            )
            prefs[Keys.discordWebhookUrl] = next.discordWebhookUrl
            prefs[Keys.discordDischarge] = next.discordDischargeThreshold
            prefs[Keys.discordCharge] = next.discordChargeThreshold
            prefs[Keys.landscape] = next.landscapeMode.name
            prefs[Keys.exitOnUnplug] = next.exitOnUnplug
            prefs[Keys.allowAutoSleep] = next.allowAutoSleep
            prefs[Keys.firstRunHintSeen] = next.firstRunHintSeen
            prefs[Keys.weekStart] = next.weekStart.name
            prefs[Keys.misoperationLock] = next.misoperationLock
            prefs[Keys.fullscreenEnabled] = next.fullscreenEnabled
            prefs[Keys.holidaysAsWhite] = next.holidaysAsWhite
            prefs[Keys.saturdayAsWhite] = next.saturdayAsWhite
            prefs[Keys.sundayAsWhite] = next.sundayAsWhite
            prefs[Keys.customHolidays] = CustomDayRule.encodeList(next.customHolidays)
            prefs[Keys.customWeekdays] = CustomDayRule.encodeList(next.customWeekdays)
            // Drop legacy debug scrim keys if present (read-discard migration).
            prefs.remove(booleanPreferencesKey("debug_scrim_always_show"))
            prefs.remove(intPreferencesKey("debug_scrim_opacity"))
            prefs.remove(intPreferencesKey("debug_scrim_blur"))
        }
    }

    suspend fun setIdleBrightness(v: Float) = update { it.copy(idleBrightness = v) }
    suspend fun setDiscordWebhookUrl(v: String) = update { it.copy(discordWebhookUrl = v) }
    suspend fun setDiscordDischarge(v: Int) = update { it.copy(discordDischargeThreshold = v) }
    suspend fun setDiscordCharge(v: Int) = update { it.copy(discordChargeThreshold = v) }
    suspend fun setLandscape(v: LandscapeMode) = update { it.copy(landscapeMode = v) }
    suspend fun setExitOnUnplug(v: Boolean) = update { it.copy(exitOnUnplug = v) }
    suspend fun setAllowAutoSleep(v: Boolean) = update { it.copy(allowAutoSleep = v) }
    suspend fun markFirstRunHintSeen() = update { it.copy(firstRunHintSeen = true) }
    suspend fun setWeekStart(v: WeekStart) = update { it.copy(weekStart = v) }
    suspend fun setMisoperationLock(v: Boolean) = update { it.copy(misoperationLock = v) }
    suspend fun setFullscreenEnabled(v: Boolean) = update { it.copy(fullscreenEnabled = v) }
    suspend fun setHolidaysAsWhite(v: Boolean) = update { it.copy(holidaysAsWhite = v) }
    suspend fun setSaturdayAsWhite(v: Boolean) = update { it.copy(saturdayAsWhite = v) }
    suspend fun setSundayAsWhite(v: Boolean) = update { it.copy(sundayAsWhite = v) }

    suspend fun addCustomHoliday(rule: CustomDayRule) = update {
        it.copy(customHolidays = it.customHolidays + rule)
    }
    suspend fun removeCustomHoliday(id: String) = update {
        it.copy(customHolidays = it.customHolidays.filterNot { r -> r.id == id })
    }
    suspend fun addCustomWeekday(rule: CustomDayRule) = update {
        it.copy(customWeekdays = it.customWeekdays + rule)
    }
    suspend fun removeCustomWeekday(id: String) = update {
        it.copy(customWeekdays = it.customWeekdays.filterNot { r -> r.id == id })
    }

    suspend fun appendCustomHolidays(rules: List<CustomDayRule>) = update {
        it.copy(customHolidays = it.customHolidays + rules)
    }
    suspend fun replaceCustomHolidays(rules: List<CustomDayRule>) = update {
        it.copy(customHolidays = rules)
    }
    suspend fun appendCustomWeekdays(rules: List<CustomDayRule>) = update {
        it.copy(customWeekdays = it.customWeekdays + rules)
    }
    suspend fun replaceCustomWeekdays(rules: List<CustomDayRule>) = update {
        it.copy(customWeekdays = rules)
    }

    suspend fun loadThresholdAlertSnapshot(): ThresholdAlertSnapshot {
        val prefs = context.dataStore.data.first()
        val seeded = prefs[Keys.alertSeeded] ?: false
        val lastPercent = prefs[Keys.alertLastPercent] ?: -1
        return ThresholdAlertSnapshot(
            lastPercent = lastPercent,
            lastCharging = prefs[Keys.alertLastCharging] ?: false,
            dischargeArmed = prefs[Keys.alertDischargeArmed] ?: true,
            chargeArmed = prefs[Keys.alertChargeArmed] ?: true,
            lastDischargeThreshold = prefs[Keys.alertLastDischargeThreshold] ?: -1,
            lastChargeThreshold = prefs[Keys.alertLastChargeThreshold] ?: -1,
            seeded = seeded && lastPercent >= 0,
        )
    }

    suspend fun saveThresholdAlertSnapshot(snapshot: ThresholdAlertSnapshot) {
        context.dataStore.edit { prefs ->
            prefs[Keys.alertLastPercent] = snapshot.lastPercent
            prefs[Keys.alertLastCharging] = snapshot.lastCharging
            prefs[Keys.alertDischargeArmed] = snapshot.dischargeArmed
            prefs[Keys.alertChargeArmed] = snapshot.chargeArmed
            prefs[Keys.alertLastDischargeThreshold] = snapshot.lastDischargeThreshold
            prefs[Keys.alertLastChargeThreshold] = snapshot.lastChargeThreshold
            prefs[Keys.alertSeeded] = snapshot.seeded
        }
    }
}
