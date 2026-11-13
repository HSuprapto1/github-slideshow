package com.roamatlas.presentation.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.roamatlas.presentation.util.createPlaceCameraPhotoUri

/**
 * Photo attachment component for adding photos to flights, trips, or locations.
 */
@Composable
fun PhotoAttachmentSection(
    photos: List<String>,
    onAddPhoto: (Uri) -> Unit,
    onRemovePhoto: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Photos",
    maxPhotos: Int = 10,
    enableCamera: Boolean = true,
    onPhotoClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var showSourceDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onAddPhoto(it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) {
            onAddPhoto(uri)
        } else if (uri != null) {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
        pendingCameraUri = null
    }

    fun launchCamera() {
        val uri = createPlaceCameraPhotoUri(context)
        if (uri == null) {
            Toast.makeText(context, "Could not open camera", Toast.LENGTH_SHORT).show()
            return
        }
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(context, "Camera permission needed", Toast.LENGTH_SHORT).show()
    }

    fun startCamera() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${photos.size}/$maxPhotos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            if (photos.size < maxPhotos) {
                item {
                    AddPhotoButton(
                        onClick = {
                            if (enableCamera) showSourceDialog = true
                            else galleryLauncher.launch("image/*")
                        }
                    )
                }
            }

            items(photos) { photoUri ->
                PhotoThumbnail(
                    uri = photoUri,
                    onRemove = { onRemovePhoto(photoUri) },
                    onClick = onPhotoClick?.let { handler -> { handler(photoUri) } }
                )
            }
        }
    }

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("Add photo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showSourceDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Gallery")
                    }
                    TextButton(
                        onClick = {
                            showSourceDialog = false
                            startCamera()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Camera")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSourceDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AddPhotoButton(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .size(100.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.AddAPhoto,
                contentDescription = "Add photo",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Add Photo",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PhotoThumbnail(
    uri: String,
    onRemove: () -> Unit,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(uri)
                .crossfade(true)
                .build(),
            contentDescription = "Photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove photo",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Compact photo display for viewing (non-editable).
 */
@Composable
fun PhotoGalleryPreview(
    photos: List<String>,
    onPhotoClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (photos.isEmpty()) return

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(photos.size) { index ->
            val context = LocalContext.current

            Card(
                modifier = Modifier
                    .size(80.dp)
                    .clickable { onPhotoClick(index) },
                shape = RoundedCornerShape(8.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(photos[index])
                        .crossfade(true)
                        .build(),
                    contentDescription = "Photo ${index + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

/**
 * Full-screen photo viewer.
 */
@Composable
fun PhotoFullScreenViewer(
    photos: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(initialIndex) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss)
        )
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(photos.getOrNull(currentIndex))
                .crossfade(true)
                .build(),
            contentDescription = "Photo",
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clickable(onClick = onDismiss),
            contentScale = ContentScale.Fit
        )

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        if (photos.size > 1) {
            Text(
                text = "${currentIndex + 1} / ${photos.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}
