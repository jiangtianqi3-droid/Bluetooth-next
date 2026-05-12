package com.example.bluetoothusage.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.bluetoothusage.bluetooth.BluetoothDeviceChecker
import com.example.bluetoothusage.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MonitorStartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        scheduleNextCheck(appContext)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                restoreMonitorIfNeeded(appContext, intent.action)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_MAINTAIN_MONITOR = "com.example.bluetoothusage.action.MAINTAIN_MONITOR"
        private const val REQUEST_MONITOR_HEARTBEAT = 2201
        private const val HEARTBEAT_INTERVAL_MILLIS = 15L * 60L * 1_000L

        fun scheduleNextCheck(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val intent = Intent(context, MonitorStartupReceiver::class.java).apply {
                action = ACTION_MAINTAIN_MONITOR
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_MONITOR_HEARTBEAT, intent, flags)
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + HEARTBEAT_INTERVAL_MILLIS,
                pendingIntent
            )
        }

        private suspend fun restoreMonitorIfNeeded(context: Context, action: String?) {
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                // Let the adapter finish its own transition before checking profile state.
                kotlinx.coroutines.delay(2_000L)
            }

            val repository = SettingsRepository(context)
            val settings = repository.usageSettings.first()
            val bootAction = action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED
            if (bootAction && !settings.bootAutoStart) return

            val targets = repository.targetDevices.first()
            if (targets.isEmpty()) return
            val checker = BluetoothDeviceChecker(context)
            val connectedTarget = targets.firstOrNull { checker.isDeviceConnected(it.address) }

            val serviceIntent = Intent(context, BluetoothMonitorService::class.java).apply {
                if (connectedTarget != null) {
                    putExtra(BluetoothMonitorService.EXTRA_DEVICE_NAME, connectedTarget.name)
                    putExtra(BluetoothMonitorService.EXTRA_DEVICE_ADDRESS, connectedTarget.address)
                }
                setAction(if (connectedTarget != null) {
                    BluetoothMonitorService.ACTION_TARGET_CONNECTED
                } else {
                    BluetoothMonitorService.ACTION_CHECK_CURRENT_STATE
                })
            }

            if (connectedTarget != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                runCatching { context.startService(serviceIntent) }
            }
        }
    }
}
