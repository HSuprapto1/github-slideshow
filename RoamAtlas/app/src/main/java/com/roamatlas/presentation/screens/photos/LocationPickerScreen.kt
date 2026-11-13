package com.roamatlas.presentation.screens.photos

import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.roamatlas.data.photo.CapturedPhotoLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class AddressSuggestion(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    initialLatitude: Double? = null,
    initialLongitude: Double? = null,
    onNavigateBack: () -> Unit,
    onLocationChosen: (CapturedPhotoLocation) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<AddressSuggestion>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    val start = remember(initialLatitude, initialLongitude) {
        if (initialLatitude != null && initialLongitude != null) {
            LatLng(initialLatitude, initialLongitude)
        } else {
            LatLng(-6.2088, 106.8456) // Jakarta default
        }
    }
    var selected by remember { mutableStateOf(start) }
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    val markerState = remember { MarkerState(position = start) }
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(start, 14f)
    }

    LaunchedEffect(selected, mapReady) {
        markerState.position = selected
        if (mapReady) {
            runCatching {
                cameraState.animate(CameraUpdateFactory.newLatLngZoom(selected, 15f))
            }.onFailure {
                Log.w("LocationPicker", "Camera animate failed", it)
            }
        }
    }

    fun runSearch() {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || searching) return
        searching = true
        searchError = null
        suggestions = emptyList()
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { searchAddresses(context, trimmed) }
            }
            searching = false
            result.onSuccess { found ->
                suggestions = found
                if (found.isEmpty()) {
                    searchError = "No places found. Try another address, or tap the map."
                }
            }.onFailure {
                Log.e("LocationPicker", "Geocoder search failed", it)
                searchError = "Address search failed. You can still tap the map to place the photo."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pick location") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraState,
                properties = MapProperties(isMyLocationEnabled = false),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    compassEnabled = true,
                    myLocationButtonEnabled = false
                ),
                onMapLoaded = { mapReady = true },
                onMapClick = {
                    selected = it
                    selectedLabel = null
                    suggestions = emptyList()
                    searchError = null
                }
            ) {
                Marker(
                    state = markerState,
                    title = selectedLabel ?: "Photo location"
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Search an address or tap the map.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = {
                                    query = it
                                    searchError = null
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Search address") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (searching) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.padding(10.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            )
                            Button(
                                onClick = { runSearch() },
                                enabled = query.isNotBlank() && !searching
                            ) {
                                Text("Go")
                            }
                        }
                        searchError?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (suggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        LazyColumn {
                            items(suggestions) { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selected = LatLng(item.latitude, item.longitude)
                                            selectedLabel = item.title
                                            suggestions = emptyList()
                                            query = item.title
                                            searchError = null
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Text(item.title, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        item.subtitle,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        selectedLabel
                            ?: "Lat %.5f, Lon %.5f".format(selected.latitude, selected.longitude),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Drag the map view with gestures, tap to move the pin, then confirm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                onLocationChosen(
                                    CapturedPhotoLocation(
                                        latitude = selected.latitude,
                                        longitude = selected.longitude,
                                        label = selectedLabel ?: "Map pin"
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Use this location")
                        }
                    }
                }
            }
        }
    }
}

private suspend fun searchAddresses(
    context: android.content.Context,
    query: String
): List<AddressSuggestion> {
    if (!Geocoder.isPresent()) return emptyList()
    val geocoder = Geocoder(context, Locale.getDefault())
    val results = if (Build.VERSION.SDK_INT >= 33) {
        suspendCancellableCoroutine { cont ->
            runCatching {
                geocoder.getFromLocationName(query, 8) { addresses ->
                    if (cont.isActive) cont.resume(addresses)
                }
            }.onFailure { error ->
                if (cont.isActive) cont.resume(emptyList())
                Log.e("LocationPicker", "Async geocode failed", error)
            }
        }
    } else {
        withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            runCatching { geocoder.getFromLocationName(query, 8).orEmpty() }
                .getOrDefault(emptyList())
        }
    }
    return results.mapNotNull { it.toSuggestion(query) }
}

private fun Address.toSuggestion(fallbackQuery: String): AddressSuggestion? {
    if (!hasLatitude() || !hasLongitude()) return null
    val lat = latitude
    val lon = longitude
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    if (lat == 0.0 && lon == 0.0) return null
    val title = featureName
        ?.takeIf { it.isNotBlank() && !it.equals(thoroughfare, true) }
        ?: thoroughfare
        ?: locality
        ?: subAdminArea
        ?: fallbackQuery
    val subtitle = listOfNotNull(
        thoroughfare,
        subLocality,
        locality,
        adminArea,
        countryName
    ).distinct().joinToString(", ")
    return AddressSuggestion(
        title = title,
        subtitle = subtitle.ifBlank { "%.5f, %.5f".format(lat, lon) },
        latitude = lat,
        longitude = lon
    )
}
