package com.example.timewise.calendar

import android.app.Application
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timewise.AppMonitorService
import com.example.timewise.stats.StatsRepository
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

enum class CalendarView {
    DAY, WEEK, MONTH
}

data class CalendarUiState(
    val selectedDate: LocalDate        = LocalDate.now(),
    val events: List<CalendarEvent>    = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val suggestedApps: List<InstalledApp> = emptyList(),
    val loading: Boolean               = true,
    // Add/edit sheet state
    val showSheet: Boolean             = false,
    val editingEvent: CalendarEvent?   = null,
    val viewMode: CalendarView         = CalendarView.DAY
)

class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = CalendarRepository(app)
    private val statsRepo = StatsRepository(app)
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

    fun previousPeriod() {
        val state = _uiState.value
        when (state.viewMode) {
            CalendarView.DAY -> selectDate(state.selectedDate.minusDays(1))
            CalendarView.WEEK -> selectDate(state.selectedDate.minusWeeks(1))
            CalendarView.MONTH -> selectDate(state.selectedDate.minusMonths(1))
        }
    }

    fun nextPeriod() {
        val state = _uiState.value
        when (state.viewMode) {
            CalendarView.DAY -> selectDate(state.selectedDate.plusDays(1))
            CalendarView.WEEK -> selectDate(state.selectedDate.plusWeeks(1))
            CalendarView.MONTH -> selectDate(state.selectedDate.plusMonths(1))
        }
    }

    fun setViewMode(mode: CalendarView) {
        _uiState.update { it.copy(viewMode = mode) }
        loadEventsForMode(mode, _uiState.value.selectedDate)
    }

    // ── Events ────────────────────────────────────────────────────────────────

    private fun loadEventsForDate(date: LocalDate) {
        loadEventsForMode(_uiState.value.viewMode, date)
    }

    private fun loadEventsForMode(mode: CalendarView, date: LocalDate) {
        viewModelScope.launch {
            val events = withContext(Dispatchers.IO) {
                when (mode) {
                    CalendarView.DAY -> repo.loadForDate(date.format(dateFmt))
                    CalendarView.WEEK -> {
                        val start = date.minusDays(date.dayOfWeek.value.toLong() - 1)
                        val end = start.plusDays(6)
                        repo.loadAll().filter {
                            val d = LocalDate.parse(it.date, dateFmt)
                            !d.isBefore(start) && !d.isAfter(end)
                        }.sortedWith(compareBy({ it.date }, { it.startTime }))
                    }
                    CalendarView.MONTH -> {
                        val start = date.withDayOfMonth(1)
                        val end = date.withDayOfMonth(date.lengthOfMonth())
                        repo.loadAll().filter {
                            val d = LocalDate.parse(it.date, dateFmt)
                            !d.isBefore(start) && !d.isAfter(end)
                        }.sortedWith(compareBy({ it.date }, { it.startTime }))
                    }
                }
            }
            _uiState.update { it.copy(events = events, loading = false, selectedDate = date) }
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
            val suggested = getSuggestedApps(apps)
            _uiState.update { it.copy(installedApps = apps, suggestedApps = suggested) }
        }
    }

    private fun getSuggestedApps(allApps: List<InstalledApp>): List<InstalledApp> {
        val mostUsed = statsRepo.getTopUsedApps(10)
        val pkgMap = allApps.associateBy { it.packageName }

        val suggestedFromStats = mostUsed
            .mapNotNull { pkgMap[it.packageName] }
            .filter { app ->
                // Filter out Google apps except Chrome and YouTube
                val isGoogleApp = app.packageName.startsWith("com.google.") || 
                                app.packageName.startsWith("com.android.vending")
                val isAllowedGoogleApp = app.packageName == "com.android.chrome" || 
                                       app.packageName == "com.google.android.youtube"
                
                !isGoogleApp || isAllowedGoogleApp
            }
            .take(3)

        return if (suggestedFromStats.isNotEmpty()) {
            suggestedFromStats
        } else {
            // Fixed list of common distractions for demo if no stats/permission
            val fallbackPkgs = listOf(
                "com.instagram.android",
                "com.zhiliaoapp.musically", // TikTok
                "com.google.android.youtube",
                "com.android.chrome" // Google Chrome
            )
            fallbackPkgs.mapNotNull { pkgMap[it] }.take(3)
        }
    }

    private fun fetchInstalledApps(): List<InstalledApp> {
        val ctx = getApplication<Application>()
        val pm  = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(intent, 0)
            .map { info ->
                InstalledApp(
                    packageName = info.activityInfo.packageName,
                    appName     = info.loadLabel(pm).toString(),
                    icon        = info.loadIcon(pm),
                )
            }
            .filter { it.packageName != ctx.packageName }
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
