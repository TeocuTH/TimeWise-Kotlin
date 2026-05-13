package com.example.timewise.stats

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Brand colours (matching TimewiseTheme) ────────────────────────────────────
private val Purple     = Color(0xFF6C63FF)
private val PurpleLight= Color(0xFFEDECFF)
private val PurpleMid  = Color(0xFFAFA9EC)
private val Teal       = Color(0xFF1D9E75)
private val TealLight  = Color(0xFFE1F5EE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: StatsViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your progress", fontWeight = FontWeight.SemiBold) },
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Top metric row ────────────────────────────────────────────
            MetricRow(state)

            // ── Hours saved hero ──────────────────────────────────────────
            HoursSavedCard(state.minutesSaved)

            // ── Weekly bar chart ──────────────────────────────────────────
            ScreenTimeCard(state.weekBars)

            // ── Donut + outcomes ──────────────────────────────────────────
            OutcomesCard(
                interceptions = state.weekInterceptions,
                resisted      = state.weekResisted,
            )

            // ── Streak ────────────────────────────────────────────────────
            StreakCard(
                current     = state.currentStreak,
                best        = state.bestStreak,
                weekDays    = state.streakWeekDays,
            )

            // ── AI insight ────────────────────────────────────────────────
            AiInsightCard(
                insight = state.aiInsight,
                tip     = state.aiTip,
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Metric row ────────────────────────────────────────────────────────────────

@Composable
private fun MetricRow(state: StatsUiState) {
    val resistPct = if (state.weekInterceptions > 0)
        (state.weekResisted * 100f / state.weekInterceptions).roundToInt() else 0

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCell("Streak",      "${state.currentStreak}",  "days",  Purple,  Modifier.weight(1f))
        MetricCell("Blocked",     "${state.weekInterceptions}", "this week", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        MetricCell("Resisted",    "$resistPct%",             "rate",  Teal,    Modifier.weight(1f))
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    unit: String,
    valueColor: Color,
    modifier: Modifier,
) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border   = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
            Text(unit, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Hours saved hero card ─────────────────────────────────────────────────────

@Composable
private fun HoursSavedCard(minutesSaved: Int) {
    val hours        = minutesSaved / 60f
    val days         = hours / 24f
    val lifePercent  = (days / (365f * 80f)) * 100f  // fraction of 80-year life

    // Secondary equivalence — pick the most striking one
    val (equiv, equivLabel) = when {
        days >= 1f   -> "%.1f".format(days)  to "days of your life reclaimed"
        hours >= 1f  -> "%.1f".format(hours) to "hours reclaimed"
        else         -> "$minutesSaved"       to "minutes reclaimed"
    }

    // Equivalent activities
    val booksRead   = (hours / 6f).roundToInt()   // ~6h per book
    val sleepNights = (hours / 8f).roundToInt()   // ~8h per night

    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PurpleLight),
        border = BorderStroke(0.5.dp, PurpleMid),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Timer, contentDescription = null,
                    tint     = Purple,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Time reclaimed",
                    style  = MaterialTheme.typography.labelMedium,
                    color  = Purple,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                equiv,
                fontSize   = 40.sp,
                fontWeight = FontWeight.Bold,
                color      = Purple,
                lineHeight = 44.sp,
            )
            Text(
                equivLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF534AB7),
            )

            if (days >= 1f) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "That's %.4f%% of an 80-year life.".format(lifePercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7F77DD),
                    fontStyle = FontStyle.Italic,
                )
            }

            if (booksRead > 0 || sleepNights > 0) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = PurpleMid.copy(alpha = 0.4f))
                Spacer(Modifier.height(12.dp))
                Text(
                    "What you could do instead:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF534AB7),
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (booksRead > 0) {
                        EquivChip(Icons.Outlined.MenuBook, "$booksRead books read")
                    }
                    if (sleepNights > 0) {
                        EquivChip(Icons.Outlined.Bedtime, "$sleepNights nights of sleep")
                    }
                }
            }
        }
    }
}

@Composable
private fun EquivChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PurpleMid.copy(alpha = 0.25f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(icon, null, tint = Purple, modifier = Modifier.size(13.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF3C3489))
    }
}

// ── Screen time bar chart ─────────────────────────────────────────────────────

