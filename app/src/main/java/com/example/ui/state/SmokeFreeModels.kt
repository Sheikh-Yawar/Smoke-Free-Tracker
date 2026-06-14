package com.example.ui.state

enum class HealthCategory {
    CARDIOVASCULAR,
    RESPIRATORY,
    NEUROLOGICAL,
    CANCER_RISK,
    OTHER
}

data class RecoveryStage(
    val minutesAfterQuitting: Long,
    val progressPercent: Float, // 0.0 to 1.0
    val label: String
)

data class BodyStatCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconName: String,
    val gradientStartHex: String,
    val gradientEndHex: String,
    val stages: List<RecoveryStage>
)

enum class AchievementTier { BRONZE, SILVER, GOLD, PLATINUM, DIAMOND }

data class Achievement(
    val id: String,
    val title: String,
    val subtitle: String,
    val daysRequired: Int,
    val tier: AchievementTier,
    val xpReward: Int,
    val iconName: String
)

data class Badge(
    val id: String,
    val title: String,
    val subtitle: String,
    val daysRequired: Int,
    val iconName: String
)

