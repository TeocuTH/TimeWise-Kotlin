package com.example.timewise

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.app.ActivityOptions
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import android.content.ComponentName
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.timewise.calendar.CalendarScreen
import com.example.timewise.stats.StatsScreen
import com.example.timewise.ui.AppSelectionScreen
import com.example.timewise.ui.theme.TimewiseTheme

private val Purple      = Color(0xFF6C63FF)
private val PurpleLight = Color(0xFFEDECFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimewiseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background,
                ) {
                    TimewiseApp()
                }
            }
        }
    }
}

private sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    object Calendar : Screen("calendar", "Calendar", Icons.Outlined.CalendarMonth)
    object Apps     : Screen("apps",     "Apps",     Icons.Outlined.Block)
    object Stats    : Screen("stats",    "Stats",    Icons.Outlined.BarChart)
}

private val bottomNavScreens = listOf(Screen.Calendar, Screen.Apps, Screen.Stats)

@Composable
fun TimewiseApp() {
    val navController = rememberNavController()
    val navBackStack  by navController.currentBackStackEntryAsState()
    val currentDest   = navBackStack?.destination
    val context       = LocalContext.current

    var showPermissionDialog by remember { mutableStateOf(false) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            showPermissionDialog = !allPermissionsGranted(context)
        }
    }

    LaunchedEffect(showPermissionDialog) {
        if (!showPermissionDialog) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AppMonitorService::class.java)
            )
        }
    }

    if (showPermissionDialog) {
        PermissionSetupDialog(
            context   = context,
            onDismiss = {
                if (allPermissionsGranted(context)) showPermissionDialog = false
            }
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavScreens.forEach { screen ->
                    NavigationBarItem(
                        selected = currentDest?.hierarchy?.any { it.route == screen.route } == true,
                        onClick  = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                        icon  = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Calendar.route,
            modifier         = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Calendar.route) { CalendarScreen() }
            composable(Screen.Apps.route)     { AppSelectionScreen() }
            composable(Screen.Stats.route)    { StatsScreen() }
        }
    }
}

// ── Permission dialog ─────────────────────────────────────────────────────────

@Composable
private fun PermissionSetupDialog(
    context: Context,
    onDismiss: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasUsage   by remember { mutableStateOf(hasUsagePermission(context)) }
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasBattery by remember { mutableStateOf(hasBatteryOptimizationExemption(context)) }
    var hasNotif   by remember { mutableStateOf(isNotificationServiceEnabled(context)) }

    // Re-check on every onResume (returning from Settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsage   = hasUsagePermission(context)
                hasOverlay = Settings.canDrawOverlays(context)
                hasBattery = hasBatteryOptimizationExemption(context)
                hasNotif   = isNotificationServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Launchers must be at the top level of the composable, not inside a lambda
    val allGranted = hasUsage && hasOverlay && hasBattery && hasNotif

    AlertDialog(
        onDismissRequest = { /* blocked until all granted */ },
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text("Set up Timewise", fontWeight = FontWeight.Bold)
                Text(
                    "Grant these permissions so blocking works correctly.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PermissionItem(
                    icon     = Icons.Outlined.QueryStats,
                    title    = "Usage access",
                    subtitle = "Detects which app is in the foreground",
                    granted  = hasUsage,
                    onClick  = {
                        val options = ActivityOptions.makeCustomAnimation(
                            context,
                            android.R.anim.fade_in,
                            android.R.anim.fade_out
                        ).toBundle()
                        context.startActivity(
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                            options
                        )
                        pollAndReturn(context) { hasUsagePermission(context) }
                    }
                )
                PermissionItem(
                    icon     = Icons.Outlined.Layers,
                    title    = "Draw over other apps",
                    subtitle = "Shows the blocking screen on top",
                    granted  = hasOverlay,
                    onClick  = {
                        val options = ActivityOptions.makeCustomAnimation(
                            context,
                            android.R.anim.fade_in,
                            android.R.anim.fade_out
                        ).toBundle()
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + context.packageName)
                            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                            options
                        )
                        pollAndReturn(context) { Settings.canDrawOverlays(context) }
                    }
                )
                PermissionItem(
                    icon     = Icons.Outlined.BatteryChargingFull,
                    title    = "Battery optimisation",
                    subtitle = "Keeps the service running in the background",
                    granted  = hasBattery,
                    onClick  = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                // No FLAG_ACTIVITY_NEW_TASK — keeps it as inline dialog
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:" + context.packageName)
                                    )
                                )
                            } catch (e: Exception) {
                                val options = ActivityOptions.makeCustomAnimation(
                                    context,
                                    android.R.anim.fade_in,
                                    android.R.anim.fade_out
                                ).toBundle()
                                context.startActivity(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                    options
                                )
                                pollAndReturn(context) { hasBatteryOptimizationExemption(context) }
                            }
                        }
                    }
                )
                PermissionItem(
                    icon     = Icons.Outlined.Notifications,
                    title    = "Notification access",
                    subtitle = "Hides notifications from blocked apps",
                    granted  = hasNotif,
                    onClick  = {
                        val options = ActivityOptions.makeCustomAnimation(
                            context,
                            android.R.anim.fade_in,
                            android.R.anim.fade_out
                        ).toBundle()
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                            options
                        )
                        pollAndReturn(context) { isNotificationServiceEnabled(context) }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = onDismiss,
                enabled  = allGranted,
                colors   = ButtonDefaults.buttonColors(containerColor = Purple),
                shape    = RoundedCornerShape(10.dp),
            ) {
                Text(if (allGranted) "All set — let's go!" else "Grant above to continue")
            }
        },
    )
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Card(
        shape   = RoundedCornerShape(12.dp),
        colors  = CardDefaults.cardColors(
            containerColor = if (granted) PurpleLight
                             else MaterialTheme.colorScheme.surfaceVariant,
        ),
        onClick = { if (!granted) onClick() },
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = if (granted) Purple
                                     else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text       = title,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color      = if (granted) Purple
                                 else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector        = if (granted) Icons.Outlined.CheckCircle
                                     else Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint               = if (granted) Purple
                                     else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(18.dp),
            )
        }
    }
}

// ── Permission helpers ────────────────────────────────────────────────────────

private fun allPermissionsGranted(context: Context) =
    hasUsagePermission(context) &&
    Settings.canDrawOverlays(context) &&
    hasBatteryOptimizationExemption(context) &&
    isNotificationServiceEnabled(context)

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (flat != null) {
        val names = flat.split(":").toTypedArray()
        for (name in names) {
            val cn = ComponentName.unflattenFromString(name)
            if (cn != null && pkgName == cn.packageName) {
                return true
            }
        }
    }
    return false
}

private fun hasUsagePermission(context: Context): Boolean {
    val ops  = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = ops.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(), context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun hasBatteryOptimizationExemption(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

// ── Poll helper ───────────────────────────────────────────────────────────────

/**
 * Polls [isGranted] every 500ms. As soon as it returns true, brings
 * the app back to the foreground by launching MainActivity with
 * FLAG_ACTIVITY_REORDER_TO_FRONT — no back press needed from the user.
 */
private fun pollAndReturn(context: Context, isGranted: () -> Boolean) {
    val handler = Handler(Looper.getMainLooper())
    handler.post(object : Runnable {
        override fun run() {
            if (isGranted()) {
                val options = ActivityOptions.makeCustomAnimation(
                    context,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                ).toBundle()
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    },
                    options
                )
            } else {
                handler.postDelayed(this, 500)
            }
        }
    })
}