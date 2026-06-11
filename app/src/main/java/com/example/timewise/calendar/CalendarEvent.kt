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
    /** Start date as "yyyy-MM-dd" */
    val startDate: String,
    /** End date as "yyyy-MM-dd" */
    val endDate: String,
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
        put("startDate", startDate)
        put("endDate", endDate)
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
            startDate   = obj.optString("startDate", obj.optString("date")), // Fallback for old data
            endDate     = obj.optString("endDate", obj.optString("date")),
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
    PURPLE("Purple", 0xFF8E8BBF, 0xFFF1F1F8),
    SKY("Sky", 0xFF7DADB8, 0xFFF0F6F7),
    TEAL("Teal", 0xFF6AA48A, 0xFFF0F5F2),
    SAGE("Sage", 0xFF909660, 0xFFF3F4ED),
    GOLD("Gold", 0xFFC8B46A, 0xFFF8F7EE),
    AMBER("Amber", 0xFFC49A60, 0xFFF8F4ED),
    CORAL("Coral", 0xFFC98070, 0xFFF8F1EF),
    ROSE("Rose", 0xFFB87070, 0xFFF7F0F0),
    MAGENTA("Magenta", 0xFFA06AAF, 0xFFF5F1F7),
    GRAY("Gray", 0xFF9E9E9E, 0xFFF5F5F5);

    companion object {
        fun default() = PURPLE
    }
}
