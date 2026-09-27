package com.kmmm_engineering.alwayscalendar

import android.app.Application
import com.kmmm_engineering.alwayscalendar.data.SettingsRepository
import com.kmmm_engineering.alwayscalendar.holiday.HolidayRepository

class AlwaysCalendarApp : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var holidayRepository: HolidayRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        holidayRepository = HolidayRepository(this)
    }
}
