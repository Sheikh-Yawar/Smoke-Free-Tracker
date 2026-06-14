package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.SmokeFreeApp
import com.example.data.*
import com.example.ui.state.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class SmokeFreeViewModel(
    application: Application,
    private val repository: LocalRepository
) : AndroidViewModel(application) {

    // Expose flows from Repository
    val allLogs: StateFlow<List<DailyLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userSettings: StateFlow<UserSettings> = repository.userSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    val healthState: StateFlow<HealthState> = combine(
        repository.userSettings,
        repository.allLogs
    ) { settings, logs ->
        val logsMap = logs.associateBy { it.date }
        val quitDate = if (settings.quitDateMillis > 0L) {
            Instant.ofEpochMilli(settings.quitDateMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        } else {
            LocalDate.now()
        }

        // Single call — everything derived from this
        val impact = HealthImpactCalculator.compute(logsMap, quitDate, settings.quitDateMillis)

        val kpisNow = HealthImpactCalculator.computeKPIs(
            logsMap, quitDate, settings,
            cutoff = impact.cutoffNow
        )
        val kpisBaseline = HealthImpactCalculator.computeKPIs(
            logsMap, quitDate, settings,
            cutoff = impact.cutoffBaseline,
            ignoreRelapsesFrom = impact.ignoreRelapsesFrom
        )

        HealthState(
            impact = impact,
            kpisNow = kpisNow,
            kpisBaseline = kpisBaseline,
            streaks = calculateStreaks(logs, settings.quitDateMillis),
            settings = settings
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HealthState.empty()
    )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val settings = repository.userSettings.first()
                if (settings.quitDateMillis > 0L) {
                    val quitLocalDate = Instant.ofEpochMilli(settings.quitDateMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    val quitDateStr = quitLocalDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                    repository.deleteLogsBeforeDate(quitDateStr)
                }
            } catch (e: Exception) {
                // Silent catch for database pruning on startup
            }
        }
    }

    // Interactive UI States
    private val _showRelapseDialog = MutableStateFlow(false)
    val showRelapseDialog = _showRelapseDialog.asStateFlow()

    private val _showImportSuccess = MutableStateFlow(false)
    val showImportSuccess = _showImportSuccess.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState = _errorState.asStateFlow()

    fun logRelapseDay(date: LocalDate, cigarettesSmoked: Int) {
        viewModelScope.launch {
            val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            try {
                val log = DailyLog(
                    date = dateStr,
                    status = DayStatus.RELAPSED,
                    cigarettesSmoked = cigarettesSmoked,
                    notes = "Relapse logged from Dashboard"
                )
                repository.insertLog(log)
            } catch (e: Exception) {
                _errorState.value = "Something went wrong while saving your log."
            }
        }
    }

    fun resetQuitDateToNow() {
        updateQuitDate(System.currentTimeMillis())
    }
    
    fun recordCheckinClean() {
        viewModelScope.launch {
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val todayStr = formatter.format(java.util.Date())
            
            repository.updateSettings { it.copy(lastCheckinDate = todayStr) }
            logDay(todayStr, DayStatus.SMOKE_FREE, cigarettes = 0, notes = "Checked in: stayed smoke-free!")
        }
    }

    fun recordCheckinRelapse(targetDate: String, count: Int) {
        viewModelScope.launch {
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val todayStr = formatter.format(java.util.Date())
            
            repository.updateSettings { it.copy(lastCheckinDate = todayStr) }
            logDay(targetDate, DayStatus.RELAPSED, cigarettes = count, notes = "Relapse logged on $targetDate")
        }
    }

    fun recordCheckInAnswer(didSmoke: Boolean) {
        viewModelScope.launch {
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val todayStr = formatter.format(java.util.Date())
            repository.updateSettings { it.copy(lastCheckinDate = todayStr) }
        }
        if (!didSmoke) {
            recordCheckinClean()
        } else {
            _showRelapseDialog.value = true
        }
    }





    fun dismissError() { _errorState.value = null }
    fun dismissImportSuccess() { _showImportSuccess.value = false }
    fun dismissRelapseCompassion() { _showRelapseDialog.value = false }

    // Onboarding flow steps
    private val _onboardingStep = MutableStateFlow(1)
    val onboardingStep = _onboardingStep.asStateFlow()

    fun nextOnboardingStep(settings: UserSettings) {
        if (_onboardingStep.value < 3) {
            _onboardingStep.value += 1
        } else {
            viewModelScope.launch {
                repository.updateSettings { it.copy(isOnboarded = true) }
            }
        }
    }

    fun prevOnboardingStep() {
        if (_onboardingStep.value > 1) {
            _onboardingStep.value -= 1
        }
    }

    // Settings modifiers
    fun updateQuitDate(timeInMillis: Long) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(quitDateMillis = timeInMillis) }
            if (timeInMillis > 0L) {
                try {
                    val quitLocalDate = Instant.ofEpochMilli(timeInMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    
                    val limitDate = java.time.LocalDate.now().minusYears(1)
                    val backfillStartDate = if (quitLocalDate.isBefore(limitDate)) limitDate else quitLocalDate
                    
                    repository.backfillLogsFromQuitDate(backfillStartDate)
                    
                    val quitDateStr = quitLocalDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                    repository.deleteLogsBeforeDate(quitDateStr)
                } catch (e: Exception) {
                    _errorState.value = "Something went wrong while synchronizing logs."
                }
            }
        }
    }

    fun updateQuitProfile(
        cigsPerDay: Int,
        cigsPerPack: Int,
        costPerPack: Double,
        currency: String,
        brand: String
    ) {
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(
                    cigarettesPerDay = cigsPerDay,
                    cigarettesPerPack = cigsPerPack,
                    costPerPack = costPerPack,
                    currencySymbol = currency,
                    brandName = brand
                )
            }
        }
    }

    fun updateNotificationPreferences(
        enableDaily: Boolean,
        enableMilestones: Boolean,
        enableStreak: Boolean,
        time: String
    ) {
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(
                    enableDailyQuotes = enableDaily,
                    enableMilestoneNotif = enableMilestones,
                    enableStreakNotif = enableStreak,
                    notificationTime = time
                )
            }
        }
    }

    fun updateGenerativeAffirmations(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(enableGenerativeAffirmations = enabled)
            }
        }
    }

    fun updateFirstDayOfWeek(day: String) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(firstDayOfWeek = day) }
        }
    }

    // Log operations
    fun saveLog(date: LocalDate, status: DayStatus, cigarettes: Int, notes: String) {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        logDay(dateStr, status, cigarettes, notes.ifEmpty { null })
    }

    fun logDay(dateStr: String, status: DayStatus, cigarettes: Int = 0, notes: String? = null) {
        viewModelScope.launch {
            try {
                val log = DailyLog(dateStr, status, cigarettes, notes)
                repository.insertLog(log)
                if (status == DayStatus.RELAPSED) {
                    _showRelapseDialog.value = true
                }
            } catch (e: Exception) {
                _errorState.value = "Something went wrong while saving your log."
            }
        }
    }

    fun deleteLog(dateStr: String) {
        viewModelScope.launch {
            try {
                repository.deleteLogByDate(dateStr)
            } catch (e: Exception) {
                _errorState.value = "Something went wrong while deleting your log."
            }
        }
    }

    fun getBackupJson(): String {
        return Gson().toJson(
            mapOf(
                "settings" to userSettings.value,
                "logs" to allLogs.value
            )
        )
    }

    // Export/Import JSON backing
    fun exportBackup(context: Context, onShare: (Uri) -> Unit) {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                val logsList = allLogs.value
                val settings = userSettings.value
                
                val backupData = mapOf(
                    "settings" to settings,
                    "logs" to logsList
                )
                
                val gson = Gson()
                val jsonString = gson.toJson(backupData)
                
                val cacheFile = File(context.cacheDir, "smokefree_backup.json")
                FileOutputStream(cacheFile).use { fos ->
                    fos.write(jsonString.toByteArray())
                }
                                val fileUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "com.aistudio.smokefree.gthqzy.fileprovider",
                    cacheFile
                )
                onShare(fileUri)
            } catch (e: Exception) {
                _errorState.value = "Something went wrong. Export failed."
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                val contentResolver = context.contentResolver
                val jsonString = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (jsonString.isNullOrEmpty()) {
                    _errorState.value = "Something went wrong. Import file is empty or unreadable."
                    return@launch
                }

                val gson = Gson()
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val map: Map<String, Any> = gson.fromJson(jsonString, type)

                // Parse Settings
                val settingsJson = gson.toJson(map["settings"])
                val settings: UserSettings = gson.fromJson(settingsJson, UserSettings::class.java)

                // Parse Logs
                val logsJson = gson.toJson(map["logs"])
                val logs: List<DailyLog> = gson.fromJson(logsJson, object : TypeToken<List<DailyLog>>() {}.type)

                // Apply to DataStore and Room Database
                repository.updateSettings { settings }
                repository.clearAllLogs()
                for (log in logs) {
                    repository.insertLog(log)
                }

                _showImportSuccess.value = true
            } catch (e: Exception) {
                _errorState.value = "Something went wrong. Import failed. Please ensure the file is valid."
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            try {
                repository.clearAllLogs()
                repository.clearSettings()
                _onboardingStep.value = 1
            } catch (e: Exception) {
                _errorState.value = "Something went wrong. Resetting application data failed."
            }
        }
    }

    private fun calculateStreaks(logs: List<DailyLog>, quitDateMillis: Long = 0L): StreakData {
        val quitLocalDate = if (quitDateMillis > 0L) {
            Instant.ofEpochMilli(quitDateMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        } else {
            LocalDate.now()
        }

        val mappedLogs = logs.associateBy { it.date }

        var streak = 0
        var currentLocalDate = LocalDate.now()
        while (!currentLocalDate.isBefore(quitLocalDate)) {
            val log = mappedLogs[currentLocalDate.format(DateTimeFormatter.ISO_LOCAL_DATE)]
            val isClean = log == null || log.status == DayStatus.SMOKE_FREE || log.status == DayStatus.PARTIAL
            if (isClean) {
                streak++
                currentLocalDate = currentLocalDate.minusDays(1)
            } else {
                break
            }
        }
        val currentStreak = streak

        var longest = 0
        var currentRun = 0
        var date = quitLocalDate
        val today = LocalDate.now()
        
        while (!date.isAfter(today)) {
            val log = mappedLogs[date.format(DateTimeFormatter.ISO_LOCAL_DATE)]
            val isClean = log == null || log.status == DayStatus.SMOKE_FREE || log.status == DayStatus.PARTIAL
            if (isClean) {
                currentRun++
                if (currentRun > longest) longest = currentRun
            } else {
                currentRun = 0
            }
            date = date.plusDays(1)
        }
        val longestStreak = longest

        var cleanCount = 0
        var dateClean = quitLocalDate
        val todayClean = LocalDate.now()
        while (!dateClean.isAfter(todayClean)) {
            val log = mappedLogs[dateClean.format(DateTimeFormatter.ISO_LOCAL_DATE)]
            if (log == null || log.status != DayStatus.RELAPSED) {
                cleanCount++
            }
            dateClean = dateClean.plusDays(1)
        }
        val totalCleanDays = cleanCount

        return StreakData(currentStreak, longestStreak, totalCleanDays)
    }

    // Static structures helper
    class Factory(
        private val application: Application,
        private val repository: LocalRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SmokeFreeViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SmokeFreeViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
