package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SmokingRooms
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import android.app.DatePickerDialog
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DailyLog
import com.example.data.DayStatus
import com.example.data.UserSettings
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import com.example.ui.components.LogDayBottomSheet
import com.example.ui.viewmodel.SmokeFreeViewModel
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    settings: UserSettings,
    logs: List<DailyLog>,
    viewModel: SmokeFreeViewModel
) {
    val logsMap = remember(logs) { logs.associateBy { it.date } }

    // Navigation calendar month anchor
    var calendarAnchor by remember { mutableStateOf(Calendar.getInstance()) }
    val displayYear = calendarAnchor.get(Calendar.YEAR)
    val displayMonth = calendarAnchor.get(Calendar.MONTH) // 0-indexed

    val monthName = remember(calendarAnchor) {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        sdf.format(calendarAnchor.time)
    }

    // Days in current selected Month
    val daysInMonth = remember(calendarAnchor) {
        val tempCal = calendarAnchor.clone() as Calendar
        tempCal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK) // Sunday=1, Sat=7
        val daysTotal = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        Pair(firstDayOfWeek, daysTotal)
    }

    // Active calendar selection for edit dialogues
    var openEditDialogForDate by remember { mutableStateOf<String?>(null) }

    // Calculations for monthly summaries
    val monthPrefix = String.format(Locale.US, "%04d-%02d", displayYear, displayMonth + 1)
    val monthlyLogs = remember(logs, monthPrefix) {
        logs.filter { it.date.startsWith(monthPrefix) }
    }
    val cleanDaysCount = monthlyLogs.count { it.status == DayStatus.SMOKE_FREE }
    val relapseDaysCount = monthlyLogs.count { it.status == DayStatus.RELAPSED }
    val partialDaysCount = monthlyLogs.count { it.status == DayStatus.PARTIAL }
    val totalCigarettesSmoked = monthlyLogs.sumOf { it.cigarettesSmoked }

    val context = LocalContext.current

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    openEditDialogForDate = sdf.format(Date())
                },
                containerColor = VioletPrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .navigationBarsPadding()  // respects gesture nav bar on all devices
                    .padding(bottom = 8.dp, end = 4.dp)
                    .testTag("calendar_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Log Day",
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Month navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val newCal = calendarAnchor.clone() as Calendar
                    newCal.add(Calendar.MONTH, -1)
                    calendarAnchor = newCal
                },
                modifier = Modifier.testTag("prev_month_btn")
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
            }

            Text(
                text = monthName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = {
                    val newCal = calendarAnchor.clone() as Calendar
                    newCal.add(Calendar.MONTH, 1)
                    calendarAnchor = newCal
                },
                modifier = Modifier.testTag("next_month_btn")
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
            }
        }

        // Calendar Week headings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val daysHeading = if (settings.firstDayOfWeek == "SUNDAY") {
                listOf("S", "M", "T", "W", "T", "F", "S")
            } else {
                listOf("M", "T", "W", "T", "F", "S", "S")
            }
            daysHeading.forEach { dayHead ->
                Text(
                    text = dayHead,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Calendar days cells in a vertical scrollable column (to avoid cutting text fields!)
        Column(
            modifier = Modifier
                .weight(12f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
        ) {
            // Compute blank day spaces
            val firstDayIndex = daysInMonth.first // 1-indexed (Sunday = 1)
            val totalDays = daysInMonth.second

            val blankPrefixCount = if (settings.firstDayOfWeek == "SUNDAY") {
                firstDayIndex - 1
            } else {
                // Monday is first day of week
                val tempVal = firstDayIndex - 2
                if (tempVal < 0) 6 else tempVal
            }

            // Group cells into rows of 7
            val totalCells = blankPrefixCount + totalDays
            val rowsCount = (totalCells + 6) / 7

            for (rowIndex in 0 until rowsCount) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (colIndex in 0..6) {
                        val cellGlobalIndex = rowIndex * 7 + colIndex
                        if (cellGlobalIndex < blankPrefixCount || cellGlobalIndex >= totalCells) {
                            // Blank filler box
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            val activeDay = cellGlobalIndex - blankPrefixCount + 1
                            val dateString = String.format(Locale.US, "%04d-%02d-%02d", displayYear, displayMonth + 1, activeDay)
                            val log = logsMap[dateString]

                            val isFuture = dayIsInFuture(displayYear, displayMonth, activeDay)

                            val checkDate = LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE)
                            val quitLocalDate = if (settings.quitDateMillis > 0L) {
                                Instant.ofEpochMilli(settings.quitDateMillis)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                            } else {
                                null
                            }
                            val today = LocalDate.now()
                            val isBetweenQuitAndToday = quitLocalDate != null && 
                                    !checkDate.isBefore(quitLocalDate) && 
                                    !checkDate.isAfter(today)

                            // Colour-coding logic
                            val cellColor = when {
                                isFuture -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                log == null -> {
                                    if (isBetweenQuitAndToday) {
                                        CalendarClean
                                    } else {
                                        CalendarEmpty.copy(alpha = 0.15f)
                                    }
                                }
                                log.status == DayStatus.SMOKE_FREE -> CalendarClean
                                log.status == DayStatus.RELAPSED -> CalendarRelapse
                                log.status == DayStatus.PARTIAL -> CalendarPartial
                                else -> CalendarEmpty.copy(alpha = 0.4f)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.1f)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(cellColor)
                                    .clickable(enabled = !isFuture) {
                                        openEditDialogForDate = dateString
                                    }
                                    .testTag("day_cell_$dateString"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$activeDay",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (log != null && !isFuture) Color.Black else MaterialTheme.colorScheme.onBackground
                                    )
                                    if (log?.cigarettesSmoked != null && log.cigarettesSmoked > 0) {
                                        Text(
                                            text = "${log.cigarettesSmoked}",
                                            fontSize = 9.sp,
                                            color = Color.Black,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Monthly Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "MONTHLY ACTIVITY BREAKDOWN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Clean Days", style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$cleanDaysCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Outlined.Shield, contentDescription = null, tint = RecoveryGreen, modifier = Modifier.size(18.dp))
                            }
                        }
                        Column {
                            Text("Relapses", style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$relapseDaysCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Cancel, contentDescription = null, tint = AlertRed, modifier = Modifier.size(18.dp))
                            }
                        }
                        Column {
                            Text("Smoked Total", style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$totalCigarettesSmoked", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.SmokingRooms, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
    }

    // Day edit modal Dialogue
    if (openEditDialogForDate != null) {
        val targetDateStr = openEditDialogForDate!!
        val existingLog = logsMap[targetDateStr]
        val tappedDate = try {
            LocalDate.parse(targetDateStr)
        } catch (e: Exception) {
            LocalDate.now()
        }

        LogDayBottomSheet(
            initialDate = tappedDate,
            initialStatus = existingLog?.status ?: DayStatus.SMOKE_FREE,
            initialCigarettes = existingLog?.cigarettesSmoked ?: 0,
            onSave = { date, status, cigs, notes ->
                viewModel.saveLog(date, status, cigs, notes)
                openEditDialogForDate = null
            },
            onResetQuitDate = { viewModel.resetQuitDateToNow() },
            onDismiss = { openEditDialogForDate = null }
        )
    }
}

// Check if day cell is in future dates
fun dayIsInFuture(year: Int, monthIndex: Int, day: Int): Boolean {
    val cal = Calendar.getInstance()
    val todayYear = cal.get(Calendar.YEAR)
    val todayMonth = cal.get(Calendar.MONTH)
    val todayDay = cal.get(Calendar.DAY_OF_MONTH)

    return when {
        year > todayYear -> true
        year < todayYear -> false
        else -> {
            when {
                monthIndex > todayMonth -> true
                monthIndex < todayMonth -> false
                else -> day > todayDay
            }
        }
    }
}

@Composable
fun StatusChip(
    text: String,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) selectedColor else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .height(48.dp)
            .border(
                width = 1.dp,
                color = if (isSelected) selectedColor else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = text,
                maxLines = 1,
                softWrap = false,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
