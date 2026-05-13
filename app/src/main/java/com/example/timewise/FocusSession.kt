package com.example.timewise

/**
 * Represents an active timed focus session (the Forest-style feature).
 * Stored in AppPreferences so it survives process death.
 */
data class FocusSession(
    /** Epoch ms when the session started. */
    val startedAt: Long,
    /** Duration chosen by the user in minutes. */
    val durationMinutes: Int,
) {
    val endsAt: Long get() = startedAt + durationMinutes * 60_000L
    val isActive: Boolean get() = System.currentTimeMillis() < endsAt
    val remainingMs: Long get() = (endsAt - System.currentTimeMillis()).coerceAtLeast(0L)
    val remainingMinutes: Int get() = (remainingMs / 60_000L).toInt()
    val remainingSeconds: Int get() = ((remainingMs % 60_000L) / 1_000L).toInt()
}
