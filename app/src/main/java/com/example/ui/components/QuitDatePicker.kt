package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun QuitDateTile(
    quitDate: LocalDate,
    quitTime: LocalTime,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val formattedDate = quitDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    val formattedTime = quitTime.format(DateTimeFormatter.ofPattern("h:mm a"))
    val dayOfWeek = quitDate.dayOfWeek.getDisplayName(
        java.time.format.TextStyle.FULL, java.util.Locale.getDefault()
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = VioletPrimary.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.dp, VioletPrimary.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: calendar icon in colored box
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VioletPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            // Center: date and time info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quit Date & Time",
                    style = MaterialTheme.typography.labelSmall,
                    color = VioletPrimary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$dayOfWeek, $formattedDate",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Right: edit chevron
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "Edit",
                tint = VioletPrimary.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuitDatePickerSheet(
    currentQuitDate: LocalDate,
    currentQuitTime: LocalTime,
    onConfirm: (LocalDate, LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(currentQuitDate) }
    var hourInput by remember { mutableStateOf(
        currentQuitTime.format(DateTimeFormatter.ofPattern("hh"))
    )}
    var minuteInput by remember { mutableStateOf(
        currentQuitTime.format(DateTimeFormatter.ofPattern("mm"))
    )}
    var isAm by remember { mutableStateOf(currentQuitTime.hour < 12) }
    var showDatePicker by remember { mutableStateOf(false) }

    val hourItems = remember { (1..12).map { String.format("%02d", it) } }
    val minuteItems = remember { (0..59).map { String.format("%02d", it) } }
    val initialHourIndex = remember(currentQuitTime) {
        hourItems.indexOf(currentQuitTime.format(DateTimeFormatter.ofPattern("hh"))).coerceAtLeast(0)
    }
    val initialMinuteIndex = remember(currentQuitTime) {
        minuteItems.indexOf(currentQuitTime.format(DateTimeFormatter.ofPattern("mm"))).coerceAtLeast(0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Text(
                "Set Quit Date & Time",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "When did you smoke your last cigarette?",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(Modifier.height(20.dp))

            // Date selector row — same style as LogDayBottomSheet
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceElevated)
                    .clickable { showDatePicker = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null,
                    tint = VioletPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Text("Change", fontSize = 12.sp, color = VioletPrimary)
            }

            Spacer(Modifier.height(20.dp))

            Text("TIME", style = MaterialTheme.typography.labelSmall,
                color = TextSecondary, letterSpacing = 2.sp)
            Spacer(Modifier.height(12.dp))

            // Time input — scrollable drum-wheels + AM/PM
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
                        hourInput = hourItems[index]
                    },
                    modifier = Modifier.width(80.dp),
                    testTag = "quit_hour_picker"
                )

                Text(" : ", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark, modifier = Modifier.padding(horizontal = 8.dp))

                // Minute Wheel Picker
                WheelPicker(
                    items = minuteItems,
                    initialIndex = initialMinuteIndex,
                    onItemSelected = { index ->
                        minuteInput = minuteItems[index]
                    },
                    modifier = Modifier.width(80.dp),
                    testTag = "quit_minute_picker"
                )

                Spacer(Modifier.width(16.dp))

                // AM/PM selection - compact stacked toggle
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

            // Live preview of selected datetime
            Spacer(Modifier.height(10.dp))
            val previewHour = hourInput.toIntOrNull() ?: 12
            val previewMin = minuteInput.padStart(2, '0')
            val amPmLabel = if (isAm) "AM" else "PM"
            Text(
                "${selectedDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}, $previewHour:$previewMin $amPmLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // Confirm button
            Button(
                onClick = {
                    val h = hourInput.toIntOrNull() ?: 12
                    val m = minuteInput.toIntOrNull() ?: 0
                    val hour24 = when {
                        isAm && h == 12 -> 0
                        !isAm && h != 12 -> h + 12
                        else -> h
                    }
                    onConfirm(selectedDate, LocalTime.of(hour24, m))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
            ) {
                Text("Confirm Quit Date & Time",
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = Instant.ofEpochMilli(utcTimeMillis)
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                    return !d.isAfter(LocalDate.now()) &&
                           !d.isBefore(LocalDate.now().minusYears(10))
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK", color = VioletPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