@Composable
private fun ScreenTimeCard(bars: List<DayBar>) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Screen time this week", fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall)
            Text("Hours per day", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(16.dp))

            val maxH = bars.mapNotNull { it.hours }.maxOrNull() ?: 4f
            val chartHeight = 100.dp

            Row(
                modifier = Modifier.fillMaxWidth().height(chartHeight),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEach { bar ->
                    Column(
                        modifier            = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        if (bar.hours != null) {
                            Text(
                                "%.1f".format(bar.hours),
                                style    = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color    = if (bar.isToday) Purple
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(2.dp))
                            val fraction = (bar.hours / maxH).coerceIn(0.05f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(fraction)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (bar.isToday) Purple else PurpleMid)
                            )
                        } else {
                            // Future day
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.04f)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                bars.forEach { bar ->
                    Text(
                        text      = bar.label.take(2),
                        modifier  = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style     = MaterialTheme.typography.labelSmall,
                        fontSize  = 10.sp,
                        color     = if (bar.isToday) Purple
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (bar.isToday) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(PurpleMid, "Past days")
                LegendDot(Purple,    "Today")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Outcomes donut card ───────────────────────────────────────────────────────

@Composable
private fun OutcomesCard(interceptions: Int, resisted: Int) {
    val opened    = (interceptions - resisted).coerceAtLeast(0)
    val resistPct = if (interceptions > 0) resisted * 100f / interceptions else 0f

    // Animate the arc
    val animPct by animateFloatAsState(
        targetValue   = resistPct / 100f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label         = "donut"
    )

    Card(
        shape  = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Blocking outcomes", fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall)
            Text("When the overlay appeared this week",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Donut drawn with Canvas
                Box(
                    Modifier.size(110.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
                        val inset  = 9.dp.toPx()
                        val arcSize = Size(size.width - inset*2, size.height - inset*2)
                        // Track
                        drawArc(
                            color       = PurpleLight,
                            startAngle  = -90f,
                            sweepAngle  = 360f,
                            useCenter   = false,
                            topLeft     = Offset(inset, inset),
                            size        = arcSize,
                            style       = stroke,
                        )
                        // Progress
                        drawArc(
                            color       = Purple,
                            startAngle  = -90f,
                            sweepAngle  = 360f * animPct,
                            useCenter   = false,
                            topLeft     = Offset(inset, inset),
                            size        = arcSize,
                            style       = stroke,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${resistPct.roundToInt()}%",
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Purple,
                        )
                        Text("resisted", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    OutcomeStat("Resisted",     resisted, Purple,     PurpleLight)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    OutcomeStat("Opened anyway", opened,  Teal,       TealLight)
                }
            }
        }
    }
}

@Composable
private fun OutcomeStat(label: String, value: Int, color: Color, bg: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$value", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = color)
            Text("times", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

// ── Streak card ───────────────────────────────────────────────────────────────

@Composable
private fun StreakCard(current: Int, best: Int, weekDays: List<Boolean>) {
    val today    = LocalDate.now()
    val dayNames = listOf("M","T","W","T","F","S","S")

    Card(
        shape  = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Daily streak", fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall)
            Text("Days with at least one successful resist",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$current",
                    fontSize   = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Purple,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("days in a row", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium)
                    Text("Best: $best days", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Week dot row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                weekDays.forEachIndexed { idx, done ->
                    val isToday = idx == (today.dayOfWeek.value - 1)
                    val isFuture = idx > (today.dayOfWeek.value - 1)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(horizontal = 3.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isToday  -> Purple
                                    done     -> PurpleLight
                                    isFuture -> MaterialTheme.colorScheme.surfaceVariant
                                    else     -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .then(
                                if (!done && !isToday && !isFuture)
                                    Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            dayNames[idx],
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                isToday  -> Color.White
                                done     -> Color(0xFF3C3489)
                                else     -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── AI insight card ───────────────────────────────────────────────────────────

@Composable
private fun AiInsightCard(insight: String, tip: String) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Purple, modifier = Modifier.size(18.dp))
                Text(
                    "AI insight",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Purple,
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape  = RoundedCornerShape(4.dp),
                    color  = PurpleLight,
                ) {
                    Text(
                        "Stand-in",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = Color(0xFF534AB7),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                insight,
                style      = MaterialTheme.typography.bodyMedium,
                color      = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
            )

            if (tip.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp, bottomStart = 8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(start = 0.dp)
                ) {
                    Row {
                        Box(
                            Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(Purple)
                        )
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                "This week's tip",
                                style = MaterialTheme.typography.labelSmall,
                                color = Purple,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                tip,
                                style      = MaterialTheme.typography.bodySmall,
                                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
