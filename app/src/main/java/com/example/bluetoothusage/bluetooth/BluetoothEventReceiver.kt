package com.example.bluetoothusage.bluetooth

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.bluetoothusage.repository.SettingsRepository
import com.example.bluetoothusage.service.BluetoothMonitorService
import com.example.bluetoothusage.bluetooth.readBatteryLevel
import com.example.bluetoothusage.service.MonitorStartupReceiver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BluetoothEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED &&
            action != BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED &&
            action != BluetoothStateReceiver.ACTION_BATTERY_LEVEL_CHANGED
        ) return

        val device = intent.getParcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            ?: return
        val targets = runCatching {
            runBlocking { SettingsRepository(context.applicationContext).targetDevices.first() }
        }.getOrNull().orEmpty()
        if (targets.none { device.address.equals(it.address, ignoreCase = true) }) return
        MonitorStartupReceiver.refreshSchedule(context.applicationContext)

        val serviceIntent = Intent(context, BluetoothMonitorService::class.java).apply {
            putExtra(BluetoothMonitorService.EXTRA_DEVICE_NAME, device.safeName())
            putExtra(BluetoothMonitorService.EXTRA_DEVICE_ADDRESS, device.address)
        }

        when (action) {
            BluetoothStateReceiver.ACTION_BATTERY_LEVEL_CHANGED -> {
                val level = intent.readBatteryLevel()
                if (level in 0..100) {
                    serviceIntent.action = BluetoothMonitorService.ACTION_TARGET_BATTERY_CHANGED
                    serviceIntent.putExtra(BluetoothMonitorService.EXTRA_BATTERY_LEVEL, level)
                    runCatching { context.startService(serviceIntent) }
                }
            }
            else -> {
                when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        serviceIntent.action = BluetoothMonitorService.ACTION_TARGET_CONNECTED
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ContextCompat.startForegroundService(context, serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        serviceIntent.action = BluetoothMonitorService.ACTION_TARGET_DISCONNECTED
                        runCatching { context.startService(serviceIntent) }
                    }
                }
            }
        }
    }
}
