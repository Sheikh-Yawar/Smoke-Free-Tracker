package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smokefree_settings")

class AppPreferences(private val context: Context) {

    companion object {
        private val IS_ONBOARDED = booleanPreferencesKey("is_onboarded")
        private val QUIT_DATE_MILLIS = longPreferencesKey("quit_date_millis")
        private val CIGARETTES_PER_DAY = intPreferencesKey("cigarettes_per_day")
        private val CIGARETTES_PER_PACK = intPreferencesKey("cigarettes_per_pack")
        private val COST_PER_PACK = doublePreferencesKey("cost_per_pack")
        private val CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
        private val BRAND_NAME = stringPreferencesKey("brand_name")
        private val ENABLE_DAILY_QUOTES = booleanPreferencesKey("enable_daily_quotes")
        private val ENABLE_MILESTONE_NOTIF = booleanPreferencesKey("enable_milestone_notif")
        private val ENABLE_STREAK_NOTIF = booleanPreferencesKey("enable_streak_notif")
        private val NOTIFICATION_TIME = stringPreferencesKey("notification_time")
        private val ACCENT_COLOR_INDEX = intPreferencesKey("accent_color_index")
        private val FIRST_DAY_OF_WEEK = stringPreferencesKey("first_day_of_week")
        private val ENABLE_GENERATIVE_AFFIRMATIONS = booleanPreferencesKey("enable_generative_affirmations")
        private val FIRST_COACH_OPEN = booleanPreferencesKey("first_coach_open")
        private val LAST_CHECKIN_DATE = stringPreferencesKey("last_checkin_date")
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            isOnboarded = preferences[IS_ONBOARDED] ?: false,
            quitDateMillis = preferences[QUIT_DATE_MILLIS] ?: 0L,
            cigarettesPerDay = preferences[CIGARETTES_PER_DAY] ?: 10,
            cigarettesPerPack = preferences[CIGARETTES_PER_PACK] ?: 20,
            costPerPack = preferences[COST_PER_PACK] ?: 150.0,
            currencySymbol = preferences[CURRENCY_SYMBOL] ?: "₹",
            brandName = preferences[BRAND_NAME] ?: "",
            enableDailyQuotes = preferences[ENABLE_DAILY_QUOTES] ?: true,
            enableMilestoneNotif = preferences[ENABLE_MILESTONE_NOTIF] ?: true,
            enableStreakNotif = preferences[ENABLE_STREAK_NOTIF] ?: true,
            notificationTime = preferences[NOTIFICATION_TIME] ?: "09:00",
            accentColorIndex = preferences[ACCENT_COLOR_INDEX] ?: 0,
            firstDayOfWeek = preferences[FIRST_DAY_OF_WEEK] ?: "SUNDAY",
            enableGenerativeAffirmations = preferences[ENABLE_GENERATIVE_AFFIRMATIONS] ?: false,
            firstCoachOpen = preferences[FIRST_COACH_OPEN] ?: true,
            lastCheckinDate = preferences[LAST_CHECKIN_DATE] ?: ""
        )
    }

    suspend fun updateSettings(update: (UserSettings) -> UserSettings) {
        context.dataStore.edit { preferences ->
            val current = UserSettings(
                isOnboarded = preferences[IS_ONBOARDED] ?: false,
                quitDateMillis = preferences[QUIT_DATE_MILLIS] ?: 0L,
                cigarettesPerDay = preferences[CIGARETTES_PER_DAY] ?: 10,
                cigarettesPerPack = preferences[CIGARETTES_PER_PACK] ?: 20,
                costPerPack = preferences[COST_PER_PACK] ?: 150.0,
                currencySymbol = preferences[CURRENCY_SYMBOL] ?: "₹",
                brandName = preferences[BRAND_NAME] ?: "",
                enableDailyQuotes = preferences[ENABLE_DAILY_QUOTES] ?: true,
                enableMilestoneNotif = preferences[ENABLE_MILESTONE_NOTIF] ?: true,
                enableStreakNotif = preferences[ENABLE_STREAK_NOTIF] ?: true,
                notificationTime = preferences[NOTIFICATION_TIME] ?: "09:00",
                accentColorIndex = preferences[ACCENT_COLOR_INDEX] ?: 0,
                firstDayOfWeek = preferences[FIRST_DAY_OF_WEEK] ?: "SUNDAY",
                enableGenerativeAffirmations = preferences[ENABLE_GENERATIVE_AFFIRMATIONS] ?: false,
                firstCoachOpen = preferences[FIRST_COACH_OPEN] ?: true,
                lastCheckinDate = preferences[LAST_CHECKIN_DATE] ?: ""
            )
            val updated = update(current)
            preferences[IS_ONBOARDED] = updated.isOnboarded
            preferences[QUIT_DATE_MILLIS] = updated.quitDateMillis
            preferences[CIGARETTES_PER_DAY] = updated.cigarettesPerDay
            preferences[CIGARETTES_PER_PACK] = updated.cigarettesPerPack
            preferences[COST_PER_PACK] = updated.costPerPack
            preferences[CURRENCY_SYMBOL] = updated.currencySymbol
            preferences[BRAND_NAME] = updated.brandName
            preferences[ENABLE_DAILY_QUOTES] = updated.enableDailyQuotes
            preferences[ENABLE_MILESTONE_NOTIF] = updated.enableMilestoneNotif
            preferences[ENABLE_STREAK_NOTIF] = updated.enableStreakNotif
            preferences[NOTIFICATION_TIME] = updated.notificationTime
            preferences[ACCENT_COLOR_INDEX] = updated.accentColorIndex
            preferences[FIRST_DAY_OF_WEEK] = updated.firstDayOfWeek
            preferences[ENABLE_GENERATIVE_AFFIRMATIONS] = updated.enableGenerativeAffirmations
            preferences[FIRST_COACH_OPEN] = updated.firstCoachOpen
            preferences[LAST_CHECKIN_DATE] = updated.lastCheckinDate
        }
    }

    suspend fun clearSettings() {
        context.dataStore.edit { it.clear() }
    }
}
