package com.example.timewise.stats

import android.content.Context
import android.app.usage.UsageStatsManager
import java.util.Calendar
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.DayOfWeek
import java.time.LocalDate
import android.app.AppOpsManager
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Data class representing usage of a single app.
 */
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val usageTimeMillis: Long,
)

/**
 * Provides stats data for the stats screen.
 *
 * Screen-time values are simulated on first launch (as agreed — no real
 * UsageStats processing needed for the demo). Interception and resist counts
 * are real, written by BlockingActivity via AppPreferences.
 *
 * "Hours saved" is estimated by assuming each resisted block would have
 * turned into ~20 minutes of scrolling (industry average for a typical
 * social-media session after opening the app).
 */
class StatsRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", java.util.Locale.US)

    // ── Seed ─────────────────────────────────────────────────────────────────

    /**
     * Called whenever the stats screen is loaded. Fills in missing "fake"
     * statistics for any days between the last launch and today.
     * On first launch, seeds 30 days of data.
     */
    fun seedIfNeeded() {
        val today = LocalDate.now()
        val lastUpdateStr = prefs.getString(KEY_LAST_UPDATE_DATE, null)
        val lastUpdate = if (lastUpdateStr != null) {
            try { LocalDate.parse(lastUpdateStr, dateFmt) } catch (e: Exception) { null }
        } else null

        if (lastUpdate != null && !(lastUpdate.isBefore(today))) return

        // If never seeded, go back 29 days. Otherwise, go back to day after lastUpdate.
        val daysToSeed = if (lastUpdate == null) 29 else {
            java.time.temporal.ChronoUnit.DAYS.between(lastUpdate, today).toInt().coerceAtMost(30)
        }

        if (daysToSeed <= 0) return

        val screenTimeTemplate = listOf(3.1f, 2.4f, 4.0f, 2.8f, 1.9f, 3.5f, 2.2f)

        prefs.edit {
            for (i in daysToSeed downTo 0) {
                val date = today.minusDays(i.toLong())
                
                // Only seed if we don't have data for this day yet (to avoid overwriting real records)
                if (getScreenTime(date) < 0) {
                    val baseHours = screenTimeTemplate[date.dayOfWeek.value % 7]
                    val jitter = (-0.4f..0.4f).random()
                    val totalHours = (baseHours + jitter).coerceAtLeast(0.5f)
                    putInt(screenKey(date), (totalHours * 10).roundToInt())
                }

                if (prefs.getInt(interKey(date), -1) < 0) {
                    // Seed interception / resist history (don't seed for "today" yet to let real data come in)
                    if (date.isBefore(today)) {
                        val interceptions = (0..7).random()
                        val resisted = (interceptions * 0.55).roundToInt().coerceAtMost(interceptions)
                        putInt(interKey(date), interceptions)
                        putInt(resistKey(date), resisted)
                        if (resisted > 0) putBoolean(streakKey(date), true)
                    }
                }
            }
            putString(KEY_LAST_UPDATE_DATE, today.format(dateFmt))
            putBoolean(KEY_SEEDED, true)
        }
    }

    // ── Screen time ───────────────────────────────────────────────────────────

    fun getScreenTime(date: LocalDate): Float =
        prefs.getInt(screenKey(date), -1).let {
            if (it < 0) -1f else it / 10f
        }

    /** Returns Mon–Sun screen times for the week containing [anchor]. */
    fun weekScreenTimes(anchor: LocalDate = LocalDate.now()): List<Pair<LocalDate, Float?>> {
        val monday = anchor.with(DayOfWeek.MONDAY)
        return (0..6).map { offset ->
            val day = monday.plusDays(offset.toLong())
            val h   = getScreenTime(day)
            day to if (h < 0) null else h
        }
    }

    // ── Interceptions ─────────────────────────────────────────────────────────

    fun recordInterception(resisted: Boolean, date: LocalDate = LocalDate.now()) {
        val interceptions = prefs.getInt(interKey(date), 0) + 1
        val resistedCount = prefs.getInt(resistKey(date), 0) + if (resisted) 1 else 0
        setDayInterceptions(date, interceptions, resistedCount)
        if (resisted) markStreakDay(date)
    }
    private fun setDayInterceptions(date: LocalDate, total: Int, resisted: Int) {
        prefs.edit {
            putInt(interKey(date), total)
            putInt(resistKey(date), resisted)
        }
    }

    fun weekInterceptions(anchor: LocalDate = LocalDate.now()): Int {
        val monday = anchor.with(DayOfWeek.MONDAY)
        return (0..6).sumOf { offset ->
            prefs.getInt(interKey(monday.plusDays(offset.toLong())), 0)
        }
    }

    fun weekResisted(anchor: LocalDate = LocalDate.now()): Int {
        val monday = anchor.with(DayOfWeek.MONDAY)
        return (0..6).sumOf { offset ->
            prefs.getInt(resistKey(monday.plusDays(offset.toLong())), 0)
        }
    }

    // ── Streak ────────────────────────────────────────────────────────────────

    fun markStreakDay(date: LocalDate = LocalDate.now()) {
        val key = streakKey(date)
        prefs.edit { putBoolean(key, true) }
    }

    fun isStreakDay(date: LocalDate): Boolean =
        prefs.getBoolean(streakKey(date), false)

    /** Current consecutive streak ending today. */
    fun currentStreak(): Int {
        var streak = 0
        var day = LocalDate.now()
        while (isStreakDay(day)) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** All-time best streak. Scans up to 365 days back. */
    fun bestStreak(): Int {
        var best = 0
        var current = 0
        val today = LocalDate.now()
        for (i in 0..364) {
            if (isStreakDay(today.minusDays(i.toLong()))) {
                current++
                if (current > best) best = current
            } else {
                current = 0
            }
        }
        return best
    }

    // ── Hours saved ───────────────────────────────────────────────────────────

    /**
     * Estimate of time saved by resisting.
     * Model: each resisted block saves [MINS_PER_RESIST] minutes of scrolling.
     *
     * All-time resisted = sum of all daily resist counts in the last 365 days.
     */
    fun totalMinutesSaved(): Int {
        val today = LocalDate.now()
        val totalResisted = (0..364).sumOf { offset ->
            prefs.getInt(resistKey(today.minusDays(offset.toLong())), 0)
        }
        return totalResisted * MINS_PER_RESIST
    }

    // ── Day-of-week worst ─────────────────────────────────────────────────────

    /**
     * Returns the 5 most used apps in the last 7 days, scaled so their
     * usageTimeMillis represents their proportion of total REAL weekly
     * usage, but calculated over the FAKE weekly total.
     */
    fun getScaledTopAppsWeekly(limit: Int = 5): List<AppUsageInfo> {
        if (!hasUsageStatsPermission()) return emptyList()

        val realApps = getTopUsedApps(limit)
        if (realApps.isEmpty()) return emptyList()

        // 1. Total REAL weekly screen time
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (7L * 24 * 60 * 60 * 1000)
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime)
        val realTotalMillis = stats?.sumOf { it.totalTimeInForeground } ?: 0L

        if (realTotalMillis == 0L) return realApps

        // 2. FAKE weekly screen time total (sum of last 7 days of simulated data)
        val today = LocalDate.now()
        val fakeTotalHours = (0..6).sumOf { i ->
            getScreenTime(today.minusDays(i.toLong())).coerceAtLeast(0f).toDouble()
        }
        val fakeTotalMillis = (fakeTotalHours * 3600000).toLong()

        // 3. Scale based on proportion of realTotalMillis
        return realApps.map { app ->
            val proportion = app.usageTimeMillis.toDouble() / realTotalMillis
            app.copy(usageTimeMillis = (proportion * fakeTotalMillis).toLong())
        }
    }

    /**
     * Returns the 5 most used apps in the last 7 days.
     */
    fun getTopUsedApps(limit: Int = 5): List<AppUsageInfo> {
        if (!hasUsageStatsPermission()) return emptyList()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager

        val endTime = System.currentTimeMillis()
        val startTime = endTime - (7L * 24 * 60 * 60 * 1000) // Last 7 days

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        )

        if (stats.isNullOrEmpty()) return emptyList()

        // Group by package name and sum up time
        val usageMap = stats.groupBy { it.packageName }
            .mapValues { entry -> entry.value.sumOf { it.totalTimeInForeground } }
            .filter { it.value > 0 }
            .toList()
            .sortedByDescending { it.second }
            .take(limit)

        return usageMap.map { (pkg, time) ->
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            }
            val icon = try {
                pm.getApplicationIcon(pkg)
            } catch (e: Exception) {
                null
            }
            AppUsageInfo(pkg, appName, icon, time)
        }
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.noteOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Returns the day name with the highest avg screen time over last 4 weeks. */
    fun hardestDay(): String {
        val today = LocalDate.now()
        val totals = mutableMapOf<DayOfWeek, Float>()
        val counts = mutableMapOf<DayOfWeek, Int>()
        for (i in 0..27) {
            val date = today.minusDays(i.toLong())
            val h = getScreenTime(date)
            if (h > 0) {
                totals[date.dayOfWeek] = (totals[date.dayOfWeek] ?: 0f) + h
                counts[date.dayOfWeek] = (counts[date.dayOfWeek] ?: 0) + 1
            }
        }
        return totals
            .mapValues { (dow, total) -> total / (counts[dow] ?: 1) }
            .maxByOrNull { it.value }
            ?.key
            ?.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
            ?: "Wednesday"
    }

    // ── Keys ──────────────────────────────────────────────────────────────────

    private fun screenKey(date: LocalDate) = "st_${date.format(dateFmt)}"
    private fun interKey(date: LocalDate)  = "ic_${date.format(dateFmt)}"
    private fun resistKey(date: LocalDate) = "rs_${date.format(dateFmt)}"
    private fun streakKey(date: LocalDate) = "sk_${date.format(dateFmt)}"
    //private fun appScreenKey(date: LocalDate, pkg: String) = "screen_time_${date}_$pkg"
    //private fun mostUsedKey(date: LocalDate) = "most_used_$date"

    companion object {
        private const val PREF_FILE      = "timewise_stats"
        private const val KEY_SEEDED     = "seeded"
        private const val KEY_LAST_UPDATE_DATE = "last_update_date"
        const val MINS_PER_RESIST        = 20   // avg session length assumed saved
    }
}

// Tiny helper so we don't need kotlin-stdlib-extras
private fun ClosedFloatingPointRange<Float>.random(): Float =
    start + (endInclusive - start) * Math.random().toFloat()

private fun IntRange.random(): Int = (first + (Math.random() * (last - first + 1)).toInt())
    .coerceIn(first, last)
