package com.example.timewise.calendar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private fun EventColor.label() = when (this) {
    EventColor.PURPLE -> "Purple"
    EventColor.TEAL   -> "Teal"
    EventColor.CORAL  -> "Coral"
    EventColor.AMBER  -> "Amber"
}

private fun EventColor.color() = when (this) {
    EventColor.PURPLE -> Color(0xFF6C63FF)
    EventColor.TEAL   -> Color(0xFF1D9E75)
    EventColor.CORAL  -> Color(0xFFD85A30)
    EventColor.AMBER  -> Color(0xFFBA7517)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventSheet(
    initial: CalendarEvent,
    installedApps: List<InstalledApp>,
    suggestedApps: List<InstalledApp>,
    isEditing: Boolean,
    onSave: (CalendarEvent) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title       by remember { mutableStateOf(initial.title) }
    var description by remember { mutableStateOf(initial.description) }
    var date        by remember { mutableStateOf(initial.date) }
    var startTime   by remember { mutableStateOf(initial.startTime) }
    var endTime     by remember { mutableStateOf(initial.endTime) }
    var color       by remember { mutableStateOf(initial.color) }
    var blocked     by remember { mutableStateOf(initial.blockedApps.toSet()) }
    var showAppPicker by remember { mutableStateOf(false) }
    var appSearch   by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDatePicker    by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(date)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                            .toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title  = { Text("Delete event?") },
            text   = { Text("This will remove \"${initial.title}\" permanently.") },
            confirmButton = {
                TextButton(onClick = { onDelete(initial.id); onDismiss() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle       = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text       = if (isEditing) "Edit event" else "New event",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isEditing) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete event",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                // Title
                OutlinedTextField(
                    value         = title,
                    onValueChange = { title = it },
                    label         = { Text("Title") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                )

                // Description
                OutlinedTextField(
                    value         = description,
                    onValueChange = { description = it },
                    label         = { Text("Description (optional)") },
                    maxLines      = 3,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                )

                // Date selection
                val displayDate = remember(date) {
                    runCatching {
                        LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    }.getOrDefault(date)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                ) {
                    OutlinedTextField(
                        value         = displayDate,
                        onValueChange = { },
                        label         = { Text("Date") },
                        readOnly      = true,
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        trailingIcon  = {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = "Select date")
                        },
                        enabled       = false,
                        colors        = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                // Time row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TimeField(
                        label    = "Start",
                        value    = startTime,
                        onChange = { startTime = it },
                        modifier = Modifier.weight(1f),
                    )
                    TimeField(
                        label    = "End",
                        value    = endTime,
                        onChange = { endTime = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Color picker
                SectionLabel("Colour")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EventColor.entries.forEach { c ->
                        val selected = c == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c.color())
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable { color = c },
                        )
                    }
                }

                // Blocked apps section
                SectionLabel("Block apps during this event")

                if (blocked.isEmpty()) {
                    OutlinedButton(
                        onClick = { showAppPicker = !showAppPicker },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add apps to block")
                    }
                } else {
                    // Chips for selected apps
                    val selectedInfos = installedApps.filter { blocked.contains(it.packageName) }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        selectedInfos.forEach { app ->
                            SelectedAppChip(
                                app     = app,
                                onRemove = { blocked = blocked - app.packageName },
                            )
                        }
                        TextButton(
                            onClick  = { showAppPicker = !showAppPicker },
                            modifier = Modifier.align(Alignment.Start),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add more")
                        }
                    }
                }

                // Inline app picker (toggles open/closed)
                if (showAppPicker) {
                    Card(
                        shape  = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            OutlinedTextField(
                                value         = appSearch,
                                onValueChange = { appSearch = it },
                                placeholder   = { Text("Search apps…") },
                                leadingIcon   = { Icon(Icons.Outlined.Search, null) },
                                singleLine    = true,
                                shape         = RoundedCornerShape(10.dp),
                                modifier      = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            val filtered = installedApps.filter {
                                it.appName.contains(appSearch, ignoreCase = true)
                            }
                            // Fixed-height scrollable list inside the card
                            LazyColumn(
                                modifier            = Modifier.heightIn(max = 260.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                if (appSearch.isEmpty() && suggestedApps.isNotEmpty()) {
                                    item {
                                        Box(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                                            SectionLabel("Suggested")
                                        }
                                    }
                                    items(suggestedApps, key = { "sug_${it.packageName}" }) { app ->
                                        AppPickerRow(
                                            app      = app,
                                            checked  = blocked.contains(app.packageName),
                                            onToggle = {
                                                blocked = if (blocked.contains(app.packageName))
                                                    blocked - app.packageName
                                                else
                                                    blocked + app.packageName
                                            },
                                        )
                                    }
                                    item {
                                        HorizontalDivider(
                                            modifier  = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                            thickness = 0.5.dp,
                                            color     = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                }

                                items(filtered, key = { it.packageName }) { app ->
                                    AppPickerRow(
                                        app       = app,
                                        checked   = blocked.contains(app.packageName),
                                        onToggle  = {
                                            blocked = if (blocked.contains(app.packageName))
                                                blocked - app.packageName
                                            else
                                                blocked + app.packageName
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Save button
            Button(
                onClick  = {
                    onSave(
                        initial.copy(
                            title       = title.trim(),
                            description = description.trim(),
                            date        = date,
                            startTime   = startTime,
                            endTime     = endTime,
                            color       = color,
                            blockedApps = blocked.toList(),
                        )
                    )
                },
                enabled  = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
            ) {
                Text(if (isEditing) "Save changes" else "Create event")
            }
        }
    }
}

// ── Reusable sub-composables ──────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text  = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val time = remember(value) {
        runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(12, 0))
    }
    val state = rememberTimePickerState(
        initialHour = time.hour,
        initialMinute = time.minute,
        is24Hour = true
    )

    if (showPicker) {
        TimePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val newTime = LocalTime.of(state.hour, state.minute)
                    onChange(newTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            TimePicker(state = state)
        }
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value         = value,
            onValueChange = { },
            label         = { Text(label) },
            readOnly      = true,
            leadingIcon   = { Icon(Icons.Outlined.AccessTime, null, modifier = Modifier.size(18.dp)) },
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
        )
        // Invisible clickable overlay to trigger the picker
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showPicker = true }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(IntrinsicSize.Min),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    text = "Select time",
                    style = MaterialTheme.typography.labelMedium
                )
                content()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    dismissButton()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
private fun SelectedAppChip(app: InstalledApp, onRemove: () -> Unit) {
    val painter = remember(app.packageName) {
        BitmapPainter(app.icon.toBitmap(64, 64).asImageBitmap())
    }
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Image(
            painter            = painter,
            contentDescription = app.appName,
            modifier           = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)),
        )
        Text(
            text     = app.appName,
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppPickerRow(
    app: InstalledApp,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val painter = remember(app.packageName) {
        BitmapPainter(app.icon.toBitmap(64, 64).asImageBitmap())
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Image(
            painter            = painter,
            contentDescription = app.appName,
            modifier           = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text     = app.appName,
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}
