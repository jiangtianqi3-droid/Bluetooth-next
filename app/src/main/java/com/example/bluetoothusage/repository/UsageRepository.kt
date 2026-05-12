package com.example.bluetoothusage.repository

import com.example.bluetoothusage.data.UsageDao
import com.example.bluetoothusage.data.UsageRecord
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class DailyUsage(
    val date: LocalDate,
    val durationMillis: Long
)

data class CleanupResult(
    val deletedCount: Int,
    val nonTargetCount: Int,
    val shortCount: Int,
    val duplicateCount: Int
)

class UsageRepository(private val usageDao: UsageDao) {
    fun observeHistory(): Flow<List<UsageRecord>> = usageDao.observeRecent()

    fun observeHistory(targetAddresses: Set<String>): Flow<List<UsageRecord>> {
        return usageDao.observeAll().map { records ->
            records
                .filterValidTargets(targetAddresses)
                .dedupeNearRecords()
                .sortedByDescending { it.startTime }
                .take(100)
        }
    }

    fun observeTodayDuration(): Flow<Long> {
        return observeMergedDurationInRange(startOfTodayMillis(), startOfTomorrowMillis())
    }

    fun observeTodayDuration(settings: UsageSettings): Flow<Long> {
        return observeAwakeMergedDurationInRange(startOfTodayMillis(), startOfTomorrowMillis(), settings)
    }

    fun observeTodayDuration(settings: UsageSettings, targetAddresses: Set<String>): Flow<Long> {
        return observeAwakeMergedDurationInRange(startOfTodayMillis(), startOfTomorrowMillis(), settings, targetAddresses)
    }

    fun observeWeekDuration(): Flow<Long> {
        return observeMergedDurationInRange(startOfWeekMillis(), startOfNextWeekMillis())
    }

    fun observeWeekDuration(settings: UsageSettings): Flow<Long> {
        return observeAwakeMergedDurationInRange(startOfWeekMillis(), startOfNextWeekMillis(), settings)
    }

    fun observeWeekDuration(settings: UsageSettings, targetAddresses: Set<String>): Flow<Long> {
        return observeAwakeMergedDurationInRange(startOfWeekMillis(), startOfNextWeekMillis(), settings, targetAddresses)
    }

    fun observeDurationInRange(startMillis: Long, endMillis: Long): Flow<Long> {
        return observeMergedDurationInRange(startMillis, endMillis)
    }

    fun observeDurationInRange(startMillis: Long, endMillis: Long, settings: UsageSettings): Flow<Long> {
        return observeAwakeMergedDurationInRange(startMillis, endMillis, settings)
    }

    fun observeRecordsInMonth(month: YearMonth): Flow<List<UsageRecord>> {
        val zone = ZoneId.systemDefault()
        val monthStart = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return usageDao.observeRecordsInRange(monthStart, monthEnd)
    }

    fun observeRecordsInMonth(month: YearMonth, targetAddresses: Set<String>): Flow<List<UsageRecord>> {
        val zone = ZoneId.systemDefault()
        val monthStart = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return usageDao.observeRecordsInRange(monthStart, monthEnd).map { records ->
            records.filterValidTargets(targetAddresses).dedupeNearRecords()
        }
    }

    suspend fun getDurationInRange(startMillis: Long, endMillis: Long): Long {
        return usageDao.getRecordsInRange(startMillis, endMillis).mergedOverlapMillis(startMillis, endMillis)
    }

    suspend fun getDurationInRange(startMillis: Long, endMillis: Long, settings: UsageSettings): Long {
        return usageDao.getRecordsInRange(startMillis, endMillis)
            .mergedAwakeOverlapMillis(startMillis, endMillis, settings)
    }

    suspend fun getDurationInRange(
        startMillis: Long,
        endMillis: Long,
        settings: UsageSettings,
        targetAddresses: Set<String>
    ): Long {
        return usageDao.getRecordsInRange(startMillis, endMillis)
            .filterValidTargets(targetAddresses)
            .mergedAwakeOverlapMillis(startMillis, endMillis, settings)
    }

    fun observeCalendarMonth(month: YearMonth): Flow<List<DailyUsage>> {
        val zone = ZoneId.systemDefault()
        val monthStart = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return usageDao.observeRecordsInRange(monthStart, monthEnd).map { records ->
            (1..month.lengthOfMonth()).map { day ->
                val date = month.atDay(day)
                val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                DailyUsage(
                    date = date,
                    durationMillis = records.mergedOverlapMillis(dayStart, dayEnd)
                )
            }
        }
    }

    fun observeCalendarMonth(month: YearMonth, settings: UsageSettings): Flow<List<DailyUsage>> {
        val zone = ZoneId.systemDefault()
        val monthStart = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return usageDao.observeRecordsInRange(monthStart, monthEnd).map { records ->
            (1..month.lengthOfMonth()).map { day ->
                val date = month.atDay(day)
                val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                DailyUsage(
                    date = date,
                    durationMillis = records.mergedAwakeOverlapMillis(dayStart, dayEnd, settings)
                )
            }
        }
    }

