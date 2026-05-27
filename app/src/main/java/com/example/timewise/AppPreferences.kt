package com.example.timewise

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.timewise.stats.StatsRepository

/**
 * Centralised persistence layer.
 *
 * Blocking now has two independent sources:
 *  1. Calendar events  — always active when an event is running, no toggle.
 *  2. Focus session    — timed manual block (Forest-style), stored here.
 *
 * The old "monitoringEnabled" global toggle is removed. The service starts
 * automatically if a session is active or a calendar event is running.
 * The service is always kept alive in the background so calendar events
 * work without user interaction.
 */
class AppPreferences(context: Context) {

    private val appContext = context.applicationContext
    private val statsRepo  = StatsRepository(appContext)
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    // ── Apps blocked during focus sessions ───────────────────────────────────
    // (Calendar events have their own blocked-app lists stored in CalendarRepository)

    var focusBlockedApps: Set<String>
        get() = prefs.getStringSet(KEY_FOCUS_APPS, emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet(KEY_FOCUS_APPS, value) }

    fun addFocusApp(pkg: String)    { focusBlockedApps = focusBlockedApps + pkg }
    fun removeFocusApp(pkg: String) { focusBlockedApps = focusBlockedApps - pkg }

    // ── Active focus session ──────────────────────────────────────────────────

    fun startFocusSession(durationMinutes: Int) {
        prefs.edit {
            putLong(KEY_SESSION_START, System.currentTimeMillis())
            putInt(KEY_SESSION_DURATION, durationMinutes)
        }
    }

    fun clearFocusSession() {
        prefs.edit {
            remove(KEY_SESSION_START)
            remove(KEY_SESSION_DURATION)
        }
    }

    fun activeFocusSession(): FocusSession? {
        val start    = prefs.getLong(KEY_SESSION_START, -1L)
        val duration = prefs.getInt(KEY_SESSION_DURATION, 0)
        if (start < 0 || duration <= 0) return null
        val session = FocusSession(start, duration)
        return if (session.isActive) session else null.also { clearFocusSession() }
    }

    // ── Blocking behaviour ────────────────────────────────────────────────────

    var delayMillis: Long
        get() = prefs.getLong(KEY_DELAY_MS, DEFAULT_DELAY_MS)
        set(value) = prefs.edit { putLong(KEY_DELAY_MS, value) }

    var gracePeriodMillis: Long
        get() = prefs.getLong(KEY_GRACE_MS, DEFAULT_GRACE_MS)
        set(value) = prefs.edit { putLong(KEY_GRACE_MS, value) }

    // ── Stats ─────────────────────────────────────────────────────────────────

    var totalInterceptions: Int
        get() = prefs.getInt(KEY_TOTAL_INTERCEPTIONS, 0)
        set(value) = prefs.edit { putInt(KEY_TOTAL_INTERCEPTIONS, value) }

    var totalResisted: Int
        get() = prefs.getInt(KEY_TOTAL_RESISTED, 0)
        set(value) = prefs.edit { putInt(KEY_TOTAL_RESISTED, value) }

    fun recordInterception(resisted: Boolean) {
        totalInterceptions++
        if (resisted) {
            totalResisted++
        }
        statsRepo.recordInterception(resisted)
    }

    companion object {
        private const val PREF_FILE              = "timewise_prefs"
        private const val KEY_FOCUS_APPS         = "focus_blocked_apps"
        private const val KEY_SESSION_START      = "session_start"
        private const val KEY_SESSION_DURATION   = "session_duration"
        private const val KEY_DELAY_MS           = "delay_ms"
        private const val KEY_GRACE_MS           = "grace_ms"
        private const val KEY_TOTAL_INTERCEPTIONS = "total_interceptions"
        private const val KEY_TOTAL_RESISTED     = "total_resisted"

        const val DEFAULT_DELAY_MS  = 5_000L
        const val DEFAULT_GRACE_MS  = 10_000L
    }
}
