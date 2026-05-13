package com.example.timewise.ui

import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timewise.AppMonitorService
import com.example.timewise.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val hasUsagePermission: Boolean   = false,
    val hasOverlayPermission: Boolean = false,
    val monitoringEnabled: Boolean    = false,
    val blockedAppCount: Int          = 0,
    val totalInterceptions: Int       = 0,
    val totalResisted: Int            = 0,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Call this from onResume — permissions can change while the app is backgrounded. */
    fun refresh() {
        val ctx = getApplication<Application>()
        _uiState.update {
            HomeUiState(
                hasUsagePermission   = hasUsagePermission(ctx),
                hasOverlayPermission = Settings.canDrawOverlays(ctx),
                monitoringEnabled    = prefs.monitoringEnabled,
                blockedAppCount      = prefs.blockedApps.size,
                totalInterceptions   = prefs.totalInterceptions,
                totalResisted        = prefs.totalResisted,
            )
        }
    }

    fun setMonitoring(enabled: Boolean) {
        val ctx = getApplication<Application>()
        prefs.monitoringEnabled = enabled
        if (enabled) {
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, AppMonitorService::class.java)
            )
        } else {
            ctx.startService(Intent(ctx, AppMonitorService::class.java).apply {
                action = AppMonitorService.ACTION_STOP
            })
        }
        _uiState.update { it.copy(monitoringEnabled = enabled) }
    }

    private fun hasUsagePermission(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(), ctx.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
