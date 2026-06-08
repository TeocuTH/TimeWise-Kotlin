package com.example.timewise.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DayBar(
    val date: LocalDate,
    val label: String,       // "Mon", "Tue" …
    val hours: Float?,       // null = future / no data
    val isToday: Boolean,
)

data class StatsUiState(
    val loading: Boolean           = true,
    val weekBars: List<DayBar>     = emptyList(),
    val weekInterceptions: Int     = 0,
    val weekResisted: Int          = 0,
    val currentStreak: Int         = 0,
    val bestStreak: Int            = 0,
    val streakWeekDays: List<Boolean> = emptyList(), // Mon–Sun, true = streak day
    val minutesSaved: Int          = 0,
    val hardestDay: String         = "",
    val aiInsight: List<String>   = emptyList(),
    val aiTip: String              = "",
    val topApps: List<AppUsageInfo> = emptyList(),
    val hasUsagePermission: Boolean = false,
)

class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = StatsRepository(app)

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val dayFmt = DateTimeFormatter.ofPattern("EEE", java.util.Locale.ENGLISH)

    init { load() }

    fun load() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.seedIfNeeded() }

            val today  = LocalDate.now()
            val bars   = repo.weekScreenTimes(today).map { (date, hours) ->
                DayBar(
                    date    = date,
                    label   = date.format(dayFmt),
                    hours   = if (date > today) null else hours,
                    isToday = date == today,
                )
            }
            val weekInter   = repo.weekInterceptions(today)
            val weekRes     = repo.weekResisted(today)
            val streak      = repo.currentStreak()
            val best        = repo.bestStreak()
            val streakDays  = run {
                val monday = today.with(java.time.DayOfWeek.MONDAY)
                (0..6).map { repo.isStreakDay(monday.plusDays(it.toLong())) }
            }
            val minSaved    = repo.totalMinutesSaved()
            val hardest     = repo.hardestDay()
            val resistPct   = if (weekInter > 0)
                (weekRes * 100 / weekInter) else 0

            val topApps     = repo.getScaledTopAppsWeekly()
            val hasPermission = repo.hasUsageStatsPermission()
            val topAppName = if (hasPermission && topApps.isNotEmpty()) topApps.first().appName else ""
            val topAppLine = if (hasPermission && topApps.isNotEmpty()) ", with $topAppName being your most used app." else "."

            val insight = buildInsight(resistPct, weekInter, hardest, topAppLine)
            val tip     = buildTip(hardest, resistPct)

            _uiState.update {
                StatsUiState(
                    loading            = false,
                    weekBars           = bars,
                    weekInterceptions  = weekInter,
                    weekResisted       = weekRes,
                    currentStreak      = streak,
                    bestStreak         = best,
                    streakWeekDays     = streakDays,
                    minutesSaved       = minSaved,
                    hardestDay         = hardest,
                    aiInsight         = insight,
                    aiTip              = tip,
                    topApps            = topApps,
                    hasUsagePermission = hasPermission,
                )
            }
        }
    }

    // ── AI Insights (Template-based) ─────────────────────────────────────────

    private fun buildInsight(resistPct: Int, interceptions: Int, hardestDay: String, topAppLine: String): List<String> {
        val trend = when {
            resistPct >= 70 -> "You're doing really well"
            resistPct >= 50 -> "You're making progress"
            else            -> "This week was challenging"
        }
        return listOf(
            "$trend — you resisted $resistPct% of the time across $interceptions blocking moments this week. $hardestDay tends to be your hardest day for screen time$topAppLine",
            "✅ Good job! You studied 3 hours more on average this week.",
            "⚠️ Try to lower your screen-time, it's higher than average!"
        )
    }

    private fun buildTip(hardestDay: String, resistPct: Int): String {
        return if (resistPct < 60) {
            "Try adding a focus event on $hardestDay evenings in your calendar " +
                    "to automatically block distracting apps during your most vulnerable window."
        } else {
            "Keep it up — consider shortening the countdown delay to 3 seconds " +
                    "to make the habit even more automatic."
        }
    }
}