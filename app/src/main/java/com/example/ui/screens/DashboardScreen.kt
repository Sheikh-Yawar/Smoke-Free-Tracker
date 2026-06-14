package com.example.ui.screens

import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.DailyLog
import com.example.data.DayStatus
import com.example.data.UserSettings
import com.example.ui.state.Badge
import com.example.ui.theme.*
import com.example.ui.components.LogDayBottomSheet
import com.example.ui.viewmodel.SmokeFreeViewModel
import com.example.ui.state.Achievement
import com.example.ui.state.AchievementTier
import com.example.ui.state.TrendDirection
import com.example.ui.state.DashboardKPIs
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin
import kotlin.math.cos
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Smoke Particle Model for the Canvas simulation
data class SmokeParticle(
    var x: Float,
    var y: Float,
    var speed: Float,
    var size: Float,
    var alpha: Float,
    var drift: Float
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    settings: UserSettings,
    logs: List<DailyLog>,
    viewModel: SmokeFreeViewModel,
    onCheckInAnswer: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current

    var selectedDetailAchievement by remember { mutableStateOf<com.example.ui.state.Achievement?>(null) }
    
    var showCheckin by remember { mutableStateOf(false) }
    var showRelapseSheet by remember { mutableStateOf(false) }
    var selectedRelapseDate by remember { mutableStateOf(LocalDate.now()) }
    var cigaretteCount by remember { mutableStateOf("") }
    
    val currentStreakLocal = remember(logs, settings.quitDateMillis) {
        calculateStreaks(logs, settings.quitDateMillis).first
    }
    
    LaunchedEffect(settings.quitDateMillis) {
        if (settings.quitDateMillis > 0L) {
            val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            val lastDate = settings.lastCheckinDate
            
            showCheckin = (lastDate != today)
        }
    }

    // 1. Time calculations & state conversion
    val now = System.currentTimeMillis()
    val quitTime = settings.quitDateMillis

    val healthState by viewModel.healthState.collectAsState()
    val kpis = healthState.kpisNow

    val logsMap = remember(logs) { logs.associateBy { it.date } }
    val quitLocalDate = remember(quitTime) {
        if (quitTime > 0L) {
            Instant.ofEpochMilli(quitTime)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        } else {
            LocalDate.now()
        }
    }

    val todayLocalDate = LocalDate.now()
    val todayStr = todayLocalDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val todayLog = logsMap[todayStr]
    val todaySmoked = todayLog?.cigarettesSmoked ?: 0

    val todayAvoided = when (todayLog?.status) {
        DayStatus.RELAPSED, DayStatus.PARTIAL -> {
            val smoked = todayLog.cigarettesSmoked.coerceAtLeast(1)
            (settings.cigarettesPerDay - smoked).coerceAtLeast(0)
        }
        else -> settings.cigarettesPerDay
    }

    val impact = healthState.impact
    val trendSuffix = impact.trendSuffix

    val cigarettesNotSmoked = healthState.kpisNow.cigarettesAvoided
    val moneySaved = healthState.kpisNow.moneySaved.toDouble()
    val lifeRegainedMinutes = healthState.kpisNow.lifeRegainedMinutes
    val lifeRegainedDays = lifeRegainedMinutes / (60 * 24)
    val lifeRegainedHours = (lifeRegainedMinutes % (60 * 24)) / 60

    val cigDelta = healthState.kpisNow.cigarettesAvoided - healthState.kpisBaseline.cigarettesAvoided
    val moneyDelta = healthState.kpisNow.moneySaved - healthState.kpisBaseline.moneySaved
    val lifeDelta = healthState.kpisNow.lifeRegainedMinutes - healthState.kpisBaseline.lifeRegainedMinutes
    val calDelta = (healthState.kpisNow.cigarettesAvoided - healthState.kpisBaseline.cigarettesAvoided) * 10

    // Helper to build trend data for each KPI:
    fun buildTrend(
        delta: Number,
        formatFn: (Double) -> String
    ): Pair<TrendDirection, String> {
        val d = delta.toDouble()
        if (!impact.hasBaseline || kotlin.math.abs(d) < 0.01) {
            return TrendDirection.NEUTRAL to ""
        }
        val dir = if (d > 0) TrendDirection.UP else TrendDirection.DOWN
        val formatted = formatFn(d)
        val sign = if (d > 0) "+" else ""
        return dir to "$sign$formatted $trendSuffix"
    }

    val (cigTrend, cigLabel) = buildTrend(cigDelta) { delta -> "${delta.toInt()}" }

    val (moneyTrend, moneyLabel) = buildTrend(moneyDelta) { delta -> "${settings.currencySymbol}${"%.2f".format(kotlin.math.abs(delta))}" }

    val (lifeTrend, lifeLabel) = buildTrend(lifeDelta) { delta -> formatMinutesShort(delta.toLong()) }

    val (calTrend, calLabel) = buildTrend(calDelta) { delta -> "${delta.toInt()} kcal" }

    // Helper to find minutes since last smoke up to a target date & time
    fun getMinutesSinceLastCigarette(
        targetLocalDate: LocalDate,
        targetMillis: Long
    ): Long {
        if (quitTime <= 0L) return 0L

        val latestRelapseDate = logs.filter { it.status == DayStatus.RELAPSED || it.status == DayStatus.PARTIAL }
            .mapNotNull { log ->
                try {
                    LocalDate.parse(log.date, DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (e: Exception) {
                    null
                }
            }
            .filter { !it.isBefore(quitLocalDate) && !it.isAfter(targetLocalDate) }
            .maxOrNull()

        return if (latestRelapseDate == null) {
            ((targetMillis - quitTime) / 60000L).coerceAtLeast(0L)
        } else {
            if (latestRelapseDate == targetLocalDate) {
                0L
            } else {
                val endOfRelapseDay = latestRelapseDate.plusDays(1)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                ((targetMillis - endOfRelapseDay) / 60000L).coerceAtLeast(0L)
            }
        }
    }

    val nowMinutesSinceLastSmoke = getMinutesSinceLastCigarette(todayLocalDate, now)
    val baselineLocalDate = todayLocalDate.minusDays(1)
    val baselineMillis = now - (24L * 60L * 60L * 1000L)
    val baselineMinutesSinceLastSmoke = getMinutesSinceLastCigarette(baselineLocalDate, baselineMillis)

    val nicotinePercent = minOf(1.0, (nowMinutesSinceLastSmoke / 60.0) / 72.0)
    val coPercent = minOf(1.0, (nowMinutesSinceLastSmoke / 60.0) / 24.0)

    val nicotinePercentBaseline = minOf(1.0, (baselineMinutesSinceLastSmoke / 60.0) / 72.0)
    val coPercentBaseline = minOf(1.0, (baselineMinutesSinceLastSmoke / 60.0) / 24.0)

    val nicotineNow = nicotinePercent * 100
    val nicotineBaseline = nicotinePercentBaseline * 100
    val (nicotineTrend, nicotineLabel) = buildTrend(nicotineNow - nicotineBaseline) { delta ->
        "${"%.0f".format(kotlin.math.abs(delta))}%"
    }

    val coNow = coPercent * 100
    val coBaseline = coPercentBaseline * 100
    val (coTrend, coLabel) = buildTrend(coNow - coBaseline) { delta ->
        "${"%.0f".format(kotlin.math.abs(delta))}%"
    }

    val diffMillis = if (quitTime > 0L && now > quitTime) now - quitTime else 0L
    val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

    // Nicotine-Free Time (since last cigarette)
    val latestSmokeLog = logs
        .filter { it.status == DayStatus.RELAPSED || (it.status == DayStatus.PARTIAL && it.cigarettesSmoked > 0) }
        .maxByOrNull { it.date }

    val nicotineFreeTimeFormatted = if (latestSmokeLog != null) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val lastSmokeDate = sdf.parse(latestSmokeLog.date)
            if (lastSmokeDate != null && now > lastSmokeDate.time) {
                val diff = now - lastSmokeDate.time
                val dDays = diff / (1000 * 60 * 60 * 24)
                if (dDays > 0) "$dDays days clean" else "Cleared: < 24 hrs"
            } else {
                "Fully Nicotine-Free!"
            }
        } catch (e: Exception) {
            "Cleared: ${diffDays} days"
        }
    } else {
        if (diffDays >= 3) {
            "Fully Nicotine-Free!"
        } else {
            val hoursLeft = maxOf(0, 72 - (diffMillis / (1000 * 60 * 60)).toInt())
            if (hoursLeft > 0) "$hoursLeft hrs to 100% clear" else "Nicotine Cleared!"
        }
    }

    val caloriesNotAbsorbed = cigarettesNotSmoked * 10
    val carbonMonoxideAvoidedMg = cigarettesNotSmoked * 12
    val deltaCalories = (healthState.kpisNow.cigarettesAvoided - healthState.kpisBaseline.cigarettesAvoided) * 10

    // Effective minutes since quit (for health recoveries) with setback system:
    val effectiveMinutes = impact.effectiveMinutesNow
    val effectiveHours = effectiveMinutes / 60.0
    val remNicotineHours = maxOf(0, 72 - (nowMinutesSinceLastSmoke / 60).toInt())
    val remCoHours = maxOf(0, 24 - (nowMinutesSinceLastSmoke / 60).toInt())


    // 4. Streak calculations
    val (currentStreak, longestStreak, totalSmokeFreeDays) = remember(logs, settings.quitDateMillis) {
        calculateStreaks(logs, settings.quitDateMillis)
    }

    // 5. Daily Quote Selector (30 saved quotes locally, displaying one unique quote on each day)
    val localQuotes = remember {
        listOf(
            "Your lungs are beginning to breathe easier. Keep going!",
            "Every cigarette not smoked is a victory for your wallet and your heart.",
            "The craving will pass whether you smoke or not.",
            "You are stronger than any urge. Keep fighting!",
            "Not smoking today is the best gift you can give your future self.",
            "Your body is clearing out toxins right now. Let it heal.",
            "One day at a time, one breath at a time. You have got this.",
            "The further you get from your last cigarette, the closer you get to a happier life.",
            "Believe you can and you are halfway there.",
            "Your health is your true wealth. Invest in yourself!",
            "Quitting smoking is a journey of self-love.",
            "Suffer the pain of discipline today, or suffer the pain of regret tomorrow.",
            "With every smoke-free day, you are reclaiming your freedom.",
            "No craving lasts forever. Take deep breaths and hold on.",
            "Your tastebuds and sense of smell are coming back to life!",
            "Money saved can be spent on real dreams, not smoke.",
            "Choose health. Choose life. Choose yourself.",
            "You do not need a cigarette to deal with stress. Breathe deep instead.",
            "Look at how far you have come. Do not let one moment ruin your progress.",
            "You are no longer a slave to nicotine. You are free.",
            "Your heart rate is normal, your carbon monoxide is gone. Keep healing!",
            "Count your victories, not your cravings.",
            "The choice to quit is the choice to live fully.",
            "Your future self is thanking you right now.",
            "Stay committed to your recovery. It gets easier every single day.",
            "Quitting makes your loved ones happy and sets a great example.",
            "Every breath you take now is clean, fresh, and healing.",
            "Deep breathing resets your focus. Try it now.",
            "Tobacco is yesterday's cage. Welcome to today's freedom.",
            "You are a non-smoker. Own your new, healthy identity!"
        )
    }

    val quoteOfTheDay = remember(localQuotes) {
        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val year = calendar.get(Calendar.YEAR)
        val index = (dayOfYear + year) % localQuotes.size
        localQuotes[index]
    }

    // 6. Achievements Badges setup (10 badges)
    val badges = listOf(
        Badge("1_day", "First Day", "1 Day smoke-free", 1, "Filter1"),
        Badge("3_days", "Breathing Room", "3 Days smoke-free", 3, "Air"),
        Badge("1_week", "Weekly Win", "1 Week smoke-free", 7, "Eco"),
        Badge("2_weeks", "Fortnight Fighter", "2 Weeks smoke-free", 14, "LocalActivity"),
        Badge("1_month", "Monthly Milestone", "1 Month smoke-free", 30, "ThumbUp"),
        Badge("3_months", "Quarter Century", "3 Months smoke-free", 90, "Star"),
        Badge("6_months", "Halfway Hero", "6 Months smoke-free", 180, "SentimentSatisfied"),
        Badge("1_year", "The Golden Year", "1 Year smoke-free", 365, "EmojiEvents"),
        Badge("2_years", "Double Crown", "2 Years smoke-free", 730, "WorkspacePremium"),
        Badge("5_years", "Legendary Freedom", "5 Years smoke-free", 1825, "Security")
    )

    // 7. Render Layout
    if (quitTime == 0L) {
        // Unconfigured state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.PriorityHigh,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Set Your Quit Date",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Configure your quit date in the Settings tab to start tracking your victories, stats, and achievements.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(bottom = 90.dp) // extra padding for bottom navigation
            ) {
                // Hero Card spans full width
                item(span = { GridItemSpan(2) }) {
                    HeroCard(settings = settings)
                }

                // Daily Check-In Card
                if (showCheckin) {
                    item(span = { GridItemSpan(2) }) {
                        DailyCheckInCard(
                            onAnswer = { didSmoke ->
                                showCheckin = false
                                if (didSmoke) {
                                    showRelapseSheet = true
                                }
                                onCheckInAnswer?.invoke(didSmoke)
                            }
                        )
                    }
                }

            // Streak overview Section (Full width)
            item(span = { GridItemSpan(2) }) {
                StreakSection(
                    currentStreak = currentStreak,
                    longestStreak = longestStreak,
                    totalDays = totalSmokeFreeDays
                )
            }

            // Title for KPIs
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "RECLAIMED BENEFITS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            // KPI Grid items (2 columns, equal sized via row weights)
            item(span = { GridItemSpan(2) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiCard(
                            title = "Cigarettes Avoided",
                            value = "$cigarettesNotSmoked",
                            icon = Icons.Outlined.SmokeFree,
                            trendDirection = cigTrend,
                            trendLabel = cigLabel,
                            modifier = Modifier.weight(1f)
                        )
                        KpiCard(
                            title = "Money Saved",
                            value = "${settings.currencySymbol}${String.format(Locale.US, "%.2f", moneySaved)}",
                            icon = Icons.Outlined.Savings,
                            trendDirection = moneyTrend,
                            trendLabel = moneyLabel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiCard(
                            title = "Life Regained",
                            value = formatMinutesShort(healthState.kpisNow.lifeRegainedMinutes),
                            icon = Icons.Outlined.HourglassEmpty,
                            trendDirection = lifeTrend,
                            trendLabel = lifeLabel,
                            modifier = Modifier.weight(1f)
                        )
                        KpiCard(
                            title = "Nicotine Cleared",
                            value = "${(nicotinePercent * 100).toInt()}%",
                            icon = Icons.Outlined.Bloodtype,
                            trendDirection = nicotineTrend,
                            trendLabel = nicotineLabel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiCard(
                            title = "Detox Energy Saved",
                            value = "${healthState.kpisNow.cigarettesAvoided * 10} kcal",
                            icon = Icons.Outlined.LocalFireDepartment,
                            trendDirection = calTrend,
                            trendLabel = calLabel,
                            modifier = Modifier.weight(1f)
                        )
                        KpiCard(
                            title = "CO Cleared",
                            value = "${(coPercent * 100).toInt()}%",
                            icon = Icons.Outlined.Air,
                            trendDirection = coTrend,
                            trendLabel = coLabel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Daily Quote section (full width)
            item(span = { GridItemSpan(2) }) {
                QuoteCard(quote = quoteOfTheDay)
            }

            // Achievements Badge head + XP stats
            item(span = { GridItemSpan(2) }) {
                val achievementsList = remember {
                    listOf(
                        Achievement("1_day", "First Day", "1 Day smoke-free", 1, AchievementTier.BRONZE, 100, "Filter1"),
                        Achievement("3_days", "Breathing Room", "3 Days smoke-free", 3, AchievementTier.BRONZE, 150, "Air"),
                        Achievement("1_week", "Weekly Win", "1 Week smoke-free", 7, AchievementTier.SILVER, 250, "Eco"),
                        Achievement("2_weeks", "Fortnight Fighter", "2 Weeks smoke-free", 14, AchievementTier.SILVER, 400, "LocalActivity"),
                        Achievement("1_month", "Monthly Milestone", "1 Month smoke-free", 30, AchievementTier.GOLD, 600, "ThumbUp"),
                        Achievement("3_months", "Quarter Century", "3 Months smoke-free", 90, AchievementTier.GOLD, 1000, "Star"),
                        Achievement("6_months", "Halfway Hero", "6 Months smoke-free", 180, AchievementTier.PLATINUM, 1500, "SentimentSatisfied"),
                        Achievement("1_year", "The Golden Year", "1 Year smoke-free", 365, AchievementTier.PLATINUM, 2500, "EmojiEvents"),
                        Achievement("2_years", "Double Crown", "2 Years smoke-free", 730, AchievementTier.DIAMOND, 5000, "WorkspacePremium"),
                        Achievement("5_years", "Legendary Freedom", "5 Years smoke-free", 1825, AchievementTier.DIAMOND, 10000, "Security")
                    )
                }

                val unlockedXp = remember(achievementsList, totalSmokeFreeDays) {
                    achievementsList.filter { totalSmokeFreeDays >= it.daysRequired }.sumOf { it.xpReward }
                }

                val currentLevel = (unlockedXp / 1000) + 1
                val currentLevelProgressXp = unlockedXp % 1000
                val progressPercentVal = currentLevelProgressXp.toFloat() / 1000f

                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        text = "ACHIEVEMENTS & LEVEL",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // Gamified level progress card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 4.dp,
                                shape = RoundedCornerShape(16.dp),
                                spotColor = ShadowColor,
                                ambientColor = ShadowColor
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceHero) 
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(VioletPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Lvl\n$currentLevel",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Progress to Level ${currentLevel + 1}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = TextOnHero
                                    )
                                    Text(
                                        text = "$currentLevelProgressXp/1000 XP",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextOnHero.copy(alpha = 0.8f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { progressPercentVal },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = MintAccent,
                                    trackColor = DividerColor.copy(alpha = 0.2f),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${1000 - currentLevelProgressXp} XP more for level up!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextOnHero.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // Achievements Badges block as a grid
            item(span = { GridItemSpan(2) }) {
                val achievementsList = remember {
                    listOf(
                        Achievement("1_day", "First Day", "1 Day smoke-free", 1, AchievementTier.BRONZE, 100, "Filter1"),
                        Achievement("3_days", "Breathing Room", "3 Days smoke-free", 3, AchievementTier.BRONZE, 150, "Air"),
                        Achievement("1_week", "Weekly Win", "1 Week smoke-free", 7, AchievementTier.SILVER, 250, "Eco"),
                        Achievement("2_weeks", "Fortnight Fighter", "2 Weeks smoke-free", 14, AchievementTier.SILVER, 400, "LocalActivity"),
                        Achievement("1_month", "Monthly Milestone", "1 Month smoke-free", 30, AchievementTier.GOLD, 600, "ThumbUp"),
                        Achievement("3_months", "Quarter Century", "3 Months smoke-free", 90, AchievementTier.GOLD, 1000, "Star"),
                        Achievement("6_months", "Halfway Hero", "6 Months smoke-free", 180, AchievementTier.PLATINUM, 1500, "SentimentSatisfied"),
                        Achievement("1_year", "The Golden Year", "1 Year smoke-free", 365, AchievementTier.PLATINUM, 2500, "EmojiEvents"),
                        Achievement("2_years", "Double Crown", "2 Years smoke-free", 730, AchievementTier.DIAMOND, 5000, "WorkspacePremium"),
                        Achievement("5_years", "Legendary Freedom", "5 Years smoke-free", 1825, AchievementTier.DIAMOND, 10000, "Security")
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val columns = 2
                    val itemPadding = 4.dp
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val chunks = achievementsList.chunked(columns)
                        chunks.forEach { rowAchievements ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                for (i in 0 until columns) {
                                    if (i < rowAchievements.size) {
                                        val achievement = rowAchievements[i]
                                        val unlocked = totalSmokeFreeDays >= achievement.daysRequired
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(itemPadding),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AchievementCard(
                                                achievement = achievement,
                                                unlocked = unlocked,
                                                cleanDays = totalSmokeFreeDays,
                                                onClick = { selectedDetailAchievement = achievement }
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet detail dialog
    if (selectedDetailAchievement != null) {
        val achievement = selectedDetailAchievement!!
        val unlocked = totalSmokeFreeDays >= achievement.daysRequired
        AchievementDetailBottomSheet(
            achievement = achievement,
            unlocked = unlocked,
            cleanDays = totalSmokeFreeDays,
            onDismiss = { selectedDetailAchievement = null }
        )
    }

    if (showRelapseSheet) {
        LogDayBottomSheet(
            initialDate = LocalDate.now(),
            initialStatus = DayStatus.RELAPSED,
            initialCigarettes = 0,
            onSave = { date, status, cigs, notes ->
                viewModel.saveLog(date, status, cigs, notes)
                showRelapseSheet = false
            },
            onResetQuitDate = { viewModel.resetQuitDateToNow() },
            onDismiss = { showRelapseSheet = false }
        )
    }
}
}

@Composable
fun DailyCheckInCard(
    onAnswer: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("ai_companion_bubble"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "DAILY CHECK-IN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Did you smoke today?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { onAnswer(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AlertRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f).testTag("check_in_yes_btn"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Yes, I smoked", style = MaterialTheme.typography.labelMedium)
                }
                
                Button(
                    onClick = { onAnswer(false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RecoveryGreen,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f).testTag("check_in_no_btn"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("No, Smoke Free!", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun HeroCard(settings: UserSettings) {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(settings.quitDateMillis) {
        if (settings.quitDateMillis > 0L) {
            while (true) {
                currentTime = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    val quitTime = settings.quitDateMillis
    val diffMillis = if (quitTime > 0L && currentTime > quitTime) currentTime - quitTime else 0L

    val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
    val remainingHours = ((diffMillis % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)).toInt()
    val remainingMinutes = ((diffMillis % (1000 * 60 * 60)) / (1000 * 60)).toInt()
    val remainingSeconds = ((diffMillis % (1000 * 60)) / 1000).toInt()

    // 8-Second Box Breathing Loop (Time from 0 to 8000ms)
    val infiniteTransition = rememberInfiniteTransition(label = "BoxBreathingLoop")
    val timeMillis by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "TimeMillis"
    )

    // Calculate breath state, scale multipliers and alpha transparency based on prompt equations
    val (scaleProgress, alphaProgress, hudStateText) = remember(timeMillis) {
        val sp: Float // Scale progress (0 to 1) Corrected to Ease-In-Out-Sine Easing Curve
        val ap: Float // Alpha progress (0 to 1)
        val stateText: String
        
        when {
            timeMillis < 3200f -> {
                // Inhale Phase (0s to 3.2s)
                val p = timeMillis / 3200f
                val eased = (-(cos(Math.PI * p) - 1.0) / 2.0).toFloat()
                sp = eased
                ap = eased
                stateText = "Inhale..."
            }
            timeMillis < 4000f -> {
                // Hold Phase (3.2s to 4.0s)
                sp = 1.0f
                ap = 1.0f
                stateText = "Hold..."
            }
            timeMillis < 7200f -> {
                // Exhale Phase (4.0s to 7.2s)
                val p = (timeMillis - 4000f) / 3200f
                val eased = (-(cos(Math.PI * p) - 1.0) / 2.0).toFloat()
                sp = 1.0f - eased
                ap = 1.0f - eased
                stateText = "Exhale..."
            }
            else -> {
                // Hold Phase (7.2s to 8.0s)
                sp = 0.0f
                ap = 0.0f
                stateText = "Hold..."
            }
        }
        Triple(sp, ap, stateText)
    }

    // Concentric breathing dimensions
    val scaleCircle1 = 1.0f + 0.40f * scaleProgress
    val scaleCircle2 = 1.15f + 0.50f * scaleProgress
    val alphaCircle1 = 0.12f + 0.16f * alphaProgress
    val alphaCircle2 = 0.08f + 0.14f * alphaProgress

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.777f) // Approximately 16:9 standard landscape banner ratio
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(30.dp),
                clip = true,
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), // 10% white rim highlight/stroke
                shape = RoundedCornerShape(30.dp)
            ),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF27215C)) // Rich deep Navy-Purple
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // GPU-Accelerated Animation Circles background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.width * 0.32f

                // Circle 2 (Outer soft purple glow)
                val r2 = baseRadius * scaleCircle2
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFA855F7).copy(alpha = alphaCircle2),
                            Color(0xFFA855F7).copy(alpha = alphaCircle2 * 0.40f),
                            Color.Transparent
                        ),
                        center = centerOffset,
                        radius = r2
                    ),
                    radius = r2,
                    center = centerOffset
                )

                // Circle 1 (Inner soft blue-cyan glow)
                val r1 = baseRadius * scaleCircle1
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF60A5FA).copy(alpha = alphaCircle1),
                            Color(0xFF60A5FA).copy(alpha = alphaCircle1 * 0.35f),
                            Color.Transparent
                        ),
                        center = centerOffset,
                        radius = r1
                    ),
                    radius = r1,
                    center = centerOffset
                )
            }

            // Foreground Layout Content stacked vertically & perfectly centered
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 22.dp, bottom = 18.dp, start = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Label
                Text(
                    text = "SMOKE FREE FOR",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFFE9D5FF).copy(alpha = 0.70f),
                        fontSize = 10.5.sp,
                        letterSpacing = 2.5.sp, // Wide letter-spacing
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )

                // Center the clock/counter columns grid inside the remaining space
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    // Counter columns grid separated by colons with lowercase descriptions below
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    // DAYS
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%02d", diffDays),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFeatureSettings = "tnum"
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "DAYS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                color = Color(0xFFE9D5FF).copy(alpha = 0.60f),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE9D5FF).copy(alpha = 0.40f)
                        ),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // HOURS
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%02d", remainingHours),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFeatureSettings = "tnum"
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "HRS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                color = Color(0xFFE9D5FF).copy(alpha = 0.60f),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE9D5FF).copy(alpha = 0.40f)
                        ),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // MINUTES
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%02d", remainingMinutes),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFeatureSettings = "tnum"
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "MIN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                color = Color(0xFFE9D5FF).copy(alpha = 0.60f),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE9D5FF).copy(alpha = 0.40f)
                        ),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // SECONDS
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        AnimatedContent(
                            targetState = remainingSeconds,
                            transitionSpec = {
                                (slideInVertically { h -> h } + fadeIn()) togetherWith
                                        (slideOutVertically { h -> -h } + fadeOut())
                            },
                            label = "SecondsAnimation"
                        ) { targetSec ->
                            Text(
                                text = String.format(Locale.US, "%02d", targetSec),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFeatureSettings = "tnum"
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "SEC",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                color = Color(0xFFE9D5FF).copy(alpha = 0.60f),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
fun StreakSection(currentStreak: Int, longestStreak: Int, totalDays: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CURRENT", style = MaterialTheme.typography.labelSmall, color = VioletPrimary, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("$currentStreak", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = TextPrimaryDark)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = "Fire", tint = AmberAccent, modifier = Modifier.size(22.dp))
                }
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(DividerColor)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LONGEST", style = MaterialTheme.typography.labelSmall, color = VioletPrimary, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("$longestStreak", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = TextPrimaryDark)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Outlined.EmojiEvents, contentDescription = "Trophy", tint = AmberAccent, modifier = Modifier.size(22.dp))
                }
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(DividerColor)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TOTAL CLEAN", style = MaterialTheme.typography.labelSmall, color = VioletPrimary, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("$totalDays", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = TextPrimaryDark)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Outlined.Shield, contentDescription = "Shield", tint = MintAccent, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
fun TrendBadge(
    direction: TrendDirection,
    deltaText: String,
    isPositive: Boolean
) {
    val tintColor = if (isPositive) RecoveryGreen else AlertRed
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = if (direction == TrendDirection.UP) Icons.Default.ArrowUpward else if (direction == TrendDirection.DOWN) Icons.Default.ArrowDownward else Icons.Default.TrendingFlat,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = deltaText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tintColor
        )
    }
}

@Composable
fun QuoteCard(quote: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.FormatQuote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = quote,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgeIcon(badge: Badge, unlocked: Boolean) {
    var showTooltip by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(
            onClick = { showTooltip = !showTooltip },
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (unlocked) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
        ) {
            val icon = when (badge.iconName) {
                "Filter1" -> Icons.Default.Filter1
                "Air" -> Icons.Default.Air
                "Eco" -> Icons.Default.Eco
                "LocalActivity" -> Icons.Default.LocalActivity
                "ThumbUp" -> Icons.Default.ThumbUp
                "Star" -> Icons.Default.Star
                "SentimentSatisfied" -> Icons.Default.SentimentSatisfiedAlt
                "EmojiEvents" -> Icons.Default.EmojiEvents
                "WorkspacePremium" -> Icons.Default.WorkspacePremium
                else -> Icons.Default.Security
            }

            // Apply greyscale filter if locked
            Icon(
                imageVector = icon,
                contentDescription = badge.title,
                tint = if (unlocked) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = badge.title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (unlocked) MaterialTheme.colorScheme.onBackground else Color.Gray
        )

            // Mini detail dialog on click
        if (showTooltip) {
            AlertDialog(
                onDismissRequest = { showTooltip = false },
                title = { Text(badge.title, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(badge.subtitle)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (unlocked) "Status: Unlocked" else "Status: Locked (Requires ${badge.daysRequired} days)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (unlocked) RecoveryGreen else AlertRed
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTooltip = false }) {
                        Text("Dismiss")
                    }
                }
            )
        }
    }
}

// Chronological Streak Calculation Helper
fun calculateStreaks(logs: List<DailyLog>, quitDateMillis: Long = 0L): Triple<Int, Int, Int> {
    val quitLocalDate = if (quitDateMillis > 0L) {
        Instant.ofEpochMilli(quitDateMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    } else {
        LocalDate.now()
    }

    val mappedLogs = logs.associateBy { it.date }

    // Fix 1 — Streak query tolerance for today:
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

    // Fix 2 — Longest streak:
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

    // Fix 3 — Total clean days:
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

    return Triple(currentStreak, longestStreak, totalCleanDays)
}

@Composable
fun AchievementCard(
    achievement: Achievement,
    unlocked: Boolean,
    cleanDays: Int,
    onClick: () -> Unit
) {
    val tierColor = when (achievement.tier) {
        AchievementTier.BRONZE -> Color(0xFFCD7F32)
        AchievementTier.SILVER -> Color(0xFFC0C0C0)
        AchievementTier.GOLD -> Color(0xFFFFD700)
        AchievementTier.PLATINUM -> Color(0xFFE5E4E2)
        AchievementTier.DIAMOND -> Color(0xFFB9F2FF)
    }

    val boxModifier = if (unlocked) {
        Modifier
            .border(2.dp, Brush.sweepGradient(listOf(tierColor, VioletPrimary, tierColor)), RoundedCornerShape(16.dp))
            .background(SurfaceCard)
    } else {
        Modifier
            .background(SurfaceElevated.copy(alpha = 0.7f))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clickable { onClick() }
            .shadow(
                elevation = if (unlocked) 3.dp else 0.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = ShadowColor
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(boxModifier)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(tierColor, CircleShape)
                )
                if (unlocked) {
                    Box(
                        modifier = Modifier
                            .background(MintAccent.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "+${achievement.xpReward}",
                            color = MintAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (unlocked) VioletPrimary.copy(alpha = 0.15f) else DividerColor.copy(alpha = 0.5f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (achievement.iconName) {
                        "Filter1" -> Icons.Default.Filter1
                        "Air" -> Icons.Default.Air
                        "Eco" -> Icons.Default.Eco
                        "LocalActivity" -> Icons.Default.LocalActivity
                        "ThumbUp" -> Icons.Default.ThumbUp
                        "Star" -> Icons.Default.Star
                        "SentimentSatisfied" -> Icons.Default.SentimentSatisfied
                        "EmojiEvents" -> Icons.Default.EmojiEvents
                        "WorkspacePremium" -> Icons.Default.WorkspacePremium
                        "Security" -> Icons.Default.Security
                        else -> Icons.Default.Stars
                    },
                    contentDescription = null,
                    tint = if (unlocked) VioletPrimary else TextSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(26.dp)
                )
                
                if (!unlocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = TextSecondary.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = achievement.title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (unlocked) TextPrimaryDark else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (!unlocked) {
                val progressVal = (cleanDays.toFloat() / achievement.daysRequired.toFloat()).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = { progressVal },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = VioletPrimary.copy(alpha = 0.5f),
                        trackColor = DividerColor.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$cleanDays/${achievement.daysRequired}d",
                        fontSize = 9.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Text(
                    text = "Unlocked!",
                    fontSize = 9.sp,
                    color = MintAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementDetailBottomSheet(
    achievement: Achievement,
    unlocked: Boolean,
    cleanDays: Int,
    onDismiss: () -> Unit
) {
    val tierColor = when (achievement.tier) {
        AchievementTier.BRONZE -> Color(0xFFCD7F32)
        AchievementTier.SILVER -> Color(0xFFC0C0C0)
        AchievementTier.GOLD -> Color(0xFFFFD700)
        AchievementTier.PLATINUM -> Color(0xFFE5E4E2)
        AchievementTier.DIAMOND -> Color(0xFFB9F2FF)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        contentColor = TextPrimaryDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(
                        if (unlocked) tierColor.copy(alpha = 0.15f) else DividerColor.copy(alpha = 0.3f),
                        CircleShape
                    )
                    .border(
                        3.dp,
                        if (unlocked) Brush.sweepGradient(listOf(tierColor, VioletPrimary, tierColor))
                        else Brush.linearGradient(listOf(DividerColor, DividerColor)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (achievement.iconName) {
                        "Filter1" -> Icons.Default.Filter1
                        "Air" -> Icons.Default.Air
                        "Eco" -> Icons.Default.Eco
                        "LocalActivity" -> Icons.Default.LocalActivity
                        "ThumbUp" -> Icons.Default.ThumbUp
                        "Star" -> Icons.Default.Star
                        "SentimentSatisfied" -> Icons.Default.SentimentSatisfied
                        "EmojiEvents" -> Icons.Default.EmojiEvents
                        "WorkspacePremium" -> Icons.Default.WorkspacePremium
                        "Security" -> Icons.Default.Security
                        else -> Icons.Default.Stars
                    },
                    contentDescription = null,
                    tint = if (unlocked) tierColor else TextSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(52.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = TextPrimaryDark
            )
            
            Text(
                text = "${achievement.tier.name} TIER",
                style = MaterialTheme.typography.labelLarge,
                color = tierColor,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = achievement.subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status:",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = if (unlocked) "Unlocked!" else "Locked",
                            fontWeight = FontWeight.Black,
                            color = if (unlocked) MintAccent else AlertRed
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "XP Reward:",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "+${achievement.xpReward} XP",
                            fontWeight = FontWeight.Black,
                            color = VioletPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val progressFraction = (cleanDays.toFloat() / achievement.daysRequired.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (unlocked) MintAccent else VioletPrimary,
                        trackColor = DividerColor
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$cleanDays of ${achievement.daysRequired} smoke-free days",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Acknowledge",
                    color = TextOnAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun formatMinutesShort(minutes: Long): String {
    val sign = if (minutes < 0) "-" else ""
    val absMin = Math.abs(minutes)
    val days = absMin / (60 * 24)
    val hours = (absMin % (60 * 24)) / 60
    return if (days > 0) "$sign${days}d ${hours}h" else "$sign${absMin}m"
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    trendDirection: TrendDirection,
    trendLabel: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(110.dp),   // fixed height — same for ALL cards
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(
            width = 0.dp,
            color = Color.Transparent
        )
    ) {
        // Purple left border accent — apply to ALL cards, not just Nicotine/CO
        Row(modifier = Modifier.fillMaxSize()) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            listOf(VioletPrimary, VioletLight)
                        )
                    )
            )

            // Card content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Title + icon row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = VioletPrimary.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Primary value — large
                Text(
                    text = value,
                    style = if (trendLabel.isEmpty()) {
                        MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp)
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    fontWeight = FontWeight.Bold,
                    color = VioletPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Trend badge row — OR empty space if no trend
                if (trendLabel.isNotEmpty()) {
                    val trendColor = when (trendDirection) {
                        TrendDirection.UP -> MintAccent
                        TrendDirection.DOWN -> CoralAccent
                        TrendDirection.NEUTRAL -> TextSecondary
                    }
                    val trendIcon = when (trendDirection) {
                        TrendDirection.UP -> Icons.Outlined.TrendingUp
                        TrendDirection.DOWN -> Icons.Outlined.TrendingDown
                        TrendDirection.NEUTRAL -> Icons.Outlined.TrendingFlat
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(trendColor.copy(alpha = 0.10f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            trendIcon,
                            contentDescription = null,
                            tint = trendColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            trendLabel,
                            fontSize = 10.sp,
                            color = trendColor,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    // Empty spacer to maintain consistent height when no trend
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}
