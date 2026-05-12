package com.example.bluetoothusage.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class PairedBluetoothDevice(
    val name: String,
    val address: String
)

class BluetoothDeviceChecker(private val context: Context) {
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val adapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

    fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun getBondedDevices(): List<PairedBluetoothDevice> {
        if (!hasBluetoothConnectPermission()) return emptyList()
        return adapter?.bondedDevices
            ?.map { device ->
                PairedBluetoothDevice(
                    name = device.name ?: "未知设备",
                    address = device.address
                )
            }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    fun getBatteryLevel(address: String): Int? {
        if (!hasBluetoothConnectPermission()) return null
        val device = adapter?.bondedDevices
            ?.firstOrNull { it.address.equals(address, ignoreCase = true) }
            ?: return null

        return runCatching {
            val method = BluetoothDevice::class.java.getMethod("getBatteryLevel")
            (method.invoke(device) as? Int)?.takeIf { it in 0..100 }
        }.getOrNull()
    }

    suspend fun isDeviceConnected(address: String): Boolean {
        if (!hasBluetoothConnectPermission()) return false
        val a2dpConnected = isConnectedInProfile(BluetoothProfile.A2DP, address)
        if (a2dpConnected) return true
        return isConnectedInProfile(BluetoothProfile.HEADSET, address)
    }

    private suspend fun isConnectedInProfile(profile: Int, address: String): Boolean {
        if (!hasBluetoothConnectPermission()) return false
        val bluetoothAdapter = adapter ?: return false

        return suspendCancellableCoroutine { continuation ->
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profileId: Int, proxy: BluetoothProfile) {
                    val connected = proxy.connectedDevices.any { it.address == address }
                    bluetoothAdapter.closeProfileProxy(profile, proxy)
                    if (continuation.isActive) continuation.resume(connected)
                }

                override fun onServiceDisconnected(profileId: Int) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }

            val started = bluetoothAdapter.getProfileProxy(context, listener, profile)
            if (!started && continuation.isActive) continuation.resume(false)

            continuation.invokeOnCancellation {
                runCatching {
                    val emptyProxy: BluetoothProfile? = null
                    if (emptyProxy != null) bluetoothAdapter.closeProfileProxy(profile, emptyProxy)
                }
            }
        }
    }
}

fun BluetoothDevice.safeName(): String = try {
    name ?: "未知设备"
} catch (_: SecurityException) {
    "未知设备"
}
