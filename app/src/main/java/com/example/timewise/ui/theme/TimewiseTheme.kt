package com.example.timewise.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Purple      = Color(0xFF6C63FF)
private val PurpleLight = Color(0xFFEDECFF)
private val PurpleDark  = Color(0xFF4A44C6)

private val LightColors = lightColorScheme(
    primary          = Purple,
    onPrimary        = Color.White,
    primaryContainer = PurpleLight,
    secondary        = Color(0xFF3B82F6),
    tertiary         = Color(0xFF10B981),
    background       = Color(0xFFF8F8FC),
    surface          = Color.White,
    surfaceVariant   = Color(0xFFF1F0FA),
    error            = Color(0xFFE53935),
)

private val DarkColors = darkColorScheme(
    primary          = Color(0xFF9D97FF),
    onPrimary        = Color(0xFF1A1A2E),
    primaryContainer = Color(0xFF2E2B5A),
    background       = Color(0xFF0F0F14),
    surface          = Color(0xFF1A1A24),
    surfaceVariant   = Color(0xFF22222E),
)

@Composable
fun TimewiseTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content     = content
    )
}
