package com.example.bluetoothusage.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class TargetDevice(
    val name: String,
    val address: String
)

data class ActiveSessionInfo(
    val deviceName: String,
    val deviceAddress: String,
    val startTime: Long
)

data class CurrentAudioInfo(
    val packageName: String,
    val appName: String,
    val title: String,
    val updatedAt: Long
)

data class UsageSettings(
    val dailyLimitMillis: Long = DEFAULT_DAILY_LIMIT_MILLIS,
    val singleSessionLimitMillis: Long = DEFAULT_SINGLE_SESSION_LIMIT_MILLIS,
    val breakReminderMillis: Long = DEFAULT_BREAK_REMINDER_MILLIS,
    val weeklyGoalMillis: Long = DEFAULT_WEEKLY_GOAL_MILLIS,
    val bedtimeReminderEnabled: Boolean = false,
    val bedtimeReminderStartMinutes: Int = 23 * 60,
    val bedtimeReminderEndMinutes: Int = 0,
    val sleepEnabled: Boolean = true,
    val sleepStartMinutes: Int = 0,
    val sleepEndMinutes: Int = 390,
    val batteryPercent: Int? = null,
    val batteryPercents: Map<String, Int> = emptyMap(),
    val lastLimitAlertDate: String = "",
    val lastSessionLimitAlertKey: String = "",
    val lastBreakReminderKey: String = "",
    val lastWeeklyGoalAlertWeek: String = "",
    val lastBedtimeAlertDate: String = "",
    val hideFromRecents: Boolean = false,
    val bootAutoStart: Boolean = true
)

private val Context.settingsDataStore by preferencesDataStore(name = "settings")
const val MINUTES_PER_DAY = 24 * 60
const val DEFAULT_DAILY_LIMIT_MILLIS = 6L * 60L * 60L * 1_000L
const val DEFAULT_SINGLE_SESSION_LIMIT_MILLIS = 2L * 60L * 60L * 1_000L
const val DEFAULT_BREAK_REMINDER_MILLIS = 60L * 60L * 1_000L
const val DEFAULT_WEEKLY_GOAL_MILLIS = DEFAULT_DAILY_LIMIT_MILLIS * 7L

class SettingsRepository(private val context: Context) {
    private val deviceNameKey = stringPreferencesKey("target_device_name")
    private val deviceAddressKey = stringPreferencesKey("target_device_address")
    private val targetDevicesJsonKey = stringPreferencesKey("target_devices_json")
    private val activeDeviceNameKey = stringPreferencesKey("active_device_name")
    private val activeDeviceAddressKey = stringPreferencesKey("active_device_address")
    private val activeStartTimeKey = longPreferencesKey("active_start_time")
    private val activeSessionsJsonKey = stringPreferencesKey("active_sessions_json")
    private val dailyLimitMillisKey = longPreferencesKey("daily_limit_millis")
    private val singleSessionLimitMillisKey = longPreferencesKey("single_session_limit_millis")
    private val breakReminderMillisKey = longPreferencesKey("break_reminder_millis")
    private val weeklyGoalMillisKey = longPreferencesKey("weekly_goal_millis")
    private val bedtimeReminderEnabledKey = booleanPreferencesKey("bedtime_reminder_enabled")
    private val bedtimeReminderStartMinutesKey = intPreferencesKey("bedtime_reminder_start_minutes")
    private val bedtimeReminderEndMinutesKey = intPreferencesKey("bedtime_reminder_end_minutes")
    private val sleepEnabledKey = booleanPreferencesKey("sleep_enabled")
    private val sleepStartMinutesKey = intPreferencesKey("sleep_start_minutes")
    private val sleepEndMinutesKey = intPreferencesKey("sleep_end_minutes")
    private val batteryPercentKey = intPreferencesKey("battery_percent")
    private val batteryPercentsJsonKey = stringPreferencesKey("battery_percents_json")
    private val lastLimitAlertDateKey = stringPreferencesKey("last_limit_alert_date")
    private val lastSessionLimitAlertKey = stringPreferencesKey("last_session_limit_alert_key")
    private val lastBreakReminderKey = stringPreferencesKey("last_break_reminder_key")
    private val lastWeeklyGoalAlertWeekKey = stringPreferencesKey("last_weekly_goal_alert_week")
    private val lastBedtimeAlertDateKey = stringPreferencesKey("last_bedtime_alert_date")
    private val hideFromRecentsKey = booleanPreferencesKey("hide_from_recents")
    private val bootAutoStartKey = booleanPreferencesKey("boot_auto_start")
    private val audioPackageKey = stringPreferencesKey("audio_package")
    private val audioAppNameKey = stringPreferencesKey("audio_app_name")
    private val audioTitleKey = stringPreferencesKey("audio_title")
    private val audioUpdatedAtKey = longPreferencesKey("audio_updated_at")

