package com.example.bluetoothusage.viewmodel

import android.content.Intent
import android.app.Application
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetoothusage.BuildConfig
import com.example.bluetoothusage.service.BluetoothMonitorService
import com.example.bluetoothusage.bluetooth.BluetoothDeviceChecker
import com.example.bluetoothusage.bluetooth.PairedBluetoothDevice
import com.example.bluetoothusage.data.UsageRecord
import com.example.bluetoothusage.repository.ActiveSessionInfo
import com.example.bluetoothusage.repository.CurrentAudioInfo
import com.example.bluetoothusage.repository.DailyUsage
import com.example.bluetoothusage.repository.SettingsRepository
import com.example.bluetoothusage.repository.TargetDevice
import com.example.bluetoothusage.repository.UsageRepository
import com.example.bluetoothusage.repository.UsageSettings
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val targetDevice: TargetDevice? = null,
    val targetDevices: List<TargetDevice> = emptyList(),
    val isConnected: Boolean = false,
    val currentSessionMillis: Long = 0,
    val todayMillis: Long = 0,
    val weekMillis: Long = 0,
    val activeSessions: List<ActiveSessionInfo> = emptyList(),
    val dailyLimitMillis: Long = UsageSettings().dailyLimitMillis,
    val batteryPercent: Int? = null,
    val batteryPercents: Map<String, Int> = emptyMap(),
    val sleepEnabled: Boolean = true,
    val sleepStartMinutes: Int = 0,
    val sleepEndMinutes: Int = 390,
    val hideFromRecents: Boolean = false,
    val history: List<UsageRecord> = emptyList(),
    val pairedDevices: List<PairedBluetoothDevice> = emptyList(),
    val calendarMonth: YearMonth = YearMonth.now(),
    val calendarDays: List<DailyUsage> = emptyList(),
    val calendarRecords: List<UsageRecord> = emptyList(),
    val currentAudioInfo: CurrentAudioInfo? = null,
    val bootAutoStart: Boolean = true,
    val lastCleanupSummary: String = "",
    val deviceSelectionGeneration: Int = 0,
    val isSelectingDevice: Boolean = false,
    val showHistoryOnly: Boolean = false,
    val showSettings: Boolean = false,
    val showCalendar: Boolean = false
) {
    val todayProgress: Float
        get() = if (dailyLimitMillis <= 0) 0f else (todayMillis.toFloat() / dailyLimitMillis).coerceIn(0f, 1.5f)

    val isOverDailyLimit: Boolean
        get() = dailyLimitMillis > 0 && todayMillis >= dailyLimitMillis
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    application: Application,
    private val usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {
    private val checker = BluetoothDeviceChecker(application)
    private val mutableUiState = MutableStateFlow(MainUiState())
    private val selectedMonth = MutableStateFlow(YearMonth.now())
    private val uiClock = MutableStateFlow(System.currentTimeMillis())
    private var timerJob: Job? = null
    private var connectionPollJob: Job? = null
    private var sessionStartTimes: List<Long> = emptyList()
    private var lastMonitorConnectedAddresses: Set<String> = emptySet()

    private val totals = combine(
        settingsRepository.usageSettings,
        settingsRepository.targetDevices
    ) { settings, targets -> settings to targetAddressSet(targets) }.flatMapLatest { (settings, targetAddresses) ->
        combine(
            usageRepository.observeTodayDuration(settings, targetAddresses),
            usageRepository.observeWeekDuration(settings, targetAddresses),
            usageRepository.observeHistory(targetAddresses)
        ) { today, week, history ->
            UsageTotals(today, week, history)
        }
    }

    private val coreData = combine(
        settingsRepository.targetDevices,
        settingsRepository.activeSessions,
        settingsRepository.usageSettings,
        settingsRepository.currentAudioInfo,
        totals
    ) { targets, activeSessions, settings, audioInfo, totals ->
        CoreData(targets, activeSessions, settings, audioInfo, totals)
    }

    private val calendarData = combine(
        selectedMonth,
        settingsRepository.usageSettings,
        settingsRepository.targetDevices
    ) { month, settings, targets -> Triple(month, settings, targetAddressSet(targets)) }.flatMapLatest { (month, settings, targetAddresses) ->
        combine(
            usageRepository.observeCalendarMonth(month, settings, targetAddresses),
            usageRepository.observeRecordsInMonth(month, targetAddresses)
        ) { days, records ->
            CalendarData(month = month, days = days, records = records)
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        mutableUiState,
        coreData,
        calendarData,
        uiClock
    ) { state, core, calendar, now ->
        val targetAddresses = core.targets.map { it.address }.toSet()
        val activeSessions = core.activeSessions.filter { session ->
            targetAddresses.any { it.equals(session.deviceAddress, ignoreCase = true) }
        }
        val activeMatchesTarget = activeSessions.isNotEmpty()
        val currentTotal = mergedActiveMillis(activeSessions, now, 0L, now, core.settings)
        val activeToday = mergedActiveMillis(activeSessions, now, todayStartMillis(), tomorrowStartMillis(), core.settings)
        val activeWeek = mergedActiveMillis(activeSessions, now, weekStartMillis(), nextWeekStartMillis(), core.settings)
        val calendarDays = addActiveSessionToCalendar(
            month = calendar.month,
            days = calendar.days,
            activeSessions = activeSessions,
            now = now,
            activeMatchesTarget = activeMatchesTarget,
            settings = core.settings
        )

        state.copy(
            targetDevice = core.targets.firstOrNull(),
            targetDevices = core.targets,
            isConnected = state.isConnected || activeMatchesTarget,
            currentSessionMillis = currentTotal,
            todayMillis = core.totals.todayMillis + activeToday,
            weekMillis = core.totals.weekMillis + activeWeek,
            activeSessions = activeSessions,
            dailyLimitMillis = core.settings.dailyLimitMillis,
            batteryPercent = core.settings.batteryPercent,
            batteryPercents = core.settings.batteryPercents,
            sleepEnabled = core.settings.sleepEnabled,
            sleepStartMinutes = core.settings.sleepStartMinutes,
            sleepEndMinutes = core.settings.sleepEndMinutes,
            hideFromRecents = core.settings.hideFromRecents,
            history = core.totals.history,
            calendarMonth = calendar.month,
            calendarDays = calendarDays,
            calendarRecords = calendar.records,
            currentAudioInfo = core.audioInfo,
            bootAutoStart = core.settings.bootAutoStart
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            settingsRepository.targetDevices.collect { targets ->
                refreshConnectionState(targets)
                restartConnectionPolling(targets)
            }
        }
        viewModelScope.launch {
            settingsRepository.activeSessions.collect { sessions ->
                handleActiveSessions(sessions)
            }
        }
    }

    fun openDeviceSelector() {
        mutableUiState.update {
            it.copy(
                isSelectingDevice = true,
                showHistoryOnly = false,
                showSettings = false,
                showCalendar = false,
                deviceSelectionGeneration = it.deviceSelectionGeneration + 1,
                pairedDevices = checker.getBondedDevices()
            )
        }
    }

    fun closeDeviceSelector() {
        mutableUiState.update { it.copy(isSelectingDevice = false) }
    }

    fun openHistory() {
        mutableUiState.update {
            it.copy(showHistoryOnly = true, isSelectingDevice = false, showSettings = false, showCalendar = false)
        }
    }

    fun closeHistory() {
        mutableUiState.update { it.copy(showHistoryOnly = false) }
    }

    fun openSettings() {
        mutableUiState.update {
            it.copy(showSettings = true, isSelectingDevice = false, showHistoryOnly = false, showCalendar = false)
        }
    }

    fun closeSettings() {
        mutableUiState.update { it.copy(showSettings = false) }
    }

    fun openCalendar() {
        mutableUiState.update {
            it.copy(showCalendar = true, isSelectingDevice = false, showHistoryOnly = false, showSettings = false)
        }
    }

    fun closeCalendar() {
        mutableUiState.update { it.copy(showCalendar = false) }
    }

    fun previousCalendarMonth() {
        selectedMonth.update { it.minusMonths(1) }
    }

    fun nextCalendarMonth() {
        selectedMonth.update { it.plusMonths(1) }
    }

    fun selectDevices(devices: List<PairedBluetoothDevice>) {
        viewModelScope.launch {
            val targets = devices.map { TargetDevice(it.name, it.address) }
            settingsRepository.saveTargetDevices(targets)
            mutableUiState.update { it.copy(isSelectingDevice = false) }
            refreshConnectionState(targets)
        }
    }

    fun refreshDeviceList() {
        mutableUiState.update { it.copy(pairedDevices = checker.getBondedDevices()) }
    }

    fun setDailyLimitHours(hours: Float) {
        val roundedHalfHours = (hours * 2f).toInt().coerceAtLeast(1) / 2f
        val millis = (roundedHalfHours * 60f * 60f * 1_000f).toLong()
        viewModelScope.launch {
            settingsRepository.saveDailyLimitMillis(millis)
        }
    }

    fun setDailyLimitMillis(millis: Long) {
        viewModelScope.launch {
            settingsRepository.saveDailyLimitMillis(millis.coerceAtLeast(30L * 60L * 1_000L))
        }
    }

    fun setSleepEnabled(enabled: Boolean) {
        val state = uiState.value
        viewModelScope.launch {
            settingsRepository.saveSleepSettings(enabled, state.sleepStartMinutes, state.sleepEndMinutes)
        }
    }

    fun adjustSleepStart(deltaMinutes: Int) {
        val state = uiState.value
        viewModelScope.launch {
            settingsRepository.saveSleepSettings(
                state.sleepEnabled,
                wrapMinutes(state.sleepStartMinutes + deltaMinutes),
                state.sleepEndMinutes
            )
        }
    }

    fun adjustSleepEnd(deltaMinutes: Int) {
        val state = uiState.value
        viewModelScope.launch {
            settingsRepository.saveSleepSettings(
                state.sleepEnabled,
                state.sleepStartMinutes,
                wrapMinutes(state.sleepEndMinutes + deltaMinutes)
            )
        }
    }

    fun setHideFromRecents(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveHideFromRecents(enabled)
        }
    }

    fun setBootAutoStart(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveBootAutoStart(enabled)
        }
    }

    fun updateRecord(record: UsageRecord, startTime: Long, endTime: Long, note: String) {
        if (endTime <= startTime) return
        viewModelScope.launch {
            usageRepository.updateRecord(
                record.copy(
                    startTime = startTime,
                    endTime = endTime,
                    durationMillis = endTime - startTime,
                    note = note.trim()
                )
            )
        }
    }

    fun deleteRecord(record: UsageRecord) {
        viewModelScope.launch {
            usageRepository.deleteRecord(record)
        }
    }

    fun exportDiagnostics() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val state = uiState.value
            val rawRecords = usageRepository.getAllRecords()
            val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
            val file = File(dir, "bluetooth-usage-diagnostics-${System.currentTimeMillis()}.txt")
            file.writeText(buildDiagnosticsText(state, rawRecords))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "蓝牙计时诊断日志")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "导出诊断日志").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    fun cleanupInvalidRecords() {
        viewModelScope.launch {
            val targets = uiState.value.targetDevices
            val result = usageRepository.cleanupInvalidRecords(targetAddressSet(targets))
            mutableUiState.update {
                it.copy(
                    lastCleanupSummary = "已清理 ${result.deletedCount} 条：非目标 ${result.nonTargetCount}，短噪声 ${result.shortCount}，重复 ${result.duplicateCount}"
                )
            }
        }
    }

    private fun refreshConnectionState(targets: List<TargetDevice>) {
        viewModelScope.launch {
            if (targets.isEmpty()) {
                stopTimer()
                mutableUiState.update { it.copy(isConnected = false, currentSessionMillis = 0) }
                return@launch
            }
            val connectedTargets = targets.filter { checker.isDeviceConnected(it.address) }
            mutableUiState.update { it.copy(isConnected = connectedTargets.isNotEmpty()) }
            connectedTargets
                .filter { target -> lastMonitorConnectedAddresses.none { it.equals(target.address, ignoreCase = true) } }
                .forEach {
                startMonitorForConnectedTarget(it)
            }
            lastMonitorConnectedAddresses = connectedTargets.map { it.address }.toSet()
        }
    }

    private fun handleActiveSessions(sessions: List<ActiveSessionInfo>) {
        val targetAddresses = uiState.value.targetDevices.map { it.address }.toSet()
        val matching = sessions.filter { session ->
            targetAddresses.any { it.equals(session.deviceAddress, ignoreCase = true) }
        }
        if (matching.isNotEmpty()) {
            mutableUiState.update { it.copy(isConnected = true) }
            startTimer(matching.map { it.startTime })
        } else {
            stopTimer()
            refreshConnectionState(uiState.value.targetDevices)
        }
    }

    private fun restartConnectionPolling(targets: List<TargetDevice>) {
        connectionPollJob?.cancel()
        lastMonitorConnectedAddresses = emptySet()
        if (targets.isEmpty()) return
        connectionPollJob = viewModelScope.launch {
            var connectedAddresses = emptySet<String>()
            while (true) {
                val connectedTargets = targets.filter { checker.isDeviceConnected(it.address) }
                val nowConnected = connectedTargets.map { it.address }.toSet()
                mutableUiState.update { it.copy(isConnected = nowConnected.isNotEmpty()) }
                connectedTargets
                    .filter { target -> connectedAddresses.none { it.equals(target.address, ignoreCase = true) } }
                    .forEach { startMonitorForConnectedTarget(it) }
                targets.filter { target ->
                    connectedAddresses.any { it.equals(target.address, ignoreCase = true) } &&
                        nowConnected.none { it.equals(target.address, ignoreCase = true) }
                }.forEach {
                    notifyMonitorDisconnected(it)
                }
                connectedAddresses = nowConnected
                lastMonitorConnectedAddresses = nowConnected
                delay(10_000)
            }
        }
    }

    private fun startMonitorForConnectedTarget(target: TargetDevice) {
        val context = getApplication<Application>()
        val intent = Intent(context, BluetoothMonitorService::class.java).apply {
            action = BluetoothMonitorService.ACTION_TARGET_CONNECTED
            putExtra(BluetoothMonitorService.EXTRA_DEVICE_NAME, target.name)
            putExtra(BluetoothMonitorService.EXTRA_DEVICE_ADDRESS, target.address)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }

    private fun notifyMonitorDisconnected(target: TargetDevice) {
        val context = getApplication<Application>()
        val intent = Intent(context, BluetoothMonitorService::class.java).apply {
            action = BluetoothMonitorService.ACTION_TARGET_DISCONNECTED
            putExtra(BluetoothMonitorService.EXTRA_DEVICE_NAME, target.name)
            putExtra(BluetoothMonitorService.EXTRA_DEVICE_ADDRESS, target.address)
        }
        context.startService(intent)
    }

    private fun startTimer(startTimes: List<Long>) {
        sessionStartTimes = startTimes
        if (timerJob != null) return
        timerJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val starts = sessionStartTimes
                uiClock.value = now
                mutableUiState.update {
                    it.copy(currentSessionMillis = starts.sumOf { start -> now - start })
                }
                delay(60_000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        sessionStartTimes = emptyList()
        uiClock.value = System.currentTimeMillis()
        mutableUiState.update { it.copy(currentSessionMillis = 0) }
    }

    private fun addActiveSessionToCalendar(
        month: YearMonth,
        days: List<DailyUsage>,
        activeSessions: List<ActiveSessionInfo>,
        now: Long,
        activeMatchesTarget: Boolean,
        settings: UsageSettings
    ): List<DailyUsage> {
        if (!activeMatchesTarget || activeSessions.isEmpty()) return days
        val today = LocalDate.now()
        if (YearMonth.from(today) != month) return days

        val dayStart = todayStartMillis()
        val dayEnd = tomorrowStartMillis()
        val activeToday = mergedActiveMillis(activeSessions, now, dayStart, dayEnd, settings)
        if (activeToday <= 0) return days
        return days.map {
            if (it.date == today) it.copy(durationMillis = it.durationMillis + activeToday) else it
        }
    }

    private fun wrapMinutes(minutes: Int): Int {
        val day = 24 * 60
        return ((minutes % day) + day) % day
    }

    private fun todayStartMillis(): Long {
        return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun tomorrowStartMillis(): Long {
        return LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun weekStartMillis(): Long {
        return LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun nextWeekStartMillis(): Long {
        return LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun overlapMillis(startTime: Long, endTime: Long, rangeStart: Long, rangeEnd: Long): Long {
        val start = maxOf(startTime, rangeStart)
        val end = minOf(endTime, rangeEnd)
        return (end - start).coerceAtLeast(0)
    }

    private fun mergedActiveMillis(
        sessions: List<ActiveSessionInfo>,
        now: Long,
        rangeStart: Long,
        rangeEnd: Long,
        settings: UsageSettings
    ): Long {
        val awakeRanges = awakeRangesMillis(rangeStart, rangeEnd, settings)
        val ranges = sessions.flatMap { session ->
            awakeRanges.mapNotNull { (awakeStart, awakeEnd) ->
                val start = maxOf(session.startTime, rangeStart, awakeStart)
                val end = minOf(now, rangeEnd, awakeEnd)
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

    private fun awakeRangesMillis(
        startMillis: Long,
        endMillis: Long,
        settings: UsageSettings
    ): List<Pair<Long, Long>> {
        if (endMillis <= startMillis) return emptyList()
        val minuteRanges = awakeMinuteRanges(settings)
        if (minuteRanges.isEmpty()) return emptyList()
        val zone = ZoneId.systemDefault()
        val firstDate = java.time.Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        val lastDate = java.time.Instant.ofEpochMilli(endMillis - 1L).atZone(zone).toLocalDate()
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

    private fun awakeMinuteRanges(settings: UsageSettings): List<Pair<Int, Int>> {
        val day = 24 * 60
        val start = settings.sleepStartMinutes.coerceIn(0, day - 1)
        val end = settings.sleepEndMinutes.coerceIn(0, day - 1)
        if (!settings.sleepEnabled || start == end) return listOf(0 to day)
        return if (start < end) {
            buildList {
                if (start > 0) add(0 to start)
                if (end < day) add(end to day)
            }
        } else {
            listOf(end to start)
        }
    }

    private data class UsageTotals(
        val todayMillis: Long,
        val weekMillis: Long,
        val history: List<UsageRecord>
    )

    private data class CoreData(
        val targets: List<TargetDevice>,
        val activeSessions: List<ActiveSessionInfo>,
        val settings: UsageSettings,
        val audioInfo: CurrentAudioInfo?,
        val totals: UsageTotals
    )

    private data class CalendarData(
        val month: YearMonth,
        val days: List<DailyUsage>,
        val records: List<UsageRecord>
    )
}

private fun targetAddressSet(targets: List<TargetDevice>): Set<String> {
    return targets.map { it.address.uppercase() }.toSet()
}

private fun buildDiagnosticsText(state: MainUiState, rawRecords: List<UsageRecord>): String {
    val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    fun formatInstant(millis: Long): String {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(formatter)
    }
    val targetAddresses = targetAddressSet(state.targetDevices)
    val rawNonTarget = rawRecords.count { record -> targetAddresses.none { it.equals(record.deviceAddress, ignoreCase = true) } }
    val rawShort = rawRecords.count { it.durationMillis < 15_000L }
    return buildString {
        appendLine("Bluetooth Usage Diagnostics")
        appendLine("version=${com.example.bluetoothusage.BuildConfig.VERSION_NAME}(${com.example.bluetoothusage.BuildConfig.VERSION_CODE})")
        appendLine("generatedAt=${formatInstant(System.currentTimeMillis())}")
        appendLine("targetDevices=${state.targetDevices.joinToString { "${it.name}/${it.address}" }}")
        appendLine("isConnected=${state.isConnected}")
        appendLine("activeSessions=${state.activeSessions.size}")
        state.activeSessions.forEach {
            appendLine("active ${it.deviceName} ${it.deviceAddress} start=${formatInstant(it.startTime)}")
        }
        appendLine("sleepEnabled=${state.sleepEnabled} sleep=${state.sleepStartMinutes}-${state.sleepEndMinutes}")
        appendLine("todayMillis=${state.todayMillis} weekMillis=${state.weekMillis} dailyLimit=${state.dailyLimitMillis}")
        appendLine("batteryPercents=${state.batteryPercents}")
        appendLine("currentAudio=${state.currentAudioInfo}")
        appendLine("lastCleanupSummary=${state.lastCleanupSummary}")
        appendLine("rawRecords=${rawRecords.size} rawNonTarget=$rawNonTarget rawShortUnder15s=$rawShort")
        appendLine("historyCount=${state.history.size}")
        state.history.take(200).forEach { record ->
            appendLine(
                "record id=${record.id} device=${record.deviceName}/${record.deviceAddress} " +
                    "start=${formatInstant(record.startTime)} end=${formatInstant(record.endTime)} " +
                    "duration=${record.durationMillis} audio=${record.audioAppName} title=${record.mediaTitleSnapshot}"
            )
        }
        appendLine("calendarMonth=${state.calendarMonth} calendarRecords=${state.calendarRecords.size}")
        state.calendarRecords.take(300).forEach { record ->
            appendLine(
                "calendarRecord id=${record.id} device=${record.deviceName}/${record.deviceAddress} " +
                    "start=${formatInstant(record.startTime)} end=${formatInstant(record.endTime)} duration=${record.durationMillis}"
            )
        }
    }
}
