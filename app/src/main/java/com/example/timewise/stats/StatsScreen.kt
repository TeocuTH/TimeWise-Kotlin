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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Brand colours (matching TimewiseTheme) ────────────────────────────────────
private val Purple     = Color(0xFF8E8BBF)
private val PurpleLight= Color(0xFFF1F1F8)
private val PurpleMid  = Color(0xFFC3C0E5)
private val Teal       = Color(0xFF6AA48A)
private val TealLight  = Color(0xFFEDF4F1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: StatsViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var outcomesOffset by remember { mutableIntStateOf(0) }
    var outcomesHeight by remember { mutableIntStateOf(0) }
    var topAppsOffset by remember { mutableIntStateOf(0) }
    var topAppsHeight by remember { mutableIntStateOf(0) }
    var viewportHeight by remember { mutableIntStateOf(0) }

    // Refresh data whenever the screen becomes visible
    LaunchedEffect(Unit) {
        vm.load()
    }

    // Refresh data if an app is installed or uninstalled (re-installation)
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                vm.load()
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
            addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

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
                .onGloballyPositioned { viewportHeight = it.size.height }
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Top metric row ────────────────────────────────────────────
            MetricRow(
                state = state,
                onMostUsedClick = {
                    val target = topAppsOffset + (topAppsHeight / 2) - (viewportHeight / 2)
                    scope.launch { scrollState.animateScrollTo(target.coerceAtLeast(0)) }
                },
                onBlockingClick = {
                    val target = outcomesOffset + (outcomesHeight / 2) - (viewportHeight / 2)
                    scope.launch { scrollState.animateScrollTo(target.coerceAtLeast(0)) }
                }
            )

            // ── Hours saved hero ──────────────────────────────────────────
            HoursSavedCard(state.minutesSaved)

            // ── Weekly bar chart ──────────────────────────────────────────
            ScreenTimeCard(state.weekBars)

            // ── Donut + outcomes ──────────────────────────────────────────
            Box(Modifier.onGloballyPositioned {
                outcomesOffset = it.positionInParent().y.toInt()
                outcomesHeight = it.size.height
            }) {
                OutcomesCard(
                    interceptions = state.weekInterceptions,
                    resisted = state.weekResisted,
                )
            }

            // ── Top Apps Bar Chart ────────────────────────────────────────
            Box(Modifier.onGloballyPositioned {
                topAppsOffset = it.positionInParent().y.toInt()
                topAppsHeight = it.size.height
            }) {
                TopAppsBarChartCard(
                    topApps = state.topApps,
                    hasPermission = state.hasUsagePermission
                )
            }

            // ── AI insight ────────────────────────────────────────────────
            AiInsightCard(
                insights = state.aiInsight,
                tip     = state.aiTip,
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Metric row ────────────────────────────────────────────────────────────────

@Composable
private fun MetricRow(
    state: StatsUiState,
    onMostUsedClick: () -> Unit,
    onBlockingClick: () -> Unit
) {
    val resistPct = if (state.weekInterceptions > 0)
        (state.weekResisted * 100f / state.weekInterceptions).roundToInt() else 0

    val topApp = if (state.hasUsagePermission && state.topApps.isNotEmpty()) {
        state.topApps.first()
    } else {
        null
    }

    val topAppIcon = topApp?.icon?.let { BitmapPainter(it.toBitmap().asImageBitmap()) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCell(
            label = "Most Used App",
            value = if (state.hasUsagePermission && topAppIcon == null) "None" else "",
            unit = if (state.hasUsagePermission) "" else "",
            valueColor = Purple,
            modifier = Modifier.weight(1f).clickable { onMostUsedClick() },
            icon = if (!state.hasUsagePermission) Icons.Outlined.Lock else null,
            appIcon = topAppIcon,
            unitColor = if (!state.hasUsagePermission) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
        MetricCell("Blocked",     "${state.weekInterceptions}", "this week", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f).clickable { onBlockingClick() })
        MetricCell("Resisted",    "$resistPct%",             "rate",  Teal,    Modifier.weight(1f).clickable { onBlockingClick() })
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    unit: String,
    valueColor: Color,
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    appIcon: androidx.compose.ui.graphics.painter.Painter? = null,
    unitColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border   = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(
            start  = 12.dp,
            end    = 12.dp,
            top    = 12.dp,
            bottom = if (appIcon != null || icon != null) 5.dp else 12.dp
        )) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1)

            Spacer(Modifier.height(4.dp))
            if(appIcon != null){
                Spacer(Modifier.height(7.dp))
            } else if (icon != null){
                Spacer(Modifier.height(7.dp))
            }
            Box(modifier = Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                if (appIcon != null) {
                    Image(
                        painter = appIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                } else if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp),
                        tint = valueColor
                    )
                } else if (value.isNotEmpty()) {
                    Text(
                        value,
                        fontSize = if (value.length > 8) 18.sp else 25.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = valueColor,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
            }
            
            Text(
                text = unit.ifEmpty { " " }, // Keep space even if empty to maintain height
                style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp,
                color = unitColor,
                maxLines = 1
            )
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
                fontSize   = 44.sp,
                fontWeight = FontWeight.Bold,
                color      = Purple,
                lineHeight = 48.sp,
            )
            Text(
                equivLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 16.sp,
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
            Text("How many times you have resisted this week",
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
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$value", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = color)
            Text("times", style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

// ── Top Apps Bar Chart Card ───────────────────────────────────────────────────

@Composable
private fun TopAppsBarChartCard(topApps: List<AppUsageInfo>, hasPermission: Boolean) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Most used apps",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "Weekly usage distribution",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            if (!hasPermission) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(PurpleLight)
                        .clickable {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            context.startActivity(intent)
                        }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Lock, null, tint = Purple)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Permission required to see usage data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Purple,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Tap to open Settings",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Purple
                        )
                    }
                }
            } else if (topApps.isEmpty()) {
                Text(
                    "No usage data found yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                val maxUsage = topApps.maxOfOrNull { it.usageTimeMillis } ?: 1L
                
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    topApps.forEach { app ->
                        AppUsageBarRow(app, maxUsage)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUsageBarRow(app: AppUsageInfo, maxUsage: Long) {
    val hours = app.usageTimeMillis / (1000f * 60 * 60)
    val minutes = (app.usageTimeMillis / (1000f * 60) % 60).toInt()
    val fraction = (app.usageTimeMillis.toFloat() / maxUsage).coerceIn(0.05f, 1f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (app.icon != null) {
            Image(
                painter = BitmapPainter(app.icon.toBitmap().asImageBitmap()),
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        } else {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    app.appName,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    if (hours >= 1f) "%dh %dm".format(hours.toInt(), minutes) else "${minutes}m",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 12.sp,
                    color = Purple,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
            // The Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(PurpleMid)
            )
        }
    }
}

// ── AI insight card ───────────────────────────────────────────────────────────

@Composable
private fun AiInsightCard(insights: List<String>, tip: String) {
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
            }

            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                insights.forEach { insight ->
                    Text(
                        insight,
                        style      = MaterialTheme.typography.bodyMedium,
                        color      = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp,
                    )
                }
            }

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
