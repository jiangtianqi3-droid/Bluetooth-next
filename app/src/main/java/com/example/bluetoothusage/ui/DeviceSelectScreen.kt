package com.example.bluetoothusage.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bluetoothusage.bluetooth.PairedBluetoothDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelectScreen(
    devices: List<PairedBluetoothDevice>,
    selectedAddresses: Set<String>,
    selectionGeneration: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onConfirm: (List<PairedBluetoothDevice>) -> Unit
) {
    val selected = remember(selectionGeneration) {
        mutableStateListOf<String>().apply { addAll(selectedAddresses) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择已配对设备") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    TextButton(onClick = onRefresh) { Text("刷新") }
                    TextButton(
                        onClick = {
                            onConfirm(devices.filter { device ->
                                selected.any { it.equals(device.address, ignoreCase = true) }
                            })
                        }
                    ) {
                        Text("确定")
                    }
                }
            )
        }
    ) { padding ->
        if (devices.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("没有读取到已配对设备", style = MaterialTheme.typography.titleMedium)
                Text("请确认已授予蓝牙权限，并在系统蓝牙设置中先完成耳机配对。")
                Button(onClick = onRefresh) { Text("重新读取") }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "可同时选择多个耳机，连接任一已选设备都会独立计时。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(devices, key = { it.address }) { device ->
                    val checked = selected.any { it.equals(device.address, ignoreCase = true) }
                    val toggle = {
                        if (checked) {
                            selected.removeAll { it.equals(device.address, ignoreCase = true) }
                        } else {
                            selected.add(device.address)
                        }
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggle() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { toggle() })
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    device.address,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DeviceColorDot(device.address)
                        }
                    }
                }
            }
        }
    }
}
