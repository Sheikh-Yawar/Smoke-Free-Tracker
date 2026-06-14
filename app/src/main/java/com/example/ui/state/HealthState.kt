package com.example.ui.state

import java.time.LocalDate
import com.example.data.DailyLog
import com.example.data.UserSettings

enum class TrendDirection { UP, DOWN, NEUTRAL }

data class SmokingImpactSummary(
    val lastCleanDate: LocalDate,
    val daysSinceLastClean: Int,
    val cigarettesSinceLastClean: Int,
    val setbackMinutes: Long,
    val effectiveMinutesNow: Long,
    val effectiveMinutesAtBaseline: Long,
    val trendSuffix: String,
    val hasBaseline: Boolean,
    val cutoffNow: LocalDate,
    val cutoffBaseline: LocalDate,
    val ignoreRelapsesFrom: LocalDate?
) {
    companion object {
        fun empty() = SmokingImpactSummary(
            lastCleanDate = LocalDate.now(),
            daysSinceLastClean = 0,
            cigarettesSinceLastClean = 0,
            setbackMinutes = 0L,
            effectiveMinutesNow = 0L,
            effectiveMinutesAtBaseline = 0L,
            trendSuffix = "today",
            hasBaseline = false,
            cutoffNow = LocalDate.now(),
            cutoffBaseline = LocalDate.now(),
            ignoreRelapsesFrom = null
        )
    }
}

data class DashboardKPIs(
    val cigarettesAvoided: Int,
    val moneySaved: Float,
    val lifeRegainedMinutes: Long,
    val totalSmoked: Int
) {
    companion object {
        fun empty() = DashboardKPIs(0, 0f, 0L, 0)
    }
}

data class StreakData(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalCleanDays: Int
)

data class HealthState(
    val impact: SmokingImpactSummary,
    val kpisNow: DashboardKPIs,
    val kpisBaseline: DashboardKPIs,      // at last clean day
    val streaks: StreakData,
    val settings: UserSettings
) {
    companion object {
        fun empty() = HealthState(
            impact = SmokingImpactSummary.empty(),
            kpisNow = DashboardKPIs.empty(),
            kpisBaseline = DashboardKPIs.empty(),
            streaks = StreakData(0, 0, 0),
            settings = UserSettings()
        )
    }
}
