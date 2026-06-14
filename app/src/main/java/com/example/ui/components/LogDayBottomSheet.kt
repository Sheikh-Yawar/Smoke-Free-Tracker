package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DayStatus
import com.example.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDayBottomSheet(
    initialDate: LocalDate,
    initialStatus: DayStatus = DayStatus.SMOKE_FREE,
    initialCigarettes: Int = 0,
    onSave: (date: LocalDate, status: DayStatus, cigarettes: Int, notes: String) -> Unit,
    onResetQuitDate: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(initialDate) }
    var selectedStatus by remember { mutableStateOf(if (initialStatus == DayStatus.PARTIAL) DayStatus.RELAPSED else initialStatus) }
    var cigaretteCount by remember { mutableStateOf(
        if (initialCigarettes > 0) initialCigarettes.toString() else "1"
    )}
    var notes by remember { mutableStateOf("") }  // ALWAYS starts empty — never pre-fill
    var showDatePicker by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val isSaveEnabled = selectedStatus == DayStatus.SMOKE_FREE || (cigaretteCount.toIntOrNull() ?: 0) > 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            // ── Title ──────────────────────────────────────────────────
            // Format date as "6 Jun 2026" — never raw ISO format
            Text(
                text = "Log Day — ${selectedDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "How did this day go?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // ── Date selector row ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { showDatePicker = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.CalendarMonth, contentDescription = null,
                    tint = VioletPrimary, modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    selectedDate.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text("Change", fontSize = 12.sp, color = VioletPrimary)
            }

            Spacer(Modifier.height(16.dp))

            // ── Status chips ───────────────────────────────────────────
            Text(
                "Status", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple(DayStatus.SMOKE_FREE, "Clean", MintAccent),
                    Triple(DayStatus.RELAPSED, "Relapsed", CoralAccent)
                ).forEach { (status, label, color) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                if (selectedStatus == status) color
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                1.5.dp,
                                if (selectedStatus == status) color
                                else DividerColor,
                                RoundedCornerShape(22.dp)
                            )
                            .clickable { selectedStatus = status },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (selectedStatus == status) FontWeight.Bold
                                        else FontWeight.Normal,
                            color = if (selectedStatus == status) Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // ── Cigarettes field — only show when Relapsed ──
            AnimatedVisibility(
                visible = selectedStatus == DayStatus.RELAPSED
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Cigarettes smoked",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cigaretteCount,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } &&
                                (input.toIntOrNull() ?: 0) <= 100) {
                                cigaretteCount = input
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true,
                        placeholder = { Text("e.g. 3") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VioletPrimary,
                            unfocusedBorderColor = DividerColor
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Optional notes ─────────────────────────────────────────
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("Add a note (optional)") },
                minLines = 2,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VioletPrimary,
                    unfocusedBorderColor = DividerColor
                )
            )

            Spacer(Modifier.height(20.dp))

            // ── Save / Cancel buttons ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Text("Cancel", maxLines = 1)
                }
                Button(
                    onClick = {
                        onSave(
                            selectedDate,
                            selectedStatus,
                            if (selectedStatus == DayStatus.SMOKE_FREE) 0 else (cigaretteCount.toIntOrNull() ?: 0),
                            notes
                        )
                    },
                    enabled = isSaveEnabled,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
                ) {
                    Text("Save Log", maxLines = 1)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Reset quit date button — present in both flows ─────────
            TextButton(
                onClick = { showResetConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Outlined.RestartAlt, contentDescription = null,
                    modifier = Modifier.size(16.dp), tint = TextSecondary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Reset my quit date to today",
                    color = TextSecondary, fontSize = 13.sp
                )
            }
        }
    }

    // ── Inline date picker — Material3 light style ─────────────────────
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = Instant.ofEpochMilli(utcTimeMillis)
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                    return !d.isAfter(LocalDate.now())
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
                containerColor = MaterialTheme.colorScheme.surface  // always light, matches app
            )
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── Reset quit date confirmation dialog ────────────────────────────
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset quit date?") },
            text = {
                Text(
                    "This will set your quit date to right now and restart your counter. Your calendar logs will be kept.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onResetQuitDate()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                    shape = RoundedCornerShape(50.dp)
                ) { Text("Yes, Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Keep Original", color = TextSecondary)
                }
            }
        )
    }
}
