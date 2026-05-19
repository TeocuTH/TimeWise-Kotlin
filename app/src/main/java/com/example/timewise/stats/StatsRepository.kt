package com.example.timewise.stats

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

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

    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // ── Seed ─────────────────────────────────────────────────────────────────

    /**
     * Called once on first launch. Writes realistic-looking simulated
     * screen-time and interception data for the past 30 days so the stats
     * screen is never empty during a demo.
     */
    fun seedIfNeeded() {
        if (prefs.getBoolean(KEY_SEEDED, false)) return

        val today = LocalDate.now()
        // Seed 30 days of screen-time (hours, as Float stored as Int*10)
        val screenTimeTemplate = listOf(3.1f, 2.4f, 4.0f, 2.8f, 1.9f, 3.5f, 2.2f)
        for (i in 29 downTo 0) {
            val date = today.minusDays(i.toLong())
            val baseHours = screenTimeTemplate[date.dayOfWeek.value % 7]
            val jitter = (-0.4f..0.4f).random()
            setScreenTime(date, (baseHours + jitter).coerceAtLeast(0.5f))
        }

        // Seed interception / resist history for past 30 days
        for (i in 29 downTo 1) {
            val date = today.minusDays(i.toLong())
            val interceptions = (2..7).random()
            val resisted      = (interceptions * 0.55).roundToInt()
                .coerceAtMost(interceptions)
            setDayInterceptions(date, interceptions, resisted)

            // Mark streak days (resisted at least once)
            if (resisted > 0) markStreakDay(date)
        }

        // Seed streak-days set
        prefs.edit { putBoolean(KEY_SEEDED, true) }
    }

    // ── Screen time ───────────────────────────────────────────────────────────

    fun setScreenTime(date: LocalDate, hours: Float) {
        prefs.edit { putInt(screenKey(date), (hours * 10).roundToInt()) }
    }

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
            ?.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
            ?: "Wednesday"
    }

    // ── Keys ──────────────────────────────────────────────────────────────────

    private fun screenKey(date: LocalDate) = "st_${date.format(dateFmt)}"
    private fun interKey(date: LocalDate)  = "ic_${date.format(dateFmt)}"
    private fun resistKey(date: LocalDate) = "rs_${date.format(dateFmt)}"
    private fun streakKey(date: LocalDate) = "sk_${date.format(dateFmt)}"

    companion object {
        private const val PREF_FILE      = "timewise_stats"
        private const val KEY_SEEDED     = "seeded"
        const val MINS_PER_RESIST        = 20   // avg session length assumed saved
    }
}

// Tiny helper so we don't need kotlin-stdlib-extras
private fun ClosedFloatingPointRange<Float>.random(): Float =
    start + (endInclusive - start) * Math.random().toFloat()

private fun IntRange.random(): Int = (first + (Math.random() * (last - first + 1)).toInt())
    .coerceIn(first, last)