    fun observeCalendarMonth(
        month: YearMonth,
        settings: UsageSettings,
        targetAddresses: Set<String>
    ): Flow<List<DailyUsage>> {
        val zone = ZoneId.systemDefault()
        val monthStart = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return usageDao.observeRecordsInRange(monthStart, monthEnd).map { records ->
            val filtered = records.filterValidTargets(targetAddresses).dedupeNearRecords()
            (1..month.lengthOfMonth()).map { day ->
                val date = month.atDay(day)
                val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                DailyUsage(
                    date = date,
                    durationMillis = filtered.mergedAwakeOverlapMillis(dayStart, dayEnd, settings)
                )
            }
        }
    }

    suspend fun addRecord(record: UsageRecord): Long {
        if (record.durationMillis < MIN_VALID_RECORD_MILLIS) return -1L
        val toleranceMillis = 5_000L
        val duplicateCount = usageDao.countNearDuplicate(
            deviceAddress = record.deviceAddress,
            startMin = record.startTime - toleranceMillis,
            startMax = record.startTime + toleranceMillis,
            endMin = record.endTime - toleranceMillis,
            endMax = record.endTime + toleranceMillis
        )
        if (duplicateCount > 0) return -1L
        return usageDao.insert(record)
    }

    suspend fun addRecord(record: UsageRecord, targetAddresses: Set<String>): Long {
        if (!record.isForTarget(targetAddresses)) return -1L
        if (record.durationMillis < MIN_VALID_RECORD_MILLIS) return -1L
        val nearbyDuplicate = usageDao
            .getRecordsInRange(record.startTime - DUPLICATE_TOLERANCE_MILLIS, record.endTime + DUPLICATE_TOLERANCE_MILLIS)
            .filterValidTargets(targetAddresses)
            .any { it.isNearDuplicateOf(record) }
        if (nearbyDuplicate) return -1L
        return usageDao.insert(record)
    }

    suspend fun updateRecord(record: UsageRecord) = usageDao.update(record)

    suspend fun deleteRecord(record: UsageRecord) = usageDao.delete(record)

    suspend fun cleanupInvalidRecords(targetAddresses: Set<String>): CleanupResult {
        if (targetAddresses.isEmpty()) return CleanupResult(0, 0, 0, 0)
        val all = usageDao.getAllRecords()
        val idsToDelete = linkedSetOf<Long>()
        var nonTargetCount = 0
        var shortCount = 0
        var duplicateCount = 0

        all.forEach { record ->
            when {
                !record.isForTarget(targetAddresses) -> {
                    idsToDelete += record.id
                    nonTargetCount++
                }
                record.durationMillis < MIN_VALID_RECORD_MILLIS -> {
                    idsToDelete += record.id
                    shortCount++
                }
            }
        }

        val kept = mutableListOf<UsageRecord>()
        all.filter { it.id !in idsToDelete }
            .sortedWith(compareBy<UsageRecord> { it.deviceAddress.uppercase() }.thenBy { it.startTime })
            .forEach { record ->
                val existingIndex = kept.indexOfFirst { it.isNearDuplicateOf(record) }
                if (existingIndex < 0) {
                    kept += record
                } else {
                    val existing = kept[existingIndex]
                    duplicateCount++
                    if (record.durationMillis > existing.durationMillis) {
                        idsToDelete += existing.id
                        kept[existingIndex] = record
                    } else {
                        idsToDelete += record.id
                    }
                }
            }

        if (idsToDelete.isNotEmpty()) {
            usageDao.deleteByIds(idsToDelete.toList())
        }
        return CleanupResult(idsToDelete.size, nonTargetCount, shortCount, duplicateCount)
    }

    suspend fun getAllRecords(): List<UsageRecord> = usageDao.getAllRecords()

