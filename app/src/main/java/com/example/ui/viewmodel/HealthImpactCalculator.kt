package com.example.ui.viewmodel

import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import com.example.data.DailyLog
import com.example.data.DayStatus
import com.example.data.UserSettings
import com.example.ui.state.DashboardKPIs
import com.example.ui.state.SmokingImpactSummary

object HealthImpactCalculator {

    private const val SETBACK_MINUTES_PER_CIGARETTE = 45L

    /**
     * Computes the full smoking impact summary from the log history.
     * This is the single source of truth for all health metrics.
     */
    fun compute(
        logs: Map<String, DailyLog>,   // date string "yyyy-MM-dd" → DailyLog
        quitDate: LocalDate,
        quitDateMillis: Long
    ): SmokingImpactSummary {
        val now = System.currentTimeMillis()
        if (quitDateMillis <= 0L || now <= quitDateMillis) {
            return SmokingImpactSummary.empty()
        }

        val rawMinutesNow = (now - quitDateMillis) / (1000L * 60L)
        val today = LocalDate.now()

        // ── Step 1: Is today clean? ───────────────────────────────────
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val todayLog = logs[todayStr]
        val todayIsClean = todayLog == null || todayLog.status == DayStatus.SMOKE_FREE

        // Check if there has been ANY relapse ever in the active logs
        val anyRelapsesEver = logs.values.any { log ->
            val logDate = try {
                LocalDate.parse(log.date, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: Exception) {
                null
            }
            logDate != null && !logDate.isBefore(quitDate) && !logDate.isAfter(today) &&
            (log.status == DayStatus.RELAPSED || log.status == DayStatus.PARTIAL)
        }

        // Determine Scenarios:
        val isScenarioA = !anyRelapsesEver
        val isScenarioB = !todayIsClean
        val isScenarioC = todayIsClean && anyRelapsesEver

        val lastCleanDate: LocalDate
        val cigarettesSinceLastClean: Int
        val daysSinceLastClean: Int
        val trendSuffix: String
        val effectiveMinutesNow: Long
        val effectiveMinutesAtBaseline: Long
        val cutoffNow: LocalDate
        val cutoffBaseline: LocalDate
        val ignoreRelapsesFrom: LocalDate?

        // ── Step 2: Total setback from ALL relapses ever up to today ─
        val totalSetback = logs.values
            .filter { log ->
                val logDate = try {
                    LocalDate.parse(log.date, DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (e: Exception) {
                    quitDate.minusDays(1)
                }
                !logDate.isBefore(quitDate) && !logDate.isAfter(today)
            }
            .filter { it.status == DayStatus.RELAPSED || it.status == DayStatus.PARTIAL }
            .sumOf { it.cigarettesSmoked.coerceAtLeast(1) * SETBACK_MINUTES_PER_CIGARETTE }

        effectiveMinutesNow = (rawMinutesNow - totalSetback).coerceAtLeast(0L)

        if (isScenarioA) {
            // Scenario A — All days clean (no relapses ever)
            lastCleanDate = today.minusDays(1) // yesterday
            cigarettesSinceLastClean = 0
            daysSinceLastClean = 0 // to force "today" suffix
            trendSuffix = "today"

            // Baseline = End of yesterday (midnight start of today)
            val endOfLastCleanMillis = lastCleanDate
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            effectiveMinutesAtBaseline = ((endOfLastCleanMillis - quitDateMillis) / (1000L * 60L))
                .coerceAtLeast(0L)

            cutoffNow = today
            cutoffBaseline = today.minusDays(1)
            ignoreRelapsesFrom = null

        } else if (isScenarioB) {
            // Scenario B — Smoking today and/or recent consecutive days
            // Find start of current relapse streak
            var streakStart = today
            var cursor = today.minusDays(1)
            while (!cursor.isBefore(quitDate)) {
                val log = logs[cursor.format(DateTimeFormatter.ISO_LOCAL_DATE)]
                val isRelapse = log?.status == DayStatus.RELAPSED ||
                                log?.status == DayStatus.PARTIAL
                if (isRelapse) {
                    streakStart = cursor
                    cursor = cursor.minusDays(1)
                } else {
                    break
                }
            }
            val relapseStreakStart = streakStart

            // Last clean day = day before the current relapse streak started
            val candidateClean = relapseStreakStart.minusDays(1)
            lastCleanDate = if (!candidateClean.isBefore(quitDate)) {
                candidateClean
            } else {
                quitDate
            }

            // Sum cigarettes since last clean
            var sumCigarettes = 0
            var scanDate = relapseStreakStart
            while (!scanDate.isAfter(today)) {
                val log = logs[scanDate.format(DateTimeFormatter.ISO_LOCAL_DATE)]
                if (log != null && (log.status == DayStatus.RELAPSED || log.status == DayStatus.PARTIAL)) {
                    sumCigarettes += log.cigarettesSmoked.coerceAtLeast(1)
                }
                scanDate = scanDate.plusDays(1)
            }
            cigarettesSinceLastClean = sumCigarettes

            daysSinceLastClean = ChronoUnit.DAYS.between(lastCleanDate, today).toInt()
            trendSuffix = when (daysSinceLastClean) {
                0 -> "today"
                1 -> "since yesterday"
                else -> "since ${daysSinceLastClean}d ago"
            }

            // Compare with the clean target today: what it WOULD have been if the user had remained clean during the relapse streak
            effectiveMinutesAtBaseline = effectiveMinutesNow + (cigarettesSinceLastClean * SETBACK_MINUTES_PER_CIGARETTE)

            cutoffNow = today
            cutoffBaseline = today
            ignoreRelapsesFrom = relapseStreakStart

        } else {
            // Scenario C — Was smoking but clean today
            lastCleanDate = today
            cigarettesSinceLastClean = 0
            daysSinceLastClean = 0
            trendSuffix = "today"

            // Baseline = End of yesterday (midnight start of today) which had relapses
            val endOfYesterdayMillis = today
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val rawAtBaseline = ((endOfYesterdayMillis - quitDateMillis) / (1000L * 60L))
                .coerceAtLeast(0L)

            // Setback that existed AT the end of yesterday (relapses before today)
            val setbackAtBaseline = logs.values
                .filter { log ->
                    val logDate = try {
                        LocalDate.parse(log.date, DateTimeFormatter.ISO_LOCAL_DATE)
                    } catch (e: Exception) {
                        quitDate.minusDays(1)
                    }
                    !logDate.isBefore(quitDate) && logDate.isBefore(today)
                }
                .filter { it.status == DayStatus.RELAPSED || it.status == DayStatus.PARTIAL }
                .sumOf { it.cigarettesSmoked.coerceAtLeast(1) * SETBACK_MINUTES_PER_CIGARETTE }

            effectiveMinutesAtBaseline = (rawAtBaseline - setbackAtBaseline).coerceAtLeast(0L)

            cutoffNow = today
            cutoffBaseline = today.minusDays(1)
            ignoreRelapsesFrom = null
        }

        return SmokingImpactSummary(
            lastCleanDate = lastCleanDate,
            daysSinceLastClean = daysSinceLastClean,
            cigarettesSinceLastClean = cigarettesSinceLastClean,
            setbackMinutes = cigarettesSinceLastClean * SETBACK_MINUTES_PER_CIGARETTE,
            effectiveMinutesNow = effectiveMinutesNow,
            effectiveMinutesAtBaseline = effectiveMinutesAtBaseline,
            trendSuffix = trendSuffix,
            hasBaseline = true,
            cutoffNow = cutoffNow,
            cutoffBaseline = cutoffBaseline,
            ignoreRelapsesFrom = ignoreRelapsesFrom
        )
    }

    /**
     * Computes KPIs at a given effective minutes snapshot or exact cutoff date.
     * Used for both "now" and "baseline" to derive trend deltas.
     */
    fun computeKPIs(
        logs: Map<String, DailyLog>,
        quitDate: LocalDate,
        settings: UserSettings,
        cutoff: LocalDate,
        ignoreRelapsesFrom: LocalDate? = null
    ): DashboardKPIs {
        var cigarettesAvoided = 0
        var totalSmoked = 0
        var date = quitDate

        while (!date.isAfter(cutoff)) {
            val log = logs[date.format(DateTimeFormatter.ISO_LOCAL_DATE)]
            val cigPerDay = settings.cigarettesPerDay
            
            val isIgnored = ignoreRelapsesFrom != null && !date.isBefore(ignoreRelapsesFrom)
            if (isIgnored) {
                cigarettesAvoided += cigPerDay
            } else {
                when (log?.status) {
                    DayStatus.RELAPSED, DayStatus.PARTIAL -> {
                        val smoked = log.cigarettesSmoked.coerceAtLeast(1)
                        totalSmoked += smoked
                        cigarettesAvoided += (cigPerDay - smoked).coerceAtLeast(0)
                    }
                    else -> cigarettesAvoided += cigPerDay
                }
            }
            date = date.plusDays(1)
        }

        val actualCigsPerPack = if (settings.cigarettesPerPack > 0) settings.cigarettesPerPack else 20
        return DashboardKPIs(
            cigarettesAvoided = cigarettesAvoided,
            moneySaved = (cigarettesAvoided.toFloat() / actualCigsPerPack) * settings.costPerPack.toFloat(),
            lifeRegainedMinutes = cigarettesAvoided * 11L,
            totalSmoked = totalSmoked
        )
    }
}
