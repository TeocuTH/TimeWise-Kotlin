package com.example.timewise.calendar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists CalendarEvents as a JSON file in the app's internal storage.
 * Simple and dependency-free — no Room needed for this scale.
 */
class CalendarRepository(context: Context) {

    private val file = File(context.filesDir, "calendar_events.json")

    // ── Read ──────────────────────────────────────────────────────────────────

    fun loadAll(): List<CalendarEvent> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    add(CalendarEvent.fromJson(arr.getJSONObject(i)))
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun loadForDate(date: String): List<CalendarEvent> =
        loadAll().filter { 
            date >= it.startDate && date <= it.endDate 
        }.sortedBy { it.startTime }

    // ── Write ─────────────────────────────────────────────────────────────────

    fun save(event: CalendarEvent) {
        val all = loadAll().toMutableList()
        val idx = all.indexOfFirst { it.id == event.id }
        if (idx >= 0) all[idx] = event else all.add(event)
        persist(all)
    }

    fun delete(eventId: String) {
        val all = loadAll().filter { it.id != eventId }
        persist(all)
    }

    private fun persist(events: List<CalendarEvent>) {
        val arr = JSONArray()
        events.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString())
    }

    // ── Active event query (used by AppMonitorService) ────────────────────────

    /**
     * Returns the set of package names that should be blocked RIGHT NOW
     * based on all active events for today.
     */
    fun currentlyBlockedApps(nowDate: String, nowTime: String): Set<String> {
        return loadAll().filter { event ->
            if (nowDate < event.startDate || nowDate > event.endDate) return@filter false
            
            val isStartDay = nowDate == event.startDate
            val isEndDay = nowDate == event.endDate
            
            when {
                isStartDay && isEndDay -> nowTime >= event.startTime && nowTime < event.endTime
                isStartDay -> nowTime >= event.startTime
                isEndDay -> nowTime < event.endTime
                else -> true // Middle day
            }
        }
        .flatMap { it.blockedApps }
        .toSet()
    }
}
