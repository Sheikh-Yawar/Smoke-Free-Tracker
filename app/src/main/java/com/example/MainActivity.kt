package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Face
import com.example.ui.screens.calculateStreaks
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.DayStatus
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SmokeFreeViewModel
import com.example.widget.SmokeFreeWidget
import com.example.worker.NotificationWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

import androidx.compose.ui.draw.drawBehind
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {

    private lateinit var filePickerLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var createDocumentLauncher: androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appRepository = (applicationContext as SmokeFreeApp).repository
        val factory = SmokeFreeViewModel.Factory(application, appRepository)
        val viewModel = androidx.lifecycle.ViewModelProvider(this, factory)[SmokeFreeViewModel::class.java]



        filePickerLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                viewModel.importBackup(this, uri)
            }
        }

        createDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    val jsonString = viewModel.getBackupJson()
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(jsonString.toByteArray())
                    }
                    Toast.makeText(this, "Backup downloaded/saved successfully", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to save backup: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        setContent {
            val context = LocalContext.current

            // Dynamic states collection
            val settings by viewModel.userSettings.collectAsState()
            val logs by viewModel.allLogs.collectAsState()
            val onboardingStep by viewModel.onboardingStep.collectAsState()
            val isRefreshing by viewModel.isRefreshing.collectAsState()
            val showRelapseCompassion by viewModel.showRelapseDialog.collectAsState()
            val showImportSuccess by viewModel.showImportSuccess.collectAsState()
            val errorState by viewModel.errorState.collectAsState()



            var currentScreen by remember { mutableStateOf("dashboard") }

            val darkTheme = false

            // A. Auto sync widget cache when QuitDate updates
            LaunchedEffect(settings.quitDateMillis) {
                if (settings.quitDateMillis > 0L) {
                    val sharedPrefs = getSharedPreferences("smokefree_widget_prefs", MODE_PRIVATE)
                    sharedPrefs.edit().putLong("quit_date", settings.quitDateMillis).apply()
                    try {
                        SmokeFreeWidget().updateAll(this@MainActivity)
                    } catch (e: Exception) {
                        // widget offline or not placed on home screen template
                    }
                }
            }

            // B. Enqueue WorkManager daily notifications scheduled for 8:00 AM
            LaunchedEffect(Unit) {
                val calendar = java.util.Calendar.getInstance()
                val nowMillis = calendar.timeInMillis
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 8)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                
                if (calendar.timeInMillis <= nowMillis) {
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                
                val initialDelayMillis = calendar.timeInMillis - nowMillis
                
                val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(initialDelayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build()
                WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                    "smokefree_daily_quotes",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
            }

            // C. Display Toasts or SnackBar alerts for system actions
            LaunchedEffect(showImportSuccess) {
                if (showImportSuccess) {
                    Toast.makeText(context, "Backup JSON imported successfully!", Toast.LENGTH_LONG).show()
                    viewModel.dismissImportSuccess()
                }
            }

            LaunchedEffect(errorState) {
                if (errorState != null) {
                    Toast.makeText(context, "Error: $errorState", Toast.LENGTH_LONG).show()
                    viewModel.dismissError()
                }
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                if (!settings.isOnboarded) {
                    OnboardingScreen(
                        currentStep = onboardingStep,
                        onNext = { updatedSettings ->
                            if (onboardingStep < 3) {
                                viewModel.updateQuitDate(updatedSettings.quitDateMillis)
                                viewModel.updateQuitProfile(
                                    updatedSettings.cigarettesPerDay,
                                    updatedSettings.cigarettesPerPack,
                                    updatedSettings.costPerPack,
                                    updatedSettings.currencySymbol,
                                    updatedSettings.brandName
                                )
                                viewModel.updateNotificationPreferences(
                                    updatedSettings.enableDailyQuotes,
                                    updatedSettings.enableMilestoneNotif,
                                    updatedSettings.enableStreakNotif,
                                    updatedSettings.notificationTime
                                )
                                viewModel.nextOnboardingStep(updatedSettings)
                            } else {
                                viewModel.nextOnboardingStep(updatedSettings)
                            }
                        },
                        onPrev = { viewModel.prevOnboardingStep() },
                        onImportClick = { filePickerLauncher.launch("application/json") }
                    )
                } else {
                    // Standard App scaffold layouts
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("main_scaffold"),
                        bottomBar = {
                            NavigationBar(
                                containerColor = SurfaceCard,
                                modifier = Modifier
                                    .testTag("bottom_nav_bar")
                                    .fillMaxWidth()
                                    .drawBehind {
                                        drawLine(
                                            color = DividerColor,
                                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                            ) {
                                NavigationBarItem(
                                    selected = currentScreen == "dashboard",
                                    onClick = { currentScreen = "dashboard" },
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                                    label = { Text("Dashboard", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.testTag("nav_tab_dashboard"),
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = VioletPrimary,
                                        selectedTextColor = VioletPrimary,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = SurfaceElevated
                                    )
                                )

                                NavigationBarItem(
                                    selected = currentScreen == "health",
                                    onClick = { currentScreen = "health" },
                                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Health") },
                                    label = { Text("Health", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.testTag("nav_tab_health"),
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = VioletPrimary,
                                        selectedTextColor = VioletPrimary,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = SurfaceElevated
                                    )
                                )

                                NavigationBarItem(
                                    selected = currentScreen == "calendar",
                                    onClick = { currentScreen = "calendar" },
                                    icon = { Icon(Icons.Default.CalendarToday, contentDescription = "Calendar") },
                                    label = { Text("Calendar", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.testTag("nav_tab_calendar"),
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = VioletPrimary,
                                        selectedTextColor = VioletPrimary,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = SurfaceElevated
                                    )
                                )

                                NavigationBarItem(
                                    selected = currentScreen == "settings",
                                    onClick = { currentScreen = "settings" },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.testTag("nav_tab_settings"),
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = VioletPrimary,
                                        selectedTextColor = VioletPrimary,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = SurfaceElevated
                                    )
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = innerPadding.calculateBottomPadding()
                                )
                        ) {
                            when (currentScreen) {
                                "dashboard" -> DashboardScreen(
                                    settings = settings,
                                    logs = logs,
                                    viewModel = viewModel,
                                    onCheckInAnswer = { didSmoke ->
                                        viewModel.recordCheckInAnswer(didSmoke)
                                    }
                                )
                                "health" -> HealthScreen(
                                    viewModel = viewModel
                                )
                                "calendar" -> CalendarScreen(
                                    settings = settings,
                                    logs = logs,
                                    viewModel = viewModel
                                )
                                "settings" -> SettingsScreen(
                                    settings = settings,
                                    onUpdateProfile = { day, pack, cost, cur, brand ->
                                        viewModel.updateQuitProfile(day, pack, cost, cur, brand)
                                        Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                                    },
                                    onUpdateQuitDate = { newTime ->
                                        viewModel.updateQuitDate(newTime)
                                        Toast.makeText(context, "Quit date updated", Toast.LENGTH_SHORT).show()
                                    },
                                    onUpdateNotifications = { quotesPref, milestonesPref, streakPref, timePref ->
                                        viewModel.updateNotificationPreferences(quotesPref, milestonesPref, streakPref, timePref)
                                    },
                                    onUpdateFirstDay = { fDay ->
                                        viewModel.updateFirstDayOfWeek(fDay)
                                    },
                                    onExportData = {
                                        createDocumentLauncher.launch("smokefree_backup.json")
                                    },
                                    onImportData = {
                                        filePickerLauncher.launch("application/json")
                                    },
                                    onResetData = {
                                        viewModel.resetAllData()
                                        Toast.makeText(context, "All data wiped", Toast.LENGTH_SHORT).show()
                                        currentScreen = "dashboard"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun shareBackupFile(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Smoke Free JSON Backup"))
    }
}
