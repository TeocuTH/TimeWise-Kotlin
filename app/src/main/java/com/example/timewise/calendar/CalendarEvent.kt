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
    PURPLE("Indigo", 0xFF6C63FE, 0xFFEDECFF),
    SKY("Sky", 0xFF64B2C4, 0xFFEFF7F9),
    TEAL("Jade", 0xFF50AA7E, 0xFFEDF6F2),
    SAGE("Sage", 0xFF97AA56, 0xFFF4F6EE),
    GOLD("Gold", 0xFFEAB657, 0xFFFDF7E9),
    AMBER("Amber", 0xFFE2914F, 0xFFFCEFE5),
    CORAL("Coral", 0xFFD96748, 0xFFFBE9E4),
    ROSE("Rose", 0xFFDA7C92, 0xFFFBECF0),
    MAGENTA("Magenta", 0xFF9D5DAF, 0xFFF3EAF6),
    GRAY("Gray", 0xFF9E9E9E, 0xFFF0F0F0);

    companion object {
        fun default() = PURPLE
    }
}
