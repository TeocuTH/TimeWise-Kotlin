package com.example.timewise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.timewise.calendar.CalendarScreen
import com.example.timewise.stats.StatsScreen
import com.example.timewise.ui.AppSelectionScreen
import com.example.timewise.ui.HomeScreen
import com.example.timewise.ui.theme.TimewiseTheme

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
    object Home     : Screen("home",     "Home",     Icons.Outlined.Home)
    object Calendar : Screen("calendar", "Calendar", Icons.Outlined.CalendarMonth)
    object Apps     : Screen("apps",     "Apps",     Icons.Outlined.Block)
    object Stats    : Screen("stats",    "Stats",    Icons.Outlined.BarChart)
}

private val bottomNavScreens = listOf(Screen.Home, Screen.Calendar, Screen.Apps, Screen.Stats)

@Composable
fun TimewiseApp() {
    val navController = rememberNavController()
    val navBackStack  by navController.currentBackStackEntryAsState()
    val currentDest   = navBackStack?.destination

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
            startDestination = Screen.Home.route,
            modifier         = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route)     { HomeScreen() }
            composable(Screen.Calendar.route) { CalendarScreen() }
            composable(Screen.Apps.route)     { AppSelectionScreen() }
            composable(Screen.Stats.route)    { StatsScreen() }
        }
    }
}
