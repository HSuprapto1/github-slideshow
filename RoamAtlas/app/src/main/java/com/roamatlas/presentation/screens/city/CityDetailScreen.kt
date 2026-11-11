package com.roamatlas.presentation.screens.city

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.roamatlas.domain.repository.CityDayRecord
import com.roamatlas.domain.repository.CityYearRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityDetailScreen(
    onNavigateBack: () -> Unit,
    onOpenMap: (LocalDate, String, String) -> Unit,
    viewModel: CityDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val detail = state.detail

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.city ?: "City History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                }
            }
            state.errorMessage?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            detail?.let { city ->
                item {
                    Text(
                        "${flagEmoji(city.countryCode)} ${city.countryName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCard(formatNumber(city.pointCount), "GPS points", Modifier.weight(1f))
                        MetricCard(formatNumber(city.daysRecorded), "recorded days", Modifier.weight(1f))
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DateMetric("First record", city.firstDate)
                            DateMetric("Latest record", city.lastDate)
                        }
                    }
                }
                item {
                    Text("Years explored", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = state.selectedYear == null,
                                onClick = { viewModel.selectYear(null) },
                                label = { Text("All years") }
                            )
                        }
                        items(state.years, key = { it.year }) { year ->
                            FilterChip(
                                selected = state.selectedYear == year.year,
                                onClick = { viewModel.selectYear(year.year) },
                                label = { Text(year.year.toString()) }
                            )
                        }
                    }
                }
                items(state.years, key = { "year-${it.year}" }) { year ->
                    CityYearCard(year)
                }
                item {
                    Text(
                        state.selectedYear?.let { "Recorded dates in $it" } ?: "Recorded dates",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Tap a date to show this city’s GPS positions and track on the map.")
                }
                items(state.visibleDays, key = { it.date.toEpochDay() }) { day ->
                    CityDayCard(day) {
                        onOpenMap(day.date, city.countryCode, city.city)
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun MetricCard(value: String, label: String, modifier: Modifier) {
    Card(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label)
        }
    }
}

@Composable
private fun DateMetric(label: String, date: LocalDate?) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: "Unknown",
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CityYearCard(year: CityYearRecord) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(year.year.toString(), fontWeight = FontWeight.Bold)
            Text("${formatNumber(year.daysRecorded)} days · ${formatNumber(year.pointCount)} points")
        }
    }
}

@Composable
private fun CityDayCard(day: CityDayRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                    fontWeight = FontWeight.Bold
                )
                Text("${formatNumber(day.pointCount)} GPS points")
            }
            Icon(Icons.Default.Map, contentDescription = "Show on map")
        }
    }
}

private fun formatNumber(value: Int): String =
    String.format(Locale.getDefault(), "%,d", value)

private fun flagEmoji(countryCode: String): String {
    if (countryCode.length != 2) return countryCode
    return countryCode.uppercase().map {
        String(Character.toChars(it.code - 'A'.code + 0x1F1E6))
    }.joinToString("")
}
