package com.example.data

data class UserSettings(
    val isOnboarded: Boolean = false,
    val quitDateMillis: Long = 0L, // 0 means not configured yet
    val cigarettesPerDay: Int = 10,
    val cigarettesPerPack: Int = 20,
    val costPerPack: Double = 150.0,
    val currencySymbol: String = "₹",
    val brandName: String = "Standard",
    val enableDailyQuotes: Boolean = true,
    val enableMilestoneNotif: Boolean = true,
    val enableStreakNotif: Boolean = true,
    val notificationTime: String = "09:00",
    val accentColorIndex: Int = 0,
    val firstDayOfWeek: String = "SUNDAY", // "SUNDAY", "MONDAY"
    val enableGenerativeAffirmations: Boolean = false,
    val firstCoachOpen: Boolean = true,
    val lastCheckinDate: String = ""
)
