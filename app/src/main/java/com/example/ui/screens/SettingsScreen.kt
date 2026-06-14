package com.example.ui.screens

import android.app.TimePickerDialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserSettings
import com.example.ui.theme.*
import com.example.ui.components.CurrencyDropdown
import com.example.ui.components.QuitDateTile
import com.example.ui.components.QuitDatePickerSheet
import com.example.ui.components.WheelPicker
import java.text.SimpleDateFormat
import java.util.*
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.foundation.BorderStroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    onUpdateProfile: (cigsPerDay: Int, cigsPerPack: Int, costPerPack: Double, currency: String, brand: String) -> Unit,
    onUpdateQuitDate: (Long) -> Unit,
    onUpdateNotifications: (enableDaily: Boolean, enableMilestones: Boolean, enableStreak: Boolean, time: String) -> Unit,
    onUpdateFirstDay: (String) -> Unit,
    onExportData: () -> Unit,
    onImportData: () -> Unit,
    onResetData: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Form states
    var cigarettesPerDay by remember(settings) { mutableStateOf(settings.cigarettesPerDay.toString()) }
    var cigarettesPerPack by remember(settings) { mutableStateOf(settings.cigarettesPerPack.toString()) }
    var costPerPack by remember(settings) { mutableStateOf(settings.costPerPack.toString()) }
    var currencySymbol by remember(settings) { mutableStateOf(settings.currencySymbol) }
    var brandName by remember(settings) { mutableStateOf(settings.brandName) }

    var enableQuotes by remember(settings) { mutableStateOf(settings.enableDailyQuotes) }
    var enableMilestones by remember(settings) { mutableStateOf(settings.enableMilestoneNotif) }
    var enableStreaks by remember(settings) { mutableStateOf(settings.enableStreakNotif) }
    var notificationTime by remember(settings) { mutableStateOf(settings.notificationTime) }

    // Validation errors
    var cigsPerDayError by remember { mutableStateOf<String?>(null) }
    var cigsPerPackError by remember { mutableStateOf<String?>(null) }
    var costError by remember { mutableStateOf<String?>(null) }
    var currencyError by remember { mutableStateOf<String?>(null) }
    var brandError by remember { mutableStateOf<String?>(null) }

    val formattedQuitDate = remember(settings.quitDateMillis) {
        val sdf = SimpleDateFormat("d MMM yyyy, hh:mm a", Locale.getDefault())
        if (settings.quitDateMillis > 0L) sdf.format(Date(settings.quitDateMillis)) else "Not set"
    }

    val quitLocalDate = remember(settings.quitDateMillis) {
        if (settings.quitDateMillis > 0L) {
            Instant.ofEpochMilli(settings.quitDateMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        } else {
            LocalDate.now()
        }
    }

    val quitLocalTime = remember(settings.quitDateMillis) {
        if (settings.quitDateMillis > 0L) {
            Instant.ofEpochMilli(settings.quitDateMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
        } else {
            LocalTime.now()
        }
    }

    // Modal safety confirmation dialogs
    var showResetDialog by remember { mutableStateOf(false) }

    var calendarExpanded by remember { mutableStateOf(false) }
    var showQuitDateSheet by remember { mutableStateOf(false) }
    var showCompanionTimePicker by remember { mutableStateOf(false) }

    // Live validation
    fun validateForm(): Boolean {
        var isValid = true

        val daysCp = cigarettesPerDay.trim()
        val daysInt = daysCp.toIntOrNull()
        if (daysCp.contains(" ") || daysInt == null || daysInt !in 1..200) {
            cigsPerDayError = "Must be a number between 1 and 200 (no spaces)"
            isValid = false
        } else {
            cigsPerDayError = null
        }

        val packCp = cigarettesPerPack.trim()
        val packInt = packCp.toIntOrNull()
        if (packCp.contains(" ") || packInt == null || packInt !in 1..100) {
            cigsPerPackError = "Must be a number between 1 and 100 (no spaces)"
            isValid = false
        } else {
            cigsPerPackError = null
        }

        val costCp = costPerPack.trim()
        val costRegex = Regex("^\\d+(\\.\\d{1,2})?$")
        if (costCp.contains(" ") || !costRegex.matches(costCp) || costCp.toDoubleOrNull() == null) {
            costError = "Invalid cost format (e.g. 150.50 or 150, no spaces or letters)"
            isValid = false
        } else {
            costError = null
        }

        val currCp = currencySymbol.trim()
        val hasSpacesOrDigits = currCp.any { it.isWhitespace() || it.isDigit() }
        if (currCp.isEmpty() || currCp.length > 3 || hasSpacesOrDigits) {
            currencyError = "Max 3 chars, no spaces or digits"
            isValid = false
        } else {
            currencyError = null
        }

        val bName = brandName
        if (bName.length > 30 || bName.contains("\n")) {
            brandError = "Max 30 characters and no newlines"
            isValid = false
        } else {
            brandError = null
        }

        return isValid
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .verticalScroll(scrollState)
            .statusBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App settings banner title
        Text(
            text = "Settings Portal",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                color = TextPrimaryDark,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // ----------------- Section 1: QUIT PROFILE -----------------
        SettingsSectionHeader("Quit Details Profile")

        Surface(
            color = SurfaceCard,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 2.dp,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                QuitDateTile(
                    quitDate = quitLocalDate,
                    quitTime = quitLocalTime,
                    modifier = Modifier.testTag("settings_choose_date"),
                    onClick = { showQuitDateSheet = true }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Form fields matching Onboarding styles
                CustomInputSettings(
                    value = cigarettesPerDay,
                    onValueChange = { cigarettesPerDay = it },
                    label = "Cigarettes per day (prior)",
                    isError = cigsPerDayError != null,
                    errorText = cigsPerDayError,
                    leadingIcon = Icons.Default.SmokingRooms,
                    testTagValue = "settings_cigs_day"
                )

                Spacer(modifier = Modifier.height(12.dp))

                CustomInputSettings(
                    value = cigarettesPerPack,
                    onValueChange = { cigarettesPerPack = it },
                    label = "Cigarettes per pack",
                    isError = cigsPerPackError != null,
                    errorText = cigsPerPackError,
                    leadingIcon = Icons.Default.Inventory
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        CurrencyDropdown(
                            selectedSymbol = currencySymbol,
                            onCurrencySelected = { currencySymbol = it.symbol },
                            modifier = Modifier.width(88.dp)
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        CustomInputSettings(
                            value = costPerPack,
                            onValueChange = { costPerPack = it },
                            label = "Cost per pack",
                            isError = costError != null,
                            errorText = costError,
                            leadingIcon = Icons.Default.PriceChange,
                            keyboardType = KeyboardType.Decimal,
                            testTagValue = "settings_cost_pack"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                CustomInputSettings(
                    value = brandName,
                    onValueChange = { brandName = it },
                    label = "Cigarette Brand REFERENCE",
                    isError = brandError != null,
                    errorText = brandError,
                    leadingIcon = Icons.Default.LocalOffer
                )

                Spacer(modifier = Modifier.height(20.dp))

                // SAVE PROFILE BUTTON WITH PULSING SCALE TRANSITION ON CLICK
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "save_btn_scale")

                Button(
                    onClick = {
                        if (validateForm()) {
                            val dayCount = cigarettesPerDay.trim().toIntOrNull() ?: 10
                            val packCount = cigarettesPerPack.trim().toIntOrNull() ?: 20
                            val packCost = costPerPack.trim().toDoubleOrNull() ?: 150.0
                            onUpdateProfile(dayCount, packCount, packCost, currencySymbol.trim(), brandName.trim())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                    shape = RoundedCornerShape(50.dp),
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .testTag("save_profile_btn")
                ) {
                    Text(
                        "Save Profile",
                        color = TextOnAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }



        // ----------------- Section 3: DISPLAY PREFS -----------------
        SettingsSectionHeader("Calendar Preferences")

        Surface(
            color = SurfaceCard,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 2.dp,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Calendar First Day Dropdown
                Box {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { calendarExpanded = true }
                            .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
                            .background(BackgroundBase, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "FIRST DAY OF WEEK",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.5.sp),
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = settings.firstDayOfWeek,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimaryDark
                                    )
                                )
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                        }
                    }

                    DropdownMenu(
                        expanded = calendarExpanded,
                        onDismissRequest = { calendarExpanded = false },
                        modifier = Modifier.background(SurfaceCard)
                    ) {
                        DropdownMenuItem(
                            text = { Text("SUNDAY", color = TextPrimaryDark, fontWeight = FontWeight.Medium) },
                            onClick = {
                                onUpdateFirstDay("SUNDAY")
                                calendarExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("MONDAY", color = TextPrimaryDark, fontWeight = FontWeight.Medium) },
                            onClick = {
                                onUpdateFirstDay("MONDAY")
                                calendarExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // ----------------- Section 4: DATA MANAGEMENT -----------------
        SettingsSectionHeader("Data Management Backups")

        Surface(
            color = SurfaceCard,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 2.dp,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp)
        ) {
            Column {
                SettingsListItem(
                    headline = "Export JSON Backup",
                    icon = Icons.Default.Share,
                    iconTint = VioletPrimary,
                    modifier = Modifier
                        .clickable(onClick = onExportData)
                        .testTag("export_backup_btn")
                )

                SettingsDivider()

                SettingsListItem(
                    headline = "Import JSON Backup File",
                    icon = Icons.Default.FileOpen,
                    iconTint = MintAccent,
                    modifier = Modifier
                        .clickable(onClick = onImportData)
                        .testTag("import_backup_btn")
                )

                SettingsDivider()

                SettingsListItem(
                    headline = "Wipe & Reset All Data",
                    icon = Icons.Default.DeleteForever,
                    iconTint = CoralAccent,
                    textColor = CoralAccent,
                    modifier = Modifier
                        .clickable { showResetDialog = true }
                        .testTag("reset_data_btn")
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }

    if (showQuitDateSheet) {
        QuitDatePickerSheet(
            currentQuitDate = quitLocalDate,
            currentQuitTime = quitLocalTime,
            onConfirm = { date, time ->
                val newMillis = date.atTime(time)
                    .atZone(ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
                onUpdateQuitDate(newMillis)
                showQuitDateSheet = false
            },
            onDismiss = { showQuitDateSheet = false }
        )
    }

    // Safety Red Warning Wipe Dialogs (Single Pop-up)
    if (showResetDialog) {
        AlertDialog(
            containerColor = SurfaceCard,
            onDismissRequest = { showResetDialog = false },
            title = { Text("Danger: Wipe Data?", fontWeight = FontWeight.Bold, color = TextPrimaryDark) },
            text = { Text("Are you absolutely sure you want to permanently delete all calendar logs and reset your setting profiles? This action is IRREVERSIBLE.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        onResetData()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralAccent),
                    modifier = Modifier.testTag("double_confirm_reset_btn")
                ) {
                    Text("Wipe EVERYTHING", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = VioletPrimary, fontWeight = FontWeight.Medium)
                }
            }
        )
    }


}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = VioletPrimary,
        style = MaterialTheme.typography.titleSmall.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, bottom = 10.dp)
    )
}

@Composable
fun SettingsListItem(
    headline: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconTint: Color = VioletPrimary,
    textColor: Color = TextPrimaryDark
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = iconTint)
                Spacer(modifier = Modifier.width(16.dp))
            }
            Text(
                text = headline,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun SettingsDivider() {
    Divider(
        color = DividerColor,
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
fun CustomInputSettings(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    errorText: String?,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Number,
    testTagValue: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val modifierBase = Modifier.fillMaxWidth()
        val textFiledModifier = if (testTagValue != null) modifierBase.testTag(testTagValue) else modifierBase

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = textFiledModifier,
            isError = isError,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(leadingIcon, contentDescription = null, tint = TextSecondary.copy(alpha = 0.5f)) },
            label = {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = if (isError) CoralAccent else TextSecondary
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VioletPrimary,
                unfocusedBorderColor = DividerColor,
                errorBorderColor = CoralAccent,
                focusedTextColor = TextPrimaryDark,
                unfocusedTextColor = TextPrimaryDark,
                focusedLabelColor = VioletPrimary,
                unfocusedLabelColor = TextSecondary,
                focusedContainerColor = SurfaceCard,
                unfocusedContainerColor = SurfaceCard
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
        if (isError && errorText != null) {
            Text(
                text = errorText,
                color = CoralAccent,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionReminderTimePickerSheet(
    initialTime: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val parts = initialTime.split(":")
    val initialHour24 = parts.getOrNull(0)?.toIntOrNull() ?: 9
    val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0

    // Convert 24h to 12h format for AM/PM picker
    val initialHour12 = when {
        initialHour24 == 0 -> 12
        initialHour24 > 12 -> initialHour24 - 12
        else -> initialHour24
    }
    val initialIsAm = initialHour24 < 12

    val hourItems = remember { (1..12).map { String.format("%02d", it) } }
    val minuteItems = remember { (0..59).map { String.format("%02d", it) } }

    val initialHourIndex = remember(initialHour12) {
        hourItems.indexOf(String.format("%02d", initialHour12)).coerceAtLeast(0)
    }
    val initialMinuteIndex = remember(initialMinute) {
        minuteItems.indexOf(String.format("%02d", initialMinute)).coerceAtLeast(0)
    }

    var selectedHour12 by remember { mutableStateOf(String.format("%02d", initialHour12)) }
    var selectedMinute by remember { mutableStateOf(String.format("%02d", initialMinute)) }
    var isAm by remember { mutableStateOf(initialIsAm) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = BackgroundBase
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            // Header
            Text(
                "Daily Companion Reminder",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryDark
            )
            Spacer(Modifier.height(8.dp))
            
            // Description of what this functionality does
            Card(
                colors = CardDefaults.cardColors(containerColor = VioletPrimary.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, VioletPrimary.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "What is the Daily Companion Reminder?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = VioletPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Your daily quit companion checks in on you at your designated time to ask about your status, calculate your streaks, progress, and generate expert, supportive, and motivational AI coaching affirmations to guide you along your journey.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("CHOOSE REMINDER TIME", style = MaterialTheme.typography.labelSmall,
                color = TextSecondary, letterSpacing = 2.sp)
            Spacer(Modifier.height(12.dp))

            // Scrollable drum-wheels + AM/PM
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Hour Wheel Picker
                WheelPicker(
                    items = hourItems,
                    initialIndex = initialHourIndex,
                    onItemSelected = { index ->
                        selectedHour12 = hourItems[index]
                    },
                    modifier = Modifier.width(80.dp),
                    testTag = "reminder_hour_picker"
                )

                Text(" : ", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark, modifier = Modifier.padding(horizontal = 8.dp))

                // Minute Wheel Picker
                WheelPicker(
                    items = minuteItems,
                    initialIndex = initialMinuteIndex,
                    onItemSelected = { index ->
                        selectedMinute = minuteItems[index]
                    },
                    modifier = Modifier.width(80.dp),
                    testTag = "reminder_minute_picker"
                )

                Spacer(Modifier.width(16.dp))

                // AM/PM selection
                Column(
                    modifier = Modifier
                        .height(80.dp)
                        .width(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
                ) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .background(if (isAm) VioletPrimary else Color.Transparent)
                            .clickable { isAm = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("AM", fontSize = 13.sp, maxLines = 1,
                            fontWeight = if (isAm) FontWeight.Bold else FontWeight.Normal,
                            color = if (isAm) Color.White else TextSecondary)
                    }
                    HorizontalDivider(color = DividerColor, thickness = 1.dp)
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .background(if (!isAm) VioletPrimary else Color.Transparent)
                            .clickable { isAm = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("PM", fontSize = 13.sp, maxLines = 1,
                            fontWeight = if (!isAm) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isAm) Color.White else TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Confirm button
            Button(
                onClick = {
                    val h12 = selectedHour12.toIntOrNull() ?: 12
                    val m = selectedMinute.toIntOrNull() ?: 0
                    val hour24 = when {
                        isAm && h12 == 12 -> 0
                        !isAm && h12 != 12 -> h12 + 12
                        else -> h12
                    }
                    val formatted = String.format(Locale.getDefault(), "%02d:%02d", hour24, m)
                    onConfirm(formatted)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
            ) {
                Text("Save Reminder Time",
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}
