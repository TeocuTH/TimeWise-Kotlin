package com.example.timewise.ui.theme

import androidx.compose.runtime.Composable

/**
 * Compatibility wrapper for the old theme name.
 *
 * The real app theme is now in Theme.kt as TimeWiseTheme.
 * Keeping this wrapper means older files that still call TimewiseTheme
 * will use the same colors and typography instead of a second separate theme.
 */
@Composable
fun TimewiseTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    TimeWiseTheme(
        darkTheme = darkTheme,
        dynamicColor = false,
        content = content
    )
}