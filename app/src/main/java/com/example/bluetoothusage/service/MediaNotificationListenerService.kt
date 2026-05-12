package com.example.bluetoothusage.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.bluetoothusage.repository.CurrentAudioInfo
import com.example.bluetoothusage.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MediaNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        if (!notification.looksLikeMediaNotification()) return

        val appName = resolveAppName(sbn.packageName)
        val title = notification.extras
            ?.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            .orEmpty()
        val text = notification.extras
            ?.getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            .orEmpty()
        val mediaTitle = listOf(title, text)
            .filter { it.isNotBlank() }
            .joinToString(" - ")

        scope.launch {
            settingsRepository.saveCurrentAudioInfo(
                CurrentAudioInfo(
                    packageName = sbn.packageName,
                    appName = appName,
                    title = mediaTitle,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        scope.launch {
            settingsRepository.clearCurrentAudioInfo(sbn.packageName)
        }
    }

    override fun onListenerDisconnected() {
        scope.launch {
            settingsRepository.clearCurrentAudioInfo()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun Notification.looksLikeMediaNotification(): Boolean {
        if (category == Notification.CATEGORY_TRANSPORT) return true
        if (extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true) return true
        val template = extras?.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        return template.contains("Media", ignoreCase = true)
    }

    private fun resolveAppName(packageName: String): String {
        return runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }
}
