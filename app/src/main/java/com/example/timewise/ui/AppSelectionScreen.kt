package com.example.timewise.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel

private val Purple      = Color(0xFF6C63FF)
private val PurpleLight = Color(0xFFEDECFF)
private val PurpleMid   = Color(0xFFAFA9EC)

// Duration presets in minutes
private val DURATION_PRESETS = listOf(5, 10, 15, 25, 30, 45, 60, 90)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(vm: AppSelectionViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Apps", fontWeight = FontWeight.SemiBold)
                        val blocked = state.focusBlockedPackages.size
                        if (blocked > 0) {
                            Text(
                                "$blocked selected for focus",
                                style = MaterialTheme.typography.labelSmall,
                                color = Purple,
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Focus session card (always visible at top) ────────────────
            FocusSessionCard(
                state   = state,
                onStart = vm::startSession,
                onStop  = vm::stopSession,
                onDurationChange = vm::setDuration,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            HorizontalDivider()

            // ── Search ────────────────────────────────────────────────────
            OutlinedTextField(
                value          = state.query,
                onValueChange  = vm::setQuery,
                placeholder    = { Text("Search apps to block…") },
                leadingIcon    = { Icon(Icons.Outlined.Search, null) },
                singleLine     = true,
                shape          = RoundedCornerShape(28.dp),
                modifier       = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val blocked = state.filtered.filter { it.isBlocked }
                val rest    = state.filtered.filter { !it.isBlocked }

                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (blocked.isNotEmpty()) {
                        item { ListSectionHeader("Blocked in focus sessions") }
                        items(blocked, key = { it.packageName }) { app ->
                            AppRow(app = app, onToggle = { vm.toggleFocusApp(app.packageName) })
                        }
                    }
                    if (rest.isNotEmpty()) {
                        item { ListSectionHeader(if (blocked.isEmpty()) "All apps" else "Not blocked") }
                        items(rest, key = { it.packageName }) { app ->
                            AppRow(app = app, onToggle = { vm.toggleFocusApp(app.packageName) })
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

// ── Focus session card ────────────────────────────────────────────────────────

@Composable
private fun FocusSessionCard(
    state: AppSelectionUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = state.activeSession

    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (session != null) PurpleLight
                             else MaterialTheme.colorScheme.surface,
        ),
        border   = androidx.compose.foundation.BorderStroke(
            width = if (session != null) 1.5.dp else 0.5.dp,
            color = if (session != null) Purple
                    else MaterialTheme.colorScheme.outlineVariant,
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (session != null) Icons.Outlined.HourglassEmpty
                                  else Icons.Outlined.Timer,
                    contentDescription = null,
                    tint     = if (session != null) Purple
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = "Focus session",
                    fontWeight = FontWeight.SemiBold,
                    style      = MaterialTheme.typography.titleSmall,
                    color      = if (session != null) Purple
                                 else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                if (session != null) {
                    // Live countdown badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Purple,
                    ) {
                        Text(
                            text     = "%02d:%02d".format(state.remainingMinutes, state.remainingSeconds),
                            style    = MaterialTheme.typography.labelLarge,
                            color    = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            AnimatedContent(
                targetState = session != null,
                label       = "session_content",
            ) { isActive ->
                if (isActive && session != null) {
                    // Active session view
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Blocking ${state.focusBlockedPackages.size} app${if (state.focusBlockedPackages.size != 1) "s" else ""} " +
                            "for ${state.remainingMinutes}m ${state.remainingSeconds}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF534AB7),
                        )
                        // Progress bar
                        val total   = session.durationMinutes * 60f
                        val elapsed = total - (state.remainingMinutes * 60 + state.remainingSeconds)
                        LinearProgressIndicator(
                            progress  = { (elapsed / total).coerceIn(0f, 1f) },
                            modifier  = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                            color     = Purple,
                            trackColor = PurpleMid.copy(alpha = 0.3f),
                        )
                        OutlinedButton(
                            onClick = onStop,
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(10.dp),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = Purple),
                            border   = androidx.compose.foundation.BorderStroke(1.dp, Purple),
                        ) {
                            Icon(Icons.Outlined.Stop, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("End session")
                        }
                    }
                } else {
                    // Setup view
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Block selected apps for a set time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // Duration chip row
                        Text(
                            "Duration",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(DURATION_PRESETS) { mins ->
                                val selected = mins == state.selectedDurationMinutes
                                Surface(
                                    shape   = RoundedCornerShape(8.dp),
                                    color   = if (selected) Purple else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable { onDurationChange(mins) },
                                ) {
                                    Text(
                                        text = if (mins < 60) "${mins}m" else "${mins/60}h",
                                        style    = MaterialTheme.typography.labelMedium,
                                        color    = if (selected) Color.White
                                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }

                        val hasApps = state.focusBlockedPackages.isNotEmpty()
                        Button(
                            onClick  = onStart,
                            enabled  = hasApps,
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(10.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Purple),
                        ) {
                            Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (hasApps) "Start ${state.selectedDurationMinutes}m session"
                                else         "Select apps below first"
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── App list helpers ──────────────────────────────────────────────────────────

@Composable
private fun ListSectionHeader(text: String) {
    Text(
        text     = text.uppercase(),
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun AppRow(app: AppItem, onToggle: () -> Unit) {
    Card(
        shape   = RoundedCornerShape(12.dp),
        colors  = CardDefaults.cardColors(
            containerColor = if (app.isBlocked) PurpleLight
                             else MaterialTheme.colorScheme.surface
        ),
        border  = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (app.isBlocked) PurpleMid
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        onClick = onToggle,
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val painter = remember(app.packageName) {
                BitmapPainter(app.icon.toBitmap(96, 96).asImageBitmap())
            }
            Image(
                painter            = painter,
                contentDescription = app.appName,
                modifier           = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = app.appName,
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines   = 1,
                    )
                    if (app.category != AppCategory.OTHER) {
                        Spacer(Modifier.width(6.dp))
                        CategoryBadge(app.category)
                    }
                }
                Text(
                    text    = app.packageName,
                    style   = MaterialTheme.typography.bodySmall,
                    color   = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Checkbox(
                checked         = app.isBlocked,
                onCheckedChange = { onToggle() },
                colors          = CheckboxDefaults.colors(
                    checkedColor = Purple,
                )
            )
        }
    }
}

@Composable
private fun CategoryBadge(category: AppCategory) {
    val (label, color) = when (category) {
        AppCategory.SOCIAL -> "Social" to MaterialTheme.colorScheme.tertiary
        AppCategory.VIDEO  -> "Video"  to MaterialTheme.colorScheme.secondary
        AppCategory.OTHER  -> return
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
