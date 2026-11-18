package com.roamatlas.presentation.screens.country

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.roamatlas.domain.model.CountryVisitClassification
import com.roamatlas.domain.model.CountryVisitStats
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountriesScreen(
    onNavigateBack: () -> Unit,
    onOpenCountry: (String) -> Unit,
    viewModel: CountriesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var search by remember { mutableStateOf("") }
    val visibleCountries = remember(search, state.countries) {
        state.countries.filter {
            it.countryName.contains(search, ignoreCase = true) ||
                it.countryCode.contains(search, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Countries Recorded") },
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "${state.countries.size} countries, ranked by recorded days",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (state.earliestDate != null && state.latestDate != null) {
                    Text(
                        "Based on records from ${formatDate(state.earliestDate)} to ${formatDate(state.latestDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Each number means dates with GPS evidence in that country—not the total " +
                        "days outside your home country.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search country") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
            itemsIndexed(
                visibleCountries,
                key = { _, country -> country.countryCode }
            ) { _, country ->
                CountryListCard(
                    rank = state.countries.indexOf(country) + 1,
                    country = country,
                    isHomeCountry = country.countryCode == state.homeCountryCode,
                    onClick = { onOpenCountry(country.countryCode) }
                )
            }
        }
    }
}

@Composable
private fun CountryListCard(
    rank: Int,
    country: CountryVisitStats,
    isHomeCountry: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "#$rank",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp)
            )
            Text(
                flagEmoji(country.countryCode),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(end = 10.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    country.countryName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${formatNumber(country.daysSpent)} days recorded " +
                        (if (isHomeCountry) {
                            "in your home country"
                        } else {
                            "in this country"
                        }) +
                        country.firstVisit?.let { first ->
                            country.lastVisit?.let { last ->
                                " · ${formatDate(first)} to ${formatDate(last)}"
                            }
                        }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall
                )
                if (country.classification != CountryVisitClassification.VISITED) {
                    Text(
                        when (country.classification) {
                            CountryVisitClassification.TRANSIT -> "Likely transit"
                            CountryVisitClassification.UNCERTAIN -> "Needs review"
                            CountryVisitClassification.VISITED -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Open country")
        }
    }
}

private fun formatDate(date: java.time.LocalDate?): String =
    date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        ?: "unknown"

private fun formatNumber(value: Int): String =
    String.format(Locale.getDefault(), "%,d", value)

private fun flagEmoji(countryCode: String): String {
    if (countryCode.length != 2) return countryCode
    return countryCode.uppercase().map {
        String(Character.toChars(it.code - 'A'.code + 0x1F1E6))
    }.joinToString("")
}
