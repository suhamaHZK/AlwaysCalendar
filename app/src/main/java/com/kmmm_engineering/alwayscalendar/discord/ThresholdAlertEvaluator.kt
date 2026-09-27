package com.kmmm_engineering.alwayscalendar.discord

import android.content.Context
import com.kmmm_engineering.alwayscalendar.AlwaysCalendarApp
import com.kmmm_engineering.alwayscalendar.R
import com.kmmm_engineering.alwayscalendar.data.UserSettings

object ThresholdAlertEvaluator {

    suspend fun evaluateAndNotify(
        context: Context,
        percent: Int,
        isCharging: Boolean,
        settings: UserSettings? = null,
    ) {
        val appCtx = context.applicationContext
        val app = appCtx as? AlwaysCalendarApp ?: return
        val repo = app.settingsRepository
        val s = settings ?: repo.getSettingsOnce()

        val tracker = ThresholdFireTracker()
        tracker.restoreKeepingSeed(repo.loadThresholdAlertSnapshot())
        val kind = tracker.check(
            percent = percent,
            isCharging = isCharging,
            dischargeThreshold = s.discordDischargeThreshold,
            chargeThreshold = s.discordChargeThreshold,
        )
        repo.saveThresholdAlertSnapshot(tracker.snapshot())

        if (kind == null || s.discordWebhookUrl.isBlank()) return

        val msg = when (kind) {
            "discharge" -> appCtx.getString(R.string.discord_discharge_msg, percent)
            else -> appCtx.getString(R.string.discord_charge_msg, percent)
        }
        DiscordNotifier.send(s.discordWebhookUrl, msg)
    }
}
