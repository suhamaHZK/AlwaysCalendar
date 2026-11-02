package com.kmmm_engineering.alwayscalendar.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Alarm fire + BOOT_COMPLETED / MY_PACKAGE_REPLACED / TIME_SET / TIMEZONE_CHANGED /
 * DATE_CHANGED / exact-alarm permission change → [WeekdayNotifyScheduler.run].
 * The planner re-checks "today is the target" at fire time, so a stale alarm never
 * sends on a wrong day.
 */
class WeekdayNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(50_000L) { WeekdayNotifyScheduler.run(context) }
            } catch (e: Exception) {
                Log.w("WeekdayNotify", "run failed (${intent?.action})", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
