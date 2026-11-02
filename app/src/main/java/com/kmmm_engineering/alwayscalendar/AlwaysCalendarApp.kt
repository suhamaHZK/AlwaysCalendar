package com.kmmm_engineering.alwayscalendar

import android.app.Application
import android.util.Log
import com.kmmm_engineering.alwayscalendar.data.SettingsRepository
import com.kmmm_engineering.alwayscalendar.holiday.HolidayRepository
import com.kmmm_engineering.alwayscalendar.notify.WeekdayNotifyScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AlwaysCalendarApp : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var holidayRepository: HolidayRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        holidayRepository = HolidayRepository(this)
        startWeekdayNotifyWatcher()
    }

    /**
     * 平日通知: re-plan on app start (same-day catch-up), on any setting that affects the
     * weekday judgement / time / webhook, and whenever holiday data arrives or changes.
     */
    private fun startWeekdayNotifyWatcher() {
        val relevant = settingsRepository.settingsFlow.map { s ->
            listOf(
                s.notifyFirstWeekday, s.notifyFirstWeekdayMinutes,
                s.notifyLastWeekday, s.notifyLastWeekdayMinutes,
                s.discordWebhookUrl.isNotBlank(),
                s.holidaysAsWhite, s.saturdayAsWhite, s.sundayAsWhite, s.showRokuyo,
                s.customHolidays, s.customWeekdays,
            )
        }.distinctUntilChanged()
        appScope.launch {
            combine(
                relevant,
                holidayRepository.holidays.map { it.size },
                holidayRepository.koyomi.days.map { it.size },
            ) { a, b, c -> Triple(a, b, c) }
                .distinctUntilChanged()
                .collectLatest {
                    delay(800L) // settle bursts (typing, startup loads)
                    try {
                        WeekdayNotifyScheduler.run(this@AlwaysCalendarApp)
                    } catch (e: Exception) {
                        Log.w("WeekdayNotify", "watcher run failed", e)
                    }
                }
        }
    }
}
