package com.example.timewise

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.Handler
import android.os.Looper
import com.example.timewise.calendar.CalendarRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class TimewiseNotificationListenerService : NotificationListenerService() {

    private lateinit var prefs: AppPreferences
    private lateinit var calendarRepo: CalendarRepository
    private val handler = Handler(Looper.getMainLooper())
    
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    private val unsnoozeRunnable = object : Runnable {
        override fun run() {
            checkAndUnsnooze()
            hideExistingBlockedNotifications()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        calendarRepo = CalendarRepository(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        hideExistingBlockedNotifications()
        handler.post(unsnoozeRunnable)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        handler.removeCallbacks(unsnoozeRunnable)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (isPackageBlocked(packageName)) {
            // Snooze indefinitely (effectively hiding it)
            snoozeNotification(sbn.key, Long.MAX_VALUE - System.currentTimeMillis() - 10000)
        }
    }

    private fun isPackageBlocked(pkg: String): Boolean {
        // 1. Calendar blocks
        val nowDate = LocalDate.now().format(dateFmt)
        val nowTime = LocalTime.now().format(timeFmt)
        val calendarBlocked = calendarRepo.currentlyBlockedApps(nowDate, nowTime)

        // 2. Focus session blocks
        val sessionApps = prefs.activeFocusSession()
            ?.let { prefs.focusBlockedApps }
            ?: emptySet()

        val allBlocked = calendarBlocked + sessionApps
        return allBlocked.contains(pkg)
    }

    private fun checkAndUnsnooze() {
        val actuallySnoozed = try {
            getSnoozedNotifications()
        } catch (e: Exception) {
            emptyArray<StatusBarNotification>()
        }

        for (sbn in actuallySnoozed) {
            if (!isPackageBlocked(sbn.packageName)) {
                // App is no longer blocked, unsnooze by re-snoozing for 100ms
                snoozeNotification(sbn.key, 100L)
            }
        }
    }

    private fun hideExistingBlockedNotifications() {
        val active = try {
            getActiveNotifications()
        } catch (e: Exception) {
            emptyArray<StatusBarNotification>()
        }

        for (sbn in active) {
            if (isPackageBlocked(sbn.packageName)) {
                snoozeNotification(sbn.key, Long.MAX_VALUE - System.currentTimeMillis() - 10000)
            }
        }
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 5_000L
    }
}
