package com.example.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

interface LocalRepository {
    val allLogs: Flow<List<DailyLog>>
    val userSettings: Flow<UserSettings>
    val allChatMessages: Flow<List<ChatMessage>>

    suspend fun insertLog(log: DailyLog)
    suspend fun deleteLogByDate(date: String)
    suspend fun deleteLogsBeforeDate(date: String)
    suspend fun clearAllLogs()

    suspend fun updateSettings(update: (UserSettings) -> UserSettings)
    suspend fun clearSettings()
    suspend fun backfillLogsFromQuitDate(quitDate: LocalDate)
    suspend fun insertChatMessage(message: ChatMessage)
    suspend fun clearChatMessages()
}

class LocalRepositoryImpl(
    private val logDao: DailyLogDao,
    private val chatMessageDao: ChatMessageDao,
    private val appPreferences: AppPreferences
) : LocalRepository {
    override val allLogs: Flow<List<DailyLog>> = logDao.getAllLogsFlow()
    override val userSettings: Flow<UserSettings> = appPreferences.userSettingsFlow
    override val allChatMessages: Flow<List<ChatMessage>> = chatMessageDao.getAllMessages()

    override suspend fun insertLog(log: DailyLog) {
        logDao.insertLog(log)
    }

    override suspend fun deleteLogByDate(date: String) {
        logDao.deleteLogByDate(date)
    }

    override suspend fun deleteLogsBeforeDate(date: String) {
        logDao.deleteLogsBeforeDate(date)
    }

    override suspend fun clearAllLogs() {
        logDao.clearAllLogs()
    }

    override suspend fun updateSettings(update: (UserSettings) -> UserSettings) {
        appPreferences.updateSettings(update)
    }

    override suspend fun clearSettings() {
        appPreferences.clearSettings()
    }

    override suspend fun insertChatMessage(message: ChatMessage) {
        chatMessageDao.insertMessage(message)
    }

    override suspend fun clearChatMessages() {
        chatMessageDao.clearMessages()
    }

    override suspend fun backfillLogsFromQuitDate(quitDate: LocalDate) {
        val today = LocalDate.now()
        var current = quitDate
        while (!current.isAfter(today)) {
            val dateStr = current.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val existing = logDao.getLogByDate(dateStr)
            if (existing == null) {
                logDao.insertLog(
                    DailyLog(
                        date = dateStr,
                        status = DayStatus.SMOKE_FREE,
                        cigarettesSmoked = 0
                    )
                )
            }
            current = current.plusDays(1)
        }
    }
}
