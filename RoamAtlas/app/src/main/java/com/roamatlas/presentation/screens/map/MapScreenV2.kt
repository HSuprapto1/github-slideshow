package com.roamatlas.presentation.screens.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.roamatlas.domain.model.LocationRecord
import com.roamatlas.domain.model.LocationSource
import com.roamatlas.presentation.theme.DemoData
import com.roamatlas.presentation.theme.UncertainLocation
import com.roamatlas.presentation.theme.VerifiedLocation
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreenV2(
    initialDate: LocalDate? = null,
    initialCountryCode: String? = null,
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var mapType by remember { mutableStateOf(MapType.NORMAL) }

    LaunchedEffect(initialDate, initialCountryCode) {
        initialDate?.let { viewModel.showLocalDate(it, initialCountryCode) }
    }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(uiState.mapCenterLat, uiState.mapCenterLng),
            uiState.mapZoom
        )
    }
    
    LaunchedEffect(uiState.mapCenterLat, uiState.mapCenterLng, uiState.mapZoom) {
        if (uiState.locations.isNotEmpty()) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(uiState.mapCenterLat, uiState.mapCenterLng),
                    uiState.mapZoom
                )
            )
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            initialDate?.let {
                                if (initialCountryCode == null) {
                                    "Track · $it"
                                } else {
                                    "$initialCountryCode track · $it"
                                }
                            } ?: "World Map"
                        )
                        Text(
                            text = "${uiState.totalLocations} locations",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFilters() }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter"
                        )
                    }
                    IconButton(onClick = {
                        mapType = when (mapType) {
                            MapType.NORMAL -> MapType.SATELLITE
                            MapType.SATELLITE -> MapType.HYBRID
                            MapType.HYBRID -> MapType.TERRAIN
                            else -> MapType.NORMAL
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Map Type"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.locations.isEmpty()) {
                EmptyMapView()
            } else {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        mapType = mapType,
                        isMyLocationEnabled = false
                    ),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = true,
                        myLocationButtonEnabled = false,
                        mapToolbarEnabled = true
                    ),
                    onMapClick = { viewModel.selectLocation(null) }
                ) {
                    val displayLocations = if (uiState.locations.size > 1000) {
                        uiState.locations.filterIndexed { index, _ -> index % (uiState.locations.size / 1000) == 0 }
                    } else {
                        uiState.locations
                    }

                    if (displayLocations.size >= 2) {
                        Polyline(
                            points = displayLocations.map {
                                LatLng(it.latitude, it.longitude)
                            },
                            color = Color(0xFF1565C0),
                            width = 7f
                        )
                    }
                    
                    displayLocations.forEach { location ->
                        val markerColor = when (location.source) {
                            LocationSource.GPS_TRACKING -> BitmapDescriptorFactory.HUE_BLUE
                            LocationSource.GOOGLE_TIMELINE -> BitmapDescriptorFactory.HUE_GREEN
                            LocationSource.MANUAL_ENTRY -> BitmapDescriptorFactory.HUE_ORANGE
                        }
                        
                        Marker(
                            state = MarkerState(position = LatLng(location.latitude, location.longitude)),
                            title = location.city ?: formatTimestamp(location.timestamp),
                            snippet = "${location.source.name} - ${formatDate(location.timestamp)}",
                            icon = BitmapDescriptorFactory.defaultMarker(markerColor),
                            onClick = {
                                viewModel.selectLocation(location)
                                true
                            }
                        )
                    }
                }
            }
            
            AnimatedVisibility(
                visible = uiState.showFilters,
                enter = slideInVertically(),
                exit = slideOutVertically(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                FilterBar(
                    countryCodes = uiState.countryCodes,
                    selectedCountry = uiState.filterCountryCode,
                    onCountrySelected = { viewModel.setCountryFilter(it) },
                    onClearFilters = { viewModel.clearFilters() }
                )
            }
            
            AnimatedVisibility(
                visible = uiState.selectedLocation != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                uiState.selectedLocation?.let { location ->
                    LocationDetailCard(
                        location = location,
                        onClose = { viewModel.selectLocation(null) }
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .padding(bottom = if (uiState.selectedLocation != null) 160.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        viewModel.centerOnLocation(
                            uiState.mapCenterLat,
                            uiState.mapCenterLng,
                            uiState.mapZoom
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = "Center on locations"
                    )
                }
            }
            
            if (uiState.locations.isNotEmpty()) {
                MapLegend(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyMapView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Locations Yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Import your Google Timeline or enable GPS tracking to see your travel history on the map.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    countryCodes: List<String>,
    selectedCountry: String?,
    onCountrySelected: (String?) -> Unit,
    onClearFilters: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter by Country",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (selectedCountry != null) {
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onClearFilters() }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(countryCodes) { code ->
                    FilterChip(
                        selected = code == selectedCountry,
                        onClick = {
                            onCountrySelected(if (code == selectedCountry) null else code)
                        },
                        label = {
                            Text("${getFlagEmoji(code)} $code")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationDetailCard(
    location: LocationRecord,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
                    if (location.city != null) {
                        Text(
                            text = location.city,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (location.countryName != null) {
                        Text(
                            text = location.countryName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DetailItem(
                    label = "Date",
                    value = formatDate(location.timestamp)
                )
                DetailItem(
                    label = "Time",
                    value = formatTime(location.timestamp)
                )
                DetailItem(
                    label = "Source",
                    value = location.source.name.replace("_", " ")
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DetailItem(
                    label = "Latitude",
                    value = String.format("%.6f", location.latitude)
                )
                DetailItem(
                    label = "Longitude",
                    value = String.format("%.6f", location.longitude)
                )
                location.accuracy?.let {
                    DetailItem(
                        label = "Accuracy",
                        value = "${it.toInt()}m"
                    )
                }
            }
            
            location.notes?.let { notes ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun DetailItem(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MapLegend(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LegendItem(color = Color(0xFF4285F4), label = "GPS")
            LegendItem(color = Color(0xFF34A853), label = "Timeline")
            LegendItem(color = Color(0xFFFBBC05), label = "Manual")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun getFlagEmoji(countryCode: String): String {
    if (countryCode.length != 2) return countryCode
    val firstLetter = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
    val secondLetter = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
}

private fun formatTimestamp(instant: java.time.Instant): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}

private fun formatDate(instant: java.time.Instant): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}

private fun formatTime(instant: java.time.Instant): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}
