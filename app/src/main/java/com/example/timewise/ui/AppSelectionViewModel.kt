package com.example.timewise.ui

import android.app.Application
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timewise.AppPreferences
import kotlinx.coroutines.Dispatchers
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
    val loading: Boolean           = true,
    val apps: List<AppItem>        = emptyList(),
    val query: String              = "",
    val blockedPackages: Set<String> = emptySet(),
) {
    val filtered: List<AppItem>
        get() = if (query.isBlank()) apps
                else apps.filter { it.appName.contains(query, ignoreCase = true) }

    val blockedCount get() = blockedPackages.size
}

class AppSelectionViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)

    private val _uiState = MutableStateFlow(AppSelectionUiState())
    val uiState: StateFlow<AppSelectionUiState> = _uiState.asStateFlow()

    init { loadApps() }

    private fun loadApps() {
        viewModelScope.launch {
            val blocked = prefs.blockedApps
            val items = withContext(Dispatchers.IO) { fetchInstalledApps(blocked) }
            _uiState.update {
                it.copy(loading = false, apps = items, blockedPackages = blocked)
            }
        }
    }

    fun setQuery(q: String) = _uiState.update { it.copy(query = q) }

    fun toggleBlock(packageName: String) {
        val current = _uiState.value.blockedPackages
        val updated = if (current.contains(packageName)) current - packageName
                      else current + packageName
        prefs.blockedApps = updated

        // Rebuild app list with updated isBlocked flag
        val newApps = _uiState.value.apps.map { item ->
            if (item.packageName == packageName) item.copy(isBlocked = !item.isBlocked)
            else item
        }
        _uiState.update { it.copy(apps = newApps, blockedPackages = updated) }
    }

    private fun fetchInstalledApps(blocked: Set<String>): List<AppItem> {
        val ctx = getApplication<Application>()
        val pm  = ctx.packageManager

        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return pm.queryIntentActivities(launchIntent, 0)
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
}