    val targetDevices: Flow<List<TargetDevice>> = context.settingsDataStore.data.map { prefs ->
        decodeTargetDevices(prefs[targetDevicesJsonKey]).ifEmpty {
            val name = prefs[deviceNameKey]
            val address = prefs[deviceAddressKey]
            if (name.isNullOrBlank() || address.isNullOrBlank()) emptyList() else listOf(TargetDevice(name, address))
        }
    }

    val targetDevice: Flow<TargetDevice?> = targetDevices.map { it.firstOrNull() }

    val activeSessions: Flow<List<ActiveSessionInfo>> = context.settingsDataStore.data.map { prefs ->
        decodeActiveSessions(prefs[activeSessionsJsonKey]).ifEmpty {
            val name = prefs[activeDeviceNameKey]
            val address = prefs[activeDeviceAddressKey]
            val startTime = prefs[activeStartTimeKey]
            if (name.isNullOrBlank() || address.isNullOrBlank() || startTime == null) {
                emptyList()
            } else {
                listOf(ActiveSessionInfo(name, address, startTime))
            }
        }
    }

    val activeSession: Flow<ActiveSessionInfo?> = activeSessions.map { it.firstOrNull() }

    val usageSettings: Flow<UsageSettings> = context.settingsDataStore.data.map { prefs ->
        val battery = prefs[batteryPercentKey]?.takeIf { it in 0..100 }
        val sleepStart = prefs[sleepStartMinutesKey] ?: 0
        UsageSettings(
            dailyLimitMillis = prefs[dailyLimitMillisKey] ?: DEFAULT_DAILY_LIMIT_MILLIS,
            singleSessionLimitMillis = prefs[singleSessionLimitMillisKey] ?: DEFAULT_SINGLE_SESSION_LIMIT_MILLIS,
            breakReminderMillis = prefs[breakReminderMillisKey] ?: DEFAULT_BREAK_REMINDER_MILLIS,
            weeklyGoalMillis = prefs[weeklyGoalMillisKey] ?: DEFAULT_WEEKLY_GOAL_MILLIS,
            bedtimeReminderEnabled = prefs[bedtimeReminderEnabledKey] ?: false,
            bedtimeReminderStartMinutes = prefs[bedtimeReminderStartMinutesKey] ?: (23 * 60),
            bedtimeReminderEndMinutes = prefs[bedtimeReminderEndMinutesKey] ?: sleepStart,
            sleepEnabled = prefs[sleepEnabledKey] ?: true,
            sleepStartMinutes = sleepStart,
            sleepEndMinutes = prefs[sleepEndMinutesKey] ?: 390,
            batteryPercent = battery,
            batteryPercents = decodeBatteryPercents(prefs[batteryPercentsJsonKey]).ifEmpty {
                battery?.let { mapOf("_legacy" to it) }.orEmpty()
            },
            lastLimitAlertDate = prefs[lastLimitAlertDateKey].orEmpty(),
            lastSessionLimitAlertKey = prefs[lastSessionLimitAlertKey].orEmpty(),
            lastBreakReminderKey = prefs[lastBreakReminderKey].orEmpty(),
            lastWeeklyGoalAlertWeek = prefs[lastWeeklyGoalAlertWeekKey].orEmpty(),
            lastBedtimeAlertDate = prefs[lastBedtimeAlertDateKey].orEmpty(),
            hideFromRecents = prefs[hideFromRecentsKey] ?: false,
            bootAutoStart = prefs[bootAutoStartKey] ?: true
        )
    }

