package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserSettings
import com.example.ui.theme.*
import com.example.ui.components.WheelPicker
import com.example.ui.components.CurrencyDropdown
import com.example.ui.components.QuitDateTile
import com.example.ui.components.QuitDatePickerSheet
import java.text.SimpleDateFormat
import java.util.*
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Offset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    currentStep: Int,
    onNext: (UserSettings) -> Unit,
    onPrev: () -> Unit,
    onImportClick: () -> Unit = {}
) {
    val context = LocalContext.current

    // Form inputs state
    var quitDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var cigarettesPerDay by remember { mutableStateOf("15") }
    var cigarettesPerPack by remember { mutableStateOf("20") }
    var costPerPack by remember { mutableStateOf("150.0") }
    var currencySymbol by remember { mutableStateOf("₹") }
    var brandName by remember { mutableStateOf("Standard") }

    // Validation errors
    var cigsPerDayError by remember { mutableStateOf<String?>(null) }
    var cigsPerPackError by remember { mutableStateOf<String?>(null) }
    var costError by remember { mutableStateOf<String?>(null) }
    var currencyError by remember { mutableStateOf<String?>(null) }
    var brandError by remember { mutableStateOf<String?>(null) }

    var showQuitDateSheet by remember { mutableStateOf(false) }

    val formattedQuitDate = remember(quitDateMillis) {
        val sdf = SimpleDateFormat("d MMM yyyy, hh:mm a", Locale.getDefault())
        sdf.format(Date(quitDateMillis))
    }

    val quitLocalDate = remember(quitDateMillis) {
        Instant.ofEpochMilli(quitDateMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }

    val quitLocalTime = remember(quitDateMillis) {
        Instant.ofEpochMilli(quitDateMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
    }

    // Live validation
    fun validateForm(): Boolean {
        var isValid = true

        // Cigarettes per day: range 1-200, digits only, no spaces
        val daysCp = cigarettesPerDay.trim()
        val daysInt = daysCp.toIntOrNull()
        if (daysCp.contains(" ") || daysInt == null || daysInt !in 1..200) {
            cigsPerDayError = "Must be a number between 1 and 200 (no spaces)"
            isValid = false
        } else {
            cigsPerDayError = null
        }

        // Cigarettes per pack: range 1-100, digits only
        val packCp = cigarettesPerPack.trim()
        val packInt = packCp.toIntOrNull()
        if (packCp.contains(" ") || packInt == null || packInt !in 1..100) {
            cigsPerPackError = "Must be a number between 1 and 100 (no spaces)"
            isValid = false
        } else {
            cigsPerPackError = null
        }

        // Cost per pack: decimal number with up to 2 decimal places, no spaces, no letters
        val costCp = costPerPack.trim()
        val costRegex = Regex("^\\d+(\\.\\d{1,2})?$")
        if (costCp.contains(" ") || !costRegex.matches(costCp) || costCp.toDoubleOrNull() == null) {
            costError = "Invalid cost format (e.g. 150.50 or 150, no spaces or letters)"
            isValid = false
        } else {
            costError = null
        }

        // Currency symbol: max 3 characters, no spaces, no digits
        val currCp = currencySymbol.trim()
        val hasSpacesOrDigits = currCp.any { it.isWhitespace() || it.isDigit() }
        if (currCp.isEmpty() || currCp.length > 3 || hasSpacesOrDigits) {
            currencyError = "Max 3 chars, no spaces or digits"
            isValid = false
        } else {
            currencyError = null
        }

        // Brand name: max 30 characters, no newlines
        val bName = brandName
        if (bName.length > 30 || bName.contains("\n")) {
            brandError = "Max 30 characters and no newlines"
            isValid = false
        } else {
            brandError = null
        }

        return isValid
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Section - Logo & Steps Dots
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                // Centered App Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(VioletPrimary, shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmokingRooms,
                        contentDescription = "Logo",
                        tint = TextOnAccent,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Steps dot indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { stepIndex ->
                        val index = stepIndex + 1
                        val active = index == currentStep
                        Box(
                            modifier = Modifier
                                .size(if (active) 10.dp else 8.dp)
                                .background(
                                    color = if (active) VioletPrimary else VioletSecondaryWashed(),
                                    shape = CircleShape
                                )
                        )
                    }
                }
                Text(
                    text = "Step $currentStep of 3",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // 2. Middle Section - White Card (Scrollable if form)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            ) {
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 8.dp,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (currentStep) {
                            1 -> {
                                // Illustration Area Top
                                Box(
                                    modifier = Modifier
                                        .height(130.dp)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    RisingSunAnimation()
                                }

                                DashedDivider(modifier = Modifier.padding(vertical = 16.dp))

                                // Welcome text bottom half
                                Text(
                                    text = "Your smoke-free journey starts here",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimaryDark
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                Text(
                                    text = "Track every smoke-free hour, watch your health recover, and see what you've reclaimed.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp,
                                        color = TextSecondary
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }

                            2 -> {
                                // Illustration Area top
                                Box(
                                    modifier = Modifier
                                        .height(100.dp)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CalendarIconAnimation()
                                }

                                DashedDivider(modifier = Modifier.padding(vertical = 12.dp))

                                // Title Section
                                Text(
                                    text = "Configure Your Quit Details",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimaryDark
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                // Custom date-time picker field
                                QuitDateTile(
                                    quitDate = quitLocalDate,
                                    quitTime = quitLocalTime,
                                    onClick = { showQuitDateSheet = true }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Cigarettes per day input
                                CustomInputOnboarding(
                                    value = cigarettesPerDay,
                                    onValueChange = { cigarettesPerDay = it },
                                    label = "Cigarettes per day (before quitting)",
                                    isError = cigsPerDayError != null,
                                    errorText = cigsPerDayError,
                                    leadingIcon = Icons.Default.SmokingRooms
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Cigarettes per pack input
                                CustomInputOnboarding(
                                    value = cigarettesPerPack,
                                    onValueChange = { cigarettesPerPack = it },
                                    label = "Cigarettes per pack",
                                    isError = cigsPerPackError != null,
                                    errorText = cigsPerPackError,
                                    leadingIcon = Icons.Default.Inventory
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Currency and Cost Row
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
                                        CustomInputOnboarding(
                                            value = costPerPack,
                                            onValueChange = { costPerPack = it },
                                            label = "Cost per pack",
                                            isError = costError != null,
                                            errorText = costError,
                                            leadingIcon = Icons.Default.PriceChange,
                                            keyboardType = KeyboardType.Number
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Brand Name input
                                CustomInputOnboarding(
                                    value = brandName,
                                    onValueChange = { brandName = it },
                                    label = "Cigarette Brand REFERENCE",
                                    isError = brandError != null,
                                    errorText = brandError,
                                    leadingIcon = Icons.Default.LocalOffer
                                )
                            }

                            3 -> {
                                // Illustration Area top
                                Box(
                                    modifier = Modifier
                                        .height(130.dp)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SparkleAnimation()
                                }

                                DashedDivider(modifier = Modifier.padding(vertical = 16.dp))

                                // Title text bottom half
                                Text(
                                    text = "You're all set",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimaryDark
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                Text(
                                    text = "Your progress is saved only on your device. No account needed, ever.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp,
                                        color = TextSecondary
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Bottom Section - Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep == 1) {
                    // Two buttons side by side
                    Button(
                        onClick = {
                            val emptySettings = UserSettings(
                                quitDateMillis = quitDateMillis,
                                cigarettesPerDay = cigarettesPerDay.toIntOrNull() ?: 10,
                                cigarettesPerPack = cigarettesPerPack.toIntOrNull() ?: 20,
                                costPerPack = costPerPack.toDoubleOrNull() ?: 150.0,
                                currencySymbol = currencySymbol.ifEmpty { "₹" },
                                brandName = brandName.ifEmpty { "Standard" }
                            )
                            onNext(emptySettings) // Navigates to Step 2
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                        shape = RoundedCornerShape(50.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("get_started_button")
                    ) {
                        Text(
                            "Get Started",
                            color = TextOnAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onImportClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VioletPrimary),
                        shape = RoundedCornerShape(50.dp),
                        border = borderStrokeDefault(color = VioletPrimary, width = 1.5.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("import_data_button")
                    ) {
                        Text(
                            "I already have data",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (currentStep == 2) {
                    // Back & Continue side-by-side
                    OutlinedButton(
                        onClick = onPrev,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VioletPrimary),
                        shape = RoundedCornerShape(50.dp),
                        border = borderStrokeDefault(color = VioletPrimary, width = 1.dp),
                        modifier = Modifier
                            .weight(0.8f)
                            .height(50.dp)
                    ) {
                        Text("Back", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (validateForm()) {
                                val settings = UserSettings(
                                    isOnboarded = false,
                                    quitDateMillis = quitDateMillis,
                                    cigarettesPerDay = cigarettesPerDay.toIntOrNull() ?: 10,
                                    cigarettesPerPack = cigarettesPerPack.toIntOrNull() ?: 20,
                                    costPerPack = costPerPack.toDoubleOrNull() ?: 150.0,
                                    currencySymbol = currencySymbol.trim(),
                                    brandName = brandName.trim()
                                )
                                onNext(settings) // advance to Step 3
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                        shape = RoundedCornerShape(50.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(50.dp)
                    ) {
                        Text("Continue", color = TextOnAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Centered Single Button "Start My Journey →"
                    Button(
                        onClick = {
                            val finalSettings = UserSettings(
                                isOnboarded = true,
                                quitDateMillis = quitDateMillis,
                                cigarettesPerDay = cigarettesPerDay.toIntOrNull() ?: 10,
                                cigarettesPerPack = cigarettesPerPack.toIntOrNull() ?: 20,
                                costPerPack = costPerPack.toDoubleOrNull() ?: 150.0,
                                currencySymbol = currencySymbol.trim(),
                                brandName = brandName.trim()
                            )
                            onNext(finalSettings) // sets Onboarded = true, completing onboarding
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                        shape = RoundedCornerShape(50.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_my_journey_button")
                    ) {
                        Text(
                            text = "Start My Journey →",
                            color = TextOnAccent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showQuitDateSheet) {
        QuitDatePickerSheet(
            currentQuitDate = quitLocalDate,
            currentQuitTime = quitLocalTime,
            onConfirm = { date, time ->
                val newMillis = date.atTime(time)
                    .atZone(ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
                quitDateMillis = newMillis
                showQuitDateSheet = false
            },
            onDismiss = { showQuitDateSheet = false }
        )
    }
}

@Composable
fun CustomInputOnboarding(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    errorText: String?,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Number
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            isError = isError,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(leadingIcon, contentDescription = null, tint = VioletSecondaryWashed()) },
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

// Custom animations drawn inside Card top half using canvas
@Composable
fun RisingSunAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "sun")
    val sunOffsetY by infiniteTransition.animateFloat(
        initialValue = 40f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sun_y"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Canvas(modifier = Modifier.size(120.dp)) {
        val centerX = size.width / 2
        val centerY = size.height / 2 + 20f + sunOffsetY

        drawCircle(
            color = AmberAccent.copy(alpha = glowAlpha * 0.15f),
            radius = 80.dp.toPx(),
            center = Offset(centerX, centerY)
        )

        drawCircle(
            color = AmberAccent,
            radius = 32.dp.toPx(),
            center = Offset(centerX, centerY)
        )

        val horizonY = size.height / 2 + 40f
        drawLine(
            color = VioletPrimary.copy(alpha = 0.6f),
            start = Offset(10.dp.toPx(), horizonY),
            end = Offset(size.width - 10.dp.toPx(), horizonY),
            strokeWidth = 4.dp.toPx()
        )
    }
}

@Composable
fun CalendarIconAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "calendar")
    val bounceY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Canvas(modifier = Modifier.size(120.dp)) {
        val centerX = size.width / 2
        val centerY = size.height / 2 + bounceY

        drawRoundRect(
            color = VioletPrimary,
            topLeft = Offset(centerX - 35.dp.toPx(), centerY - 30.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(70.dp.toPx(), 60.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
        )

        drawCircle(
            color = MintAccent,
            radius = 4.dp.toPx(),
            center = Offset(centerX - 20.dp.toPx(), centerY - 30.dp.toPx())
        )
        drawCircle(
            color = MintAccent,
            radius = 4.dp.toPx(),
            center = Offset(centerX + 20.dp.toPx(), centerY - 30.dp.toPx())
        )

        drawCircle(
            color = MintAccent,
            radius = 5.dp.toPx(),
            center = Offset(centerX, centerY)
        )
        drawCircle(
            color = VioletLight,
            radius = 4.dp.toPx(),
            center = Offset(centerX - 15.dp.toPx(), centerY + 10.dp.toPx())
        )
        drawCircle(
            color = VioletLight,
            radius = 4.dp.toPx(),
            center = Offset(centerX + 15.dp.toPx(), centerY + 10.dp.toPx())
        )
    }
}

@Composable
fun SparkleAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "sparkle")
    val rotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )
    val scaleFactor by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Canvas(modifier = Modifier.size(120.dp)) {
        val centerX = size.width / 2
        val centerY = size.height / 2

        val path = Path().apply {
            moveTo(centerX, centerY - 45.dp.toPx() * scaleFactor)
            quadraticTo(centerX, centerY, centerX + 45.dp.toPx() * scaleFactor, centerY)
            quadraticTo(centerX, centerY, centerX, centerY + 45.dp.toPx() * scaleFactor)
            quadraticTo(centerX, centerY, centerX - 45.dp.toPx() * scaleFactor, centerY)
            quadraticTo(centerX, centerY, centerX, centerY - 45.dp.toPx() * scaleFactor)
        }

        rotate(degrees = rotateAngle, pivot = Offset(centerX, centerY)) {
            drawPath(
                path = path,
                color = AmberAccent
            )
        }

        drawCircle(
            color = MintAccent,
            radius = 6.dp.toPx() * scaleFactor,
            center = Offset(centerX - 35.dp.toPx(), centerY - 25.dp.toPx())
        )
        drawCircle(
            color = VioletLight,
            radius = 5.dp.toPx() * (2f - scaleFactor),
            center = Offset(centerX + 35.dp.toPx(), centerY + 25.dp.toPx())
        )
    }
}

@Composable
fun DashedDivider(modifier: Modifier = Modifier, color: Color = DividerColor) {
    Canvas(modifier = modifier.fillMaxWidth().height(1.dp)) {
        val pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            pathEffect = pathEffect,
            strokeWidth = 1.dp.toPx()
        )
    }
}

// Helpers
@Composable
private fun VioletSecondaryWashed(): Color {
    return TextSecondary.copy(alpha = 0.5f)
}

private fun borderStrokeDefault(color: Color, width: androidx.compose.ui.unit.Dp): androidx.compose.foundation.BorderStroke {
    return androidx.compose.foundation.BorderStroke(width, color)
}

fun to24Hour(hours: Int, minutes: Int, isAm: Boolean): Pair<Int, Int> {
    val h = when {
        isAm && hours == 12 -> 0
        !isAm && hours != 12 -> hours + 12
        else -> hours
    }
    return Pair(h, minutes)
}

@Composable
fun TimeInputSection(
    hours: String, minutes: String, isAm: Boolean,
    onHoursChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    onAmPmChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "TIME",
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextSecondary,
                letterSpacing = 2.sp
            )
        )
        
        Spacer(Modifier.height(12.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Hours box
            OutlinedTextField(
                value = hours,
                onValueChange = { input ->
                    if (input.length <= 2 && input.all { it.isDigit() }) {
                        val num = input.toIntOrNull() ?: 0
                        if (input.isEmpty() || num in 1..12) onHoursChange(input)
                    }
                },
                modifier = Modifier.width(80.dp).height(72.dp),
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = VioletPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                placeholder = { Text("12", textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(), color = TextSecondary) }
            )
            
            // Colon separator
            Text(
                " : ",
                style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            
            // Minutes box
            OutlinedTextField(
                value = minutes,
                onValueChange = { input ->
                    if (input.length <= 2 && input.all { it.isDigit() }) {
                        val num = input.toIntOrNull() ?: 0
                        if (input.isEmpty() || num in 0..59) onMinutesChange(input)
                    }
                },
                modifier = Modifier.width(80.dp).height(72.dp),
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = VioletPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                placeholder = { Text("00", textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(), color = TextSecondary) }
            )
            
            Spacer(Modifier.width(12.dp))
            
            // AM/PM toggle — two stacked buttons, never breaks
            Column(
                modifier = Modifier
                    .height(72.dp)
                    .width(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, VioletMuted, RoundedCornerShape(12.dp))
            ) {
                // AM button — top half
                Box(
                    modifier = Modifier
                        .weight(1f).fillMaxWidth()
                        .background(if (isAm) VioletPrimary else Color.Transparent)
                        .clickable { onAmPmChange(true) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "AM",
                        fontSize = 13.sp,
                        fontWeight = if (isAm) FontWeight.Bold else FontWeight.Normal,
                        color = if (isAm) TextOnAccent else TextSecondary,
                        maxLines = 1  // NEVER wraps
                    )
                }
                // Thin divider
                HorizontalDivider(color = VioletMuted, thickness = 1.dp)
                // PM button — bottom half
                Box(
                    modifier = Modifier
                        .weight(1f).fillMaxWidth()
                        .background(if (!isAm) VioletPrimary else Color.Transparent)
                        .clickable { onAmPmChange(false) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "PM",
                        fontSize = 13.sp,
                        fontWeight = if (!isAm) FontWeight.Bold else FontWeight.Normal,
                        color = if (!isAm) TextOnAccent else TextSecondary,
                        maxLines = 1  // NEVER wraps
                    )
                }
            }
        }
        
        Spacer(Modifier.height(4.dp))
        
        // Live preview of selected time below inputs
        val displayHour = hours.toIntOrNull() ?: 12
        val displayMin = minutes.padStart(2, '0')
        val amPmLabel = if (isAm) "AM" else "PM"
        Text(
            "$displayHour:$displayMin $amPmLabel",
            style = TextStyle(color = TextSecondary, fontSize = 13.sp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerBottomSheet(
    initialTimeMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialTimeMs
    )

    val calendar = remember { Calendar.getInstance().apply { timeInMillis = initialTimeMs } }
    
    val initialHour = calendar.get(Calendar.HOUR)
    val initialHour12 = if (initialHour == 0) 12 else initialHour
    var hoursInput by remember { mutableStateOf(initialHour12.toString()) }
    var minutesInput by remember { mutableStateOf(String.format(Locale.US, "%02d", calendar.get(Calendar.MINUTE))) }
    var isAm by remember { mutableStateOf(calendar.get(Calendar.AM_PM) == Calendar.AM) }

    var timeError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        contentColor = TextPrimaryDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select Quit Date & Time",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryDark,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            TimeInputSection(
                hours = hoursInput,
                minutes = minutesInput,
                isAm = isAm,
                onHoursChange = { hoursInput = it },
                onMinutesChange = { minutesInput = it },
                onAmPmChange = { isAm = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            DatePicker(
                state = datePickerState,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = VioletPrimary,
                    selectedDayContentColor = TextOnAccent,
                    todayContentColor = VioletPrimary,
                    todayDateBorderColor = VioletPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (timeError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = timeError!!,
                    color = CoralAccent,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val selectedDateMs = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    val hrVal = hoursInput.toIntOrNull() ?: 12
                    val minVal = minutesInput.toIntOrNull() ?: 0
                    val (hr, min) = to24Hour(hrVal, minVal, isAm)

                    // Selected date millisecond is UTC mid-night, so we parse it relative to system default local timezone
                    val tempCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        timeInMillis = selectedDateMs
                    }
                    val resultCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, tempCal.get(Calendar.YEAR))
                        set(Calendar.MONTH, tempCal.get(Calendar.MONTH))
                        set(Calendar.DAY_OF_MONTH, tempCal.get(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, hr)
                        set(Calendar.MINUTE, min)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val finalMs = resultCal.timeInMillis
                    val nowMs = System.currentTimeMillis()

                    val tenYearsAgo = Calendar.getInstance().apply {
                        add(Calendar.YEAR, -10)
                    }.timeInMillis

                    if (finalMs > nowMs) {
                        timeError = "Quit date cannot be in the future"
                    } else if (finalMs < tenYearsAgo) {
                        timeError = "Quit date cannot be more than 10 years in the past"
                    } else {
                        onConfirm(finalMs)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Set Date & Time", color = TextOnAccent, fontWeight = FontWeight.Bold)
            }
        }
    }
}
