package com.example.bluetoothusage.bluetooth

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BluetoothStateReceiver(
    private val targetAddressProvider: () -> Set<String>,
    private val onTargetConnected: (BluetoothDevice) -> Unit,
    private val onTargetDisconnected: (BluetoothDevice) -> Unit,
    private val onTargetBatteryChanged: (BluetoothDevice, Int) -> Unit = { _, _ -> }
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED &&
            action != BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED &&
            action != ACTION_BATTERY_LEVEL_CHANGED
        ) return

        val device = intent.getParcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            ?: return
        val targetAddresses = targetAddressProvider()
        if (targetAddresses.none { device.address.equals(it, ignoreCase = true) }) return

        if (action == ACTION_BATTERY_LEVEL_CHANGED) {
            val level = intent.readBatteryLevel()
            if (level in 0..100) onTargetBatteryChanged(device, level)
            return
        }

        when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)) {
            BluetoothProfile.STATE_CONNECTED -> onTargetConnected(device)
            BluetoothProfile.STATE_DISCONNECTED -> onTargetDisconnected(device)
        }
    }

    companion object {
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    }
}

fun Intent.readBatteryLevel(): Int {
    val candidates = listOf(
        BluetoothStateReceiver.EXTRA_BATTERY_LEVEL,
        "android.bluetooth.device.extra.BATTERY_LEVEL",
        "android.bluetooth.headset.extra.BATTERY_LEVEL",
        "android.bluetooth.device.extra.BATTERY"
    )
    return candidates.firstNotNullOfOrNull { key ->
        getIntExtra(key, -1).takeIf { it in 0..100 }
    } ?: -1
}

inline fun <reified T> Intent.getParcelableExtraCompat(name: String): T? {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name) as? T
    }
}
