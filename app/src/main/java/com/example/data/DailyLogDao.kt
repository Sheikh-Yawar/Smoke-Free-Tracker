package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyLogDao {
    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    fun getAllLogsFlow(): Flow<List<DailyLog>>

    @Query("SELECT * FROM daily_logs WHERE date = :date LIMIT 1")
    suspend fun getLogByDate(date: String): DailyLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DailyLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: DailyLog)

    @Query("DELETE FROM daily_logs WHERE date = :date")
    suspend fun deleteLogByDate(date: String)

    @Query("DELETE FROM daily_logs WHERE date < :date")
    suspend fun deleteLogsBeforeDate(date: String)

    @Query("DELETE FROM daily_logs")
    suspend fun clearAllLogs()
}
