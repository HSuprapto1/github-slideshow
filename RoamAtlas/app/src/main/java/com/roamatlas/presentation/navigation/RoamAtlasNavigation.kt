package com.roamatlas.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector? = null,
    val unselectedIcon: ImageVector? = null
) {
    data object Dashboard : Screen(
        route = "dashboard",
        title = "Dashboard",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard
    )
    
    data object Map : Screen(
        route = "map",
        title = "Map",
        selectedIcon = Icons.Filled.Map,
        unselectedIcon = Icons.Outlined.Map
    )
    
    data object Statistics : Screen(
        route = "statistics",
        title = "Statistics",
        selectedIcon = Icons.Filled.QueryStats,
        unselectedIcon = Icons.Outlined.QueryStats
    )
    
    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
    
    data object ImportTimeline : Screen(
        route = "import_timeline",
        title = "Import Timeline"
    )
    
    data object GpsTracking : Screen(
        route = "gps_tracking",
        title = "GPS Tracking"
    )
    
    data object HomeCountry : Screen(
        route = "home_country",
        title = "Home Country"
    )
    
    data object AddFlight : Screen(
        route = "add_flight",
        title = "Add Flight"
    )
    
    data object ImportFlights : Screen(
        route = "import_flights",
        title = "Import Flights"
    )
    
    data object FlightHistory : Screen(
        route = "flight_history",
        title = "Flight History"
    )
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Map,
    Screen.Statistics,
    Screen.Settings
)
