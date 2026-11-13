package com.roamatlas.presentation.screens.photos

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.photo.PhotoTimelineImporter
import com.roamatlas.domain.model.LocationRecord
import com.roamatlas.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

data class PhotosUiState(
    val photos: List<LocationRecord> = emptyList(),
    val selected: LocationRecord? = null,
    val isLoading: Boolean = true,
    val message: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class PhotosViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationRepository: LocationRepository,
    private val photoTimelineImporter: PhotoTimelineImporter
) : ViewModel() {
    private val _uiState = MutableStateFlow(PhotosUiState())
    val uiState: StateFlow<PhotosUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                locationRepository.getLocationsWithPhotos(limit = 1_000).first()
            }.onSuccess { photos ->
                _uiState.value = _uiState.value.copy(
                    photos = photos.sortedByDescending { it.timestamp },
                    isLoading = false,
                    selected = _uiState.value.selected?.let { selected ->
                        photos.firstOrNull { it.id == selected.id }
                    }
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = it.message ?: "Could not load photos."
                )
            }
        }
    }

    fun select(photo: LocationRecord?) {
        _uiState.value = _uiState.value.copy(selected = photo, message = null)
    }

    fun shareSelected(): Intent? {
        val photo = _uiState.value.selected ?: return null
        val shareUri = shareableUri(photo) ?: return null
        val whenText = formatInstant(photo.timestamp)
        val where = listOfNotNull(photo.city, photo.countryName).joinToString(", ")
        val text = buildString {
            append("RoamAtlas photo")
            if (where.isNotBlank()) append(" · ").append(where)
            append(" · ").append(whenText)
            append("\n")
            append("%.5f, %.5f".format(photo.latitude, photo.longitude))
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun deleteSelected(alsoDeleteFromGallery: Boolean) {
        val photo = _uiState.value.selected ?: return
        viewModelScope.launch {
            val message = photoTimelineImporter.deletePhotoPoint(
                locationId = photo.id,
                alsoDeleteFromGallery = alsoDeleteFromGallery
            )
            _uiState.value = _uiState.value.copy(selected = null, message = message)
            refresh()
        }
    }

    private fun shareableUri(photo: LocationRecord): Uri? {
        val photoUri = photo.photoUri?.let(Uri::parse) ?: return null
        return runCatching {
            when (photoUri.scheme) {
                "file" -> {
                    val file = File(photoUri.path ?: return null)
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
                "content" -> photoUri
                else -> {
                    // Copy into cache for sharing if needed.
                    val cacheDir = File(context.cacheDir, "shared_photos").apply { mkdirs() }
                    val out = File(cacheDir, "share_${photo.id}.jpg")
                    context.contentResolver.openInputStream(photoUri)?.use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        out
                    )
                }
            }
        }.getOrNull()
    }

    private fun formatInstant(instant: Instant): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
            .format(instant)
}
