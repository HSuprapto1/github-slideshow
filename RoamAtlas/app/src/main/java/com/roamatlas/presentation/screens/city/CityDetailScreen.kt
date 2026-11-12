package com.roamatlas.presentation.screens.city

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.roamatlas.presentation.util.createPlaceCameraPhotoUri
import java.io.File
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
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
    val context = LocalContext.current
    var showLabelDialog by remember { mutableStateOf(false) }
    var labelInput by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        viewModel.addPhotos(uris.map(Uri::toString))
        Toast.makeText(
            context,
            "Added ${uris.size} photo${if (uris.size == 1) "" else "s"}",
            Toast.LENGTH_SHORT
        ).show()
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) {
            viewModel.addPhotos(listOf(uri.toString()))
            Toast.makeText(context, "Photo saved to this place", Toast.LENGTH_SHORT).show()
        } else if (uri != null) {
            runCatching {
                uri.path?.let { path -> File(path).delete() }
                // FileProvider content URIs: delete via contentResolver when possible
                context.contentResolver.delete(uri, null, null)
            }
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
        if (granted) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.customLabel ?: detail?.city ?: "City History") },
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
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                state.customLabel ?: city.city,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (state.customLabel != null) {
                                Text(
                                    "Detected location name: ${city.city}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                "${flagEmoji(city.countryCode)} ${city.countryName}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            OutlinedButton(
                                onClick = {
                                    labelInput = state.customLabel.orEmpty()
                                    showLabelDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (state.customLabel == null) {
                                        "Label this place"
                                    } else {
                                        "Edit place label"
                                    }
                                )
                            }
                        }
                    }
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
                    Text(
                        "Photos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (state.photoUris.isEmpty()) {
                            "No photos yet. Choose from your gallery or take a new photo for this place."
                        } else {
                            "${state.photoUris.size} photo${if (state.photoUris.size == 1) "" else "s"} linked to this place. " +
                                "Gallery originals stay on your phone; RoamAtlas keeps links, not duplicate full-size files."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (state.photoUris.isNotEmpty()) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(state.photoUris, key = { it }) { uri ->
                                SavedPlacePhoto(
                                    uri = uri,
                                    onOpen = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                            )
                                        }
                                    },
                                    onRemove = { viewModel.removePhoto(uri) }
                                )
                            }
                        }
                    }
                }
                item {
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
                            Text("Choose photos", modifier = Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(
                            onClick = { startTakePhoto() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null)
                            Text("Take photo", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    Text(
                        "Gallery photo links are not copied into encrypted backups. Keep gallery originals. " +
                            "Photos taken here are stored with RoamAtlas on this phone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

    if (showLabelDialog) {
        AlertDialog(
            onDismissRequest = { showLabelDialog = false },
            title = { Text("Label this place") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Give this detected place a name that is meaningful to you, such as Home. " +
                            "The original detected name will remain visible."
                    )
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        label = { Text("Your label") },
                        placeholder = { Text("Example: Home") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveCustomLabel(labelInput)
                        showLabelDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLabelDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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

@Composable
private fun SavedPlacePhoto(
    uri: String,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    Card {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = Uri.parse(uri),
                contentDescription = "Saved place photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .clickable(onClick = onOpen)
            )
            TextButton(onClick = onRemove) {
                Text("Remove")
            }
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
