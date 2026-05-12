package com.example.bluetoothusage.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    @Insert
    suspend fun insert(record: UsageRecord): Long

    @Query(
        """
        SELECT COUNT(*) FROM usage_records
        WHERE deviceAddress = :deviceAddress COLLATE NOCASE
        AND startTime BETWEEN :startMin AND :startMax
        AND endTime BETWEEN :endMin AND :endMax
        """
    )
    suspend fun countNearDuplicate(
        deviceAddress: String,
        startMin: Long,
        startMax: Long,
        endMin: Long,
        endMax: Long
    ): Int

    @Update
    suspend fun update(record: UsageRecord)

    @Delete
    suspend fun delete(record: UsageRecord)

    @Query("DELETE FROM usage_records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM usage_records ORDER BY startTime DESC")
    fun observeAll(): Flow<List<UsageRecord>>

    @Query(
        """
        SELECT COALESCE(SUM(
            (CASE WHEN endTime < :endMillis THEN endTime ELSE :endMillis END) -
            (CASE WHEN startTime > :startMillis THEN startTime ELSE :startMillis END)
        ), 0)
        FROM usage_records
        WHERE startTime < :endMillis AND endTime > :startMillis
        """
    )
    fun observeDurationInRange(startMillis: Long, endMillis: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(
            (CASE WHEN endTime < :endMillis THEN endTime ELSE :endMillis END) -
            (CASE WHEN startTime > :startMillis THEN startTime ELSE :startMillis END)
        ), 0)
        FROM usage_records
        WHERE startTime < :endMillis AND endTime > :startMillis
        """
    )
    suspend fun getDurationInRange(startMillis: Long, endMillis: Long): Long

    @Query(
        """
        SELECT * FROM usage_records
        WHERE startTime < :endMillis AND endTime > :startMillis
        ORDER BY startTime DESC
        """
    )
    fun observeRecordsInRange(startMillis: Long, endMillis: Long): Flow<List<UsageRecord>>

    @Query(
        """
        SELECT * FROM usage_records
        WHERE startTime < :endMillis AND endTime > :startMillis
        ORDER BY startTime ASC
        """
    )
    suspend fun getRecordsInRange(startMillis: Long, endMillis: Long): List<UsageRecord>

    @Query("SELECT * FROM usage_records ORDER BY startTime DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<UsageRecord>>

    @Query("SELECT * FROM usage_records ORDER BY startTime DESC")
    suspend fun getAllRecords(): List<UsageRecord>
}
