package com.example.bluetoothusage

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.bluetoothusage.service.BluetoothMonitorService
import com.example.bluetoothusage.ui.BluetoothUsageTheme
import com.example.bluetoothusage.ui.MainScreen
import com.example.bluetoothusage.viewmodel.MainViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        startMonitorService()
    }

    private val viewModel by viewModels<MainViewModel> {
        val app = application as BluetoothUsageApp
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(
                    application = app,
                    usageRepository = app.usageRepository,
                    settingsRepository = app.settingsRepository
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestHighestRefreshRate()
        if (!requestRequiredPermissions()) {
            startMonitorService()
        }
        observeRecentsVisibility()

        setContent {
            BluetoothUsageTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestHighestRefreshRate()
    }

    private fun requestRequiredPermissions(): Boolean {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
            return true
        }
        return false
    }

    private fun startMonitorService() {
        val intent = Intent(this, BluetoothMonitorService::class.java).apply {
            action = BluetoothMonitorService.ACTION_CHECK_CURRENT_STATE
        }
        startService(intent)
    }

    private fun observeRecentsVisibility() {
        val app = application as BluetoothUsageApp
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.settingsRepository.usageSettings
                    .map { it.hideFromRecents }
                    .distinctUntilChanged()
                    .collect { hidden ->
                        setTaskExcludedFromRecents(hidden)
                    }
            }
        }
    }

    private fun setTaskExcludedFromRecents(hidden: Boolean) {
        val activityManager = getSystemService(ActivityManager::class.java)
        activityManager.appTasks.forEach { task ->
            runCatching { task.setExcludeFromRecents(hidden) }
        }
    }

    private fun requestHighestRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val currentDisplay: Display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display ?: return
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        val bestMode = currentDisplay.supportedModes.maxByOrNull { it.refreshRate } ?: return
        val params = window.attributes
        params.preferredDisplayModeId = bestMode.modeId
        window.attributes = params
    }
}
