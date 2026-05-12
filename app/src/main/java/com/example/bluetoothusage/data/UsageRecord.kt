package com.example.bluetoothusage.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_records")
data class UsageRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceName: String,
    val deviceAddress: String,
    val startTime: Long,
    val endTime: Long,
    val durationMillis: Long,
    val note: String = "",
    val audioAppPackage: String = "",
    val audioAppName: String = "",
    val mediaTitleSnapshot: String = ""
)
