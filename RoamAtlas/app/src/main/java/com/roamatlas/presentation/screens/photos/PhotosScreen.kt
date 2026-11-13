package com.roamatlas.presentation.screens.photos

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.roamatlas.domain.model.LocationRecord
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    onOpenMap: (LocalDate) -> Unit,
    viewModel: PhotosViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmGalleryDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photos") },
                navigationIcon = {
                    if (state.selected != null) {
                        IconButton(onClick = { viewModel.select(null) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    state.selected?.let {
                        IconButton(
                            onClick = {
                                viewModel.shareSelected()?.let { intent ->
                                    context.startActivity(
                                        Intent.createChooser(intent, "Share photo")
                                    )
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(
                            onClick = {
                                onOpenMap(
                                    it.timestamp.atZone(ZoneId.systemDefault()).toLocalDate()
                                )
                            }
                        ) {
                            Icon(Icons.Default.Map, contentDescription = "Show on map")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            state.selected != null -> {
                PhotoDetail(
                    photo = state.selected!!,
                    message = state.message,
                    modifier = Modifier.padding(padding),
                    onShowMap = {
                        onOpenMap(
                            state.selected!!.timestamp
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        )
                    },
                    onShare = {
                        viewModel.shareSelected()?.let { intent ->
                            context.startActivity(Intent.createChooser(intent, "Share photo"))
                        }
                    },
                    onDelete = { confirmDelete = true }
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Text(
                        "${state.photos.size} photo point${if (state.photos.size == 1) "" else "s"}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (state.photos.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No photos yet. Use Dashboard + → Photo to timeline.",
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(120.dp),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.photos, key = { it.id }) { photo ->
                                AsyncImage(
                                    model = photo.photoUri?.let(Uri::parse),
                                    contentDescription = "Photo thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { viewModel.select(photo) }
                                )
                            }
                        }
                    }
                    state.message?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    state.errorMessage?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete photo from RoamAtlas?") },
            text = {
                Text(
                    "This removes the timeline point and thumbnail from RoamAtlas. " +
                        "Your Gallery original stays unless you choose to delete it next."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        confirmGalleryDelete = true
                    }
                ) { Text("Delete from RoamAtlas") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmGalleryDelete) {
        AlertDialog(
            onDismissRequest = {
                confirmGalleryDelete = false
                viewModel.deleteSelected(alsoDeleteFromGallery = false)
            },
            title = { Text("Also delete from Gallery?") },
            text = {
                Text(
                    "Do you also want to permanently delete the original photo from your Gallery? " +
                        "This cannot be undone. Some cloud or shared photos cannot be deleted by RoamAtlas."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmGalleryDelete = false
                        viewModel.deleteSelected(alsoDeleteFromGallery = true)
                    }
                ) { Text("Delete Gallery original too") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmGalleryDelete = false
                        viewModel.deleteSelected(alsoDeleteFromGallery = false)
                    }
                ) { Text("Keep Gallery original") }
            }
        )
    }
}

@Composable
private fun PhotoDetail(
    photo: LocationRecord,
    message: String?,
    modifier: Modifier = Modifier,
    onShowMap: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = photo.photoUri?.let(Uri::parse),
            contentDescription = "Photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
        )
        Text(
            listOfNotNull(photo.city, photo.countryName).joinToString(", ")
                .ifBlank { "Unknown place" },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withZone(ZoneId.systemDefault())
                .format(photo.timestamp)
        )
        Text("%.5f, %.5f".format(photo.latitude, photo.longitude))
        photo.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onShowMap, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Map, contentDescription = null)
                Spacer(modifier = Modifier.padding(4.dp))
                Text("Map")
            }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.padding(4.dp))
                Text("Share")
            }
        }
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(modifier = Modifier.padding(4.dp))
            Text("Delete")
        }
    }
}