    private fun startOfTodayMillis(): Long {
        return LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun startOfTomorrowMillis(): Long {
        return LocalDate.now()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun startOfWeekMillis(): Long {
        return LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun startOfNextWeekMillis(): Long {
        return LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun UsageRecord.overlapMillis(startMillis: Long, endMillis: Long): Long {
        val overlapStart = maxOf(startTime, startMillis)
        val overlapEnd = minOf(endTime, endMillis)
        return (overlapEnd - overlapStart).coerceAtLeast(0)
    }

    private fun observeMergedDurationInRange(startMillis: Long, endMillis: Long): Flow<Long> {
        return usageDao.observeRecordsInRange(startMillis, endMillis).map { records ->
            records.mergedOverlapMillis(startMillis, endMillis)
        }
    }

    private fun observeAwakeMergedDurationInRange(
        startMillis: Long,
        endMillis: Long,
        settings: UsageSettings
    ): Flow<Long> {
        return usageDao.observeRecordsInRange(startMillis, endMillis).map { records ->
            records.mergedAwakeOverlapMillis(startMillis, endMillis, settings)
        }
    }

    private fun observeAwakeMergedDurationInRange(
        startMillis: Long,
        endMillis: Long,
        settings: UsageSettings,
        targetAddresses: Set<String>
    ): Flow<Long> {
        return usageDao.observeRecordsInRange(startMillis, endMillis).map { records ->
            records
                .filterValidTargets(targetAddresses)
                .dedupeNearRecords()
                .mergedAwakeOverlapMillis(startMillis, endMillis, settings)
        }
    }

    private fun List<UsageRecord>.mergedOverlapMillis(startMillis: Long, endMillis: Long): Long {
        val ranges = mapNotNull { record ->
            val start = maxOf(record.startTime, startMillis)
            val end = minOf(record.endTime, endMillis)
            if (end > start) start to end else null
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

    private fun List<UsageRecord>.mergedAwakeOverlapMillis(
        startMillis: Long,
        endMillis: Long,
        settings: UsageSettings
    ): Long {
        if (!settings.sleepEnabled || settings.sleepStartMinutes == settings.sleepEndMinutes) {
            return mergedOverlapMillis(startMillis, endMillis)
        }
        val awakeRanges = awakeRangesMillis(startMillis, endMillis, settings)
        if (awakeRanges.isEmpty()) return 0L
        val ranges = flatMap { record ->
            awakeRanges.mapNotNull { (awakeStart, awakeEnd) ->
                val start = maxOf(record.startTime, startMillis, awakeStart)
                val end = minOf(record.endTime, endMillis, awakeEnd)
                if (end > start) start to end else null
            }
        }.sortedBy { it.first }
        return ranges.mergedMillis()
    }

    private fun List<Pair<Long, Long>>.mergedMillis(): Long {
        if (isEmpty()) return 0L
        var total = 0L
        var currentStart = first().first
        var currentEnd = first().second
        drop(1).forEach { (start, end) ->
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
        val zone = ZoneId.systemDefault()
        val minuteRanges = awakeMinuteRanges(settings)
        if (minuteRanges.isEmpty()) return emptyList()
        val firstDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        val lastDate = Instant.ofEpochMilli(endMillis - 1L).atZone(zone).toLocalDate()
        val ranges = mutableListOf<Pair<Long, Long>>()
        var date = firstDate
        while (!date.isAfter(lastDate)) {
            val dayStart = date.atStartOfDay(zone)
            minuteRanges.forEach { (startMinute, endMinute) ->
                val rangeStart = dayStart.plusMinutes(startMinute.toLong()).toInstant().toEpochMilli()
                val rangeEnd = dayStart.plusMinutes(endMinute.toLong()).toInstant().toEpochMilli()
                val clippedStart = maxOf(rangeStart, startMillis)
                val clippedEnd = minOf(rangeEnd, endMillis)
                if (clippedEnd > clippedStart) ranges += clippedStart to clippedEnd
            }
            date = date.plusDays(1)
        }
        return ranges
    }

    private fun awakeMinuteRanges(settings: UsageSettings): List<Pair<Int, Int>> {
        val start = settings.sleepStartMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
        val end = settings.sleepEndMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
        if (!settings.sleepEnabled || start == end) return listOf(0 to MINUTES_PER_DAY)
        return if (start < end) {
            buildList {
                if (start > 0) add(0 to start)
                if (end < MINUTES_PER_DAY) add(end to MINUTES_PER_DAY)
            }
        } else {
            listOf(end to start)
        }
    }

    private fun List<UsageRecord>.filterValidTargets(targetAddresses: Set<String>): List<UsageRecord> {
        if (targetAddresses.isEmpty()) return emptyList()
        return filter { it.isForTarget(targetAddresses) && it.durationMillis >= MIN_VALID_RECORD_MILLIS }
    }

    private fun UsageRecord.isForTarget(targetAddresses: Set<String>): Boolean {
        return targetAddresses.any { it.equals(deviceAddress, ignoreCase = true) }
    }

    private fun List<UsageRecord>.dedupeNearRecords(): List<UsageRecord> {
        val kept = mutableListOf<UsageRecord>()
        sortedWith(compareBy<UsageRecord> { it.deviceAddress.uppercase() }.thenBy { it.startTime }).forEach { record ->
            val existingIndex = kept.indexOfFirst { it.isNearDuplicateOf(record) }
            if (existingIndex < 0) {
                kept += record
            } else if (record.durationMillis > kept[existingIndex].durationMillis) {
                kept[existingIndex] = record
            }
        }
        return kept.sortedBy { it.startTime }
    }

    private fun UsageRecord.isNearDuplicateOf(other: UsageRecord): Boolean {
        return kotlin.math.abs(startTime - other.startTime) <= DUPLICATE_TOLERANCE_MILLIS &&
            kotlin.math.abs(endTime - other.endTime) <= DUPLICATE_TOLERANCE_MILLIS
    }

    companion object {
        const val MIN_VALID_RECORD_MILLIS = 15_000L
        private const val DUPLICATE_TOLERANCE_MILLIS = 5_000L
    }
}
