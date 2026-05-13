package com.example.timewise.calendar

import android.app.Application
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timewise.AppMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
)

data class CalendarUiState(
    val selectedDate: LocalDate        = LocalDate.now(),
    val events: List<CalendarEvent>    = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val loading: Boolean               = true,
    // Add/edit sheet state
    val showSheet: Boolean             = false,
    val editingEvent: CalendarEvent?   = null,
)

class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = CalendarRepository(app)
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        loadInstalledApps()
        loadEventsForDate(LocalDate.now())
    }

    // ── Date navigation ───────────────────────────────────────────────────────

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        loadEventsForDate(date)
    }

    fun previousDay() = selectDate(_uiState.value.selectedDate.minusDays(1))
    fun nextDay()     = selectDate(_uiState.value.selectedDate.plusDays(1))

    // ── Events ────────────────────────────────────────────────────────────────

    private fun loadEventsForDate(date: LocalDate) {
        viewModelScope.launch {
            val events = withContext(Dispatchers.IO) {
                repo.loadForDate(date.format(dateFmt))
            }
            _uiState.update { it.copy(events = events, loading = false) }
        }
    }

    fun saveEvent(event: CalendarEvent) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.save(event) }
            loadEventsForDate(_uiState.value.selectedDate)
            notifyService()
        }
        closeSheet()
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(eventId) }
            loadEventsForDate(_uiState.value.selectedDate)
            notifyService()
        }
    }

    // Nudge the service so it re-reads calendar blocks immediately
    private fun notifyService() {
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, AppMonitorService::class.java).apply {
            action = AppMonitorService.ACTION_REFRESH_CALENDAR
        })
    }

    // ── Sheet ─────────────────────────────────────────────────────────────────

    fun openSheetForNew() {
        _uiState.update { it.copy(showSheet = true, editingEvent = null) }
    }

    fun openSheetForEdit(event: CalendarEvent) {
        _uiState.update { it.copy(showSheet = true, editingEvent = event) }
    }

    fun closeSheet() {
        _uiState.update { it.copy(showSheet = false, editingEvent = null) }
    }

    // ── Installed apps ────────────────────────────────────────────────────────

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { fetchInstalledApps() }
            _uiState.update { it.copy(installedApps = apps) }
        }
    }

    private fun fetchInstalledApps(): List<InstalledApp> {
        val ctx = getApplication<Application>()
        val pm  = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.activityInfo.packageName,
                    appName     = info.loadLabel(pm).toString(),
                    icon        = info.loadIcon(pm),
                )
            }
            .sortedBy { it.appName.lowercase() }
    }

    // ── New event factory ─────────────────────────────────────────────────────

    fun newEventForDate(date: LocalDate): CalendarEvent = CalendarEvent(
        id          = UUID.randomUUID().toString(),
        title       = "",
        description = "",
        date        = date.format(dateFmt),
        startTime   = "09:00",
        endTime     = "10:00",
        blockedApps = emptyList(),
        color       = EventColor.PURPLE,
    )
}
