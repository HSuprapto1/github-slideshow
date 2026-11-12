package com.roamatlas.presentation.screens.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.roamatlas.data.photo.CapturedPhotoLocation
import com.roamatlas.presentation.util.createPlaceCameraPhotoUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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
                    Text("Photo to timeline")
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
fun AddPhotoToTimelineDialog(
    isImporting: Boolean,
    lastResultMessage: String?,
    onDismiss: () -> Unit,
    onImportGallery: (List<Uri>) -> Unit,
    onImportCaptured: (Uri, CapturedPhotoLocation) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var waitingForLocation by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris ->
        if (uris.isNotEmpty()) onImportGallery(uris)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (!saved || uri == null) {
            if (uri != null) runCatching { context.contentResolver.delete(uri, null, null) }
            return@rememberLauncherForActivityResult
        }
        waitingForLocation = true
        scope.launch {
            val capture = withContext(Dispatchers.IO) {
                readCurrentLocation(context)
            }
            waitingForLocation = false
            if (capture == null) {
                Toast.makeText(
                    context,
                    "Could not read GPS for this photo. Enable location and try again, or load a geotagged gallery photo.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            onImportCaptured(uri, capture)
        }
    }

    fun launchCamera() {
        val uri = createPlaceCameraPhotoUri(context)
        if (uri == null) {
            Toast.makeText(
                context,
                "Could not open the camera. Try Load photos instead.",
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
        if (granted) launchCamera()
        else Toast.makeText(context, "Camera permission is needed to take a photo.", Toast.LENGTH_LONG).show()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val ok = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) {
            val camGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (camGranted) launchCamera()
            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            Toast.makeText(
                context,
                "Location permission is needed so a taken photo can become a timeline point.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun startTakePhoto() {
        val locationGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!locationGranted) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        val camGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (camGranted) launchCamera()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    AlertDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        title = { Text("Photo to timeline") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Load geotagged photos or take a new picture. RoamAtlas reads the GPS " +
                        "position and adds a timeline point with a thumbnail, or links the photo " +
                        "if a nearby point already exists.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (isImporting || waitingForLocation) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            if (waitingForLocation) "Reading GPS…" else "Importing photos…"
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                lastResultMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isImporting && !waitingForLocation
            ) { Text("Done") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isImporting && !waitingForLocation
            ) { Text("Cancel") }
        }
    )
}

@SuppressLint("MissingPermission")
private suspend fun readCurrentLocation(context: android.content.Context): CapturedPhotoLocation? {
    return runCatching {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cancellation = CancellationTokenSource()
        val current: Location? = try {
            client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellation.token
            ).await()
        } catch (_: Exception) {
            null
        }
        val location = current ?: client.lastLocation.await()
        location?.let {
            CapturedPhotoLocation(
                latitude = it.latitude,
                longitude = it.longitude,
                accuracyMeters = it.accuracy.takeIf { meters -> meters > 0f },
                altitudeMeters = if (it.hasAltitude()) it.altitude else null,
                timestampMillis = it.time.takeIf { time -> time > 0L }
                    ?: System.currentTimeMillis()
            )
        }
    }.getOrNull()
}
