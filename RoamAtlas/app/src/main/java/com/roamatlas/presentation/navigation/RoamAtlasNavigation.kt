package com.roamatlas.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.CalendarMonth
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
    ) {
        const val DAY_ROUTE = "map/day/{date}"
        const val DAY_COUNTRY_ROUTE = "map/day/{date}/{country}"
        fun forDate(date: String) = "map/day/$date"
        fun forCountryDate(date: String, countryCode: String) =
            "map/day/$date/${countryCode.uppercase()}"
    }

    data object Days : Screen(
        route = "days",
        title = "Days",
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth
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
    
    data object FlightReminders : Screen(
        route = "flight_reminders",
        title = "Flight Reminders"
    )
    
    data object LocalTimelineImport : Screen(
        route = "local_timeline_import",
        title = "Import from Phone"
    )

    data object Backup : Screen(
        route = "encrypted_backup",
        title = "Encrypted Backup"
    )

    data object StorageUsage : Screen(
        route = "storage_usage",
        title = "Storage Usage"
    )

    data object CountryDetail : Screen(
        route = "country/{code}",
        title = "Country History"
    ) {
        fun forCode(countryCode: String) = "country/${countryCode.uppercase()}"
    }
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Map,
    Screen.Days,
    Screen.Statistics,
    Screen.Settings
)
