@file:Suppress("DEPRECATION")
package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.state.*
import com.example.ui.theme.*
import java.io.InputStream
import kotlin.math.cos
import kotlin.math.sin
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.example.data.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.foundation.interaction.MutableInteractionSource

import com.example.ui.viewmodel.SmokeFreeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    viewModel: SmokeFreeViewModel
) {
    val healthState by viewModel.healthState.collectAsState()
    val logs by viewModel.allLogs.collectAsState()
    val impact = healthState.impact
    val settings = healthState.settings
    val quitTime = settings.quitDateMillis
    val now = System.currentTimeMillis()
    val todayLocalDate = LocalDate.now()
    val quitLocalDate = if (quitTime > 0L) {
        Instant.ofEpochMilli(quitTime)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    } else {
        LocalDate.now()
    }

    val nicotineCoNowMinutes = if (quitTime <= 0L) 0L
    else {
        val latestRelapseDate = logs.filter { it.status == DayStatus.RELAPSED || it.status == DayStatus.PARTIAL }
            .mapNotNull { log ->
                try {
                    LocalDate.parse(log.date, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (e: Exception) {
                    null
                }
            }
            .filter { !it.isBefore(quitLocalDate) && !it.isAfter(todayLocalDate) }
            .maxOrNull()

        if (latestRelapseDate == null) {
            ((now - quitTime) / 60000L).coerceAtLeast(0L)
        } else {
            if (latestRelapseDate == todayLocalDate) {
                0L
            } else {
                val endOfRelapseDay = latestRelapseDate.plusDays(1)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                ((now - endOfRelapseDay) / 60000L).coerceAtLeast(0L)
            }
        }
    }

    val nicotineCoBaselineMinutes = if (quitTime <= 0L) 0L
    else {
        val baselineLocalDate = todayLocalDate.minusDays(1)
        val baselineMillis = now - (24L * 60L * 60L * 1000L)
        val latestRelapseDate = logs.filter { it.status == DayStatus.RELAPSED || it.status == DayStatus.PARTIAL }
            .mapNotNull { log ->
                try {
                    LocalDate.parse(log.date, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (e: Exception) {
                    null
                }
            }
            .filter { !it.isBefore(quitLocalDate) && !it.isAfter(baselineLocalDate) }
            .maxOrNull()

        if (latestRelapseDate == null) {
            ((baselineMillis - quitTime) / 60000L).coerceAtLeast(0L)
        } else {
            if (latestRelapseDate == baselineLocalDate) {
                0L
            } else {
                val endOfRelapseDay = latestRelapseDate.plusDays(1)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                ((baselineMillis - endOfRelapseDay) / 60000L).coerceAtLeast(0L)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BodyStatsContent(
            minutesSinceQuit = impact.effectiveMinutesNow,
            minutesBaseline = impact.effectiveMinutesAtBaseline,
            hasBaseline = impact.hasBaseline,
            nicotineCoNowMinutes = nicotineCoNowMinutes,
            nicotineCoBaselineMinutes = nicotineCoBaselineMinutes
        )
    }
}

// -------------------------------------------------------------
// Body Stats Tab Recovery Display
// -------------------------------------------------------------
@Composable
fun BodyStatsContent(
    minutesSinceQuit: Long,        // effectiveMinutesNow
    minutesBaseline: Long,         // effectiveMinutesAtBaseline
    hasBaseline: Boolean,          // impact.hasBaseline
    nicotineCoNowMinutes: Long,
    nicotineCoBaselineMinutes: Long
) {
    val items = remember { getStaticBodyStats() }
    var selectedDetailCard by remember { mutableStateOf<BodyStatCard?>(null) }

    val progressMap = remember(minutesSinceQuit, nicotineCoNowMinutes) {
        items.associate { card ->
            val minutes = if (card.id == "blood") nicotineCoNowMinutes else minutesSinceQuit
            card.id to calculateBodyProgress(card, minutes)
        }
    }

    // Compute average body recovery score
    val overallPercentTotal = items.sumOf { card ->
        val minutes = if (card.id == "blood") nicotineCoNowMinutes else minutesSinceQuit
        val score = calculateBodyProgress(card, minutes)
        (score * 100).toInt()
    }
    val overallScore = overallPercentTotal / items.size

    val progressTransition = remember { Animatable(0f) }
    LaunchedEffect(overallScore) {
        progressTransition.animateTo(
            targetValue = overallScore.toFloat() / 100f,
            animationSpec = tween(durationMillis = 1500, easing = EaseOutCubic)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("health_body_stats_list"),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp)
    ) {
        // Overall Circle Ring average progress
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Overall Body Recovery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(140.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color.Gray.copy(alpha = 0.2f),
                                radius = size.minDimension / 2,
                                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = PrimaryTealDark,
                                startAngle = -90f,
                                sweepAngle = progressTransition.value * 360f,
                                useCenter = false,
                                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(progressTransition.value * 100).toInt()}%",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = PrimaryTealDark,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible
                            )
                            Text("RECOVERED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your body is ${(progressTransition.value * 100).toInt()}% recovered from smoking damage",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Title for Organs
        item {
            Text(
                "ORGAN RECOVERY MONITORS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }

        // Render each Organ stat card
        items(items, key = { card -> card.id }) { card ->
            OrganStatCardRow(
                card = card,
                minutesSinceQuit = minutesSinceQuit,
                minutesBaseline = minutesBaseline,
                hasBaseline = hasBaseline,
                onTap = { selectedDetailCard = card },
                nicotineCoNowMinutes = nicotineCoNowMinutes,
                nicotineCoBaselineMinutes = nicotineCoBaselineMinutes
            )
        }
    }

    if (selectedDetailCard != null) {
        val minutesForSelected = if (selectedDetailCard!!.id == "blood") nicotineCoNowMinutes else minutesSinceQuit
        OrganDetailDialog(
            card = selectedDetailCard!!,
            minutesSinceQuit = minutesForSelected,
            onDismiss = { selectedDetailCard = null }
        )
    }
}

@Suppress("DEPRECATION")
@Composable
fun OrganStatCardRow(
    card: BodyStatCard,
    minutesSinceQuit: Long,
    minutesBaseline: Long,
    hasBaseline: Boolean,
    onTap: () -> Unit,
    nicotineCoNowMinutes: Long,
    nicotineCoBaselineMinutes: Long
) {
    val minutesNow = if (card.id == "blood") nicotineCoNowMinutes else minutesSinceQuit
    val minutesBase = if (card.id == "blood") nicotineCoBaselineMinutes else minutesBaseline

    val progressNow = calculateBodyProgress(card, minutesNow)
    val percentage = (progressNow * 100).toInt()

    val progressBaseline = if (hasBaseline)
        calculateBodyProgress(card, minutesBase)
    else null

    val percentDeltaRaw = if (progressBaseline != null)
        (progressNow - progressBaseline) * 100f
    else null

    val statusLabel = when {
        percentage < 15 -> "Critical"
        percentage < 35 -> "Healing"
        percentage < 60 -> "Recovering"
        percentage < 85 -> "Good"
        percentage < 98 -> "Nearly Healed"
        else -> "Fully Healed"
    }

    val statusColor = when {
        percentage < 35 -> Color(0xFFE53935)
        percentage < 60 -> Color(0xFFFF8F00)
        percentage < 85 -> Color(0xFF7B1FA2)
        else -> Color(0xFF2E7D32)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(
                    color = Color(android.graphics.Color.parseColor(card.gradientEndHex))
                )
            ) { onTap() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
 
            // ── Header Row ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    // Larger icon box — 48dp instead of 32dp
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(android.graphics.Color.parseColor(card.gradientStartHex)),
                                        Color(android.graphics.Color.parseColor(card.gradientEndHex))
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getOrganIcon(card.id),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)  // larger icon inside
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = card.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = card.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
 
                // Percentage badge
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = Color(android.graphics.Color.parseColor(card.gradientEndHex)).copy(alpha = 0.15f),
                    modifier = Modifier
                        .widthIn(min = 52.dp)    // minimum width
                        .wrapContentWidth()       // expands if needed for 3-digit values
                ) {
                    Text(
                        text = "$percentage%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = Color(android.graphics.Color.parseColor(card.gradientEndHex)),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        maxLines = 1,           // hard single line
                        softWrap = false,       // disable all wrapping
                        overflow = TextOverflow.Visible  // let it expand rather than clip
                    )
                }
            }
 
            Spacer(modifier = Modifier.height(14.dp))
 
            // ── Canvas Visual ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(android.graphics.Color.parseColor(card.gradientStartHex)).copy(alpha = 0.4f),
                                Color(android.graphics.Color.parseColor(card.gradientEndHex)).copy(alpha = 0.15f)
                            )
                        )
                    )
            ) {
                OrganCanvasAnimator(cardId = card.id, progress = progressNow)
            }
 
            Spacer(modifier = Modifier.height(14.dp))
 
            // ── Status + Progress Bar ──────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status pill
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
 
                // Trend pill — shown only when delta is meaningful and baseline exists
                if (percentDeltaRaw != null && kotlin.math.abs(percentDeltaRaw) >= 0.005f) {
                    val isUp = percentDeltaRaw > 0
                    val trendColor = if (isUp) MintAccent else CoralAccent
                    val trendIcon = if (isUp) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown
                    val sign = if (isUp) "+" else ""
                    val formattedDelta = if (kotlin.math.abs(percentDeltaRaw) < 1f) {
                        String.format(java.util.Locale.US, "%.2f", percentDeltaRaw)
                    } else {
                        String.format(java.util.Locale.US, "%.0f", percentDeltaRaw)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(trendColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            trendIcon,
                            contentDescription = null,
                            tint = trendColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "$sign$formattedDelta%",
                            fontSize = 10.sp,
                            color = trendColor,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                } else if (!hasBaseline) {
                    // New user — show nothing, not even "Tap for details"
                    Spacer(Modifier.height(18.dp))
                } else {
                    Text(
                        "Tap for details",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
 
            Spacer(modifier = Modifier.height(8.dp))
 
            // Animated segmented bar
            val animatedProgress by animateFloatAsState(
                targetValue = progressNow,
                animationSpec = tween(durationMillis = 1200, easing = EaseOutCubic),
                label = "progressBar"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (i in 1..5) {
                    val segmentFilled = animatedProgress >= (i * 0.20f)
                    val segmentColor = if (segmentFilled)
                        Color(android.graphics.Color.parseColor(card.gradientEndHex))
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(7.dp)
                            .clip(RoundedCornerShape(50.dp))
                            .background(segmentColor)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Interactive Organ Canvas drawings coordinate mapping!
// -------------------------------------------------------------
@Composable
fun OrganCanvasAnimator(cardId: String, progress: Float) {
    // 3s looping animation for pulsating organs
    val animTransition = rememberInfiniteTransition(label = "organPulse")
    
    val pulseScale by animTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    val waveOffset by animTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Wave"
    )

    val cleanColor = Color(0xFFE81123).copy(alpha = 0.05f + 0.95f * progress) // muddy brown gradient base to bright cherry pink as progress scales!
    val damagedColor = Color(0xFF6F5C5C).copy(alpha = 1.0f - progress) // decay overlay scales away!

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2

        when (cardId) {
            "lung" -> {
                // Animated Lung expand/collapse scaling pulse
                val scale = pulseScale
                val centerOffset = Offset(cx, cy)
                
                // Draw left lung lobe and right lung lobe silhouette
                val path = Path().apply {
                    // Left lobe
                    moveTo(cx - 10f * scale, cy - 30f * scale)
                    cubicTo(cx - 40f * scale, cy - 60f * scale, cx - 110f * scale, cy - 40f * scale, cx - 100f * scale, cy + 30f * scale)
                    cubicTo(cx - 90f * scale, cy + 60f * scale, cx - 40f * scale, cy + 70f * scale, cx - 15f * scale, cy + 15f * scale)
                    close()

                    // Right lobe
                    moveTo(cx + 10f * scale, cy - 30f * scale)
                    cubicTo(cx + 40f * scale, cy - 60f * scale, cx + 110f * scale, cy - 40f * scale, cx + 100f * scale, cy + 30f * scale)
                    cubicTo(cx + 90f * scale, cy + 60f * scale, cx + 40f * scale, cy + 70f * scale, cx + 15f * scale, cy + 15f * scale)
                    close()
                }

                // Lung health fill transition: damaged grey brown to vibrant healthy pink
                val lungColor = Color.interpolate(Color(0xFF5A4D4A), Color(0xFFFFB2B2), progress)
                drawPath(path = path, color = lungColor)
                drawPath(path = path, color = Color.White.copy(alpha = 0.15f), style = Stroke(width = 6f))

                // Breathing trachea/windpipe line drawing
                val airwayPath = Path().apply {
                    moveTo(cx, cy - 60f)
                    lineTo(cx, cy - 10f)
                    lineTo(cx - 20f, cy + 10f)
                    moveTo(cx, cy - 10f)
                    lineTo(cx + 20f, cy + 10f)
                }
                drawPath(path = airwayPath, color = Color.White.copy(alpha = 0.35f), style = Stroke(width = 8f, cap = StrokeCap.Round))
            }

            "blood" -> {
                // Droplet shape with sweeping water wave inside represent Blood Purity
                val dropletPath = Path().apply {
                    moveTo(cx, cy - 50f)
                    cubicTo(cx - 40f, cy, cx - 40f, cy + 40f, cx, cy + 50f)
                    cubicTo(cx + 40f, cy + 40f, cx + 40f, cy, cx, cy - 50f)
                    close()
                }

                // Color transitions from dark CO brownish red to pure vibrant ruby red!
                val pureRed = Color.interpolate(Color(0xFF533F3A), Color(0xFFFF2525), progress)

                // Fill droplet shape
                drawPath(path = dropletPath, color = pureRed.copy(alpha = 0.2f))
                
                // Draw animated ripple inside droplet using clipPath
                drawIntoCanvas { canvas ->
                    canvas.save()
                    canvas.clipPath(dropletPath)
                    
                    // Wave offset sweep
                    val wavePath = Path().apply {
                        moveTo(0f, cy + 50f - waveOffset / 10f)
                        for (i in 0..w.toInt() step 10) {
                            val dy = cy + 10f + sin((i + waveOffset) * (Math.PI / 180f)).toFloat() * 10f
                            lineTo(i.toFloat(), dy)
                        }
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(path = wavePath, color = pureRed)
                    canvas.restore()
                }
                drawPath(path = dropletPath, color = Color.White.copy(alpha = 0.3f), style = Stroke(width = 6f))
            }

            "heart" -> {
                // Heart scale pulse + ECG loop scanner line drawing
                val scale = pulseScale
                val heartPath = Path().apply {
                    moveTo(cx, cy - 15f * scale)
                    cubicTo(cx - 35f * scale, cy - 45f * scale, cx - 65f * scale, cy - 15f * scale, cx, cy + 45f * scale)
                    cubicTo(cx + 65f * scale, cy - 15f * scale, cx + 35f * scale, cy - 45f * scale, cx, cy - 15f * scale)
                    close()
                }

                // Heart rate color becomes more regular calming pink-rose from frantic hyper-active!
                val heartColor = Color.interpolate(Color(0xFFE53935), Color(0xFFFF4081), progress)
                drawPath(path = heartPath, color = heartColor)

                // Sweep ECG line across
                val ecgPath = Path().apply {
                    moveTo(cx - 100f, cy + 50f)
                    lineTo(cx - 40f, cy + 50f)
                    lineTo(cx - 30f, cy + 30f)
                    lineTo(cx - 20f, cy + 70f)
                    lineTo(cx - 10f, cy + 40f)
                    lineTo(cx, cy + 50f)
                    lineTo(cx + 100f, cy + 50f)
                }
                drawPath(
                    path = ecgPath,
                    color = Color(0xFF00E676).copy(alpha = 0.7f),
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )
            }

            "airway" -> {
                // Draw trees of airways representing Bronchial tubes with moving clean air dots!
                val path = Path().apply {
                    moveTo(cx, cy - 50f)
                    lineTo(cx, cy)
                    lineTo(cx - 35f, cy + 35f)
                    moveTo(cx, cy)
                    lineTo(cx + 35f, cy + 35f)
                    
                    // mini twigs
                    moveTo(cx - 35f, cy + 35f)
                    lineTo(cx - 55f, cy + 50f)
                    moveTo(cx - 35f, cy + 35f)
                    lineTo(cx - 20f, cy + 55f)
                    
                    moveTo(cx + 35f, cy + 35f)
                    lineTo(cx + 55f, cy + 50f)
                    moveTo(cx + 35f, cy + 35f)
                    lineTo(cx + 20f, cy + 55f)
                }
                drawPath(path = path, color = Color.White.copy(alpha = 0.4f), style = Stroke(width = 8f, cap = StrokeCap.Round))

                // Draw small animated air flowing dots
                val t = (waveOffset % 100f) / 100f
                val dotX = cx - 35f * t
                val dotY = cy + 35f * t
                drawCircle(
                    color = Color(0xFF00BFA5),
                    radius = 5f,
                    center = Offset(dotX, dotY)
                )

                val dotX2 = cx + 35f * t
                val dotY2 = cy + 35f * t
                drawCircle(
                    color = Color(0xFF00BFA5),
                    radius = 5f,
                    center = Offset(dotX2, dotY2)
                )
            }

            "brain" -> {
                // Brain outline with simple visual synaptic connecting neural pulses
                drawCircle(color = Color.White.copy(alpha = 0.15f), radius = 45f * pulseScale, center = Offset(cx, cy))
                
                // Draw erratic/frequent tiny synaptic sparkling spark dots at 0% and steady aligned pulses at 100%
                val maxSparks = if (progress < 0.5f) 8 else 3
                for (i in 0 until maxSparks) {
                    val angle = (waveOffset * (i + 1) * 35f) % 360f
                    val rx = cx + cos(angle * (Math.PI / 180f)).toFloat() * 35f * pulseScale
                    val ry = cy + sin(angle * (Math.PI / 180f)).toFloat() * 35f * pulseScale
                    drawCircle(
                        color = Color(0xFFD500F9).copy(alpha = 0.8f),
                        radius = 4f,
                        center = Offset(rx, ry)
                    )
                }
            }

            "taste" -> {
                // Sensory Taste & Smell: Draw flower or radiating circular rings symbolizing sensory capacity
                for (i in 1..4) {
                    val r = (15f * i) * (1f + (waveOffset % 360f) / 360f)
                    drawCircle(
                        color = Color(0xFFFFAB00).copy(alpha = maxOf(0f, 1f - r / 100f)),
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = 4f)
                    )
                }
                drawCircle(color = Color.White.copy(alpha = 0.8f), radius = 10f, center = Offset(cx, cy))
            }

            "mouth" -> {
                // Draw clean smile + looping golden sparkle shimmer swept left-to-right!
                val smilePath = Path().apply {
                    moveTo(cx - 45f, cy)
                    quadraticTo(cx, cy + 35f, cx + 45f, cy)
                    quadraticTo(cx, cy - 5f, cx - 45f, cy)
                }

                // teeth color, white as progress scales!
                val teethColor = Color.interpolate(Color(0xFFFFF176), Color.White, progress)
                drawPath(path = smilePath, color = teethColor)
                drawPath(path = smilePath, color = Color(0xFFFF4081).copy(alpha = 0.3f), style = Stroke(width = 6f))

                // Golden sparkle sweep shimmer line
                val sweepX = (waveOffset / 360f) * w
                drawLine(
                    color = Color(0xFFFFD700).copy(alpha = 0.6f),
                    start = Offset(sweepX - 20f, 0f),
                    end = Offset(sweepX + 20f, h),
                    strokeWidth = 10f
                )
            }

            "cancer" -> {
                // Cancer Risk scanner shield with scanning horizontal laser line
                val shieldPath = Path().apply {
                    moveTo(cx, cy - 45f)
                    lineTo(cx + 35f, cy - 35f)
                    lineTo(cx + 35f, cy + 10f)
                    quadraticTo(cx + 35f, cy + 40f, cx, cy + 50f)
                    quadraticTo(cx - 35f, cy + 40f, cx - 35f, cy + 10f)
                    lineTo(cx - 35f, cy - 35f)
                    close()
                }

                // Red for high cancer risk, Forest green for highly reduced risk!
                val shieldColor = Color.interpolate(Color(0xFFB71C1C), Color(0xFF1B5E20), progress)
                drawPath(path = shieldPath, color = shieldColor.copy(alpha = 0.25f))
                drawPath(path = shieldPath, color = shieldColor, style = Stroke(width = 6f))

                // Scanned laser line sweep
                val laserY = cy - 45f + (waveOffset / 360f) * 95f
                drawLine(
                    color = Color(0xFF00E676),
                    start = Offset(cx - 45f, laserY),
                    end = Offset(cx + 45f, laserY),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }

            "sexual_health" -> {
                // Two overlapping heart-shaped rings pulsing — representing vascular flow
                // Outer ring: damaged dark color fading to vibrant pink as progress increases
                val ringColor = Color.interpolate(Color(0xFF6D0033), Color(0xFFE91E8C), progress)
                for (i in 1..3) {
                    val radius = 20f * i * pulseScale
                    drawCircle(
                        color = ringColor.copy(alpha = maxOf(0f, 0.6f - i * 0.15f)),
                        radius = radius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 4f)
                    )
                }
                // Central pulse dot
                drawCircle(color = ringColor, radius = 10f * pulseScale, center = Offset(cx, cy))
                // Small satellite dots orbiting — representing blood cells
                for (i in 0..4) {
                    val angle = (waveOffset + i * 72f) * (Math.PI / 180f)
                    val ox = cx + cos(angle).toFloat() * 40f
                    val oy = cy + sin(angle).toFloat() * 40f
                    drawCircle(color = ringColor.copy(alpha = 0.7f), radius = 4f, center = Offset(ox, oy))
                }
            }

            "hormones" -> {
                // Molecular/atom visualization — nucleus + orbiting electrons
                val nucleusColor = Color.interpolate(Color(0xFF1A237E), Color(0xFF7C4DFF), progress)
                // Nucleus
                drawCircle(color = nucleusColor, radius = 18f * pulseScale, center = Offset(cx, cy))
                drawCircle(color = Color.White.copy(alpha = 0.2f), radius = 18f * pulseScale,
                    center = Offset(cx, cy), style = Stroke(width = 3f))
                // Three orbital rings at different angles
                val orbits = listOf(0f, 60f, 120f)
                orbits.forEach { baseAngle ->
                    val ellipsePath = Path().apply {
                        addOval(Rect(center = Offset(cx, cy), radius = 45f))
                    }
                    drawPath(ellipsePath, nucleusColor.copy(alpha = 0.25f), style = Stroke(width = 2f))
                    // Electron on each orbit
                    val electronAngle = (waveOffset + baseAngle) * (Math.PI / 180f)
                    val ex = cx + cos(electronAngle).toFloat() * 45f
                    val ey = cy + sin(electronAngle).toFloat() * 45f
                    drawCircle(color = Color(0xFFE040FB), radius = 5f, center = Offset(ex, ey))
                }
            }

            "circulation" -> {
                // Sonar ripple rings expanding outward — represent blood flowing
                val rippleColor = Color.interpolate(Color(0xFF263238), Color(0xFF0097A7), progress)
                val maxRings = 4
                for (i in 0 until maxRings) {
                    val phase = ((waveOffset + i * 90f) % 360f) / 360f
                    val radius = phase * 70f
                    val alpha = maxOf(0f, 1f - phase)
                    drawCircle(
                        color = rippleColor.copy(alpha = alpha * 0.6f),
                        radius = radius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 3f)
                    )
                }
                drawCircle(color = rippleColor, radius = 10f, center = Offset(cx, cy))
            }

            "immune" -> {
                // Shield with orbiting defender particles
                val shieldColor = Color.interpolate(Color(0xFF1B5E20).copy(alpha = 0.3f),
                    Color(0xFF2E7D32), progress)
                val shieldPath = Path().apply {
                    moveTo(cx, cy - 45f)
                    lineTo(cx + 35f, cy - 30f)
                    lineTo(cx + 35f, cy + 10f)
                    quadraticTo(cx + 35f, cy + 38f, cx, cy + 50f)
                    quadraticTo(cx - 35f, cy + 38f, cx - 35f, cy + 10f)
                    lineTo(cx - 35f, cy - 30f)
                    close()
                }
                drawPath(shieldPath, shieldColor.copy(alpha = 0.3f))
                drawPath(shieldPath, shieldColor, style = Stroke(width = 5f))
                // Orbiting particles — more particles = stronger immunity = higher progress
                val particleCount = (3 + (progress * 5).toInt()).coerceAtMost(8)
                for (i in 0 until particleCount) {
                    val angle = (waveOffset * 1.5f + i * (360f / particleCount)) * (Math.PI / 180f)
                    val ox = cx + cos(angle).toFloat() * 55f
                    val oy = cy + sin(angle).toFloat() * 30f
                    drawCircle(color = Color(0xFF69F0AE).copy(alpha = 0.85f), radius = 4f,
                        center = Offset(ox, oy))
                }
            }

            "energy" -> {
                // Vertical energy bar filling from bottom — battery style
                val barWidth = 40f
                val barHeight = 80f
                val barLeft = cx - barWidth / 2
                val barTop = cy - barHeight / 2
                // Background bar (empty)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.15f),
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f)
                )
                // Fill from bottom based on progress
                val fillHeight = barHeight * progress
                val energyColor = when {
                    progress < 0.33f -> Color(0xFFE53935)
                    progress < 0.66f -> Color(0xFFFF8F00)
                    else -> Color(0xFF43A047)
                }
                drawRoundRect(
                    color = energyColor,
                    topLeft = Offset(barLeft, barTop + barHeight - fillHeight),
                    size = Size(barWidth, fillHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f)
                )
                // Shimmer sweep on the fill
                val shimmerX = barLeft + (waveOffset % 360f) / 360f * barWidth
                drawLine(
                    color = Color.White.copy(alpha = 0.4f),
                    start = Offset(shimmerX, barTop + barHeight - fillHeight),
                    end = Offset(shimmerX, barTop + barHeight),
                    strokeWidth = 4f
                )
                // Lightning bolt above bar
                val boltPath = Path().apply {
                    moveTo(cx + 5f, cy - barHeight / 2 - 20f)
                    lineTo(cx - 5f, cy - barHeight / 2 - 5f)
                    lineTo(cx + 3f, cy - barHeight / 2 - 5f)
                    lineTo(cx - 3f, cy - barHeight / 2 + 10f)
                }
                drawPath(boltPath, Color(0xFFFFD600), style = Stroke(width = 3f, cap = StrokeCap.Round))
            }

            "sleep" -> {
                // Moon + twinkling stars
                val moonColor = Color.interpolate(Color(0xFF37474F), Color(0xFFE3F2FD), progress)
                // Moon crescent
                drawCircle(color = moonColor, radius = 30f * pulseScale, center = Offset(cx - 5f, cy))
                drawCircle(
                    color = Color(0xFF0D0D2B),
                    radius = 22f * pulseScale,
                    center = Offset(cx + 10f, cy - 5f)
                )
                // Stars twinkling
                val starPositions = listOf(
                    Offset(cx + 50f, cy - 40f), Offset(cx - 50f, cy - 35f),
                    Offset(cx + 60f, cy + 10f), Offset(cx - 60f, cy + 20f),
                    Offset(cx + 20f, cy - 55f), Offset(cx - 20f, cy + 50f)
                )
                starPositions.forEachIndexed { i, pos ->
                    val twinkle = sin((waveOffset + i * 60f) * (Math.PI / 180f)).toFloat()
                    val alpha = 0.4f + 0.6f * ((twinkle + 1f) / 2f) * progress
                    drawCircle(color = Color.White.copy(alpha = alpha), radius = 3f, center = pos)
                }
            }

            "stress" -> {
                // EEG brainwave — erratic at low progress, smooth sine wave at high progress
                val waveColor = Color.interpolate(Color(0xFF6A1B9A).copy(alpha = 0.5f),
                    Color(0xFFCE93D8), progress)
                val wavePath = Path()
                val amplitude = 25f * (1f - progress * 0.7f) // reduces amplitude as calm increases
                val frequency = 2f + (1f - progress) * 4f    // more erratic at low progress
                val startX = cx - 80f
                wavePath.moveTo(startX, cy)
                var x = startX
                while (x <= cx + 80f) {
                    val y = cy + amplitude * sin((x - startX + waveOffset) * frequency * (Math.PI / 180f)).toFloat()
                    wavePath.lineTo(x, y)
                    x += 2f
                }
                drawPath(wavePath, waveColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
                // Small calm dot at end of wave
                drawCircle(color = waveColor, radius = 6f * pulseScale, center = Offset(cx + 80f, cy))
            }

            else -> {
                // Generic fallback box
                drawCircle(color = Color.Gray.copy(alpha = 0.2f), radius = 30f, center = Offset(cx, cy))
            }
        }
    }
}

// -------------------------------------------------------------
// Detailed modal overview sheet for each organ!
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganDetailDialog(
    card: BodyStatCard,
    minutesSinceQuit: Long,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val progress = calculateBodyProgress(card, minutesSinceQuit)
    val accentColor = Color(android.graphics.Color.parseColor(card.gradientEndHex))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            // Custom drag handle with title
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(android.graphics.Color.parseColor(card.gradientStartHex)),
                                        accentColor
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(getOrganIcon(card.id), contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(card.title, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        Text("${(progress * 100).toInt()}% recovered",
                            style = MaterialTheme.typography.bodySmall,
                            color = accentColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            // Recovery curve chart (keep existing Canvas chart code, unchanged)
            Text("RECOVERY CURVE", style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = accentColor,
                letterSpacing = 1.5.sp)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                // [KEEP the existing Canvas chart drawing code from the original OrganDetailDialog here]
                Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    val cw = size.width
                    val ch = size.height
                    drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, ch*0.25f), Offset(cw, ch*0.25f))
                    drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, ch*0.5f), Offset(cw, ch*0.5f))
                    drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, ch*0.75f), Offset(cw, ch*0.75f))
                    val points = card.stages.mapIndexed { idx, stage ->
                        Offset((idx.toFloat() / (card.stages.size - 1)) * cw, ch - (stage.progressPercent * ch))
                    }
                    val curvePath = Path().apply {
                        points.forEachIndexed { idx, pt -> if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
                    }
                    drawPath(curvePath, accentColor, style = Stroke(width = 3.dp.toPx()))
                    points.forEach { pt ->
                        drawCircle(Color.White, radius = 5f, center = pt)
                        drawCircle(accentColor, radius = 3f, center = pt)
                    }
                    val currentX = progress * cw
                    drawLine(Color.Red.copy(alpha=0.7f), Offset(currentX, 0f), Offset(currentX, ch),
                        strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f,10f), 0f))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Stage list
            Text("MILESTONES", style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = accentColor, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(10.dp))

            card.stages.forEach { stage ->
                val achieved = minutesSinceQuit >= stage.minutesAfterQuitting
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(28.dp).clip(CircleShape)
                            .background(if (achieved) accentColor.copy(alpha=0.15f) else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (achieved) Icons.Default.Check else Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (achieved) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stage.label, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (achieved) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (achieved) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("at ${formatDurationFriendly(stage.minutesAfterQuitting)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (achieved) {
                        Surface(shape = RoundedCornerShape(50.dp),
                            color = accentColor.copy(alpha = 0.1f)) {
                            Text("Done", style = MaterialTheme.typography.labelSmall,
                                color = accentColor, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
                if (stage != card.stages.last()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Source: WHO, CDC, NHS, American Heart Association clinical recovery timelines.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                lineHeight = 14.sp
            )
        }
    }
}

// Interpolation calculations helper
fun calculateBodyProgress(card: BodyStatCard, minutesSinceQuit: Long): Float {
    if (card.stages.isEmpty()) return 0.0f
    
    val sorted = card.stages.sortedBy { it.minutesAfterQuitting }

    if (minutesSinceQuit <= sorted.first().minutesAfterQuitting) {
        return sorted.first().progressPercent
    }
    if (minutesSinceQuit >= sorted.last().minutesAfterQuitting) {
        return sorted.last().progressPercent
    }

    // Find the enclosing interval of stages
    for (i in 0 until sorted.size - 1) {
        val stageA = sorted[i]
        val stageB = sorted[i + 1]
        if (minutesSinceQuit >= stageA.minutesAfterQuitting && minutesSinceQuit <= stageB.minutesAfterQuitting) {
            val range = stageB.minutesAfterQuitting - stageA.minutesAfterQuitting
            val offset = minutesSinceQuit - stageA.minutesAfterQuitting
            val ratio = if (range > 0) offset.toFloat() / range else 0.0f
            
            // Linear interpolation computation!
            return stageA.progressPercent + ratio * (stageB.progressPercent - stageA.progressPercent)
        }
    }
    return 0.0f
}

// Static Icon selectors mapping
fun getOrganIcon(cardId: String): ImageVector {
    return when (cardId) {
        "lung" -> Icons.Default.Air
        "blood" -> Icons.Default.Bloodtype
        "heart" -> Icons.Default.Favorite
        "airway" -> Icons.Default.FilterList
        "brain" -> Icons.Default.Psychology
        "taste" -> Icons.Default.Restaurant
        "mouth" -> Icons.Default.SentimentSatisfied
        "sexual_health" -> Icons.Default.Favorite
        "hormones"      -> Icons.Default.Science
        "circulation"   -> Icons.Default.Waves
        "immune"        -> Icons.Default.HealthAndSafety
        "energy"        -> Icons.Default.FlashOn
        "sleep"         -> Icons.Default.NightsStay
        "stress"        -> Icons.Default.SelfImprovement
        else -> Icons.Default.Security
    }
}

// Friendly duration translator
fun formatDurationFriendly(minutes: Long): String {
    return when {
        minutes < 60 -> "$minutes mins"
        minutes < 1440 -> "${minutes / 60} hrs"
        minutes < 43200 -> "${minutes / 1440} days"
        minutes < 525600 -> "${minutes / 43200} months"
        else -> "${minutes / 525600} years"
    }
}

// 8 static health cards setup curve
fun getStaticBodyStats(): List<BodyStatCard> {
    return listOf(
        BodyStatCard(
            "lung", "Lungs", "Lung cells and air sacks clearing", "Lung", "#1A1A2E", "#00BFA5",
            listOf(
                RecoveryStage(0L, 0.0f, "Damaged from smoking"),
                RecoveryStage(4320L, 0.08f, "Healing begun"),
                RecoveryStage(20160L, 0.20f, "Cilia starting to stand up"),
                RecoveryStage(43200L, 0.35f, "Debris clearing begun"),
                RecoveryStage(129600L, 0.55f, "Significant cell expansion"),
                RecoveryStage(388800L, 0.85f, "Cilia fully active"),
                RecoveryStage(525600L, 0.90f, "Virtually healed"),
                RecoveryStage(7884000L, 1.0f, "100% Regenerated")
            )
        ),
        BodyStatCard(
            "blood", "Nicotine & CO", "Clearance of toxins in blood", "Blood", "#B71C1C", "#FF5252",
            listOf(
                RecoveryStage(0L, 0.0f, "High CO blockage"),
                RecoveryStage(120L, 0.50f, "Nicotine level halved"),
                RecoveryStage(1440L, 0.80f, "Carbon monoxide normal"),
                RecoveryStage(4320L, 0.95f, "Nicotine virtually cleared"),
                RecoveryStage(20160L, 1.0f, "Toxins fully cleared")
            )
        ),
        BodyStatCard(
            "heart", "Heart & Vessels", "Cardiovascular pulse stabilization", "Heart", "#880E4F", "#FF4081",
            listOf(
                RecoveryStage(20L, 0.10f, "Heart rate normalizing"),
                RecoveryStage(1440L, 0.20f, "Oxygen supplies restored"),
                RecoveryStage(525600L, 0.60f, "Half stroke risk decrease"),
                RecoveryStage(5256000L, 0.85f, "Standard non-smoker risk levels"),
                RecoveryStage(7884000L, 1.0f, "Complete vessels elasticity restored")
            )
        ),
        BodyStatCard(
            "airway", "Breathing Airways", "Bronchial tubes and windpipes", "FilterList", "#0D47A1", "#00BFA5",
            listOf(
                RecoveryStage(0L, 0.05f, "Clogged air corridors"),
                RecoveryStage(4320L, 0.20f, "Bronchials relax opening"),
                RecoveryStage(43200L, 0.40f, "Airways cilia standing up"),
                RecoveryStage(129600L, 0.65f, "Deep breath capacity back"),
                RecoveryStage(525600L, 0.90f, "Airway cilia pristine"),
                RecoveryStage(7884000L, 1.0f, "Airways fully cleared")
            )
        ),
        BodyStatCard(
            "brain", "Brain receptors", "Receptor downregulation progress", "Psychology", "#4A148C", "#D500F9",
            listOf(
                RecoveryStage(0L, 0.0f, "Highly dependent receptors active"),
                RecoveryStage(4320L, 0.15f, "Transition spike withdrawal down"),
                RecoveryStage(43200L, 0.40f, "Urges calming"),
                RecoveryStage(129600L, 0.75f, "Dopamine receptors back to normal density"),
                RecoveryStage(2628000L, 1.0f, "Neural networks fully pristine")
            )
        ),
        BodyStatCard(
            "taste", "Smell & Taste", "Taste bud nerve updates", "Restaurant", "#E65100", "#FFC400",
            listOf(
                RecoveryStage(0L, 0.10f, "Dull senses"),
                RecoveryStage(2880L, 0.40f, "Taste cells coming alive"),
                RecoveryStage(20160L, 0.65f, "Flavors are fully vivid"),
                RecoveryStage(129600L, 1.0f, "Pristine taste and scent sensitivity")
            )
        ),
        BodyStatCard(
            "mouth", "Mouth & Gums", "Teeth yellowing and bad breath clearing", "SentimentSatisfied", "#004D40", "#00E676",
            listOf(
                RecoveryStage(0L, 0.05f, "Enamel stain and bacterial gum buildup"),
                RecoveryStage(20160L, 0.20f, "Oxygen supplies restored to tissues"),
                RecoveryStage(43200L, 0.40f, "Breath becomes crisp"),
                RecoveryStage(129600L, 0.60f, "White sparkle sweep returning"),
                RecoveryStage(2628000L, 1.0f, "Oral health equals normal baseline")
            )
        ),
        BodyStatCard(
            "cancer", "Cancer Risk Index", "Reduced mutation multiplier index", "Security", "#212121", "#4CAF50",
            listOf(
                RecoveryStage(0L, 0.0f, "Active smoker mutation mult"),
                RecoveryStage(525600L, 0.10f, "Mutation risks start softening"),
                RecoveryStage(2628000L, 0.40f, "Substantial reduction in cell mutation risk"),
                RecoveryStage(5256000L, 0.60f, "Lung cancer risk halved"),
                RecoveryStage(13140000L, 1.0f, "Baseline health risk index")
            )
        ),
        // Card 9 — Erectile Function & Sexual Health
        BodyStatCard(
            "sexual_health",
            "Sexual Health",
            "Vascular blood flow and erectile function",
            "Favorite",
            "#3E0033",
            "#E91E8C",
            listOf(
                RecoveryStage(0L, 0.05f, "Severe vascular restriction from smoking"),
                RecoveryStage(20160L, 0.15f, "Microcirculation starting to recover"),
                RecoveryStage(43200L, 0.30f, "Penile blood pressure index improving"),
                RecoveryStage(129600L, 0.50f, "Endothelial function measurably better"),
                RecoveryStage(525600L, 0.70f, "Erectile function significantly improved"),
                RecoveryStage(2628000L, 0.88f, "Near-normal vascular erectile response"),
                RecoveryStage(7884000L, 1.0f, "Full baseline sexual vascular health")
            )
        ),
        // Card 10 — Testosterone & Hormonal Balance
        BodyStatCard(
            "hormones",
            "Hormonal Balance",
            "Testosterone and endocrine system recovery",
            "Science",
            "#1A237E",
            "#7C4DFF",
            listOf(
                RecoveryStage(0L, 0.10f, "Testosterone suppressed by nicotine"),
                RecoveryStage(4320L, 0.20f, "Nicotine suppression lifting"),
                RecoveryStage(20160L, 0.40f, "Leydig cell activity recovering"),
                RecoveryStage(43200L, 0.55f, "Testosterone rising measurably"),
                RecoveryStage(129600L, 0.72f, "Hormonal axis recalibrating"),
                RecoveryStage(525600L, 0.88f, "Near-normal testosterone levels"),
                RecoveryStage(2628000L, 1.0f, "Full endocrine baseline restored")
            )
        ),
        // Card 11 — Circulation & Skin Health
        BodyStatCard(
            "circulation",
            "Circulation & Skin",
            "Peripheral blood flow and skin renewal",
            "Waves",
            "#1A3A4A",
            "#0097A7",
            listOf(
                RecoveryStage(0L, 0.05f, "Restricted peripheral circulation"),
                RecoveryStage(20160L, 0.25f, "Improved blood flow to extremities"),
                RecoveryStage(43200L, 0.45f, "Skin cell turnover accelerating"),
                RecoveryStage(129600L, 0.65f, "Visible skin tone improvement"),
                RecoveryStage(525600L, 0.80f, "Circulation at non-smoker range"),
                RecoveryStage(2628000L, 1.0f, "Full peripheral vascular health")
            )
        ),
        // Card 12 — Immune System
        BodyStatCard(
            "immune",
            "Immune System",
            "White blood cell activity and infection defense",
            "HealthAndSafety",
            "#1A2A1A",
            "#2E7D32",
            listOf(
                RecoveryStage(0L, 0.10f, "Chronic immune activation from smoke"),
                RecoveryStage(43200L, 0.30f, "Inflammatory markers reducing"),
                RecoveryStage(129600L, 0.50f, "White cell count normalising"),
                RecoveryStage(525600L, 0.70f, "Immune response calibrated"),
                RecoveryStage(2628000L, 0.90f, "Infection resistance near baseline"),
                RecoveryStage(7884000L, 1.0f, "Full immune baseline restored")
            )
        ),
        // Card 13 — Energy & Stamina
        BodyStatCard(
            "energy",
            "Energy & Stamina",
            "Oxygen efficiency and physical endurance",
            "FlashOn",
            "#3E2700",
            "#FF8F00",
            listOf(
                RecoveryStage(0L, 0.15f, "Oxygen delivery impaired"),
                RecoveryStage(20160L, 0.35f, "Circulation improving stamina"),
                RecoveryStage(129600L, 0.60f, "VO2 max measurably higher"),
                RecoveryStage(525600L, 0.80f, "Exercise tolerance non-smoker range"),
                RecoveryStage(2628000L, 0.95f, "Near-peak stamina baseline"),
                RecoveryStage(7884000L, 1.0f, "Full aerobic capacity restored")
            )
        ),
        // Card 14 — Sleep Quality
        BodyStatCard(
            "sleep",
            "Sleep Quality",
            "REM normalization after nicotine withdrawal",
            "NightsStay",
            "#0D0D2B",
            "#283593",
            listOf(
                RecoveryStage(0L, 0.20f, "Nicotine-dependent sleep pattern"),
                RecoveryStage(4320L, 0.10f, "Peak withdrawal sleep disruption"),
                RecoveryStage(20160L, 0.35f, "REM cycles stabilising"),
                RecoveryStage(43200L, 0.55f, "Sleep architecture improving"),
                RecoveryStage(129600L, 0.75f, "Deep sleep stages restored"),
                RecoveryStage(525600L, 0.90f, "Sleep quality at non-smoker level"),
                RecoveryStage(2628000L, 1.0f, "Optimal sleep architecture")
            )
        ),
        // Card 15 — Stress & Mental Calm
        BodyStatCard(
            "stress",
            "Mental Calm",
            "Baseline anxiety without nicotine dependency",
            "SelfImprovement",
            "#2A1A2A",
            "#6A1B9A",
            listOf(
                RecoveryStage(0L, 0.15f, "Nicotine masking underlying anxiety"),
                RecoveryStage(4320L, 0.05f, "Peak withdrawal anxiety spike"),
                RecoveryStage(20160L, 0.30f, "Withdrawal subsiding"),
                RecoveryStage(43200L, 0.50f, "Natural stress response returning"),
                RecoveryStage(129600L, 0.70f, "Baseline anxiety lower than smoker"),
                RecoveryStage(525600L, 0.85f, "Calm baseline established"),
                RecoveryStage(2628000L, 1.0f, "Full mental calm restoration")
            )
        )
    )
}

// Extrapolate color helper
fun Color.Companion.interpolate(start: Color, end: Color, ratio: Float): Color {
    val r = start.red + ratio * (end.red - start.red)
    val g = start.green + ratio * (end.green - start.green)
    val b = start.blue + ratio * (end.blue - start.blue)
    val a = start.alpha + ratio * (end.alpha - start.alpha)
    return Color(r, g, b, a)
}
