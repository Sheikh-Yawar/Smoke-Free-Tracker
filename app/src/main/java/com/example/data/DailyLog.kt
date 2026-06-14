package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_logs")
data class DailyLog(
    @PrimaryKey val date: String,    // "yyyy-MM-dd"
    val status: DayStatus,
    val cigarettesSmoked: Int = 0,
    val notes: String? = null
)
