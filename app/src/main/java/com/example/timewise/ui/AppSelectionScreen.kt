package com.example.timewise.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(
    vm: AppSelectionViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Block apps", fontWeight = FontWeight.SemiBold)
                        if (state.blockedCount > 0) {
                            Text(
                                text  = "${state.blockedCount} blocked",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },

            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value          = state.query,
                onValueChange  = vm::setQuery,
                placeholder    = { Text("Search apps…") },
                leadingIcon    = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine     = true,
                shape          = RoundedCornerShape(28.dp),
                modifier       = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement   = Arrangement.spacedBy(4.dp)
                ) {
                    // Section header: blocked apps
                    val blocked = state.filtered.filter { it.isBlocked }
                    val rest    = state.filtered.filter { !it.isBlocked }

                    if (blocked.isNotEmpty()) {
                        item {
                            ListSectionHeader("Blocked")
                        }
                        items(blocked, key = { it.packageName }) { app ->
                            AppRow(app = app, onToggle = { vm.toggleBlock(app.packageName) })
                        }
                    }

                    if (rest.isNotEmpty()) {
                        item {
                            ListSectionHeader(if (blocked.isEmpty()) "All apps" else "Not blocked")
                        }
                        items(rest, key = { it.packageName }) { app ->
                            AppRow(app = app, onToggle = { vm.toggleBlock(app.packageName) })
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ListSectionHeader(text: String) {
    Text(
        text     = text.uppercase(),
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun AppRow(app: AppItem, onToggle: () -> Unit) {
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (app.isBlocked)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surface
        ),
        onClick = onToggle
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon: convert Drawable → Bitmap → Painter (no Coil needed)
            val painter = remember(app.packageName) {
                BitmapPainter(app.icon.toBitmap(96, 96).asImageBitmap())
            }
            Image(
                painter           = painter,
                contentDescription = app.appName,
                modifier          = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = app.appName,
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines   = 1,
                    )
                    if (app.category != AppCategory.OTHER) {
                        Spacer(Modifier.width(6.dp))
                        CategoryBadge(app.category)
                    }
                }
                Text(
                    text    = app.packageName,
                    style   = MaterialTheme.typography.bodySmall,
                    color   = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Checkbox(
                checked         = app.isBlocked,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
private fun CategoryBadge(category: AppCategory) {
    val (label, color) = when (category) {
        AppCategory.SOCIAL -> "Social" to MaterialTheme.colorScheme.tertiary
        AppCategory.VIDEO  -> "Video"  to MaterialTheme.colorScheme.secondary
        AppCategory.OTHER  -> return
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
