package com.roamatlas.presentation.screens.city

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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.roamatlas.data.repository.SavedPlaceRepository
import com.roamatlas.domain.repository.CityRecord
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitiesScreen(
    onNavigateBack: () -> Unit,
    onOpenCity: (CityRecord) -> Unit,
    viewModel: CitiesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshLabels()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var search by remember { mutableStateOf("") }
    val visibleCities = remember(search, state.cities, state.customLabels) {
        state.cities.filter {
            val customLabel = state.customLabels[
                SavedPlaceRepository.placeId(it.countryCode, it.city)
            ].orEmpty()
            it.city.contains(search, true) ||
                customLabel.contains(search, true) ||
                it.countryName.contains(search, true) ||
                it.countryCode.contains(search, true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cities Explored") },
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
                    "${state.cities.size} cities, ranked by recorded days",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search city or country") },
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
                visibleCities,
                key = { _, city -> "${city.countryCode}:${city.city}" }
            ) { index, city ->
                CityListCard(
                    rank = state.cities.indexOf(city) + 1,
                    city = city,
                    customLabel = state.customLabels[
                        SavedPlaceRepository.placeId(city.countryCode, city.city)
                    ],
                    onClick = { onOpenCity(city) }
                )
            }
        }
    }
}

@Composable
private fun CityListCard(
    rank: Int,
    city: CityRecord,
    customLabel: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    customLabel ?: city.city,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (customLabel != null) {
                    Text(
                        "Detected location: ${city.city}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text("${flagEmoji(city.countryCode)} ${city.countryName}")
                Text(
                    "${formatNumber(city.daysRecorded)} days · ${formatNumber(city.pointCount)} GPS points",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Open city")
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
