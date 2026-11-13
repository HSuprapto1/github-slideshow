package com.roamatlas.presentation.screens.photos

import android.location.Geocoder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
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
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.roamatlas.data.photo.CapturedPhotoLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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
    var selected by remember {
        mutableStateOf(
            if (initialLatitude != null && initialLongitude != null) {
                LatLng(initialLatitude, initialLongitude)
            } else {
                LatLng(-6.2, 106.8)
            }
        )
    }
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selected, 14f)
    }

    LaunchedEffect(selected) {
        cameraState.animate(CameraUpdateFactory.newLatLngZoom(selected, 15f))
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Search an address or tap the map to place the photo.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search address") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searching) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    }
                }
            )
            Button(
                onClick = {
                    if (query.isBlank()) return@Button
                    searching = true
                    scope.launch {
                        suggestions = withContext(Dispatchers.IO) {
                            searchAddresses(context, query)
                        }
                        searching = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = query.isNotBlank() && !searching
            ) {
                Text("Search")
            }

            if (suggestions.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
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
                                    }
                                    .padding(12.dp)
                            ) {
                                Text(item.title, fontWeight = FontWeight.SemiBold)
                                Text(item.subtitle, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraState,
                    onMapClick = {
                        selected = it
                        selectedLabel = null
                    }
                ) {
                    Marker(
                        state = MarkerState(position = selected),
                        title = selectedLabel ?: "Photo location"
                    )
                }
            }

            Text(
                selectedLabel
                    ?: "Lat %.5f, Lon %.5f".format(selected.latitude, selected.longitude),
                style = MaterialTheme.typography.bodyMedium
            )
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Use this location")
            }
        }
    }
}

private fun searchAddresses(
    context: android.content.Context,
    query: String
): List<AddressSuggestion> {
    if (!Geocoder.isPresent()) return emptyList()
    return runCatching {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val results = geocoder.getFromLocationName(query, 8).orEmpty()
        results.mapNotNull { address ->
            val lat = address.latitude
            val lon = address.longitude
            val title = address.featureName
                ?: address.thoroughfare
                ?: address.locality
                ?: query
            val subtitle = listOfNotNull(
                address.thoroughfare,
                address.subLocality,
                address.locality,
                address.adminArea,
                address.countryName
            ).distinct().joinToString(", ")
            AddressSuggestion(title, subtitle.ifBlank { "$lat, $lon" }, lat, lon)
        }
    }.getOrDefault(emptyList())
}
