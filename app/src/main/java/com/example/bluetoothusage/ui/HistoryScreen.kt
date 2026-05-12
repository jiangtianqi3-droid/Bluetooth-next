package com.example.bluetoothusage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bluetoothusage.data.UsageRecord
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    records: List<UsageRecord>,
    onBack: () -> Unit,
    onUpdateRecord: (UsageRecord, Long, Long, String) -> Unit,
    onDeleteRecord: (UsageRecord) -> Unit
) {
    var editingRecord by remember { mutableStateOf<UsageRecord?>(null) }
    var deletingRecord by remember { mutableStateOf<UsageRecord?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史记录") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (records.isEmpty()) {
                item { Text("暂无历史记录") }
            } else {
                items(records, key = { it.id }) { record ->
                    UsageRecordRow(
                        record = record,
                        trailing = {
                            Column {
                                TextButton(onClick = { editingRecord = record }) { Text("编辑") }
                                TextButton(onClick = { deletingRecord = record }) { Text("删除") }
                            }
                        }
                    )
                }
            }
        }
    }

    editingRecord?.let { record ->
        EditRecordDialog(
            record = record,
            onDismiss = { editingRecord = null },
            onSave = { start, end, note ->
                onUpdateRecord(record, start, end, note)
                editingRecord = null
            }
        )
    }

    deletingRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { deletingRecord = null },
            title = { Text("删除记录") },
            text = { Text("确定删除 ${record.deviceName} 的这条使用记录吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRecord(record)
                        deletingRecord = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingRecord = null }) { Text("取消") }
            }
        )
    }
}

@Composable
fun EditRecordDialog(
    record: UsageRecord,
    onDismiss: () -> Unit,
    onSave: (Long, Long, String) -> Unit
) {
    var startText by remember(record.id) { mutableStateOf(formatEditTime(record.startTime)) }
    var endText by remember(record.id) { mutableStateOf(formatEditTime(record.endTime)) }
    var noteText by remember(record.id) { mutableStateOf(record.note) }
    var errorText by remember(record.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (record.audioAppName.isNotBlank()) {
                    Text(
                        text = "播放应用：${record.audioAppName}${record.mediaTitleSnapshot.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text("开始时间") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = { Text("结束时间") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("备注") },
                    minLines = 2
                )
                Text("格式：HH:mm，仅修改当天时间", color = MaterialTheme.colorScheme.onSurfaceVariant)
                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val start = parseRecordTime(record.startTime, startText)
                    val end = parseRecordTime(record.startTime, endText)
                    when {
                        start == null || end == null -> errorText = "时间格式不正确"
                        end <= start -> errorText = "结束时间必须晚于开始时间"
                        else -> onSave(start, end, noteText)
                    }
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun parseRecordTime(anchorMillis: Long, text: String): Long? {
    return runCatching {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(anchorMillis).atZone(zone).toLocalDate()
        val time = LocalTime.parse(text.trim(), DateTimeFormatter.ofPattern("HH:mm"))
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()
}

private fun formatEditTime(timeMillis: Long): String {
    return Instant.ofEpochMilli(timeMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}
