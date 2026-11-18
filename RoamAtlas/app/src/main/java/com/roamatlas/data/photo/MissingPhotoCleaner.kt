package com.roamatlas.data.photo

import android.content.Context
import android.net.Uri
import com.roamatlas.data.local.dao.FlightDao
import com.roamatlas.data.local.dao.LocationDao
import com.roamatlas.data.local.dao.TripDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class MissingPhotoCleanupResult(
    val locationPhotosCleared: Int = 0,
    val journeyAlbumPhotosCleared: Int = 0,
    val journeyCoversCleared: Int = 0,
    val flightTicketsCleared: Int = 0
) {
    val totalCleared: Int
        get() = locationPhotosCleared + journeyAlbumPhotosCleared +
            journeyCoversCleared + flightTicketsCleared

    val isEmpty: Boolean get() = totalCleared == 0

    val summary: String
        get() = buildList {
            if (locationPhotosCleared > 0) {
                add(
                    "$locationPhotosCleared timeline photo${if (locationPhotosCleared == 1) "" else "s"}"
                )
            }
            if (journeyAlbumPhotosCleared > 0 || journeyCoversCleared > 0) {
                val n = journeyAlbumPhotosCleared + journeyCoversCleared
                add("$n journey photo${if (n == 1) "" else "s"}")
            }
            if (flightTicketsCleared > 0) {
                add(
                    "$flightTicketsCleared ticket attachment${if (flightTicketsCleared == 1) "" else "s"}"
                )
            }
        }.joinToString(", ").ifBlank { "No missing photos" }
}

/**
 * Clears photo/ticket URI links when the file is gone, while keeping GPS / history points.
 */
@Singleton
class MissingPhotoCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationDao: LocationDao,
    private val tripDao: TripDao,
    private val flightDao: FlightDao
) {
    private val mutex = Mutex()

    suspend fun cleanup(): MissingPhotoCleanupResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            var locationsCleared = 0
            var albumCleared = 0
            var coversCleared = 0
            var ticketsCleared = 0
            val now = System.currentTimeMillis()

            locationDao.getLocationsWithAnyPhoto().forEach { location ->
                val photoOk = isReadable(location.photoUri)
                val originalOk = isReadable(location.originalPhotoUri)
                val nextPhoto = location.photoUri.takeIf { photoOk }
                val nextOriginal = location.originalPhotoUri.takeIf { originalOk }
                if (nextPhoto != location.photoUri || nextOriginal != location.originalPhotoUri) {
                    locationDao.update(
                        location.copy(
                            photoUri = nextPhoto,
                            originalPhotoUri = nextOriginal,
                            updatedAtMillis = now
                        )
                    )
                    if (location.photoUri != null && nextPhoto == null) {
                        locationsCleared++
                    }
                }
            }

            tripDao.getAllSnapshot().forEach { trip ->
                val photos = decodePhotoUris(trip.photoUrisJson)
                val kept = photos.filter { isReadable(it) }
                val coverOk = isReadable(trip.coverPhotoUri)
                val nextCover = when {
                    coverOk -> trip.coverPhotoUri
                    kept.isNotEmpty() -> kept.first()
                    else -> null
                }
                val albumRemoved = photos.size - kept.size
                val coverRemoved =
                    if (!coverOk && !trip.coverPhotoUri.isNullOrBlank()) 1 else 0
                if (albumRemoved > 0 ||
                    coverRemoved > 0 ||
                    nextCover != trip.coverPhotoUri ||
                    kept.size != photos.size
                ) {
                    tripDao.update(
                        trip.copy(
                            photoUrisJson = encodePhotoUris(kept),
                            coverPhotoUri = nextCover,
                            updatedAtMillis = now
                        )
                    )
                    albumCleared += albumRemoved
                    coversCleared += coverRemoved
                }
            }

            flightDao.getAllFlights().first().forEach { flight ->
                if (!flight.photoUri.isNullOrBlank() && !isReadable(flight.photoUri)) {
                    flightDao.update(
                        flight.copy(
                            photoUri = null,
                            updatedAtMillis = now
                        )
                    )
                    ticketsCleared++
                }
            }

            MissingPhotoCleanupResult(
                locationPhotosCleared = locationsCleared,
                journeyAlbumPhotosCleared = albumCleared,
                journeyCoversCleared = coversCleared,
                flightTicketsCleared = ticketsCleared
            )
        }
    }

    fun isReadable(uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        return runCatching {
            val uri = Uri.parse(uriString)
            when (uri.scheme?.lowercase()) {
                "file" -> {
                    val path = uri.path ?: return false
                    val file = File(path)
                    file.exists() && file.canRead() && file.length() > 0L
                }
                "content" -> {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.read() >= 0
                    } == true
                }
                else -> false
            }
        }.getOrDefault(false)
    }

    private fun decodePhotoUris(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodePhotoUris(uris: List<String>): String? {
        val unique = uris.filter { it.isNotBlank() }.distinct()
        if (unique.isEmpty()) return null
        return JSONArray(unique).toString()
    }
}
