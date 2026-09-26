package com.kmmm_engineering.alwayscalendar

import android.app.Application
import com.kmmm_engineering.alwayscalendar.data.SettingsRepository

class AlwaysCalendarApp : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
    }
}
