package com.example.timewise.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ── Colour helpers ────────────────────────────────────────────────────────────

private fun EventColor.containerColor() = when (this) {
    EventColor.PURPLE -> Color(0xFFEDECFF)
    EventColor.TEAL   -> Color(0xFFE0F5EE)
    EventColor.CORAL  -> Color(0xFFFAECE7)
    EventColor.AMBER  -> Color(0xFFFAEEDA)
}

private fun EventColor.accentColor() = when (this) {
    EventColor.PURPLE -> Color(0xFF6C63FF)
    EventColor.TEAL   -> Color(0xFF1D9E75)
    EventColor.CORAL  -> Color(0xFFD85A30)
    EventColor.AMBER  -> Color(0xFFBA7517)
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: CalendarViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()

    if (state.showSheet) {
        AddEventSheet(
            initial      = state.editingEvent ?: vm.newEventForDate(state.selectedDate),
            installedApps = state.installedApps,
            suggestedApps = state.suggestedApps,
            isEditing    = state.editingEvent != null,
            onSave       = vm::saveEvent,
            onDelete     = { vm.deleteEvent(it) },
            onDismiss    = vm::closeSheet,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar", fontWeight = FontWeight.SemiBold) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick            = vm::openSheetForNew,
                containerColor     = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add event")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Date navigation header
            DateHeader(
                date       = state.selectedDate,
                onPrevious = vm::previousDay,
                onNext     = vm::nextDay,
            )

            HorizontalDivider()

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                DayTimeline(
                    events  = state.events,
                    onTap   = vm::openSheetForEdit,
                )
            }
        }
    }
}

// ── Date navigation header ────────────────────────────────────────────────────

@Composable
private fun DateHeader(
    date: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val isToday   = date == LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous day")
        }

        Column(
            modifier            = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text       = date.format(formatter),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (isToday) {
                Text(
                    text  = "Today",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        IconButton(onClick = onNext) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Next day")
        }
    }
}

// ── Timeline ──────────────────────────────────────────────────────────────────

@Composable
private fun DayTimeline(
    events: List<CalendarEvent>,
    onTap: (CalendarEvent) -> Unit,
) {
    if (events.isEmpty()) {
        Box(
            modifier            = Modifier.fillMaxSize(),
            contentAlignment    = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.CalendarToday,
                    contentDescription = null,
                    modifier    = Modifier.size(48.dp),
                    tint        = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text  = "No events today",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text  = "Tap + to add one",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
        return
    }

    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(events, key = { it.id }) { event ->
            EventCard(event = event, onTap = { onTap(event) })
        }
    }
}

// ── Event card ────────────────────────────────────────────────────────────────

@Composable
private fun EventCard(event: CalendarEvent, onTap: () -> Unit) {
    val accent    = event.color.accentColor()
    val container = event.color.containerColor()

    Card(
        shape   = RoundedCornerShape(14.dp),
        colors  = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                // Time range
                Text(
                    text  = "${event.startTime} – ${event.endTime}",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(Modifier.height(2.dp))

                // Title
                Text(
                    text       = event.title.ifBlank { "Untitled event" },
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )

                // Description
                if (event.description.isNotBlank()) {
                    Text(
                        text     = event.description,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Blocked apps badge
                if (event.blockedApps.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector        = Icons.Outlined.Block,
                            contentDescription = null,
                            modifier           = Modifier.size(13.dp),
                            tint               = accent,
                        )
                        Text(
                            text  = "${event.blockedApps.size} app${if (event.blockedApps.size > 1) "s" else ""} blocked",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                        )
                    }
                }
            }

            // Edit chevron
            Icon(
                imageVector        = Icons.Outlined.ChevronRight,
                contentDescription = "Edit",
                tint               = accent.copy(alpha = 0.5f),
                modifier           = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 12.dp),
            )
        }
    }
}
