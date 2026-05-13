package com.example.timewise

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restarts AppMonitorService after a reboot, if monitoring was enabled
 * when the device was last shut down.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val prefs = AppPreferences(context)
        if (prefs.monitoringEnabled) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AppMonitorService::class.java)
            )
        }
    }
}
