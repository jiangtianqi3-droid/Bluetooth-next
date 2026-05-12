package com.example.bluetoothusage

import android.app.Application
import com.example.bluetoothusage.data.AppDatabase
import com.example.bluetoothusage.repository.SettingsRepository
import com.example.bluetoothusage.repository.UsageRepository
import com.example.bluetoothusage.service.MonitorStartupReceiver

class BluetoothUsageApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val usageRepository by lazy { UsageRepository(database.usageDao()) }
    val settingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        MonitorStartupReceiver.scheduleNextCheck(this)
    }
}