    val currentAudioInfo: Flow<CurrentAudioInfo?> = context.settingsDataStore.data.map { prefs ->
        val packageName = prefs[audioPackageKey]
        val appName = prefs[audioAppNameKey]
        val updatedAt = prefs[audioUpdatedAtKey]
        if (packageName.isNullOrBlank() || appName.isNullOrBlank() || updatedAt == null) {
            null
        } else {
            CurrentAudioInfo(
                packageName = packageName,
                appName = appName,
                title = prefs[audioTitleKey].orEmpty(),
                updatedAt = updatedAt
            )
        }
    }

    suspend fun saveTargetDevice(name: String, address: String) {
        saveTargetDevices(listOf(TargetDevice(name, address)))
    }

    suspend fun saveTargetDevices(devices: List<TargetDevice>) {
        val distinct = devices.distinctBy { it.address.uppercase() }
        context.settingsDataStore.edit { prefs ->
            if (distinct.isEmpty()) {
                prefs.remove(targetDevicesJsonKey)
                prefs.remove(deviceNameKey)
                prefs.remove(deviceAddressKey)
            } else {
                prefs[targetDevicesJsonKey] = encodeTargetDevices(distinct)
                prefs[deviceNameKey] = distinct.first().name
                prefs[deviceAddressKey] = distinct.first().address
            }
        }
    }

    suspend fun setActiveSession(name: String, address: String, startTime: Long) {
        setActiveSessions(listOf(ActiveSessionInfo(name, address, startTime)))
    }

    suspend fun setActiveSessions(sessions: List<ActiveSessionInfo>) {
        val distinct = sessions.distinctBy { it.deviceAddress.uppercase() }
        context.settingsDataStore.edit { prefs ->
            if (distinct.isEmpty()) {
                prefs.remove(activeSessionsJsonKey)
                prefs.remove(activeDeviceNameKey)
                prefs.remove(activeDeviceAddressKey)
                prefs.remove(activeStartTimeKey)
            } else {
                prefs[activeSessionsJsonKey] = encodeActiveSessions(distinct)
                prefs[activeDeviceNameKey] = distinct.first().deviceName
                prefs[activeDeviceAddressKey] = distinct.first().deviceAddress
                prefs[activeStartTimeKey] = distinct.first().startTime
            }
        }
    }

    suspend fun clearActiveSession() {
        setActiveSessions(emptyList())
    }

    suspend fun removeActiveSession(address: String) {
        context.settingsDataStore.edit { prefs ->
            val remaining = decodeActiveSessions(prefs[activeSessionsJsonKey]).filterNot {
                it.deviceAddress.equals(address, ignoreCase = true)
            }
            if (remaining.isEmpty()) {
                prefs.remove(activeSessionsJsonKey)
                prefs.remove(activeDeviceNameKey)
                prefs.remove(activeDeviceAddressKey)
                prefs.remove(activeStartTimeKey)
            } else {
                prefs[activeSessionsJsonKey] = encodeActiveSessions(remaining)
                prefs[activeDeviceNameKey] = remaining.first().deviceName
                prefs[activeDeviceAddressKey] = remaining.first().deviceAddress
                prefs[activeStartTimeKey] = remaining.first().startTime
            }
        }
    }

