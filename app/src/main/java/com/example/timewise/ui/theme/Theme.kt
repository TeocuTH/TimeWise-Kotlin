package com.example.timewise.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val AppWhite = Color(0xFFFBFAF6)
private val AppBlack = Color(0xFF2C2C28)
private val Matcha = Color(0xFF768050)
private val MatchaLight = Color(0xFFEFF5D7)
private val MatchaMedium = Color(0xFFBAC981)
private val GreyLight= Color(0xFFEAEAE5)

private val DarkColorScheme = darkColorScheme(
    primary = Matcha,
    onPrimary = Color(0xFF1A1A2E),
    primaryContainer = Color(0xFF2E2B5A),
    secondary = Color(0xFF93C5FD),
    tertiary = Color(0xFF6EE7B7),
    background = AppWhite,
    surface = AppWhite,
    surfaceVariant = Color(0xFFF1F0EA),

    onBackground = AppBlack,
    onSurface = AppBlack,
    onSurfaceVariant = AppBlack.copy(alpha = 0.65f),
)

private val LightColorScheme = lightColorScheme(
    primary = Matcha,
    onPrimary = AppWhite,
    primaryContainer = MatchaLight,

    secondary = Matcha,
    tertiary = MatchaMedium,

    background = AppWhite,
    surface = AppWhite,
    surfaceVariant = GreyLight,

    onBackground = AppBlack,
    onSurface = AppBlack,
    onSurfaceVariant = AppBlack.copy(alpha = 0.65f),
)

@Composable
fun TimeWiseTheme(
    darkTheme: Boolean = false,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
){
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}