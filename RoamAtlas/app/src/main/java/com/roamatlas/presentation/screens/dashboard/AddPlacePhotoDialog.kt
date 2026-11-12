package com.roamatlas.presentation.screens.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.roamatlas.data.repository.SavedPlaceRepository
import com.roamatlas.domain.repository.CityRecord
import com.roamatlas.presentation.util.createPlaceCameraPhotoUri

@Composable
fun DashboardAddMenuDialog(
    onDismiss: () -> Unit,
    onAddDayCounter: () -> Unit,
    onAddPlacePhoto: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to RoamAtlas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What would you like to add?")
                Button(
                    onClick = {
                        onDismiss()
                        onAddDayCounter()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Day counter")
                }
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onAddPlacePhoto()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Place photo (take or load)")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddPlacePhotoDialog(
    cities: List<CityRecord>,
    labels: Map<String, String>,
    photoCounts: Map<String, Int>,
    onDismiss: () -> Unit,
    onAddPhotos: (countryCode: String, city: String, uris: List<String>) -> Unit
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<CityRecord?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val matches = remember(search, cities, labels) {
        cities.filter { city ->
            val label = labels[SavedPlaceRepository.placeId(city.countryCode, city.city)].orEmpty()
            search.isBlank() ||
                city.city.contains(search, true) ||
                label.contains(search, true) ||
                city.countryName.contains(search, true) ||
                city.countryCode.contains(search, true)
        }.take(12)
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris ->
        val place = selected ?: return@rememberLauncherForActivityResult
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        onAddPhotos(place.countryCode, place.city, uris.map(Uri::toString))
        Toast.makeText(
            context,
            "Added ${uris.size} photo${if (uris.size == 1) "" else "s"}",
            Toast.LENGTH_SHORT
        ).show()
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val place = selected
        val uri = pendingCameraUri
        if (saved && uri != null && place != null) {
            onAddPhotos(place.countryCode, place.city, listOf(uri.toString()))
            Toast.makeText(context, "Photo saved to this place", Toast.LENGTH_SHORT).show()
        } else if (uri != null) {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
        pendingCameraUri = null
    }

    fun launchCamera() {
        val uri = createPlaceCameraPhotoUri(context)
        if (uri == null) {
            Toast.makeText(
                context,
                "Could not open the camera. Try Choose photos instead.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(
                context,
                "Camera permission is needed to take a photo.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun startTakePhoto() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add place photo") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "Choose a place, then load photos from your gallery or take a new one.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Search place") },
                        placeholder = { Text("Example: UTAN or Home") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (selected != null) {
                    item {
                        val place = selected!!
                        val placeId = SavedPlaceRepository.placeId(place.countryCode, place.city)
                        val label = labels[placeId]
                        val count = photoCounts[placeId] ?: 0
                        Text(
                            "Selected: ${label ?: place.city}",
                            fontWeight = FontWeight.Bold
                        )
                        if (label != null) {
                            Text(
                                "Detected location: ${place.city}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text(
                            if (count > 0) {
                                "$count photo${if (count == 1) "" else "s"} already attached"
                            } else {
                                "No photos yet for this place"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                Text("Load", modifier = Modifier.padding(start = 6.dp))
                            }
                            OutlinedButton(
                                onClick = { startTakePhoto() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null)
                                Text("Take", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
                items(
                    matches,
                    key = { "${it.countryCode}:${it.city}" }
                ) { city ->
                    val placeId = SavedPlaceRepository.placeId(city.countryCode, city.city)
                    val label = labels[placeId]
                    val count = photoCounts[placeId] ?: 0
                    val isSelected = selected?.city == city.city &&
                        selected?.countryCode == city.countryCode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = city }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                label ?: city.city,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                buildString {
                                    append(city.countryName)
                                    if (label != null) append(" · detected as ${city.city}")
                                    if (count > 0) append(" · $count photos")
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (isSelected) {
                            Text("Selected", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (matches.isEmpty()) {
                    item {
                        Text(
                            "No matching places in your imported history.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
