package com.example.timewise.ui

import android.app.Application
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timewise.AppMonitorService
import com.example.timewise.AppPreferences
import com.example.timewise.FocusSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppCategory { SOCIAL, VIDEO, OTHER }

data class AppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val isBlocked: Boolean,
    val category: AppCategory,
)

data class AppSelectionUiState(
    val loading: Boolean              = true,
    val apps: List<AppItem>           = emptyList(),
    val query: String                 = "",
    val focusBlockedPackages: Set<String> = emptySet(),
    // Focus session
    val activeSession: FocusSession?  = null,
    val selectedDurationMinutes: Int  = 25,
    // Ticker driven from the VM coroutine
    val remainingMinutes: Int         = 0,
    val remainingSeconds: Int         = 0,
) {
    val filtered: List<AppItem>
        get() = if (query.isBlank()) apps
                else apps.filter { it.appName.contains(query, ignoreCase = true) }
}

class AppSelectionViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)
    private val _uiState = MutableStateFlow(AppSelectionUiState())
    val uiState: StateFlow<AppSelectionUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null

    init { loadApps() }

    // ── Apps ──────────────────────────────────────────────────────────────────

    private fun loadApps() {
        viewModelScope.launch {
            val focusPkgs = prefs.focusBlockedApps
            val session   = prefs.activeFocusSession()
            val items     = withContext(Dispatchers.IO) { fetchInstalledApps(focusPkgs) }
            _uiState.update {
                it.copy(
                    loading              = false,
                    apps                 = items,
                    focusBlockedPackages = focusPkgs,
                    activeSession        = session,
                )
            }
            if (session != null) startTicker()
        }
    }

    fun setQuery(q: String) = _uiState.update { it.copy(query = q) }

    fun toggleFocusApp(packageName: String) {
        val current = _uiState.value.focusBlockedPackages
        val updated = if (current.contains(packageName)) current - packageName
                      else current + packageName
        prefs.focusBlockedApps = updated

        val newApps = _uiState.value.apps.map { item ->
            if (item.packageName == packageName) item.copy(isBlocked = !item.isBlocked)
            else item
        }
        _uiState.update { it.copy(apps = newApps, focusBlockedPackages = updated) }
    }

    // ── Focus session ─────────────────────────────────────────────────────────

    fun setDuration(minutes: Int) =
        _uiState.update { it.copy(selectedDurationMinutes = minutes) }

    fun startSession() {
        val duration = _uiState.value.selectedDurationMinutes
        prefs.startFocusSession(duration)
        val session = prefs.activeFocusSession() ?: return
        _uiState.update { it.copy(activeSession = session) }
        ensureServiceRunning()
        startTicker()
    }

    fun stopSession() {
        prefs.clearFocusSession()
        tickerJob?.cancel()
        _uiState.update { it.copy(activeSession = null, remainingMinutes = 0, remainingSeconds = 0) }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                val session = prefs.activeFocusSession()
                if (session == null) {
                    _uiState.update { it.copy(activeSession = null, remainingMinutes = 0, remainingSeconds = 0) }
                    break
                }
                _uiState.update {
                    it.copy(
                        activeSession    = session,
                        remainingMinutes = session.remainingMinutes,
                        remainingSeconds = session.remainingSeconds,
                    )
                }
                delay(1_000)
            }
        }
    }

    private fun ensureServiceRunning() {
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, AppMonitorService::class.java))
    }

    // ── Installed apps ────────────────────────────────────────────────────────

    private fun fetchInstalledApps(blocked: Set<String>): List<AppItem> {
        val ctx = getApplication<Application>()
        val pm  = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { info ->
                val pkg = info.activityInfo.packageName
                AppItem(
                    packageName = pkg,
                    appName     = info.loadLabel(pm).toString(),
                    icon        = info.loadIcon(pm),
                    isBlocked   = blocked.contains(pkg),
                    category    = categorise(pkg)
                )
            }
            .sortedWith(
                compareByDescending<AppItem> { it.isBlocked }
                    .thenBy { it.appName.lowercase() }
            )
    }

    private fun categorise(pkg: String): AppCategory {
        val p = pkg.lowercase()
        val social = listOf("instagram","facebook","twitter","tiktok","snapchat","reddit","linkedin","discord","whatsapp","telegram")
        val video  = listOf("youtube","netflix","twitch","disney","hulu","prime","vimeo")
        return when {
            social.any { p.contains(it) } -> AppCategory.SOCIAL
            video.any  { p.contains(it) } -> AppCategory.VIDEO
            else -> AppCategory.OTHER
        }
    }

    override fun onCleared() {
        tickerJob?.cancel()
        super.onCleared()
    }
}
