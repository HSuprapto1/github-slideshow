package com.hardian.phonetracker.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.hardian.phonetracker.data.LiveLocation
import com.hardian.phonetracker.data.TrackedContact
import com.hardian.phonetracker.data.UserProfile
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.DateFormat
import java.util.Date

@Composable
fun MapScreen(
    profile: UserProfile,
    liveLocation: LiveLocation?,
    contacts: List<TrackedContact>,
    sharingEnabled: Boolean,
    onStartSharing: () -> Unit,
    onStopSharing: () -> Unit,
) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(hasFineLocation(context))
    }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasLocationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            val permissions = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(16.0)
                    controller.setCenter(GeoPoint(-6.2088, 106.8456))
                    mapView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.overlays.removeAll { it is Marker }
                liveLocation?.let { loc ->
                    val point = GeoPoint(loc.latitude, loc.longitude)
                    val me = Marker(map).apply {
                        position = point
                        title = profile.displayName.ifBlank { "You" }
                        snippet = profile.e164
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    map.overlays.add(me)
                    map.controller.animateTo(point)
                }
                contacts.forEach { contact ->
                    val lat = contact.lastLatitude ?: return@forEach
                    val lon = contact.lastLongitude ?: return@forEach
                    val marker = Marker(map).apply {
                        position = GeoPoint(lat, lon)
                        title = contact.name
                        snippet = contact.e164
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    map.overlays.add(marker)
                }
                map.invalidate()
            },
        )

        DisposableEffect(Unit) {
            onDispose {
                mapView?.onDetach()
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = profile.displayName.ifBlank { "You" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = profile.e164,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (liveLocation != null) {
                    Text(
                        text = "Live: ${"%.5f".format(liveLocation.latitude)}, ${"%.5f".format(liveLocation.longitude)} (±${liveLocation.accuracyMeters.toInt()} m)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Updated ${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(liveLocation.updatedAtMs))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                } else {
                    Text(
                        text = if (hasLocationPermission) {
                            "Waiting for GPS… turn on Live share below."
                        } else {
                            "Location permission needed for real-time tracking."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (sharingEnabled) {
                    OutlinedButton(
                        onClick = onStopSharing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Stop live share")
                    }
                } else {
                    Button(
                        onClick = {
                            if (!hasLocationPermission) {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            } else {
                                onStartSharing()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Start live share (this phone)")
                    }
                }
            }
        }
    }
}

private fun hasFineLocation(context: android.content.Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}
