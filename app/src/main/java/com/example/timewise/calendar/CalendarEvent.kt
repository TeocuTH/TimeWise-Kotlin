package com.example.timewise.calendar

import org.json.JSONArray
import org.json.JSONObject

/**
 * A single calendar event that optionally blocks a set of apps
 * while it is active.
 */
data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String,
    /** Date as "yyyy-MM-dd" */
    val date: String,
    /** Start time as "HH:mm" (24h) */
    val startTime: String,
    /** End time as "HH:mm" (24h) */
    val endTime: String,
    /** Package names of apps to block during this event */
    val blockedApps: List<String> = emptyList(),
    val color: EventColor = EventColor.PURPLE,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("description", description)
        put("date", date)
        put("startTime", startTime)
        put("endTime", endTime)
        put("blockedApps", JSONArray(blockedApps))
        put("color", color.name)
    }

    companion object {
        fun fromJson(obj: JSONObject) = CalendarEvent(
            id          = obj.getString("id"),
            title       = obj.getString("title"),
            description = obj.optString("description", ""),
            date        = obj.getString("date"),
            startTime   = obj.getString("startTime"),
            endTime     = obj.getString("endTime"),
            blockedApps = buildList {
                val arr = obj.optJSONArray("blockedApps") ?: return@buildList
                for (i in 0 until arr.length()) add(arr.getString(i))
            },
            color       = runCatching {
                EventColor.valueOf(obj.optString("color", "PURPLE"))
            }.getOrDefault(EventColor.PURPLE),
        )
    }
}

enum class EventColor {
    PURPLE, TEAL, CORAL, AMBER;
}
