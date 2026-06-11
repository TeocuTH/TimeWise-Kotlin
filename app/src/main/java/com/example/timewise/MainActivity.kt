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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
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

private val Purple      = Color(0xFF8E8BBF)
private val PurpleLight = Color(0xFFF1F1F8)

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

//private val bottomNavScreens = listOf(Screen.Calendar, Screen.Apps, Screen.Stats)
private val bottomNavScreens = listOf(Screen.Calendar, Screen.Stats)

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
        SequentialPermissionDialogs(
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
private fun SequentialPermissionDialogs(
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

    val allGranted = hasUsage && hasOverlay && hasBattery && hasNotif
    if (allGranted) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    // Determine which dialog to show
    val stepData = when {
        !hasUsage -> PermissionStep(
            id = 1,
            title = "App Usage Permission",
            description = "Allows Timewise to analyze your app usage locally and monitor your improvements.",
            icon = Icons.Outlined.QueryStats,
            buttonText = "Grant Usage Access",
            onClick = {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                }
                try { context.startActivity(intent) } catch (e: Exception) { intent.data = null; context.startActivity(intent) }
                pollAndReturn(context) { hasUsagePermission(context) }
            }
        )
        !hasOverlay -> PermissionStep(
            id = 2,
            title = "Enable Blocking Screen",
            description = "Allows Timewise to block distracting apps by displaying its overlay on top of them.",
            icon = Icons.Outlined.Layers,
            buttonText = "Allow Overlay",
            onClick = {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:" + context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try { context.startActivity(intent) } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }
                pollAndReturn(context) { Settings.canDrawOverlays(context) }
            }
        )
        !hasBattery -> PermissionStep(
            id = 3,
            title = "Disable Battery Saving",
            description = "Keeps TimeWise running in the background so your focus sessions don't get interrupted.",
            icon = Icons.Outlined.BatteryChargingFull,
            buttonText = "Exclude from Optimization",
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName)))
                    } catch (e: Exception) {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                    }
                    pollAndReturn(context) { hasBatteryOptimizationExemption(context) }
                }
            }
        )
        !hasNotif -> PermissionStep(
            id = 4,
            title = "Allow to Hide Notifications",
            description = "Allows TimeWise to hide distracting alerts while you're concentrated.",
            icon = Icons.Outlined.Notifications,
            buttonText = "Enable Notification Access",
            onClick = {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        val cn = ComponentName(context, TimewiseNotificationListenerService::class.java)
                        putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString())
                    }
                } else {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                }
                try { context.startActivity(intent) } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }
                pollAndReturn(context) { isNotificationServiceEnabled(context) }
            }
        )
        else -> null
    }

    stepData?.let { SinglePermissionDialog(it) }
}

private data class PermissionStep(
    val id: Int,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val buttonText: String,
    val onClick: () -> Unit
)

@Composable
private fun SinglePermissionDialog(data: PermissionStep) {
    AlertDialog(
        onDismissRequest = { /* forced */ },
        shape = RoundedCornerShape(24.dp),
        icon = {
            AnimatedContent(
                targetState = data,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "icon"
            ) { step ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${step.id}/4",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(PurpleLight, CircleShape)
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(step.icon, contentDescription = null, tint = Purple, modifier = Modifier.size(32.dp))
                    }
                }
            }
        },
        title = {
            AnimatedContent(targetState = data.title, label = "title") { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        text = {
            AnimatedContent(targetState = data.description, label = "desc") { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = data.onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Purple),
                shape = RoundedCornerShape(12.dp)
            ) {
                AnimatedContent(targetState = data.buttonText, label = "btn") { text ->
                    Text(text, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
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
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                val options = ActivityOptions.makeCustomAnimation(
                    context,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                ).toBundle()
                context.startActivity(intent, options)
            } else {
                handler.postDelayed(this, 500)
            }
        }
    })
}