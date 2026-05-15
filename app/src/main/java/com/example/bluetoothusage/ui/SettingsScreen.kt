package com.example.bluetoothusage.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bluetoothusage.service.MediaNotificationListenerService
import com.example.bluetoothusage.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onDailyLimitHoursChange: (Float) -> Unit,
    onDailyLimitShortcut: (Long) -> Unit,
    onSingleSessionLimitChange: (Long) -> Unit,
    onBreakReminderChange: (Long) -> Unit,
    onWeeklyGoalChange: (Long) -> Unit,
    onBedtimeReminderEnabledChange: (Boolean) -> Unit,
    onAdjustBedtimeReminderStart: (Int) -> Unit,
    onAdjustBedtimeReminderEnd: (Int) -> Unit,
    onSleepEnabledChange: (Boolean) -> Unit,
    onAdjustSleepStart: (Int) -> Unit,
    onAdjustSleepEnd: (Int) -> Unit,
    onHideFromRecentsChange: (Boolean) -> Unit,
    onBootAutoStartChange: (Boolean) -> Unit,
    onExportDiagnostics: () -> Unit,
    onCleanupInvalidRecords: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DailyLimitCard(
                    dailyLimitMillis = state.dailyLimitMillis,
                    onDailyLimitHoursChange = onDailyLimitHoursChange,
                    onDailyLimitShortcut = onDailyLimitShortcut
                )
            }
            item {
                UsageGoalReminderCard(
                    singleSessionLimitMillis = state.singleSessionLimitMillis,
                    breakReminderMillis = state.breakReminderMillis,
                    weeklyGoalMillis = state.weeklyGoalMillis,
                    bedtimeEnabled = state.bedtimeReminderEnabled,
                    bedtimeStartMinutes = state.bedtimeReminderStartMinutes,
                    bedtimeEndMinutes = state.bedtimeReminderEndMinutes,
                    onSingleSessionLimitChange = onSingleSessionLimitChange,
                    onBreakReminderChange = onBreakReminderChange,
                    onWeeklyGoalChange = onWeeklyGoalChange,
                    onBedtimeEnabledChange = onBedtimeReminderEnabledChange,
                    onAdjustBedtimeStart = onAdjustBedtimeReminderStart,
                    onAdjustBedtimeEnd = onAdjustBedtimeReminderEnd
                )
            }
            item {
                NotificationAccessCard()
            }
            item {
                RecentsVisibilityCard(
                    hideFromRecents = state.hideFromRecents,
                    onHideFromRecentsChange = onHideFromRecentsChange
                )
            }
            item {
                BootAutoStartCard(
                    enabled = state.bootAutoStart,
                    onEnabledChange = onBootAutoStartChange
                )
            }
            item {
                DiagnosticsCard(
                    lastCleanupSummary = state.lastCleanupSummary,
                    onExportDiagnostics = onExportDiagnostics,
                    onCleanupInvalidRecords = onCleanupInvalidRecords
                )
            }
            item {
                SleepSettingsCard(
                    enabled = state.sleepEnabled,
                    startMinutes = state.sleepStartMinutes,
                    endMinutes = state.sleepEndMinutes,
                    onEnabledChange = onSleepEnabledChange,
                    onAdjustStart = onAdjustSleepStart,
                    onAdjustEnd = onAdjustSleepEnd
                )
            }
        }
    }
}

@Composable
private fun UsageGoalReminderCard(
    singleSessionLimitMillis: Long,
    breakReminderMillis: Long,
    weeklyGoalMillis: Long,
    bedtimeEnabled: Boolean,
    bedtimeStartMinutes: Int,
    bedtimeEndMinutes: Int,
    onSingleSessionLimitChange: (Long) -> Unit,
    onBreakReminderChange: (Long) -> Unit,
    onWeeklyGoalChange: (Long) -> Unit,
    onBedtimeEnabledChange: (Boolean) -> Unit,
    onAdjustBedtimeStart: (Int) -> Unit,
    onAdjustBedtimeEnd: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("目标提醒", style = MaterialTheme.typography.titleMedium)
                Text(
                    "这些提醒只提示休息或留意使用时间，不会停止计时。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ReminderSliderRow(
                title = "单次最长",
                valueMillis = singleSessionLimitMillis,
                valueRangeHours = 0.5f..4f,
                steps = 6,
                onValueChange = onSingleSessionLimitChange
            )
            ReminderSliderRow(
                title = "连续佩戴休息",
                valueMillis = breakReminderMillis,
                valueRangeHours = 0.25f..3f,
                steps = 10,
                onValueChange = onBreakReminderChange
            )
            ReminderSliderRow(
                title = "每周目标",
                valueMillis = weeklyGoalMillis,
                valueRangeHours = 7f..56f,
                steps = 48,
                onValueChange = onWeeklyGoalChange
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("睡前使用提醒", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatMinutesOfDay(bedtimeStartMinutes)} - ${formatMinutesOfDay(bedtimeEndMinutes)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = bedtimeEnabled, onCheckedChange = onBedtimeEnabledChange)
            }
            TimeAdjustRow("睡前开始", bedtimeStartMinutes, bedtimeEnabled, onAdjustBedtimeStart)
            TimeAdjustRow("睡前结束", bedtimeEndMinutes, bedtimeEnabled, onAdjustBedtimeEnd)
        }
    }
}

