package com.roamatlas.presentation.screens.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.roamatlas.domain.model.CountryVisitStats
import com.roamatlas.domain.model.CountryVisitClassification
import com.roamatlas.domain.model.TravelStatistics
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreenV2(
    onSelectHomeCountry: () -> Unit = {},
    onOpenCountry: (String) -> Unit = {},
    onOpenCountries: () -> Unit = {},
    onOpenCities: () -> Unit = {},
    viewModel: DashboardViewModelV2 = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RoamAtlas") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            if (uiState.hasData) {
                FloatingActionButton(
                    onClick = { viewModel.showAddTrackerDialog() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add day counter")
                }
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (!uiState.hasData) {
            // Empty state - no data imported yet
            EmptyStateScreen(modifier = Modifier.padding(paddingValues))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.errorMessage != null) {
                    item {
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (uiState.homeCountryCode == null) {
                    item {
                        HomeCountryPrompt(onSelectHomeCountry)
                    }
                }

                item {
                    DataCoverageSection(uiState.travelStats)
                }

                item {
                    TravelOverviewSection(
                        stats = uiState.travelStats,
                        onOpenCountries = onOpenCountries,
                        onOpenCities = onOpenCities
                    )
                }
                
                item {
                    FlightStatisticsSection(uiState.travelStats)
                }
                
                if (uiState.homeCountryCode != null) {
                    item {
                        HomeCountrySection(
                            daysInside = uiState.travelStats.daysInsideHomeCountry,
                            daysOutside = uiState.travelStats.daysOutsideHomeCountry,
                            homeCountry = uiState.homeCountryName ?: uiState.homeCountryCode!!
                        )
                    }
                }

                item {
                    TravelFactsSection(
                        stats = uiState.travelStats,
                        homeCountryCode = uiState.homeCountryCode,
                        homeCountryName = uiState.homeCountryName
                    )
                }
                
                if (uiState.travelStats.countriesVisitedList.isNotEmpty()) {
                    item {
                        TopCountriesSection(
                            countries = uiState.travelStats.countriesVisitedList,
                            onOpenCountry = onOpenCountry
                        )
                    }
                }
                
                if (uiState.customTrackers.isNotEmpty()) {
                    item {
                        Text(
                            text = "Custom Trackers",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    items(uiState.customTrackers) { result ->
                        CustomTrackerCard(
                            result = result,
                            onEdit = { viewModel.editTracker(result.tracker) },
                            onDelete = { viewModel.requestDeleteTracker(result.tracker) }
                        )
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    if (uiState.showAddTrackerDialog) {
        AddTrackerDialog(
            countries = uiState.countries,
            defaultCountryCode = uiState.homeCountryCode,
            historyStart = uiState.travelStats.earliestRecordDate,
            historyEnd = uiState.travelStats.latestRecordDate,
            existingTracker = uiState.editingTracker,
            onDismiss = viewModel::hideAddTrackerDialog,
            onAdd = viewModel::saveTracker
        )
    }

    uiState.trackerPendingDelete?.let { tracker ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteTracker,
            title = { Text("Delete this counter?") },
            text = {
                Text("\"${tracker.name}\" will be removed. Your Timeline data will not be deleted.")
            },
            confirmButton = {
                Button(onClick = viewModel::confirmDeleteTracker) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteTracker) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyStateScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Icon(
                imageVector = Icons.Default.TravelExplore,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        }
        item {
            Text(
                text = "Welcome to RoamAtlas",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        item {
            Text(
                text = "Your personal travel history companion",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Get Started",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    GetStartedItem("1", "Import your Google Timeline from Settings")
                    GetStartedItem("2", "Or enable GPS tracking to record new locations")
                    GetStartedItem("3", "Add flights manually or import from Gmail")
                }
            }
        }
        item {
            Text(
                text = "Go to Settings to import your data",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun HomeCountryPrompt(onSelectHomeCountry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Choose your home country",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "RoamAtlas needs this to show days at home and days abroad.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(onClick = onSelectHomeCountry) {
                Text("Select home country")
            }
        }
    }
}

@Composable
private fun GetStartedItem(number: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun DataCoverageSection(stats: TravelStatistics) {
    val first = stats.earliestRecordDate
    val last = stats.latestRecordDate
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                "What these numbers cover",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${stats.totalDaysTracked} days have location records",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (first != null && last != null) {
                Text(
                    "From ${formatDashboardDate(first)} to ${formatDashboardDate(last)}"
                )
            }
            Text(
                "The country and city totals below use this same period. Days without a location record are not counted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TravelFactsSection(
    stats: TravelStatistics,
    homeCountryCode: String?,
    homeCountryName: String?
) {
    val first = stats.earliestRecordDate
    val last = stats.latestRecordDate
    val spanYears = if (first != null && last != null) {
        last.year - first.year + 1
    } else 0
    val classifiedHomeDays = stats.daysInsideHomeCountry + stats.daysOutsideHomeCountry
    val abroadPercent = if (classifiedHomeDays > 0) {
        stats.daysOutsideHomeCountry * 100 / classifiedHomeDays
    } else null
    val destinationsBeyondHome = stats.countriesVisitedList.count {
        it.countryCode != homeCountryCode &&
            it.classification != CountryVisitClassification.TRANSIT
    }
    val firstCountry = stats.countriesVisitedList
        .filter { it.firstVisit != null }
        .minByOrNull { it.firstVisit!! }
    val latestCountry = stats.countriesVisitedList
        .filter { it.lastVisit != null }
        .maxByOrNull { it.lastVisit!! }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Interesting facts",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (spanYears > 0) {
                    FactLine("Your records span $spanYears calendar ${if (spanYears == 1) "year" else "years"}.")
                }
                if (homeCountryCode != null) {
                    FactLine(
                        "Beyond ${homeCountryName ?: homeCountryCode}, records appear in " +
                            "$destinationsBeyondHome other ${if (destinationsBeyondHome == 1) "country" else "countries"}."
                    )
                }
                abroadPercent?.let {
                    FactLine(
                        "$it% of days with home/abroad evidence are marked outside " +
                            "${homeCountryName ?: homeCountryCode}."
                    )
                }
                firstCountry?.let {
                    FactLine(
                        "First country record: ${it.countryName} on ${formatDashboardDate(it.firstVisit!!)}."
                    )
                }
                latestCountry?.let {
                    FactLine(
                        "Latest country record: ${it.countryName} on ${formatDashboardDate(it.lastVisit!!)}."
                    )
                }
            }
        }
    }
}

@Composable
private fun FactLine(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("•", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TravelOverviewSection(
    stats: TravelStatistics,
    onOpenCountries: () -> Unit,
    onOpenCities: () -> Unit
) {
    Column {
        Text(
            text = "Travel Overview",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Public,
                value = stats.totalCountriesVisited.toString(),
                label = "Countries",
                color = MaterialTheme.colorScheme.primary,
                onClick = onOpenCountries
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.LocationCity,
                value = stats.totalCitiesVisited.toString(),
                label = "Cities",
                color = MaterialTheme.colorScheme.secondary,
                onClick = onOpenCities
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Schedule,
                value = stats.totalDaysTracked.toString(),
                label = "Days",
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        
        if (stats.mostVisitedCountry != null) {
            Spacer(modifier = Modifier.height(12.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Most Visited",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = stats.mostVisitedCountry,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${stats.mostVisitedCountryDays}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "days",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlightStatisticsSection(stats: TravelStatistics) {
    Column {
        Text(
            text = "Flight Statistics",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FlightStatItem(
                        icon = Icons.Default.Flight,
                        value = stats.totalFlights.toString(),
                        label = "Total Flights"
                    )
                    FlightStatItem(
                        icon = Icons.Default.Route,
                        value = formatDistance(stats.totalFlightDistanceKm),
                        label = "Distance Flown"
                    )
                    FlightStatItem(
                        icon = Icons.Default.Schedule,
                        value = formatDuration(stats.totalFlightDurationMinutes),
                        label = "Time in Air"
                    )
                }
                
                if (stats.longestFlightKm != null && stats.longestFlightRoute != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlightTakeoff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Longest Flight",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = stats.longestFlightRoute,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = formatDistance(stats.longestFlightKm),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlightStatItem(
    icon: ImageVector,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HomeCountrySection(
    daysInside: Int,
    daysOutside: Int,
    homeCountry: String
) {
    Column {
        Text(
            text = "Home vs Abroad",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = daysInside.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Days in $homeCountry",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.TravelExplore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = daysOutside.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Days Abroad",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun TopCountriesSection(
    countries: List<CountryVisitStats>,
    onOpenCountry: (String) -> Unit
) {
    Column {
        Text(
            text = "Top Countries",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(countries.take(10)) { country ->
                CountryChip(country, onClick = { onOpenCountry(country.countryCode) })
            }
        }
    }
}

@Composable
private fun CountryChip(country: CountryVisitStats, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = getFlagEmoji(country.countryCode),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = country.countryName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${country.daysSpent} days",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun CustomTrackerCard(
    result: com.roamatlas.domain.model.TrackerResult,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.tracker.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (result.tracker.countInsideCountry) {
                            "Inside ${result.tracker.countryName}"
                        } else {
                            "Outside ${result.tracker.countryName}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${result.totalDays}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "days",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrackerMetric(
                    value = result.totalDays.toString(),
                    label = if (result.tracker.countInsideCountry) {
                        "inside so far"
                    } else {
                        "outside so far"
                    },
                    modifier = Modifier.weight(1f)
                )
                TrackerMetric(
                    value = result.daysUntilRangeEnd.toString(),
                    label = "days until range ends",
                    modifier = Modifier.weight(1f)
                )
                TrackerMetric(
                    value = when {
                        result.tracker.thresholdDays == null -> "Not set"
                        result.thresholdExceeded == true -> "Reached"
                        else -> result.daysNeededForThreshold.toString()
                    },
                    label = when {
                        result.tracker.thresholdDays == null -> "threshold"
                        result.thresholdExceeded == true -> "threshold"
                        else -> "more days needed"
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            result.tracker.thresholdDays?.let { threshold ->
                Text(
                    text = "Target: $threshold " +
                        if (result.tracker.countInsideCountry) "inside days" else "outside days",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.thresholdExceeded == true) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (result.thresholdExceeded == true) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEdit) {
                    Text("Edit")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun TrackerMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

private fun getFlagEmoji(countryCode: String): String {
    if (countryCode.length != 2) return countryCode
    val firstLetter = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
    val secondLetter = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
}

private fun formatDistance(km: Double): String {
    return when {
        km >= 1000 -> "${NumberFormat.getInstance(Locale.US).format((km / 1000).toInt())}k km"
        else -> "${km.toInt()} km"
    }
}

private fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    return when {
        hours >= 24 -> "${hours / 24}d ${hours % 24}h"
        else -> "${hours}h"
    }
}

private fun formatDashboardDate(date: java.time.LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
