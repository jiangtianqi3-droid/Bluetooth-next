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
import kotlinx.coroutines.CompletableDeferred

data class PairedBluetoothDevice(
    val name: String,
    val address: String
)

class BluetoothDeviceChecker(private val context: Context) {
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val profileLock = Any()
    private val profileProxies = mutableMapOf<Int, BluetoothProfile>()
    private val profileRequests = mutableMapOf<Int, CompletableDeferred<BluetoothProfile?>>()
    private val profileListeners = mutableMapOf<Int, BluetoothProfile.ServiceListener>()

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

    suspend fun warmUpProfileProxies() {
        getOrCreateProfileProxy(BluetoothProfile.A2DP)
        getOrCreateProfileProxy(BluetoothProfile.HEADSET)
    }

    fun close() {
        val bluetoothAdapter = adapter
        val proxies = synchronized(profileLock) {
            val current = profileProxies.toMap()
            profileProxies.clear()
            profileListeners.clear()
            profileRequests.values.forEach { request ->
                if (!request.isCompleted) request.complete(null)
            }
            profileRequests.clear()
            current
        }
        proxies.forEach { (profile, proxy) ->
            if (bluetoothAdapter != null) {
                runCatching { bluetoothAdapter.closeProfileProxy(profile, proxy) }
            }
        }
    }

    private suspend fun isConnectedInProfile(profile: Int, address: String): Boolean {
        if (!hasBluetoothConnectPermission()) return false
        val proxy = getOrCreateProfileProxy(profile) ?: return false
        return runCatching {
            proxy.connectedDevices.any { it.address.equals(address, ignoreCase = true) }
        }.getOrDefault(false)
    }

    private suspend fun getOrCreateProfileProxy(profile: Int): BluetoothProfile? {
        if (!hasBluetoothConnectPermission()) return null
        val cachedProxy = synchronized(profileLock) { profileProxies[profile] }
        if (cachedProxy != null) return cachedProxy

        val bluetoothAdapter = adapter ?: return null
        var shouldStartRequest = false
        val request = synchronized(profileLock) {
            profileRequests[profile] ?: CompletableDeferred<BluetoothProfile?>().also {
                profileRequests[profile] = it
                shouldStartRequest = true
            }
        }

        if (shouldStartRequest) {
            startProfileRequest(bluetoothAdapter, profile, request)
        }
        return request.await()
    }

    private fun startProfileRequest(
        bluetoothAdapter: BluetoothAdapter,
        profile: Int,
        request: CompletableDeferred<BluetoothProfile?>
    ) {
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profileId: Int, proxy: BluetoothProfile) {
                synchronized(profileLock) {
                    profileProxies[profile] = proxy
                    profileListeners[profile] = this
                    profileRequests.remove(profile)
                }
                if (!request.isCompleted) request.complete(proxy)
            }

            override fun onServiceDisconnected(profileId: Int) {
                synchronized(profileLock) {
                    profileProxies.remove(profile)
                    profileListeners.remove(profile)
                    profileRequests.remove(profile)
                }
                if (!request.isCompleted) request.complete(null)
            }
        }
        synchronized(profileLock) {
            profileListeners[profile] = listener
        }
        val started = bluetoothAdapter.getProfileProxy(context, listener, profile)
        if (!started) {
            synchronized(profileLock) {
                profileListeners.remove(profile)
                profileRequests.remove(profile)
            }
            if (!request.isCompleted) request.complete(null)
        }
    }
}

fun BluetoothDevice.safeName(): String = try {
    name ?: "未知设备"
} catch (_: SecurityException) {
    "未知设备"
}
