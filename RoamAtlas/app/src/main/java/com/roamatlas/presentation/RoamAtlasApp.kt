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
import com.roamatlas.presentation.screens.country.CountryDetailScreen
import com.roamatlas.presentation.screens.country.CountriesScreen
import com.roamatlas.presentation.screens.city.CitiesScreen
import com.roamatlas.presentation.screens.city.CityDetailScreen
import com.roamatlas.presentation.screens.days.DayAuditScreen
import com.roamatlas.presentation.screens.photos.PhotosScreen
import com.roamatlas.presentation.screens.timelineimport.ImportTimelineScreen
import com.roamatlas.presentation.screens.map.MapScreenV2
import com.roamatlas.presentation.screens.flights.AddFlightScreen
import com.roamatlas.presentation.screens.flights.FlightRemindersScreen
import com.roamatlas.presentation.screens.flights.ImportFlightsScreen
import com.roamatlas.presentation.screens.journey.JourneyDetailScreen
import com.roamatlas.presentation.screens.journey.JourneysScreen
import com.roamatlas.presentation.screens.timelineimport.LocalTimelineImportScreen
import com.roamatlas.presentation.screens.settings.HomeCountryScreen
import com.roamatlas.presentation.screens.settings.BackupScreen
import com.roamatlas.presentation.screens.settings.SettingsScreen
import com.roamatlas.presentation.screens.settings.StorageUsageScreen
import com.roamatlas.presentation.screens.statistics.StatisticsScreen
import com.roamatlas.presentation.screens.tracking.GpsTrackingScreen
import java.time.LocalDate

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
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route ||
                                (screen == Screen.Map && it.route?.startsWith("map/") == true)
                        } == true
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
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route ||
                                (screen == Screen.Map && it.route?.startsWith("map/") == true)
                        } == true
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
                        },
                        onOpenCountry = { countryCode ->
                            navController.navigate(Screen.CountryDetail.forCode(countryCode))
                        },
                        onOpenCountries = {
                            navController.navigate(Screen.Countries.route)
                        },
                        onOpenCities = {
                            navController.navigate(Screen.Cities.route)
                        },
                        onOpenToday = {
                            navController.navigate(
                                Screen.Map.forDate(java.time.LocalDate.now().toString())
                            )
                        },
                        onOpenJourneys = {
                            navController.navigate(Screen.Journeys.route)
                        },
                        onOpenJourney = { journeyId ->
                            navController.navigate(Screen.JourneyDetail.forId(journeyId))
                        }
                    )
                }
                composable(Screen.Map.route) {
                    MapScreenV2()
                }
                composable(Screen.Map.DAY_ROUTE) { entry ->
                    val date = entry.arguments?.getString("date")?.let(LocalDate::parse)
                    MapScreenV2(initialDate = date)
                }
                composable(Screen.Map.DAY_COUNTRY_ROUTE) { entry ->
                    val date = entry.arguments?.getString("date")?.let(LocalDate::parse)
                    val country = entry.arguments?.getString("country")
                    MapScreenV2(initialDate = date, initialCountryCode = country)
                }
                composable(Screen.Map.DAY_CITY_ROUTE) { entry ->
                    val date = entry.arguments?.getString("date")?.let(LocalDate::parse)
                    val country = entry.arguments?.getString("country")
                    val city = entry.arguments?.getString("city")
                    MapScreenV2(
                        initialDate = date,
                        initialCountryCode = country,
                        initialCity = city
                    )
                }
                composable(Screen.Days.route) {
                    DayAuditScreen(
                        onOpenMap = { date ->
                            navController.navigate(Screen.Map.forDate(date.toString()))
                        }
                    )
                }
                composable(Screen.Statistics.route) {
                    StatisticsScreen(
                        onOpenCountry = { countryCode ->
                            navController.navigate(Screen.CountryDetail.forCode(countryCode))
                        },
                        onOpenCountries = {
                            navController.navigate(Screen.Countries.route)
                        },
                        onOpenCities = {
                            navController.navigate(Screen.Cities.route)
                        }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onNavigateToImport = {
                            navController.navigate(Screen.ImportTimeline.route)
                        },
                        onNavigateToGpsTracking = {
                            navController.navigate(Screen.GpsTracking.route)
                        },
                        onNavigateToStatistics = {
                            navController.navigate(Screen.Statistics.route)
                        },
                        onNavigateToHomeCountry = {
                            navController.navigate(Screen.HomeCountry.route)
                        },
                        onNavigateToImportFlights = {
                            navController.navigate(Screen.ImportFlights.route)
                        },
                        onNavigateToJourneys = {
                            navController.navigate(Screen.Journeys.route)
                        },
                        onNavigateToFlightReminders = {
                            navController.navigate(Screen.FlightReminders.route)
                        },
                        onNavigateToLocalTimelineImport = {
                            navController.navigate(Screen.LocalTimelineImport.route)
                        },
                        onNavigateToBackup = {
                            navController.navigate(Screen.Backup.route)
                        },
                        onNavigateToStorageUsage = {
                            navController.navigate(Screen.StorageUsage.route)
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
                composable(Screen.Journeys.route) {
                    JourneysScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenJourney = { journeyId ->
                            navController.navigate(Screen.JourneyDetail.forId(journeyId))
                        }
                    )
                }
                composable(Screen.JourneyDetail.route) {
                    JourneyDetailScreen(
                        onNavigateBack = { navController.popBackStack() }
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
                composable(Screen.StorageUsage.route) {
                    StorageUsageScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.CountryDetail.route) {
                    CountryDetailScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenMap = { date, countryCode ->
                            navController.navigate(
                                Screen.Map.forCountryDate(date.toString(), countryCode)
                            )
                        }
                    )
                }
                composable(Screen.Countries.route) {
                    CountriesScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenCountry = { countryCode ->
                            navController.navigate(Screen.CountryDetail.forCode(countryCode))
                        }
                    )
                }
                composable(Screen.Cities.route) {
                    CitiesScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenCity = { city ->
                            navController.navigate(
                                Screen.CityDetail.forCity(city.countryCode, city.city)
                            )
                        }
                    )
                }
                composable(Screen.CityDetail.route) {
                    CityDetailScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenMap = { date, countryCode, city ->
                            navController.navigate(
                                Screen.Map.forCityDate(
                                    date.toString(),
                                    countryCode,
                                    city
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}
