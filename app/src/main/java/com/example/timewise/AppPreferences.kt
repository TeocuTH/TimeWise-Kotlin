package com.example.timewise

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Centralised persistence layer using SharedPreferences.
 * Survives process restarts — no more losing blocked apps when the service dies.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    // ── Blocked apps ────────────────────────────────────────────────────────

    var blockedApps: Set<String>
        get() = prefs.getStringSet(KEY_BLOCKED_APPS, emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet(KEY_BLOCKED_APPS, value) }

    fun addBlockedApp(packageName: String) {
        blockedApps = blockedApps + packageName
    }

    fun removeBlockedApp(packageName: String) {
        blockedApps = blockedApps - packageName
    }

    fun isBlocked(packageName: String): Boolean = blockedApps.contains(packageName)

    // ── Blocking behaviour ──────────────────────────────────────────────────

    /** Countdown duration shown to the user before they can continue (ms). */
    var delayMillis: Long
        get() = prefs.getLong(KEY_DELAY_MS, DEFAULT_DELAY_MS)
        set(value) = prefs.edit { putLong(KEY_DELAY_MS, value) }

    /** Grace period after the user taps Continue before monitoring resumes (ms). */
    var gracePeriodMillis: Long
        get() = prefs.getLong(KEY_GRACE_MS, DEFAULT_GRACE_MS)
        set(value) = prefs.edit { putLong(KEY_GRACE_MS, value) }

    // ── Service state ───────────────────────────────────────────────────────

    var monitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITORING, false)
        set(value) = prefs.edit { putBoolean(KEY_MONITORING, value) }

    // ── Stats ───────────────────────────────────────────────────────────────

    /** Total number of times the blocking screen has been shown. */
    var totalInterceptions: Int
        get() = prefs.getInt(KEY_TOTAL_INTERCEPTIONS, 0)
        set(value) = prefs.edit { putInt(KEY_TOTAL_INTERCEPTIONS, value) }

    /** Number of times the user chose to resist (closed, not continued). */
    var totalResisted: Int
        get() = prefs.getInt(KEY_TOTAL_RESISTED, 0)
        set(value) = prefs.edit { putInt(KEY_TOTAL_RESISTED, value) }

    fun recordInterception(resisted: Boolean) {
        totalInterceptions++
        if (resisted) totalResisted++
    }

    companion object {
        private const val PREF_FILE = "timewise_prefs"
        private const val KEY_BLOCKED_APPS = "blocked_apps"
        private const val KEY_DELAY_MS = "delay_ms"
        private const val KEY_GRACE_MS = "grace_ms"
        private const val KEY_MONITORING = "monitoring_enabled"
        private const val KEY_TOTAL_INTERCEPTIONS = "total_interceptions"
        private const val KEY_TOTAL_RESISTED = "total_resisted"

        const val DEFAULT_DELAY_MS = 5_000L   // 5-second default countdown
        const val DEFAULT_GRACE_MS = 10_000L  // 10-second grace after continuing
    }
}
