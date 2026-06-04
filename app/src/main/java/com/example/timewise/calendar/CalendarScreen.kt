package com.example.timewise.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.Duration

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
                actions = {
                    ViewModeSelector(
                        currentMode = state.viewMode,
                        onModeSelected = vm::setViewMode
                    )
                }
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
                viewMode   = state.viewMode,
                onPrevious = vm::previousPeriod,
                onNext     = vm::nextPeriod,
            )

            HorizontalDivider()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(state.viewMode, state.selectedDate) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (totalDrag > 100) vm.previousPeriod()
                                else if (totalDrag < -100) vm.nextPeriod()
                                totalDrag = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                            }
                        )
                    }
            ) {
                if (state.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    when (state.viewMode) {
                        CalendarView.DAY -> DayTimeline(events = state.events, onTap = vm::openSheetForEdit)
                        CalendarView.WEEK -> WeekTimeline(
                            events = state.events,
                            selectedDate = state.selectedDate,
                            onTap = vm::openSheetForEdit
                        )
                        CalendarView.MONTH -> MonthTimeline(
                            events = state.events,
                            selectedDate = state.selectedDate,
                            onTap = vm::openSheetForEdit
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeSelector(
    currentMode: CalendarView,
    onModeSelected: (CalendarView) -> Unit
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.padding(end = 8.dp)
    ) {
        CalendarView.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = CalendarView.entries.size),
                onClick = { onModeSelected(mode) },
                selected = mode == currentMode,
                label = {
                    Text(
                        text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}

// ── Date navigation header ────────────────────────────────────────────────────

@Composable
private fun DateHeader(
    date: LocalDate,
    viewMode: CalendarView,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val isToday   = date == LocalDate.now()
    val formatter = when (viewMode) {
        CalendarView.DAY -> DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")
        CalendarView.WEEK -> {
            DateTimeFormatter.ofPattern("d MMM")
        }
        CalendarView.MONTH -> DateTimeFormatter.ofPattern("MMMM yyyy")
    }

    val headerText = when (viewMode) {
        CalendarView.DAY -> date.format(formatter)
        CalendarView.WEEK -> {
            val start = date.minusDays(date.dayOfWeek.value.toLong() - 1)
            val end = start.plusDays(6)
            "${start.format(formatter)} – ${end.format(formatter)}, ${start.year}"
        }
        CalendarView.MONTH -> date.format(formatter)
    }

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous")
        }

        Column(
            modifier            = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text       = headerText,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (isToday && viewMode == CalendarView.DAY) {
                Text(
                    text  = "Today",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        IconButton(onClick = onNext) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Next")
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

@Composable
private fun WeekTimeline(
    events: List<CalendarEvent>,
    selectedDate: LocalDate,
    onTap: (CalendarEvent) -> Unit,
) {
    val startOfWeek = selectedDate.minusDays(selectedDate.dayOfWeek.value.toLong() - 1)
    val timeWidth = 40.dp
    val hourHeight = 64.dp
    val totalHeight = hourHeight * 24
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header (Day names)
        Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            Spacer(modifier = Modifier.width(timeWidth))
            for (i in 0..6) {
                val date = startOfWeek.plusDays(i.toLong())
                val isToday = date == LocalDate.now()
                Column(
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = date.dayOfWeek.name.take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // ── Main Timeline Area ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // 1. Grid Background (Vertical lines)
            Row(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
                Spacer(modifier = Modifier.width(timeWidth))
                for (i in 0..6) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(0.25.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    )
                }
            }

            // 2. Horizontal lines & Time labels
            Column(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
                for (h in 0..23) {
                    Row(modifier = Modifier.height(hourHeight).fillMaxWidth()) {
                        // Time label
                        Box(modifier = Modifier.width(timeWidth).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                            Text(
                                text = "%02d:00".format(h),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        // Divider
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            HorizontalDivider(
                                modifier = Modifier.align(Alignment.TopStart),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }

            // 3. Events Layer
            Row(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
                Spacer(modifier = Modifier.width(timeWidth))
                for (i in 0..6) {
                    val date = startOfWeek.plusDays(i.toLong())
                    val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    val dayEvents = events.filter { it.date == dateStr }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        dayEvents.forEach { event ->
                            val start = runCatching { LocalTime.parse(event.startTime) }.getOrNull()
                            val end = runCatching { LocalTime.parse(event.endTime) }.getOrNull()
                            
                            if (start != null && end != null) {
                                val startMinutes = start.hour * 60 + start.minute
                                val endMinutes = end.hour * 60 + end.minute
                                val duration = (endMinutes - startMinutes).coerceAtLeast(20)

                                val topOffset = (startMinutes * hourHeight.value / 60).dp
                                val boxHeight = (duration * hourHeight.value / 60).dp

                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .offset(y = topOffset)
                                        .height(boxHeight)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(event.color.accentHex))
                                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .clickable { onTap(event) }
                                        .padding(2.dp)
                                ) {
                                    if (boxHeight > 16.dp) {
                                        Text(
                                            text = event.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthTimeline(
    events: List<CalendarEvent>,
    selectedDate: LocalDate,
    onTap: (CalendarEvent) -> Unit,
) {
    val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val firstOfMonth = selectedDate.withDayOfMonth(1)
    val daysInMonth = selectedDate.lengthOfMonth()

    val firstDayOfWeek = firstOfMonth.dayOfWeek.value
    val paddingDays = firstDayOfWeek - 1

    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // Weekday headers
        Row(modifier = Modifier.fillMaxWidth()) {
            dayNames.forEach { name ->
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Grid of days
        val weeks = mutableListOf<List<Int?>>()
        var currentWeek = mutableListOf<Int?>()
        
        // Add padding
        for (i in 0 until paddingDays) {
            currentWeek.add(null)
        }
        
        for (day in 1..daysInMonth) {
            currentWeek.add(day)
            if (currentWeek.size == 7) {
                weeks.add(currentWeek)
                currentWeek = mutableListOf()
            }
        }
        
        // Final row padding
        if (currentWeek.isNotEmpty()) {
            while (currentWeek.size < 7) currentWeek.add(null)
            weeks.add(currentWeek)
        }

        Column(modifier = Modifier.weight(1f)) {
            weeks.forEach { week ->
                Row(modifier = Modifier.weight(1f)) {
                    week.forEach { dayNum ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            if (dayNum != null) {
                                val date = selectedDate.withDayOfMonth(dayNum)
                                val dateStr = date.format(dateFmt)
                                val dayEvents = events.filter { it.date == dateStr }
                                val isToday = date == LocalDate.now()

                                Column(
                                    modifier = Modifier.fillMaxSize().padding(2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = dayNum.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    // Event indicators
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        dayEvents.take(3).forEach { ev ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(1.dp))
                                                    .background(Color(ev.color.accentHex))
                                                    .clickable { onTap(ev) }
                                            )
                                        }
                                        if (dayEvents.size > 3) {
                                            Text(
                                                text = "+${dayEvents.size - 3}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 7.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.align(Alignment.CenterHorizontally)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTimeline(message: String) {
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
                text  = message,
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
}

// ── Event card ────────────────────────────────────────────────────────────────

@Composable
private fun EventCard(event: CalendarEvent, onTap: () -> Unit) {
    val accent    = Color(event.color.accentHex)
    val container = Color(event.color.containerHex)

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
