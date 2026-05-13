package com.example.timewise

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restarts AppMonitorService after reboot.
 * The service now always runs when permissions are granted — no toggle to check.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        ContextCompat.startForegroundService(
            context,
            Intent(context, AppMonitorService::class.java)
        )
    }
}
