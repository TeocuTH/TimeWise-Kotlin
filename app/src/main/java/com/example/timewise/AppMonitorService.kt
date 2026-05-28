package com.example.timewise

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.example.timewise.calendar.CalendarRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Always-on background service.
 *
 * Blocking sources (independent, unioned together):
 *  1. Active calendar events  — unconditional, no user toggle needed.
 *  2. Active focus session    — timed manual block from the Apps screen.
 *
 * The service is started on boot and kept alive. It stops itself only
 * when explicitly told to (ACTION_STOP), which is only used in edge cases.
 */
class AppMonitorService : Service() {

    private lateinit var prefs: AppPreferences
    private lateinit var calendarRepo: CalendarRepository
    private val handler = Handler(Looper.getMainLooper())

    private var whitelistedPackage: String? = null
    var lastBlockedPackage: String? = null
        private set
    private var overlayVisible = false

    private var calendarBlockedApps: Set<String> = emptySet()

    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    private val pollRunnable = object : Runnable {
        override fun run() {
            poll()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private fun poll() {
        // 1. Calendar blocks — always active
        refreshCalendarBlocks()

        // 2. Focus session blocks — active only while session timer is running
        val sessionApps = prefs.activeFocusSession()
            ?.let { prefs.focusBlockedApps }
            ?: emptySet()

        val allBlocked = calendarBlockedApps + sessionApps
        if (allBlocked.isEmpty()) { 
            overlayVisible = false
            whitelistedPackage = null
            return 
        }

        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()

        val foreground = usm
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - WINDOW_MS, now)
            ?.filter { it.lastTimeUsed > 0 }
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName ?: return

        val isHomeOrSelf = foreground == packageName || isLauncher(foreground)

        // Handle whitelist logic
        if (foreground == whitelistedPackage) {
            overlayVisible = false
            return
        }

        // Clear whitelist if user moves away from the whitelisted app
        if (whitelistedPackage != null && foreground != whitelistedPackage) {
            whitelistedPackage = null
        }

        if (allBlocked.contains(foreground) && !isHomeOrSelf) {
            if (!overlayVisible) {
                overlayVisible = true
                lastBlockedPackage = foreground
                startActivity(
                    Intent(this, BlockingActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(BlockingActivity.EXTRA_BLOCKED_PACKAGE, foreground)
                    }
                )
            }
        } else {
            overlayVisible = false
        }
    }

    private fun isLauncher(pkg: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == pkg
    }

    private fun refreshCalendarBlocks() {
        val nowDate = LocalDate.now().format(dateFmt)
        val nowTime = LocalTime.now().format(timeFmt)
        calendarBlockedApps = calendarRepo.currentlyBlockedApps(nowDate, nowTime)
    }

    override fun onCreate() {
        super.onCreate()
        prefs        = AppPreferences(this)
        calendarRepo = CalendarRepository(this)
        instance     = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                whitelistedPackage = intent.getStringExtra(EXTRA_PAUSE_PACKAGE)
                overlayVisible = false
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH_CALENDAR -> refreshCalendarBlocks()
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = "timewise_monitor"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Focus Monitor", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val openPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, channelId)
            .setContentTitle("Timewise is active")
            .setContentText("Tap to manage blocked apps")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(openPi)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1_000L
        private const val WINDOW_MS        = 10_000L
        private const val NOTIFICATION_ID  = 1

        const val ACTION_PAUSE            = "com.example.timewise.ACTION_PAUSE"
        const val ACTION_STOP             = "com.example.timewise.ACTION_STOP"
        const val ACTION_REFRESH_CALENDAR = "com.example.timewise.ACTION_REFRESH_CALENDAR"
        const val EXTRA_PAUSE_PACKAGE     = "pause_package"

        @Volatile var instance: AppMonitorService? = null

        fun pause(context: Context, packageName: String?) {
            context.startService(
                Intent(context, AppMonitorService::class.java).apply {
                    action = ACTION_PAUSE
                    putExtra(EXTRA_PAUSE_PACKAGE, packageName)
                }
            )
        }
    }
}
