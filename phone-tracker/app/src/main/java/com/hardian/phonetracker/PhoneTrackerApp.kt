package com.hardian.phonetracker

import android.app.Application
import com.hardian.phonetracker.data.AppRepository
import org.osmdroid.config.Configuration

class PhoneTrackerApp : Application() {
    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this)
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(
            this,
            getSharedPreferences("osmdroid", MODE_PRIVATE),
        )
    }
}
