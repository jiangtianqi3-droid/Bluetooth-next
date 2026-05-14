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
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MediaNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private var pendingAudioInfoJob: Job? = null
    private var pendingAudioSignature: String? = null
    private var lastSavedAudioSignature: String? = null

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
        val info = CurrentAudioInfo(
            packageName = sbn.packageName,
            appName = appName,
            title = mediaTitle,
            updatedAt = System.currentTimeMillis()
        )
        val signature = info.signature()
        if (signature == lastSavedAudioSignature || signature == pendingAudioSignature) return

        pendingAudioSignature = signature
        pendingAudioInfoJob?.cancel()
        pendingAudioInfoJob = scope.launch {
            delay(AUDIO_INFO_DEBOUNCE_MILLIS)
            settingsRepository.saveCurrentAudioInfo(info)
            lastSavedAudioSignature = signature
            pendingAudioSignature = null
            pendingAudioInfoJob = null
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (pendingAudioSignature?.startsWith("${sbn.packageName}\u0000") == true) {
            pendingAudioInfoJob?.cancel()
            pendingAudioInfoJob = null
            pendingAudioSignature = null
        }
        if (lastSavedAudioSignature?.startsWith("${sbn.packageName}\u0000") == true) {
            lastSavedAudioSignature = null
        }
        scope.launch {
            settingsRepository.clearCurrentAudioInfo(sbn.packageName)
        }
    }

    override fun onListenerDisconnected() {
        pendingAudioInfoJob?.cancel()
        pendingAudioInfoJob = null
        pendingAudioSignature = null
        lastSavedAudioSignature = null
        scope.launch {
            settingsRepository.clearCurrentAudioInfo()
        }
    }

    override fun onDestroy() {
        pendingAudioInfoJob?.cancel()
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

    private fun CurrentAudioInfo.signature(): String {
        return buildString {
            append(packageName)
            append('\u0000')
            append(appName)
            append('\u0000')
            append(title)
        }
    }

    companion object {
        private const val AUDIO_INFO_DEBOUNCE_MILLIS = 3_000L
    }
}
