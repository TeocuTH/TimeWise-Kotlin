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

enum class EventColor(val label: String, val accentHex: Long, val containerHex: Long) {
    PURPLE("Purple", 0xFF6C63FF, 0xFFEDECFF),
    SKY("Sky", 0xFF45A2B9, 0xFFE0F7FA),
    TEAL("Teal", 0xFF1D9E75, 0xFFE0F5EE),
    SAGE("Sage", 0xFF85884B, 0xFFF1F2E4),
    GOLD("Gold", 0xFFB7951B, 0xFFF9F4DF),
    AMBER("Amber", 0xFFBA7517, 0xFFFAEEDA),
    CORAL("Coral", 0xFFD85A30, 0xFFFAECE7),
    ROSE("Rose", 0xFFC13B4A, 0xFFFCE8E9),
    MAGENTA("Magenta", 0xFF8E24AA, 0xFFF3E5F5),
    GRAY("Gray", 0xFF757575, 0xFFEEEEEE);

    companion object {
        fun default() = PURPLE
    }
}
