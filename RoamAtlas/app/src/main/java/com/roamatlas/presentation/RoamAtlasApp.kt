package com.roamatlas.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.roamatlas.presentation.navigation.Screen
import com.roamatlas.presentation.navigation.bottomNavItems
import com.roamatlas.presentation.screens.dashboard.DashboardScreenV2
import com.roamatlas.presentation.screens.days.DayAuditScreen
import com.roamatlas.presentation.screens.timelineimport.ImportTimelineScreen
import com.roamatlas.presentation.screens.map.MapScreenV2
import com.roamatlas.presentation.screens.flights.AddFlightScreen
import com.roamatlas.presentation.screens.flights.FlightRemindersScreen
import com.roamatlas.presentation.screens.flights.ImportFlightsScreen
import com.roamatlas.presentation.screens.timelineimport.LocalTimelineImportScreen
import com.roamatlas.presentation.screens.settings.HomeCountryScreen
import com.roamatlas.presentation.screens.settings.BackupScreen
import com.roamatlas.presentation.screens.settings.SettingsScreen
import com.roamatlas.presentation.screens.statistics.StatisticsScreen
import com.roamatlas.presentation.screens.tracking.GpsTrackingScreen

@Composable
fun RoamAtlasApp(
    windowSizeClass: WindowSizeClass
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val useNavigationRail = windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
    
    Scaffold(
        bottomBar = {
            if (!useNavigationRail) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon!! else screen.unselectedIcon!!,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (useNavigationRail) {
                NavigationRail {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationRailItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon!! else screen.unselectedIcon!!,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
            
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier.weight(1f)
            ) {
                composable(Screen.Dashboard.route) {
                    DashboardScreenV2(
                        onSelectHomeCountry = {
                            navController.navigate(Screen.HomeCountry.route)
                        }
                    )
                }
                composable(Screen.Map.route) {
                    MapScreenV2()
                }
                composable(Screen.Days.route) {
                    DayAuditScreen()
                }
                composable(Screen.Statistics.route) {
                    StatisticsScreen()
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onNavigateToImport = {
                            navController.navigate(Screen.ImportTimeline.route)
                        },
                        onNavigateToGpsTracking = {
                            navController.navigate(Screen.GpsTracking.route)
                        },
                        onNavigateToHomeCountry = {
                            navController.navigate(Screen.HomeCountry.route)
                        },
                        onNavigateToImportFlights = {
                            navController.navigate(Screen.ImportFlights.route)
                        },
                        onNavigateToFlightReminders = {
                            navController.navigate(Screen.FlightReminders.route)
                        },
                        onNavigateToLocalTimelineImport = {
                            navController.navigate(Screen.LocalTimelineImport.route)
                        },
                        onNavigateToBackup = {
                            navController.navigate(Screen.Backup.route)
                        }
                    )
                }
                composable(Screen.ImportTimeline.route) {
                    ImportTimelineScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }
                composable(Screen.GpsTracking.route) {
                    GpsTrackingScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }
                composable(Screen.HomeCountry.route) {
                    HomeCountryScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }
                composable(Screen.ImportFlights.route) {
                    ImportFlightsScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        },
                        onNavigateToAddFlight = {
                            navController.navigate(Screen.AddFlight.route)
                        }
                    )
                }
                composable(Screen.AddFlight.route) {
                    AddFlightScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        },
                        onFlightAdded = {
                            // Flight was added successfully
                        }
                    )
                }
                composable(Screen.FlightReminders.route) {
                    FlightRemindersScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }
                composable(Screen.LocalTimelineImport.route) {
                    LocalTimelineImportScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }
                composable(Screen.Backup.route) {
                    BackupScreen(onNavigateBack = { navController.popBackStack() })
                }
            }
        }
    }
}
