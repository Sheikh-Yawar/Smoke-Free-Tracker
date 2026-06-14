package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun toStatus(value: String): DayStatus {
        return try {
            DayStatus.valueOf(value)
        } catch (e: Exception) {
            DayStatus.SMOKE_FREE
        }
    }

    @TypeConverter
    fun fromStatus(status: DayStatus): String {
        return status.name
    }
}

@Database(entities = [DailyLog::class, ChatMessage::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun chatMessageDao(): ChatMessageDao
}