    suspend fun clearActiveSessionLegacy() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(activeDeviceNameKey)
            prefs.remove(activeDeviceAddressKey)
            prefs.remove(activeStartTimeKey)
            prefs.remove(activeSessionsJsonKey)
        }
    }

    suspend fun saveDailyLimitMillis(limitMillis: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[dailyLimitMillisKey] = limitMillis
        }
    }

    suspend fun saveSingleSessionLimitMillis(limitMillis: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[singleSessionLimitMillisKey] = limitMillis
        }
    }

    suspend fun saveBreakReminderMillis(limitMillis: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[breakReminderMillisKey] = limitMillis
        }
    }

    suspend fun saveWeeklyGoalMillis(limitMillis: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[weeklyGoalMillisKey] = limitMillis
        }
    }

    suspend fun saveBedtimeReminderSettings(enabled: Boolean, startMinutes: Int, endMinutes: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[bedtimeReminderEnabledKey] = enabled
            prefs[bedtimeReminderStartMinutesKey] = startMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
            prefs[bedtimeReminderEndMinutesKey] = endMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
        }
    }

    suspend fun saveSleepSettings(enabled: Boolean, startMinutes: Int, endMinutes: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[sleepEnabledKey] = enabled
            prefs[sleepStartMinutesKey] = startMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
            prefs[sleepEndMinutesKey] = endMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
        }
    }

    suspend fun saveBatteryPercent(percent: Int?) {
        context.settingsDataStore.edit { prefs ->
            if (percent == null) {
                prefs.remove(batteryPercentKey)
            } else {
                prefs[batteryPercentKey] = percent.coerceIn(0, 100)
            }
        }
    }

    suspend fun saveBatteryPercent(address: String, percent: Int?) {
        if (address.isBlank()) {
            saveBatteryPercent(percent)
            return
        }
        context.settingsDataStore.edit { prefs ->
            val current = decodeBatteryPercents(prefs[batteryPercentsJsonKey]).toMutableMap()
            val normalized = address.uppercase()
            if (percent == null) {
                current.remove(normalized)
            } else {
                val value = percent.coerceIn(0, 100)
                current[normalized] = value
                prefs[batteryPercentKey] = value
            }
            if (current.isEmpty()) {
                prefs.remove(batteryPercentsJsonKey)
            } else {
                prefs[batteryPercentsJsonKey] = encodeBatteryPercents(current)
            }
        }
    }

    suspend fun saveLastLimitAlertDate(date: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[lastLimitAlertDateKey] = date
        }
    }

    suspend fun saveLastSessionLimitAlertKey(key: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[lastSessionLimitAlertKey] = key
        }
    }

    suspend fun saveLastBreakReminderKey(key: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[lastBreakReminderKey] = key
        }
    }

    suspend fun saveLastWeeklyGoalAlertWeek(week: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[lastWeeklyGoalAlertWeekKey] = week
        }
    }

    suspend fun saveLastBedtimeAlertDate(date: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[lastBedtimeAlertDateKey] = date
        }
    }

    suspend fun saveHideFromRecents(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[hideFromRecentsKey] = enabled
        }
    }

    suspend fun saveBootAutoStart(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[bootAutoStartKey] = enabled
        }
    }

    suspend fun saveCurrentAudioInfo(info: CurrentAudioInfo) {
        context.settingsDataStore.edit { prefs ->
            prefs[audioPackageKey] = info.packageName
            prefs[audioAppNameKey] = info.appName
            prefs[audioTitleKey] = info.title
            prefs[audioUpdatedAtKey] = info.updatedAt
        }
    }

    suspend fun clearCurrentAudioInfo(packageName: String? = null) {
        context.settingsDataStore.edit { prefs ->
            if (packageName != null && prefs[audioPackageKey] != packageName) return@edit
            prefs.remove(audioPackageKey)
            prefs.remove(audioAppNameKey)
            prefs.remove(audioTitleKey)
            prefs.remove(audioUpdatedAtKey)
        }
    }

    private fun encodeTargetDevices(devices: List<TargetDevice>): String {
        val array = JSONArray()
        devices.forEach {
            array.put(JSONObject().put("name", it.name).put("address", it.address))
        }
        return array.toString()
    }

    private fun decodeTargetDevices(raw: String?): List<TargetDevice> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val name = item.optString("name")
                    val address = item.optString("address")
                    if (name.isNotBlank() && address.isNotBlank()) add(TargetDevice(name, address))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeActiveSessions(sessions: List<ActiveSessionInfo>): String {
        val array = JSONArray()
        sessions.forEach {
            array.put(
                JSONObject()
                    .put("name", it.deviceName)
                    .put("address", it.deviceAddress)
                    .put("startTime", it.startTime)
            )
        }
        return array.toString()
    }

    private fun decodeActiveSessions(raw: String?): List<ActiveSessionInfo> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val name = item.optString("name")
                    val address = item.optString("address")
                    val startTime = item.optLong("startTime", -1L)
                    if (name.isNotBlank() && address.isNotBlank() && startTime > 0L) {
                        add(ActiveSessionInfo(name, address, startTime))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeBatteryPercents(levels: Map<String, Int>): String {
        val json = JSONObject()
        levels.forEach { (address, percent) ->
            if (address.isNotBlank() && percent in 0..100) {
                json.put(address.uppercase(), percent)
            }
        }
        return json.toString()
    }

    private fun decodeBatteryPercents(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key ->
                    val value = json.optInt(key, -1)
                    if (key.isNotBlank() && value in 0..100) put(key.uppercase(), value)
                }
            }
        }.getOrDefault(emptyMap())
    }
}
