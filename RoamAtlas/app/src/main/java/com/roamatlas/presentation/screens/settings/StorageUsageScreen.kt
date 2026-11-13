package com.roamatlas.presentation.screens.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageUsageScreen(
    onNavigateBack: () -> Unit,
    viewModel: StorageUsageViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage Usage") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Text("Calculating local storage…", modifier = Modifier.padding(top = 12.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null)
                            Text(
                                formatBytes(state.localDataBytes),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("RoamAtlas local data")
                        }
                    }
                }
                item {
                    StorageBreakdownCard(state)
                }
                item {
                    RecordCountsCard(state)
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("What is stored", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "RoamAtlas stores processed location records locally. " +
                                    "The original Timeline JSON is not copied into the app."
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Use Encrypted backup in Settings before deleting or reinstalling.",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                state.errorMessage?.let { error ->
                    item {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
                item {
                    Button(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth()) {
                        Text("Refresh usage")
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageBreakdownCard(state: StorageUsageUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Storage breakdown", fontWeight = FontWeight.Bold)
            UsageRow("Travel database", formatBytes(state.databaseBytes))
            UsageRow("App files and settings", formatBytes(state.filesBytes))
            UsageRow("Temporary cache", formatBytes(state.cacheBytes))
            UsageRow("Installed application", formatBytes(state.installedAppBytes))
        }
    }
}

@Composable
private fun RecordCountsCard(state: StorageUsageUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Saved records", fontWeight = FontWeight.Bold)
            UsageRow("Location points", "%,d".format(Locale.getDefault(), state.locationCount))
            UsageRow("Flights", "%,d".format(Locale.getDefault(), state.flightCount))
            UsageRow("Day counters", "%,d".format(Locale.getDefault(), state.counterCount))
            UsageRow("Trips", "%,d".format(Locale.getDefault(), state.tripCount))
        }
    }
}

@Composable
private fun UsageRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}
