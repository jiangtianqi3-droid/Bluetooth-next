package com.example.bluetoothusage.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.bluetoothusage.BluetoothUsageApp
import com.example.bluetoothusage.MainActivity
import com.example.bluetoothusage.R
import com.example.bluetoothusage.bluetooth.BluetoothDeviceChecker
import com.example.bluetoothusage.bluetooth.BluetoothStateReceiver
import com.example.bluetoothusage.bluetooth.safeName
import com.example.bluetoothusage.data.UsageRecord
import com.example.bluetoothusage.repository.ActiveSessionInfo
import com.example.bluetoothusage.repository.CurrentAudioInfo
import com.example.bluetoothusage.repository.MINUTES_PER_DAY
import com.example.bluetoothusage.repository.TargetDevice
import com.example.bluetoothusage.repository.UsageSettings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BluetoothMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var checker: BluetoothDeviceChecker
    private lateinit var receiver: BluetoothStateReceiver
    private var targetDevices: List<TargetDevice> = emptyList()
    private var usageSettings = UsageSettings()
    private val activeSessions = mutableMapOf<String, ActiveSession>()
    private val pendingDisconnectJobs = mutableMapOf<String, Job>()
    private var currentAudioInfo: CurrentAudioInfo? = null
    private var storedTodayMillisForNotification: Long = 0L
    private val connectedAddresses = mutableSetOf<String>()
    private var targetDeviceLoaded = false
    private var settingsJob: Job? = null
    private var usageSettingsJob: Job? = null
    private var audioInfoJob: Job? = null
    private var todayUsageJob: Job? = null
    private var monitorJob: Job? = null
    private var isInForeground = false

    override fun onCreate() {
        super.onCreate()
        checker = BluetoothDeviceChecker(this)
        createNotificationChannel()
        registerBluetoothReceiver()
        observeTargetDevice()
        observeUsageSettings()
        observeCurrentAudioInfo()
        observeTodayUsage()
        startMonitorLoop()
        loadInitialSnapshot()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TARGET_CONNECTED -> {
                val name = intent.getStringExtra(EXTRA_DEVICE_NAME).orEmpty().ifBlank { "未知设备" }
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS).orEmpty()
                if (address.isNotBlank()) {
                    serviceScope.launch {
                        ensureTargetDeviceLoaded()
                        val target = targetForAddress(address) ?: return@launch
                        usageSettings = (application as BluetoothUsageApp).settingsRepository.usageSettings.first()
                        connectedAddresses.add(target.address)
                        refreshBattery(target.address)
                        startSession(target.name.ifBlank { name }, target.address)
                    }
                }
            }
            ACTION_TARGET_DISCONNECTED -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS).orEmpty()
                val name = intent.getStringExtra(EXTRA_DEVICE_NAME).orEmpty().ifBlank { "目标设备" }
                scheduleConfirmedDisconnect(address, name)
            }
            ACTION_MONITOR_NOTIFICATION_DISMISSED -> {
                serviceScope.launch {
                    ensureTargetDeviceLoaded()
                    applyConnectionRules()
                    if (shouldShowMonitorNotification()) {
                        updateNotification()
                    }
                }
            }
            ACTION_TARGET_BATTERY_CHANGED -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS).orEmpty()
                serviceScope.launch {
                    ensureTargetDeviceLoaded()
                    if (targetForAddress(address) != null) {
                        saveBattery(
                            address = address,
                            level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1).takeIf { it in 0..100 }
                        )
                    }
                }
            }
            else -> {
                serviceScope.launch {
                    ensureTargetDeviceLoaded()
                    applyConnectionRules()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun loadInitialSnapshot() {
        val app = application as BluetoothUsageApp
        serviceScope.launch {
            targetDevices = app.settingsRepository.targetDevices.first()
            targetDeviceLoaded = true
            usageSettings = app.settingsRepository.usageSettings.first()
            currentAudioInfo = app.settingsRepository.currentAudioInfo.first()
            val persisted = app.settingsRepository.activeSessions.first()
            val filtered = persisted.filter { targetForAddress(it.deviceAddress) != null }
            if (filtered.size != persisted.size) {
                app.settingsRepository.setActiveSessions(filtered)
            }
            filtered.forEach { session ->
                activeSessions[session.deviceAddress] = ActiveSession(
                    deviceName = session.deviceName,
                    deviceAddress = session.deviceAddress,
                    startTime = session.startTime,
                    audioAppPackage = currentAudioInfo?.packageName.orEmpty(),
                    audioAppName = currentAudioInfo?.appName.orEmpty(),
                    mediaTitleSnapshot = currentAudioInfo?.title.orEmpty()
                )
                connectedAddresses.add(session.deviceAddress)
                updateNotification()
            }
            applyConnectionRules()
        }
    }

    private fun registerBluetoothReceiver() {
        receiver = BluetoothStateReceiver(
            targetAddressProvider = { targetDevices.map { it.address }.toSet() },
            onTargetConnected = { device ->
                val target = targetForAddress(device.address)
                if (target != null) {
                    connectedAddresses.add(target.address)
                    refreshBattery(target.address)
                    startSession(target.name.ifBlank { device.safeName() }, target.address)
                }
            },
            onTargetDisconnected = { device ->
                scheduleConfirmedDisconnect(device.address, device.safeName())
            },
            onTargetBatteryChanged = { device, level ->
                saveBattery(device.address, level)
            }
        )
        val filter = IntentFilter().apply {
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothStateReceiver.ACTION_BATTERY_LEVEL_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun observeTargetDevice() {
        val app = application as BluetoothUsageApp
        settingsJob?.cancel()
        settingsJob = serviceScope.launch {
            app.settingsRepository.targetDevices.collectLatest { targets ->
                val previousAddresses = targetDevices.map { it.address }.toSet()
                val currentAddresses = targets.map { it.address }.toSet()
                targetDeviceLoaded = true
                val removedAddresses = previousAddresses - currentAddresses
                removedAddresses.forEach { address ->
                    endActiveSession(address, System.currentTimeMillis(), "已移除监控设备")
                }
                if (removedAddresses.isNotEmpty()) {
                    connectedAddresses.removeAll(removedAddresses)
                    removedAddresses.forEach { app.settingsRepository.saveBatteryPercent(it, null) }
                }
                targetDevices = targets
                if (targets.isEmpty()) {
                    connectedAddresses.clear()
                    removeMonitorNotification(stopService = activeSessions.isEmpty())
                } else {
                    targets.forEach { refreshBattery(it.address) }
                    applyConnectionRules()
                }
            }
        }
    }

    private fun observeUsageSettings() {
        val app = application as BluetoothUsageApp
        usageSettingsJob?.cancel()
        usageSettingsJob = serviceScope.launch {
            app.settingsRepository.usageSettings.collectLatest { settings ->
                usageSettings = settings
                applyConnectionRules()
                checkDailyLimit()
                updateNotification()
            }
        }
    }

    private fun observeCurrentAudioInfo() {
        val app = application as BluetoothUsageApp
        audioInfoJob?.cancel()
        audioInfoJob = serviceScope.launch {
            app.settingsRepository.currentAudioInfo.collectLatest { info ->
                currentAudioInfo = info
                if (info != null) {
                    activeSessions.replaceAll { _, session ->
                        session.copy(
                            audioAppPackage = info.packageName,
                            audioAppName = info.appName,
                            mediaTitleSnapshot = info.title
                        )
                    }
                }
                updateNotification()
            }
        }
    }

    private fun observeTodayUsage() {
        val app = application as BluetoothUsageApp
        todayUsageJob?.cancel()
        todayUsageJob = serviceScope.launch {
            combine(
                app.settingsRepository.usageSettings,
                app.settingsRepository.targetDevices
            ) { settings, targets -> settings to targets.map { it.address.uppercase() }.toSet() }
                .collectLatest { (settings, addresses) ->
                app.usageRepository.observeTodayDuration(settings, addresses).collectLatest { millis ->
                    storedTodayMillisForNotification = millis
                    updateNotification()
                }
            }
        }
    }

    private fun startMonitorLoop() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (true) {
                delay(MONITOR_INTERVAL_MILLIS)
                applyConnectionRules()
                checkDailyLimit()
            }
        }
    }

    private suspend fun applyConnectionRules() {
        ensureTargetDeviceLoaded()
        if (!targetDeviceLoaded) return

        val targets = targetDevices
        if (targets.isEmpty()) {
            connectedAddresses.clear()
            removeMonitorNotification(stopService = activeSessions.isEmpty())
            return
        }

        val connectedTargets = targets.filter { checker.isDeviceConnected(it.address) }
        val connectedTargetAddresses = connectedTargets.map { it.address }.toSet()

        connectedAddresses.clear()
        connectedAddresses.addAll(connectedTargetAddresses)
        connectedTargets.forEach { target ->
            cancelPendingDisconnect(target.address)
            refreshBattery(target.address)
        }

        activeSessions.keys.toList()
            .filter { activeAddress -> connectedTargetAddresses.none { it.equals(activeAddress, ignoreCase = true) } }
            .forEach { activeAddress ->
                val name = activeSessions[activeAddress]?.deviceName ?: targetForAddress(activeAddress)?.name ?: "目标设备"
                scheduleConfirmedDisconnect(activeAddress, name)
            }

        if (isInSleepWindow()) {
            activeSessions.keys.toList().forEach { address ->
                endActiveSession(address, System.currentTimeMillis(), "睡眠时间暂停记录")
            }
            if (connectedTargets.isNotEmpty()) {
                updateNotification("已连接：${connectedDeviceNames()}，睡眠时间暂停记录")
            } else {
                removeMonitorNotification(stopService = false)
            }
            return
        }

        val activeAddress = activeSessions.keys.firstOrNull()
        val activeStillConnected = activeAddress != null &&
            connectedTargetAddresses.any { it.equals(activeAddress, ignoreCase = true) }
        if (activeStillConnected) {
            updateNotification()
        } else if (activeSessions.isEmpty() && connectedTargets.isNotEmpty()) {
            val target = connectedTargets.first()
            startSession(target.name, target.address)
        } else if (connectedTargets.isEmpty() && activeSessions.isEmpty()) {
            removeMonitorNotification(stopService = false)
        } else {
            updateNotification()
        }
    }

    private fun startSession(deviceName: String, deviceAddress: String) {
        val target = targetForAddress(deviceAddress) ?: return
        cancelPendingDisconnect(target.address)
        connectedAddresses.add(target.address)
        if (isInSleepWindow()) {
            updateNotification("已连接：${target.name.ifBlank { deviceName }}，睡眠时间暂停记录")
            return
        }

        if (activeSessionKey(target.address) != null) return
        val alreadyActiveAddress = activeSessions.keys.firstOrNull { activeAddress ->
            connectedAddresses.any { it.equals(activeAddress, ignoreCase = true) }
        }
        if (alreadyActiveAddress != null) {
            updateNotification()
            return
        }
        activeSessions.keys
            .filterNot { it.equals(target.address, ignoreCase = true) }
            .toList()
            .forEach { address ->
                endActiveSession(address, System.currentTimeMillis(), "切换到：${target.name.ifBlank { deviceName }}")
            }

        val now = System.currentTimeMillis()
        activeSessions[target.address] = ActiveSession(
            deviceName = target.name.ifBlank { deviceName },
            deviceAddress = target.address,
            startTime = now,
            audioAppPackage = currentAudioInfo?.packageName.orEmpty(),
            audioAppName = currentAudioInfo?.appName.orEmpty(),
            mediaTitleSnapshot = currentAudioInfo?.title.orEmpty()
        )
        serviceScope.launch {
            val app = application as BluetoothUsageApp
            app.settingsRepository.setActiveSessions(activeSessions.values.map { it.toInfo() })
            checkDailyLimit()
        }
        updateNotification("已连接：${target.name.ifBlank { deviceName }}，正在计时")
    }

    private fun endActiveSession(deviceAddress: String, endTime: Long, text: String) {
        val sessionKey = activeSessionKey(deviceAddress) ?: deviceAddress
        val session = activeSessions.remove(sessionKey)

        serviceScope.launch(Dispatchers.IO) {
            val app = application as BluetoothUsageApp
            if (session != null) {
                val duration = (endTime - session.startTime).coerceAtLeast(0)
                val targetAddresses = targetDevices.map { it.address.uppercase() }.toSet()
                if (duration >= 15_000L && targetForAddress(session.deviceAddress) != null) {
                    app.usageRepository.addRecord(
                        UsageRecord(
                            deviceName = session.deviceName,
                            deviceAddress = session.deviceAddress,
                            startTime = session.startTime,
                            endTime = endTime,
                            durationMillis = duration,
                            audioAppPackage = session.audioAppPackage,
                            audioAppName = session.audioAppName,
                            mediaTitleSnapshot = session.mediaTitleSnapshot
                        ),
                        targetAddresses
                    )
                }
            }
            app.settingsRepository.setActiveSessions(activeSessions.values.map { it.toInfo() })
        }
        if (connectedAddresses.isNotEmpty()) {
            updateNotification(text)
        } else {
            removeMonitorNotification(stopService = targetDevices.isEmpty())
        }
    }

    private fun targetForAddress(address: String): TargetDevice? {
        if (address.isBlank()) return null
        return targetDevices.firstOrNull { it.address.equals(address, ignoreCase = true) }
    }

    private fun activeSessionKey(address: String): String? {
        if (address.isBlank()) return null
        return activeSessions.keys.firstOrNull { it.equals(address, ignoreCase = true) }
    }

    private fun pendingDisconnectKey(address: String): String = address.uppercase()

    private fun cancelPendingDisconnect(address: String) {
        if (address.isBlank()) return
        val keys = pendingDisconnectJobs.keys.filter { it.equals(address, ignoreCase = true) }
        keys.forEach { key ->
            pendingDisconnectJobs.remove(key)?.cancel()
        }
    }

    private fun scheduleConfirmedDisconnect(deviceAddress: String, deviceName: String) {
        if (deviceAddress.isBlank()) return
        serviceScope.launch {
            ensureTargetDeviceLoaded()
            val target = targetForAddress(deviceAddress) ?: return@launch
            val key = pendingDisconnectKey(target.address)
            pendingDisconnectJobs.remove(key)?.cancel()
            pendingDisconnectJobs[key] = launch disconnectJob@{
                delay(DISCONNECT_CONFIRM_DELAY_MILLIS)
                if (checker.isDeviceConnected(target.address)) {
                    connectedAddresses.add(target.address)
                    pendingDisconnectJobs.remove(key)
                    updateNotification()
                    return@disconnectJob
                }

                connectedAddresses.removeAll(
                    connectedAddresses.filter { it.equals(target.address, ignoreCase = true) }.toSet()
                )
                pendingDisconnectJobs.remove(key)
                endActiveSession(
                    deviceAddress = target.address,
                    endTime = System.currentTimeMillis(),
                    text = "已断开：${target.name.ifBlank { deviceName }}"
                )
                if (activeSessions.isEmpty() && connectedAddresses.isEmpty()) {
                    removeMonitorNotification(stopService = targetDevices.isEmpty())
                }
            }
        }
    }

    private fun refreshBattery(address: String) {
        val battery = checker.getBatteryLevel(address)
        if (battery != null) saveBattery(address, battery)
    }

    private fun saveBattery(address: String, level: Int?) {
        serviceScope.launch {
            val app = application as BluetoothUsageApp
            if (address.isBlank()) {
                app.settingsRepository.saveBatteryPercent(level)
                usageSettings = usageSettings.copy(batteryPercent = level)
                updateNotification()
                return@launch
            }
            app.settingsRepository.saveBatteryPercent(address, level)
            val normalized = address.uppercase()
            val levels = usageSettings.batteryPercents.toMutableMap()
            if (level == null) {
                levels.remove(normalized)
            } else {
                levels[normalized] = level
            }
            usageSettings = usageSettings.copy(batteryPercent = level, batteryPercents = levels)
            updateNotification()
        }
    }

    private suspend fun checkDailyLimit() {
        val limit = usageSettings.dailyLimitMillis
        if (limit <= 0) return

        val today = LocalDate.now()
        if (usageSettings.lastLimitAlertDate == today.toString()) return

        val app = application as BluetoothUsageApp
        val start = todayStartMillis(today)
        val end = todayStartMillis(today.plusDays(1))
        val targetAddresses = targetDevices.map { it.address.uppercase() }.toSet()
        val stored = app.usageRepository.getDurationInRange(start, end, usageSettings, targetAddresses)
        val active = mergedActiveOverlapMillis(start, end)
        val total = stored + active

        if (total >= limit) {
            app.settingsRepository.saveLastLimitAlertDate(today.toString())
            usageSettings = usageSettings.copy(lastLimitAlertDate = today.toString())
            postLimitNotification(total, limit)
        }
    }

    private fun postLimitNotification(total: Long, limit: Long) {
        val manager = getSystemService(NotificationManager::class.java)
        val text = "今日已使用 ${formatDuration(total)} / ${formatDuration(limit)}"
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(selectBatteryIcon())
            .setContentTitle("今日蓝牙使用已达目标")
            .setContentText(text)
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(LIMIT_NOTIFICATION_ID, notification)
    }

    private fun startAsForeground(text: String? = null) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(text), type)
            isInForeground = true
        }.onFailure {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    private fun updateNotification(text: String? = null) {
        if (!targetDeviceLoaded && activeSessions.isEmpty() && connectedAddresses.isEmpty()) return
        if (!shouldShowMonitorNotification()) {
            removeMonitorNotification(stopService = targetDevices.isEmpty())
            return
        }
        if (!isInForeground) {
            startAsForeground(text)
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(overrideText: String? = null): Notification {
        val session = activeSessions.values.firstOrNull()
        val battery = notificationBattery()
        val deviceName = when {
            activeSessions.size > 1 -> "${activeSessions.size} 个耳机"
            connectedAddresses.size > 1 -> "${connectedAddresses.size} 个耳机"
            else -> session?.deviceName ?: connectedDeviceNames().ifBlank { targetDevices.firstOrNull()?.name ?: "目标设备" }
        }
        val title = batteryText(battery)
        val text = "今日使用 ${formatDuration(todayUsageForNotification())}"
        val expandedText = when {
            overrideText != null -> "$overrideText\n$text\n播放应用：${audioText()}\n${batteryText(battery)}"
            activeSessions.isNotEmpty() -> "$deviceName 正在计时\n$text\n播放应用：${audioText()}\n${batteryText(battery)}"
            connectedAddresses.isNotEmpty() && isInSleepWindow() -> "$deviceName 已连接\n$text\n睡眠时间暂停记录\n${batteryText(battery)}"
            connectedAddresses.isNotEmpty() -> "$deviceName 已连接\n$text\n播放应用：${audioText()}\n${batteryText(battery)}"
            else -> "$deviceName 未连接\n$text\n${batteryText(battery)}"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(selectBatteryIcon())
            .setColor(notificationAccentColor(battery))
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("今日")
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setContentIntent(contentIntent())
            .setDeleteIntent(notificationDeleteIntent())
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        return builder.build()
    }

    private fun shouldShowMonitorNotification(): Boolean {
        return activeSessions.isNotEmpty() || connectedAddresses.isNotEmpty()
    }

    private suspend fun ensureTargetDeviceLoaded() {
        if (targetDeviceLoaded) return
        val app = application as BluetoothUsageApp
        targetDevices = app.settingsRepository.targetDevices.first()
        targetDeviceLoaded = true
    }

    private suspend fun ensureActiveSessionLoaded() {
        if (activeSessions.isNotEmpty()) return
        val app = application as BluetoothUsageApp
        app.settingsRepository.activeSessions.first().forEach { session ->
            activeSessions[session.deviceAddress] = ActiveSession(
                deviceName = session.deviceName,
                deviceAddress = session.deviceAddress,
                startTime = session.startTime,
                audioAppPackage = currentAudioInfo?.packageName.orEmpty(),
                audioAppName = currentAudioInfo?.appName.orEmpty(),
                mediaTitleSnapshot = currentAudioInfo?.title.orEmpty()
            )
        }
    }

    private fun removeMonitorNotification(stopService: Boolean) {
        if (isInForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isInForeground = false
        }
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        if (stopService) stopSelf()
    }

    private fun selectBatteryIcon(): Int {
        val battery = notificationBattery() ?: return R.drawable.ic_notification
        return when {
            battery < 15 -> R.drawable.ic_battery_0
            battery < 40 -> R.drawable.ic_battery_25
            battery < 65 -> R.drawable.ic_battery_50
            battery < 90 -> R.drawable.ic_battery_75
            else -> R.drawable.ic_battery_100
        }
    }

    private fun notificationAccentColor(percent: Int?): Int {
        return if (percent != null && percent < 15) Color.RED else Color.rgb(20, 120, 255)
    }

    private fun notificationBattery(): Int? {
        val connectedLevels = connectedAddresses.mapNotNull { address ->
            usageSettings.batteryPercents[address.uppercase()]
        }
        return connectedLevels.minOrNull() ?: usageSettings.batteryPercent
    }

    private fun todayUsageForNotification(): Long {
        val today = LocalDate.now()
        val start = todayStartMillis(today)
        val end = todayStartMillis(today.plusDays(1))
        val active = mergedActiveOverlapMillis(start, end)
        return storedTodayMillisForNotification + active
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun notificationDeleteIntent(): PendingIntent {
        val intent = Intent(this, BluetoothMonitorService::class.java).apply {
            action = ACTION_MONITOR_NOTIFICATION_DISMISSED
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getService(this, 1, intent, flags)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.monitor_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun isInSleepWindow(): Boolean {
        if (!usageSettings.sleepEnabled) return false
        val start = usageSettings.sleepStartMinutes
        val end = usageSettings.sleepEndMinutes
        if (start == end) return false

        val now = LocalDateTime.now().toLocalTime()
        val minutes = now.hour * 60 + now.minute
        return if (start < end) {
            minutes >= start && minutes < end
        } else {
            minutes >= start || minutes < end
        }
    }

    private fun todayStartMillis(date: LocalDate): Long {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun ActiveSession.overlapMillis(startMillis: Long, endMillis: Long): Long {
        val overlapStart = maxOf(startTime, startMillis)
        val overlapEnd = minOf(System.currentTimeMillis(), endMillis)
        return (overlapEnd - overlapStart).coerceAtLeast(0)
    }

    private fun mergedActiveOverlapMillis(startMillis: Long, endMillis: Long): Long {
        val now = System.currentTimeMillis()
        val awakeRanges = awakeRangesMillis(startMillis, endMillis)
        val ranges = activeSessions.values.flatMap { session ->
            awakeRanges.mapNotNull { (awakeStart, awakeEnd) ->
                val start = maxOf(session.startTime, startMillis, awakeStart)
                val end = minOf(now, endMillis, awakeEnd)
                if (end > start) start to end else null
            }
        }.sortedBy { it.first }
        if (ranges.isEmpty()) return 0L
        var total = 0L
        var currentStart = ranges.first().first
        var currentEnd = ranges.first().second
        ranges.drop(1).forEach { (start, end) ->
            if (start <= currentEnd) {
                currentEnd = maxOf(currentEnd, end)
            } else {
                total += currentEnd - currentStart
                currentStart = start
                currentEnd = end
            }
        }
        total += currentEnd - currentStart
        return total
    }

    private fun awakeRangesMillis(startMillis: Long, endMillis: Long): List<Pair<Long, Long>> {
        if (endMillis <= startMillis) return emptyList()
        val minuteRanges = awakeMinuteRanges()
        if (minuteRanges.isEmpty()) return emptyList()
        val zone = ZoneId.systemDefault()
        val firstDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        val lastDate = Instant.ofEpochMilli(endMillis - 1L).atZone(zone).toLocalDate()
        val ranges = mutableListOf<Pair<Long, Long>>()
        var date = firstDate
        while (!date.isAfter(lastDate)) {
            val dayStart = date.atStartOfDay(zone)
            minuteRanges.forEach { (startMinute, endMinute) ->
                val start = dayStart.plusMinutes(startMinute.toLong()).toInstant().toEpochMilli()
                val end = dayStart.plusMinutes(endMinute.toLong()).toInstant().toEpochMilli()
                val clippedStart = maxOf(start, startMillis)
                val clippedEnd = minOf(end, endMillis)
                if (clippedEnd > clippedStart) ranges += clippedStart to clippedEnd
            }
            date = date.plusDays(1)
        }
        return ranges
    }

    private fun awakeMinuteRanges(): List<Pair<Int, Int>> {
        val start = usageSettings.sleepStartMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
        val end = usageSettings.sleepEndMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
        if (!usageSettings.sleepEnabled || start == end) return listOf(0 to MINUTES_PER_DAY)
        return if (start < end) {
            buildList {
                if (start > 0) add(0 to start)
                if (end < MINUTES_PER_DAY) add(end to MINUTES_PER_DAY)
            }
        } else {
            listOf(end to start)
        }
    }

    private fun batteryText(percent: Int?): String {
        return percent?.let { "电量 $it%" } ?: "电量未知"
    }

    private fun audioText(): String {
        val info = currentAudioInfo ?: return "音频来源未知"
        return if (info.title.isBlank()) info.appName else "${info.appName}：${info.title}"
    }

    private fun audioAppText(): String {
        return currentAudioInfo?.appName?.takeIf { it.isNotBlank() } ?: "未知"
    }

    private fun connectedDeviceNames(): String {
        return targetDevices
            .filter { target -> connectedAddresses.any { it.equals(target.address, ignoreCase = true) } }
            .joinToString("、") { it.name }
    }

    private fun formatDuration(durationMillis: Long): String {
        val totalMinutes = durationMillis / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}小时${minutes}分"
            hours > 0 -> "${hours}小时"
            else -> "${minutes}分"
        }
    }

    private data class ActiveSession(
        val deviceName: String,
        val deviceAddress: String,
        val startTime: Long,
        val audioAppPackage: String = "",
        val audioAppName: String = "",
        val mediaTitleSnapshot: String = ""
    ) {
        fun toInfo(): ActiveSessionInfo {
            return ActiveSessionInfo(
                deviceName = deviceName,
                deviceAddress = deviceAddress,
                startTime = startTime
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "bluetooth_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val LIMIT_NOTIFICATION_ID = 1002
        private const val MONITOR_INTERVAL_MILLIS = 30_000L
        private const val DISCONNECT_CONFIRM_DELAY_MILLIS = 2_000L
        const val ACTION_CHECK_CURRENT_STATE = "com.example.bluetoothusage.action.CHECK_CURRENT_STATE"
        const val ACTION_TARGET_CONNECTED = "com.example.bluetoothusage.action.TARGET_CONNECTED"
        const val ACTION_TARGET_DISCONNECTED = "com.example.bluetoothusage.action.TARGET_DISCONNECTED"
        const val ACTION_TARGET_BATTERY_CHANGED = "com.example.bluetoothusage.action.TARGET_BATTERY_CHANGED"
        private const val ACTION_MONITOR_NOTIFICATION_DISMISSED = "com.example.bluetoothusage.action.MONITOR_NOTIFICATION_DISMISSED"
        const val EXTRA_DEVICE_NAME = "extra_device_name"
        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_BATTERY_LEVEL = "extra_battery_level"
    }
}
