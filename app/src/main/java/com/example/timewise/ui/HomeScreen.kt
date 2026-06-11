package com.example.timewise.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private val Purple      = Color(0xFF8E8BBF)
private val PurpleLight = Color(0xFFF1F1F8)
private val TealColor   = Color(0xFF6AA48A)
private val TealLight   = Color(0xFFEDF4F1)

@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val state   by vm.uiState.collectAsState()
    val context = LocalContext.current

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refresh() }
    }

    val allPermissionsGranted = state.hasUsagePermission && state.hasOverlayPermission

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 56.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Text("Timewise", style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold)
        Text("Intentional phone use, one pause at a time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(16.dp))

        // ── Active status banner ──────────────────────────────────────────
        if (allPermissionsGranted) {
            val (bannerColor, bannerText, bannerIcon) = when {
                state.hasActiveSession -> Triple(
                    PurpleLight,
                    "Focus session active — apps are blocked",
                    Icons.Outlined.Timer,
                )
                state.hasActiveCalendarEvent -> Triple(
                    PurpleLight,
                    "Calendar event active — apps are blocked",
                    Icons.Outlined.Event,
                )
                else -> Triple(
                    MaterialTheme.colorScheme.surfaceVariant,
                    "No active block — calendar events will block automatically",
                    Icons.Outlined.CheckCircle,
                )
            }
            Card(
                shape  = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = bannerColor),
            ) {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(bannerIcon, null, tint = Purple, modifier = Modifier.size(20.dp))
                    Text(bannerText, style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF3C3489))
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        // ── Permissions ───────────────────────────────────────────────────
        SectionLabel("Permissions")

        PermissionRow(
            icon     = Icons.Outlined.QueryStats,
            label    = "Usage access",
            subtitle = "Detect which app is in the foreground",
            granted  = state.hasUsagePermission,
            onClick  = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
        )
        PermissionRow(
            icon     = Icons.Outlined.Layers,
            label    = "Draw over other apps",
            subtitle = "Show the blocking screen",
            granted  = state.hasOverlayPermission,
            onClick  = {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"))
                )
            },
        )

        Spacer(Modifier.height(8.dp))

        // ── How blocking works ────────────────────────────────────────────
        SectionLabel("How it works")

        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HowItWorksRow(
                    icon    = Icons.Outlined.CalendarMonth,
                    title   = "Calendar events",
                    body    = "Any apps you attach to a calendar event are blocked automatically while that event is running.",
                    tint    = Purple,
                )
                HorizontalDivider()
                HowItWorksRow(
                    icon    = Icons.Outlined.Timer,
                    title   = "Focus sessions",
                    body    = "Start a timed session from the Apps tab to block your chosen apps for a set duration.",
                    tint    = TealColor,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Quick stats ───────────────────────────────────────────────────
        SectionLabel("Today")

        Card(shape = RoundedCornerShape(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatCell(state.totalInterceptions.toString(), "Interceptions")
                HorizontalDivider(
                    modifier = Modifier.height(48.dp).width(1.dp)
                        .align(Alignment.CenterVertically)
                )
                StatCell(state.totalResisted.toString(), "Times resisted")
            }
        }
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text.uppercase(),
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    label: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Card(
        shape    = RoundedCornerShape(16.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge,
                    color = if (granted) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector        = if (granted) Icons.Outlined.CheckCircle
                                     else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (granted) "Granted" else "Not granted",
                tint               = if (granted) MaterialTheme.colorScheme.primary
                                     else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun HowItWorksRow(
    icon: ImageVector,
    title: String,
    body: String,
    tint: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = Purple)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
