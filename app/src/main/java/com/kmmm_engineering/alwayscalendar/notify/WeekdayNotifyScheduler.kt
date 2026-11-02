package com.kmmm_engineering.alwayscalendar.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.kmmm_engineering.alwayscalendar.AlwaysCalendarApp
import com.kmmm_engineering.alwayscalendar.data.DayColorOptions
import com.kmmm_engineering.alwayscalendar.data.UserSettings
import com.kmmm_engineering.alwayscalendar.discord.DiscordNotifier
import com.kmmm_engineering.alwayscalendar.holiday.mergeHolidayDates
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * その月最初の平日 / 最後の平日の Discord 通知。
 *
 * One AlarmManager alarm per kind (process death safe). [run] is the single entry:
 * it sends what is due today (same-day catch-up), then arms the next target.
 * Called from app start, settings/holiday-data changes, the alarm itself, and
 * BOOT_COMPLETED / time / timezone / date changes ([WeekdayNotifyReceiver]).
 *
 * Alarm: [AlarmManager.setExactAndAllowWhileIdle] when exact alarms are allowed
 * (Android 12+: SCHEDULE_EXACT_ALARM, guided from Settings). Otherwise
 * [AlarmManager.setAndAllowWhileIdle] (may be late; still sent if same day).
 * setAlarmClock is not used: it would show an alarm icon / "next alarm" for a
 * once-a-month Discord post.
 */
object WeekdayNotifyScheduler {
    private const val TAG = "WeekdayNotify"
    const val ACTION_FIRE = "com.kmmm_engineering.alwayscalendar.action.WEEKDAY_NOTIFY"
    const val EXTRA_KIND = "kind"
    private const val RC_FIRST = 52001
    private const val RC_LAST = 52002
    private const val RETRY_MS = 5 * 60_000L
    private const val HOLIDAY_FETCH_TIMEOUT_MS = 20_000L

    private val mutex = Mutex()

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    suspend fun run(context: Context, allowNetwork: Boolean = true) = mutex.withLock {
        val appCtx = context.applicationContext
        val app = appCtx as? AlwaysCalendarApp ?: return@withLock
        val settings = app.settingsRepository.getSettingsOnce()
        if (!settings.notifyFirstWeekday && !settings.notifyLastWeekday) {
            cancel(appCtx, WeekdayNotifyKind.FIRST)
            cancel(appCtx, WeekdayNotifyKind.LAST)
            return@withLock
        }

        val holidays = app.holidayRepository
        holidays.loadCached()
        if (!settings.holidaysAsWhite && allowNetwork) {
            val y = LocalDate.now().year
            if (!holidays.hasYearData(y) || !holidays.hasYearData(y + 1)) {
                withTimeoutOrNull(HOLIDAY_FETCH_TIMEOUT_MS) {
                    holidays.ensureYearsAround(y, settings.showRokuyo)
                }
            }
        }
        val holidayDates: Set<String> = if (settings.holidaysAsWhite) emptySet()
        else mergeHolidayDates(holidays.holidays.value, holidays.koyomi.days.value, settings.showRokuyo)
        val isApiHoliday: (LocalDate) -> Boolean = { d ->
            holidayDates.contains("%04d-%02d-%02d".format(d.year, d.monthValue, d.dayOfMonth))
        }
        val options = DayColorOptions.from(settings)

        handleKind(appCtx, app, settings, WeekdayNotifyKind.FIRST, isApiHoliday, options)
        handleKind(appCtx, app, settings, WeekdayNotifyKind.LAST, isApiHoliday, options)
    }

    private suspend fun handleKind(
        ctx: Context,
        app: AlwaysCalendarApp,
        settings: UserSettings,
        kind: WeekdayNotifyKind,
        isApiHoliday: (LocalDate) -> Boolean,
        options: DayColorOptions,
    ) {
        val enabled = if (kind == WeekdayNotifyKind.FIRST) settings.notifyFirstWeekday else settings.notifyLastWeekday
        if (!enabled) {
            cancel(ctx, kind)
            return
        }
        val minutes = if (kind == WeekdayNotifyKind.FIRST) {
            settings.notifyFirstWeekdayMinutes
        } else {
            settings.notifyLastWeekdayMinutes
        }
        val time = LocalTime.of(minutes / 60, minutes % 60)
        val repo = app.settingsRepository
        val first = kind == WeekdayNotifyKind.FIRST

        var plan = WeekdayNotifyPlanner.plan(
            kind, LocalDateTime.now(), time, repo.weekdayNotifySentMonth(first), isApiHoliday, options,
        )
        if (plan is WeekdayNotifyPlanner.Plan.SendNow) {
            val target = plan.target
            val url = settings.discordWebhookUrl.trim()
            if (url.isEmpty()) {
                // Webhook empty → do not send and do not mark. Arm next month as if this one
                // were done; a URL entered later today re-runs via the settings collector.
                Log.i(TAG, "$kind due but webhook empty")
                plan = WeekdayNotifyPlanner.plan(
                    kind, LocalDateTime.now(), time,
                    WeekdayNotifyPlanner.monthKey(YearMonth.from(target)), isApiHoliday, options,
                )
            } else {
                val ok = DiscordNotifier.send(url, WeekdayNotifyPlanner.message(kind, target))
                if (ok) {
                    repo.markWeekdayNotifySent(first, WeekdayNotifyPlanner.monthKey(YearMonth.from(target)))
                    plan = WeekdayNotifyPlanner.plan(
                        kind, LocalDateTime.now(), time, repo.weekdayNotifySentMonth(first), isApiHoliday, options,
                    )
                } else {
                    Log.w(TAG, "$kind send failed; retry in 5 min (same day only)")
                    scheduleRetry(ctx, kind)
                    return
                }
            }
        }
        when (val p = plan) {
            is WeekdayNotifyPlanner.Plan.ScheduleAt -> {
                val ms = p.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                setAlarm(ctx, kind, ms)
            }
            else -> cancel(ctx, kind)
        }
    }

    /** Retry later today (planner refuses past days, so it naturally stops at midnight). */
    private fun scheduleRetry(ctx: Context, kind: WeekdayNotifyKind) {
        val now = System.currentTimeMillis()
        val endOfDay = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val at = now + RETRY_MS
        if (at < endOfDay) setAlarm(ctx, kind, at) else cancel(ctx, kind)
    }

    private fun setAlarm(ctx: Context, kind: WeekdayNotifyKind, atMillis: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(ctx, kind)
        try {
            if (canScheduleExact(ctx)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "exact alarm denied; inexact fallback", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun cancel(ctx: Context, kind: WeekdayNotifyKind) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(ctx, kind))
    }

    private fun pendingIntent(ctx: Context, kind: WeekdayNotifyKind): PendingIntent {
        val intent = Intent(ctx, WeekdayNotifyReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_KIND, kind.name)
        val rc = if (kind == WeekdayNotifyKind.FIRST) RC_FIRST else RC_LAST
        return PendingIntent.getBroadcast(
            ctx, rc, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
