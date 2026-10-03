package com.kmmm_engineering.alwayscalendar.discord

import android.content.Context
import com.kmmm_engineering.alwayscalendar.AlwaysCalendarApp
import com.kmmm_engineering.alwayscalendar.R
import com.kmmm_engineering.alwayscalendar.data.UserSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ThresholdAlertEvaluator {

    private val evalMutex = Mutex()

    /**
     * False until this process has completed one evaluation. The first call uses
     * [ThresholdFireTracker.restore] so [ThresholdFireTracker.check] seeds from the live
     * battery and stays quiet (a cross while the process was dead must not notify).
     * Later calls in the same process use [ThresholdFireTracker.restoreKeepingSeed]
     * and notify only on a real cross. Charge and discharge share this gate.
     * Threshold edits still re-arm inside [ThresholdFireTracker.check]; this flag does
     * not reset on settings changes.
     */
    private var evaluatedInThisProcess = false

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

        val kind = evalMutex.withLock {
            val tracker = ThresholdFireTracker()
            val snapshot = repo.loadThresholdAlertSnapshot()
            if (!evaluatedInThisProcess) {
                tracker.restore(snapshot)
            } else {
                tracker.restoreKeepingSeed(snapshot)
            }
            val fired = tracker.check(
                percent = percent,
                isCharging = isCharging,
                dischargeThreshold = s.discordDischargeThreshold,
                chargeThreshold = s.discordChargeThreshold,
            )
            repo.saveThresholdAlertSnapshot(tracker.snapshot())
            evaluatedInThisProcess = true
            fired
        }

        if (kind == null || s.discordWebhookUrl.isBlank()) return

        val msg = when (kind) {
            "discharge" -> appCtx.getString(R.string.discord_discharge_msg, percent)
            else -> appCtx.getString(R.string.discord_charge_msg, percent)
        }
        DiscordNotifier.send(s.discordWebhookUrl, msg)
    }
}
