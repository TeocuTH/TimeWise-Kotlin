package com.example.timewise.calendar

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
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
            isEditing    = state.isEditing,
            onSave       = vm::saveEvent,
            onDelete     = { vm.deleteEvent(it) },
            onDismiss    = vm::closeSheet,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                onClick            = { vm.openSheetForNew() },
                containerColor     = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add event")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
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
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
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
                    val periodKey = when (state.viewMode) {
                        CalendarView.DAY -> state.selectedDate.toString()
                        CalendarView.WEEK -> state.selectedDate.minusDays(state.selectedDate.dayOfWeek.value.toLong() - 1).toString()
                        CalendarView.MONTH -> "${state.selectedDate.year}-${state.selectedDate.monthValue}"
                    }

                    AnimatedContent(
                        targetState = Triple(state.viewMode, periodKey, state.selectedDate),
                        transitionSpec = {
                            val (oldMode, oldPeriod, oldDate) = initialState
                            val (newMode, newPeriod, newDate) = targetState

                            val transition = when {
                                newMode != oldMode -> {
                                    if (newMode.ordinal > oldMode.ordinal) {
                                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                            slideOutHorizontally { width -> -width } + fadeOut()
                                        )
                                    } else {
                                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                            slideOutHorizontally { width -> width } + fadeOut()
                                        )
                                    }
                                }
                                newPeriod != oldPeriod -> {
                                    if (newDate.isAfter(oldDate)) {
                                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                            slideOutHorizontally { width -> -width } + fadeOut()
                                        )
                                    } else {
                                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                            slideOutHorizontally { width -> width } + fadeOut()
                                        )
                                    }
                                }
                                else -> EnterTransition.None togetherWith ExitTransition.None
                            }
                            transition.using(SizeTransform(clip = false))
                        },
                        label = "CalendarTransition"
                    ) { (targetMode, _, targetDate) ->
                        val targetEvents = state.events
                        when (targetMode) {
                            CalendarView.DAY -> DayTimeline(
                                events = targetEvents,
                                selectedDate = targetDate,
                                onTap = vm::openSheetForEdit,
                                onSlotTap = { d, t -> vm.openSheetForNew(d, t) },
                                onDragUpdate = vm::updateGhostEvent,
                                onDragEnd = vm::finishGhostDrag,
                                ghostEvent = if (state.showGhost) state.editingEvent else null
                            )
                            CalendarView.WEEK -> WeekTimeline(
                                events = targetEvents,
                                selectedDate = targetDate,
                                onTap = vm::openSheetForEdit,
                                onSlotTap = { d, t -> vm.openSheetForNew(d, t) },
                                onDragUpdate = vm::updateGhostEvent,
                                onDragEnd = vm::finishGhostDrag,
                                ghostEvent = if (state.showGhost) state.editingEvent else null
                            )
                            CalendarView.MONTH -> MonthTimeline(
                                events = targetEvents,
                                selectedDate = targetDate,
                                onTap = vm::openSheetForEdit,
                                onDayTap = vm::openDayFromMonth
                            )
                        }
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

// ── Timeline overlap helper ───────────────────────────────────────────────────

private data class TimedDayEvent(
    val event: CalendarEvent,
    val startMinutes: Int,
    val endMinutes: Int,
    val originalIndex: Int,
)

private data class PositionedDayEvent(
    val event: CalendarEvent,
    val startMinutes: Int,
    val endMinutes: Int,
    val column: Int,
    val columnCount: Int,
)

private fun buildDayEventLayout(
    events: List<CalendarEvent>,
    dateStr: String
): List<PositionedDayEvent> {
    val timedEvents = events.mapIndexedNotNull { index, event ->
        if (dateStr < event.startDate || dateStr > event.endDate) return@mapIndexedNotNull null

        val start = runCatching {
            if (event.startDate == dateStr) LocalTime.parse(event.startTime) else LocalTime.MIDNIGHT
        }.getOrNull() ?: return@mapIndexedNotNull null

        val end = runCatching {
            if (event.endDate == dateStr) LocalTime.parse(event.endTime) else LocalTime.MAX
        }.getOrNull() ?: return@mapIndexedNotNull null

        val startMinutes = start.hour * 60 + start.minute
        val endMinutes = if (end == LocalTime.MAX) {
            24 * 60
        } else {
            end.hour * 60 + end.minute
        }

        TimedDayEvent(
            event = event,
            startMinutes = startMinutes,
            endMinutes = endMinutes.coerceAtLeast(startMinutes + 25),
            originalIndex = index
        )
    }.sortedWith(
        compareBy<TimedDayEvent> { it.startMinutes }
            .thenBy { it.originalIndex }
    )

    val startGroups = mutableListOf<MutableList<TimedDayEvent>>()

    timedEvents.forEach { item ->
        val matchingGroup = startGroups.firstOrNull { group ->
            group.any { other ->
                kotlin.math.abs(other.startMinutes - item.startMinutes) <= 25
            }
        }

        if (matchingGroup != null) {
            matchingGroup.add(item)
        } else {
            startGroups.add(mutableListOf(item))
        }
    }

    return startGroups.flatMap { group ->
        val sortedGroup = group.sortedWith(
            compareBy<TimedDayEvent> { it.startMinutes }
                .thenBy { it.originalIndex }
        )

        val columnCount = sortedGroup.size

        sortedGroup.mapIndexed { column, item ->
            PositionedDayEvent(
                event = item.event,
                startMinutes = item.startMinutes,
                endMinutes = item.endMinutes,
                column = if (columnCount > 1) column else 0,
                columnCount = if (columnCount > 1) columnCount else 1
            )
        }
    }
}

// ── Day timeline ──────────────────────────────────────────────────────────────

@Composable
private fun DayTimeline(
    events: List<CalendarEvent>,
    selectedDate: LocalDate,
    onTap: (CalendarEvent) -> Unit,
    onSlotTap: (LocalDate, String) -> Unit,
    onDragUpdate: (LocalDate, LocalTime, LocalTime) -> Unit = { _, _, _ -> },
    onDragEnd: (LocalDate) -> Unit = { _ -> },
    ghostEvent: CalendarEvent? = null,
) {
    val timeWidth = 56.dp
    val hourHeight = 64.dp
    val totalHeight = hourHeight * 24
    val scrollState = rememberScrollState()
    val isToday = selectedDate == LocalDate.now()

    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    if (isToday) {
        LaunchedEffect(Unit) {
            while (true) {
                currentTime = LocalTime.now()
                kotlinx.coroutines.delay(60000)
            }
        }
    }

    val density = LocalDensity.current
    val hourHeightPx = with(density) { hourHeight.toPx() }

    fun getTimeAt(y: Float): LocalTime {
        val totalMinutes = (y / hourHeightPx * 60).toInt().coerceIn(0, 24 * 60 - 1)
        val roundedMinutes = (totalMinutes / 15) * 15
        return LocalTime.of(roundedMinutes / 60, roundedMinutes % 60)
    }

    var dragStartLocalTime by remember { mutableStateOf<LocalTime?>(null) }

    LaunchedEffect(selectedDate) {
        val targetHour = if (isToday) (LocalTime.now().hour - 1).coerceAtLeast(0) else 7
        scrollState.scrollTo(with(density) { (targetHour * hourHeight.value).dp.roundToPx() })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(scrollState)
    ) {
        Column(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
            for (h in 0..23) {
                Row(modifier = Modifier.height(hourHeight).fillMaxWidth()) {
                    Box(
                        modifier = Modifier.width(timeWidth).fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (h > 0) {
                            Text(
                                text = "%02d:00".format(h),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeight)
                .padding(start = timeWidth, end = 8.dp)
                .pointerInput(selectedDate) {
                    detectTapGestures(
                        onTap = { offset ->
                            val time = getTimeAt(offset.y)
                            onSlotTap(selectedDate, time.format(DateTimeFormatter.ofPattern("HH:mm")))
                        }
                    )
                }
                .pointerInput(selectedDate) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val time = getTimeAt(offset.y)
                            dragStartLocalTime = time
                            onDragUpdate(selectedDate, time, time.plusMinutes(30))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val startTime = dragStartLocalTime ?: return@detectDragGesturesAfterLongPress
                            val currentTime = getTimeAt(change.position.y)
                            val start = if (currentTime.isBefore(startTime)) currentTime else startTime
                            val end = if (currentTime.isAfter(startTime)) currentTime else startTime
                            onDragUpdate(selectedDate, start, end.coerceAtLeast(start.plusMinutes(15)))
                        },
                        onDragEnd = {
                            onDragEnd(selectedDate)
                            dragStartLocalTime = null
                        },
                        onDragCancel = {
                            dragStartLocalTime = null
                        }
                    )
                }
        ) {
            val dateStr = selectedDate.toString()
            val positionedEvents = buildDayEventLayout(events, dateStr)

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                positionedEvents.forEach { positioned ->
                    val event = positioned.event
                    val duration = (positioned.endMinutes - positioned.startMinutes).coerceAtLeast(25)

                    val topOffset = (positioned.startMinutes * hourHeight.value / 60).dp
                    val boxHeight = (duration * hourHeight.value / 60).dp

                    val gap = 4.dp
                    val columnWidth = maxWidth / positioned.columnCount

                    val eventWidth = if (positioned.columnCount > 1) {
                        columnWidth - gap
                    } else {
                        maxWidth
                    }

                    val eventX = if (positioned.columnCount > 1) {
                        columnWidth * positioned.column
                    } else {
                        0.dp
                    }

                    DayEventItem(
                        event = event,
                        modifier = Modifier
                            .offset(x = eventX, y = topOffset)
                            .width(eventWidth)
                            .height(boxHeight),
                        onTap = { onTap(event) }
                    )
                }
            }

            if (ghostEvent != null && ghostEvent.startDate == selectedDate.toString()) {
                val start = runCatching { LocalTime.parse(ghostEvent.startTime) }.getOrNull()
                val end = runCatching { LocalTime.parse(ghostEvent.endTime) }.getOrNull()

                if (start != null && end != null) {
                    val startMin = start.hour * 60 + start.minute
                    val endMin = end.hour * 60 + end.minute
                    val topOff = (startMin * hourHeight.value / 60).dp
                    val bHeight = ((endMin - startMin) * hourHeight.value / 60).dp

                    Box(
                        modifier = Modifier
                            .offset(y = topOff)
                            .height(bHeight)
                            .fillMaxWidth()
                            .border(2.dp, Color(0xFF6C63FF), RoundedCornerShape(6.dp))
                            .background(Color(0xFF6C63FF).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .align(Alignment.TopStart)
                                .offset(x = (-2).dp, y = (-2).dp)
                                .background(Color(0xFF6C63FF), CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.background, CircleShape)
                        )
                        Box(
                            Modifier
                                .size(8.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = 2.dp, y = 2.dp)
                                .background(Color(0xFF6C63FF), CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.background, CircleShape)
                        )
                    }
                }
            }

            if (isToday) {
                val nowMinutes = currentTime.hour * 60 + currentTime.minute
                val nowOffset = (nowMinutes * hourHeight.value / 60).dp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = nowOffset - 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.onBackground, CircleShape)
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@Composable
private fun DayEventItem(
    event: CalendarEvent,
    modifier: Modifier = Modifier,
    onTap: () -> Unit
) {
    val accent = Color(event.color.accentHex)

    Box(
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(accent)
            .clickable { onTap() }
            .padding(8.dp)
    ) {
        Column {
            Text(
                text = event.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.background,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (event.description.isNotBlank()) {
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Week timeline ─────────────────────────────────────────────────────────────

@Composable
private fun WeekTimeline(
    events: List<CalendarEvent>,
    selectedDate: LocalDate,
    onTap: (CalendarEvent) -> Unit,
    onSlotTap: (LocalDate, String) -> Unit,
    onDragUpdate: (LocalDate, LocalTime, LocalTime) -> Unit = { _, _, _ -> },
    onDragEnd: (LocalDate) -> Unit = { _ -> },
    ghostEvent: CalendarEvent? = null,
) {
    val startOfWeek = selectedDate.minusDays(selectedDate.dayOfWeek.value.toLong() - 1)
    val timeWidth = 40.dp
    val hourHeight = 64.dp
    val totalHeight = hourHeight * 24
    val scrollState = rememberScrollState()

    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            kotlinx.coroutines.delay(60000)
        }
    }

    val density = LocalDensity.current
    val hourHeightPx = with(density) { hourHeight.toPx() }

    fun getTimeAt(y: Float): LocalTime {
        val totalMinutes = (y / hourHeightPx * 60).toInt().coerceIn(0, 24 * 60 - 1)
        val roundedMinutes = (totalMinutes / 15) * 15
        return LocalTime.of(roundedMinutes / 60, roundedMinutes % 60)
    }

    var dragStartLocalTime by remember { mutableStateOf<LocalTime?>(null) }

    LaunchedEffect(selectedDate) {
        val endOfWeek = startOfWeek.plusDays(6)
        val today = LocalDate.now()
        val isThisWeek = !today.isBefore(startOfWeek) && !today.isAfter(endOfWeek)
        val targetHour = if (isThisWeek) (LocalTime.now().hour - 1).coerceAtLeast(0) else 7
        scrollState.scrollTo(with(density) { (targetHour * hourHeight.value).dp.roundToPx() })
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
                Spacer(modifier = Modifier.width(timeWidth))
                for (i in 0..6) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(0.25.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        for (h in 0..23) {
                            Box(modifier = Modifier.fillMaxWidth().height(hourHeight / 2))
                            Box(modifier = Modifier.fillMaxWidth().height(hourHeight / 2))
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
                for (h in 0..23) {
                    Row(modifier = Modifier.height(hourHeight).fillMaxWidth()) {
                        Box(modifier = Modifier.width(timeWidth).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                            Text(
                                text = "%02d:00".format(h),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
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

            Row(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
                Spacer(modifier = Modifier.width(timeWidth))
                for (i in 0..6) {
                    val date = startOfWeek.plusDays(i.toLong())
                    val dateStr = date.toString()
                    val dayEvents = events.filter { dateStr >= it.startDate && dateStr <= it.endDate }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(date) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val time = getTimeAt(offset.y)
                                        onSlotTap(date, time.format(DateTimeFormatter.ofPattern("HH:mm")))
                                    }
                                )
                            }
                            .pointerInput(date) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val time = getTimeAt(offset.y)
                                        dragStartLocalTime = time
                                        onDragUpdate(date, time, time.plusMinutes(30))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val startTime = dragStartLocalTime ?: return@detectDragGesturesAfterLongPress
                                        val currentTime = getTimeAt(change.position.y)
                                        val start = if (currentTime.isBefore(startTime)) currentTime else startTime
                                        val end = if (currentTime.isAfter(startTime)) currentTime else startTime
                                        onDragUpdate(date, start, end.coerceAtLeast(start.plusMinutes(15)))
                                    },
                                    onDragEnd = {
                                        onDragEnd(date)
                                        dragStartLocalTime = null
                                    },
                                    onDragCancel = {
                                        dragStartLocalTime = null
                                    }
                                )
                            }
                    ) {
                        val positionedEvents = buildDayEventLayout(dayEvents, dateStr)

                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            positionedEvents.forEach { positioned ->
                                val event = positioned.event
                                val duration = (positioned.endMinutes - positioned.startMinutes).coerceAtLeast(25)

                                val topOffset = (positioned.startMinutes * hourHeight.value / 60).dp
                                val boxHeight = (duration * hourHeight.value / 60).dp

                                val gap = 3.dp
                                val columnWidth = maxWidth / positioned.columnCount

                                val eventWidth = if (positioned.columnCount > 1) {
                                    columnWidth - gap
                                } else {
                                    maxWidth
                                }

                                val eventX = if (positioned.columnCount > 1) {
                                    columnWidth * positioned.column
                                } else {
                                    0.dp
                                }

                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .offset(x = eventX, y = topOffset)
                                        .width(eventWidth)
                                        .height(boxHeight)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(event.color.accentHex))
                                        .border(1.dp, MaterialTheme.colorScheme.background.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .clickable { onTap(event) }
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    if (boxHeight > 16.dp && positioned.columnCount == 1) {
                                        Text(
                                            text = event.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.background,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        if (ghostEvent != null && ghostEvent.startDate == dateStr) {
                            val start = runCatching { LocalTime.parse(ghostEvent.startTime) }.getOrNull()
                            val end = runCatching { LocalTime.parse(ghostEvent.endTime) }.getOrNull()

                            if (start != null && end != null) {
                                val startMin = start.hour * 60 + start.minute
                                val endMin = end.hour * 60 + end.minute
                                val topOff = (startMin * hourHeight.value / 60).dp
                                val bHeight = ((endMin - startMin) * hourHeight.value / 60).dp

                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .offset(y = topOff)
                                        .height(bHeight)
                                        .fillMaxWidth()
                                        .border(2.dp, Color(0xFF6C63FF), RoundedCornerShape(6.dp))
                                        .background(Color(0xFF6C63FF).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                ) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .align(Alignment.TopStart)
                                            .offset(x = (-2).dp, y = (-2).dp)
                                            .background(Color(0xFF6C63FF), CircleShape)
                                            .border(1.dp, MaterialTheme.colorScheme.background, CircleShape)
                                    )
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .align(Alignment.BottomEnd)
                                            .offset(x = 2.dp, y = 2.dp)
                                            .background(Color(0xFF6C63FF), CircleShape)
                                            .border(1.dp, MaterialTheme.colorScheme.background, CircleShape)
                                    )
                                }
                            }
                        }

                        if (date == LocalDate.now()) {
                            val nowMinutes = currentTime.hour * 60 + currentTime.minute
                            val nowOffset = (nowMinutes * hourHeight.value / 60).dp

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = nowOffset - 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(MaterialTheme.colorScheme.onBackground, CircleShape)
                                )
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    thickness = 1.5.dp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Month timeline ────────────────────────────────────────────────────────────

@Composable
private fun MonthTimeline(
    events: List<CalendarEvent>,
    selectedDate: LocalDate,
    onTap: (CalendarEvent) -> Unit,
    onDayTap: (LocalDate) -> Unit,
) {
    val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val firstOfMonth = selectedDate.withDayOfMonth(1)
    val daysInMonth = selectedDate.lengthOfMonth()

    val firstDayOfWeek = firstOfMonth.dayOfWeek.value
    val paddingDays = firstDayOfWeek - 1

    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(8.dp)) {
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

        val weeks = mutableListOf<List<Int?>>()
        var currentWeek = mutableListOf<Int?>()

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
                                .clickable(enabled = dayNum != null) {
                                    dayNum?.let {
                                        val date = selectedDate.withDayOfMonth(it)
                                        onDayTap(date)
                                    }
                                }
                        ) {
                            if (dayNum != null) {
                                val date = selectedDate.withDayOfMonth(dayNum)
                                val dateStr = date.toString()
                                val dayEvents = events.filter { dateStr >= it.startDate && dateStr <= it.endDate }
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
                Text(
                    text  = "${event.startTime} – ${event.endTime}",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text       = event.title.ifBlank { "Untitled event" },
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )

                if (event.description.isNotBlank()) {
                    Text(
                        text     = event.description,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

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