@Composable
private fun ReminderSliderRow(
    title: String,
    valueMillis: Long,
    valueRangeHours: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Long) -> Unit
) {
    val hours = (valueMillis / (60f * 60f * 1_000f)).coerceIn(valueRangeHours.start, valueRangeHours.endInclusive)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(formatDuration(valueMillis), color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = hours,
            onValueChange = { value ->
                val roundedQuarterHours = (value * 4f).toInt().coerceAtLeast(1) / 4f
                onValueChange(hoursToMillis(roundedQuarterHours))
            },
            valueRange = valueRangeHours,
            steps = steps
        )
    }
}

@Composable
private fun DiagnosticsCard(
    lastCleanupSummary: String,
    onExportDiagnostics: () -> Unit,
    onCleanupInvalidRecords: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("诊断日志", style = MaterialTheme.typography.titleMedium)
            Text(
                "导出当前设备、活跃会话、历史记录和统计状态，方便排查重复记录或条带显示问题。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onExportDiagnostics,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("导出诊断日志")
            }
            Button(
                onClick = onCleanupInvalidRecords,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("清理异常记录")
            }
            if (lastCleanupSummary.isNotBlank()) {
                Text(
                    lastCleanupSummary,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun BootAutoStartCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("开机自启动", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (enabled) {
                        "手机重启后自动恢复蓝牙监听。"
                    } else {
                        "重启后需要打开 App 才会恢复监听。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun RecentsVisibilityCard(
    hideFromRecents: Boolean,
    onHideFromRecentsChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("不在最近任务中显示", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (hideFromRecents) {
                        "从系统最近使用的应用列表中隐藏这个任务。"
                    } else {
                        "保留在最近任务中，便于快速切回。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = hideFromRecents,
                onCheckedChange = onHideFromRecentsChange
            )
        }
    }
}

@Composable
private fun NotificationAccessCard() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("当前播放 App 监控", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (enabled) {
                            "已获得通知使用权，会从媒体通知识别播放来源。"
                        } else {
                            "需要开启通知使用权，才能识别当前播放音频的软件。"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (enabled) "已开启" else "未开启",
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    enabled = isNotificationListenerEnabled(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("打开通知使用权设置")
            }
        }
    }
}

@Composable
private fun DailyLimitCard(
    dailyLimitMillis: Long,
    onDailyLimitHoursChange: (Float) -> Unit,
    onDailyLimitShortcut: (Long) -> Unit
) {
    val hours = (dailyLimitMillis / (60f * 60f * 1_000f)).coerceIn(0.5f, 8f)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("每日最长使用时间", style = MaterialTheme.typography.titleMedium)
                Text(formatDuration(dailyLimitMillis), fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = hours,
                onValueChange = onDailyLimitHoursChange,
                valueRange = 0.5f..8f,
                steps = 14
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShortcutButton("1小时", Modifier.weight(1f)) { onDailyLimitShortcut(hoursToMillis(1f)) }
                    ShortcutButton("2小时", Modifier.weight(1f)) { onDailyLimitShortcut(hoursToMillis(2f)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShortcutButton("3小时", Modifier.weight(1f)) { onDailyLimitShortcut(hoursToMillis(3f)) }
                    ShortcutButton("6小时", Modifier.weight(1f)) { onDailyLimitShortcut(hoursToMillis(6f)) }
                }
            }
        }
    }
}

@Composable
private fun SleepSettingsCard(
    enabled: Boolean,
    startMinutes: Int,
    endMinutes: Int,
    onEnabledChange: (Boolean) -> Unit,
    onAdjustStart: (Int) -> Unit,
    onAdjustEnd: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("睡眠期间不记录", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "连接跨过睡眠边界时自动分段",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            TimeAdjustRow("开始", startMinutes, enabled, onAdjustStart)
            TimeAdjustRow("结束", endMinutes, enabled, onAdjustEnd)
        }
    }
}

@Composable
private fun TimeAdjustRow(
    label: String,
    minutes: Int,
    enabled: Boolean,
    onAdjust: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onAdjust(-15) }, enabled = enabled) { Text("-15") }
            Text(formatMinutesOfDay(minutes), fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { onAdjust(15) }, enabled = enabled) { Text("+15") }
        }
    }
}

@Composable
private fun ShortcutButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier) {
        Text(text)
    }
}

private fun hoursToMillis(hours: Float): Long {
    return (hours * 60f * 60f * 1_000f).toLong()
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ).orEmpty()
    val component = ComponentName(context, MediaNotificationListenerService::class.java)
    return enabledListeners.split(":").any {
        it.equals(component.flattenToString(), ignoreCase = true) ||
            it.equals(component.flattenToShortString(), ignoreCase = true)
    }
}
