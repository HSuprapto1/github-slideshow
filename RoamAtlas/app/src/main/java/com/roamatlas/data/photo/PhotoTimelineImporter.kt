package com.roamatlas.data.photo

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.roamatlas.data.geo.OfflineGeoResolver
import com.roamatlas.data.local.dao.LocationDao
import com.roamatlas.data.local.entity.LocationEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos

data class PhotoImportResult(
    val added: Int = 0,
    val attachedToExisting: Int = 0,
    val skippedDuplicate: Int = 0,
    val skippedNoGps: Int = 0,
    val failed: Int = 0,
    val pendingNoGpsUris: List<Uri> = emptyList()
) {
    fun summaryMessage(): String {
        val parts = buildList {
            if (added > 0) add("added $added new timeline point${if (added == 1) "" else "s"}")
            if (attachedToExisting > 0) {
                add("linked $attachedToExisting photo${if (attachedToExisting == 1) "" else "s"} to existing points")
            }
            if (skippedDuplicate > 0) add("skipped $skippedDuplicate already imported")
            if (skippedNoGps > 0) {
                add("$skippedNoGps photo${if (skippedNoGps == 1) "" else "s"} need a location")
            }
            if (failed > 0) add("failed $failed")
        }
        return if (parts.isEmpty()) {
            "No photos were imported."
        } else {
            parts.joinToString(". ").replaceFirstChar { it.uppercase() } + "."
        }
    }
}

data class CapturedPhotoLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val label: String? = null
)

private data class ResolvedPhotoLocation(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val timestampMillis: Long? = null,
    val source: String
)

@Singleton
class PhotoTimelineImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationDao: LocationDao,
    private val geoResolver: OfflineGeoResolver
) {
    suspend fun importGalleryPhotos(uris: List<Uri>): PhotoImportResult {
        var added = 0
        var attached = 0
        var skippedDuplicate = 0
        var skippedNoGps = 0
        var failed = 0
        val pending = mutableListOf<Uri>()
        uris.forEach { uri ->
            when (val outcome = importOne(uri, fallbackLocation = null)) {
                ImportOutcome.ADDED -> added++
                ImportOutcome.ATTACHED -> attached++
                ImportOutcome.DUPLICATE -> skippedDuplicate++
                ImportOutcome.NO_GPS -> {
                    skippedNoGps++
                    pending += uri
                }
                ImportOutcome.FAILED -> failed++
            }
        }
        return PhotoImportResult(added, attached, skippedDuplicate, skippedNoGps, failed, pending)
    }

    suspend fun importCapturedPhoto(
        uri: Uri,
        captureLocation: CapturedPhotoLocation
    ): PhotoImportResult {
        return when (importOne(uri, captureLocation)) {
            ImportOutcome.ADDED -> PhotoImportResult(added = 1)
            ImportOutcome.ATTACHED -> PhotoImportResult(attachedToExisting = 1)
            ImportOutcome.DUPLICATE -> PhotoImportResult(skippedDuplicate = 1)
            ImportOutcome.NO_GPS -> PhotoImportResult(skippedNoGps = 1, pendingNoGpsUris = listOf(uri))
            ImportOutcome.FAILED -> PhotoImportResult(failed = 1)
        }
    }

    suspend fun importWithChosenLocation(
        uri: Uri,
        location: CapturedPhotoLocation
    ): PhotoImportResult = importCapturedPhoto(uri, location)

    suspend fun deletePhotoPoint(
        locationId: Long,
        alsoDeleteFromGallery: Boolean
    ): String {
        val entity = locationDao.getLocationById(locationId)
            ?: return "Photo point not found."
        entity.photoUri?.let { deleteLocalFileUri(it) }
        locationDao.deleteById(locationId)
        if (!alsoDeleteFromGallery) {
            return "Removed from RoamAtlas."
        }
        val original = entity.originalPhotoUri?.let(Uri::parse)
        if (original == null) {
            return "Removed from RoamAtlas. Original gallery file was not linked, so it was not deleted."
        }
        val deleted = runCatching {
            context.contentResolver.delete(original, null, null) > 0
        }.getOrDefault(false)
        return if (deleted) {
            "Removed from RoamAtlas and deleted from Gallery."
        } else {
            "Removed from RoamAtlas. Could not delete the Gallery original (permission or cloud photo)."
        }
    }

    private enum class ImportOutcome { ADDED, ATTACHED, DUPLICATE, NO_GPS, FAILED }

    private suspend fun importOne(
        uri: Uri,
        fallbackLocation: CapturedPhotoLocation?
    ): ImportOutcome {
        return try {
            val sourceId = "photo_${stablePhotoId(uri)}"
            locationDao.getByOriginalSourceId(sourceId)?.let { return ImportOutcome.DUPLICATE }

            val resolved = resolvePhotoLocation(uri)
                ?: fallbackLocation?.let {
                    ResolvedPhotoLocation(
                        latitude = it.latitude,
                        longitude = it.longitude,
                        altitudeMeters = it.altitudeMeters,
                        timestampMillis = it.timestampMillis,
                        source = it.label?.let { label -> "chosen:$label" } ?: "chosen_location"
                    )
                }
            if (resolved == null) return ImportOutcome.NO_GPS

            val latitude = resolved.latitude
            val longitude = resolved.longitude
            val timestampMillis = resolved.timestampMillis
                ?: fallbackLocation?.timestampMillis
                ?: System.currentTimeMillis()
            val altitude = resolved.altitudeMeters ?: fallbackLocation?.altitudeMeters
            val accuracy = fallbackLocation?.accuracyMeters
            val thumbnailUri = createThumbnail(uri, sourceId) ?: return ImportOutcome.FAILED
            val originalUri = uri.toString()
            persistReadPermission(uri)

            val nearby = findNearbyPoint(latitude, longitude, timestampMillis)
            if (nearby != null) {
                if (nearby.photoUri.isNullOrBlank()) {
                    locationDao.update(
                        nearby.copy(
                            photoUri = thumbnailUri,
                            originalPhotoUri = nearby.originalPhotoUri ?: originalUri,
                            notes = mergeNotes(
                                nearby.notes,
                                "Photo thumbnail attached (${resolved.source})"
                            ),
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    )
                    return ImportOutcome.ATTACHED
                }
                return ImportOutcome.DUPLICATE
            }

            val place = geoResolver.resolve(latitude, longitude)
            val now = System.currentTimeMillis()
            val note = buildString {
                append("Imported from photo (${resolved.source})")
                fallbackLocation?.label?.takeIf { it.isNotBlank() }?.let {
                    append(" · ")
                    append(it)
                }
            }
            val inserted = locationDao.insert(
                LocationEntity(
                    latitude = latitude,
                    longitude = longitude,
                    timestampMillis = timestampMillis,
                    source = "MANUAL_ENTRY",
                    accuracy = accuracy,
                    altitude = altitude,
                    speed = null,
                    countryCode = place.countryCode,
                    countryName = place.countryName,
                    city = place.city,
                    confidence = "VERIFIED",
                    originalSourceId = sourceId,
                    notes = note,
                    photoUri = thumbnailUri,
                    originalPhotoUri = originalUri,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            if (inserted == -1L) ImportOutcome.DUPLICATE else ImportOutcome.ADDED
        } catch (error: Exception) {
            Log.e(TAG, "Photo import failed for $uri", error)
            ImportOutcome.FAILED
        }
    }

    private fun resolvePhotoLocation(uri: Uri): ResolvedPhotoLocation? {
        openExif(uri)?.let { exif ->
            val latLon = exif.latLong
            if (latLon != null && latLon.size >= 2 && isValidLatLon(latLon[0], latLon[1])) {
                return ResolvedPhotoLocation(
                    latitude = latLon[0],
                    longitude = latLon[1],
                    altitudeMeters = exif.getAltitude(Double.NaN).takeIf { !it.isNaN() },
                    timestampMillis = readExifTimestamp(exif),
                    source = "exif"
                )
            }
            readManualGpsTags(exif)?.let { (lat, lon) ->
                return ResolvedPhotoLocation(
                    latitude = lat,
                    longitude = lon,
                    altitudeMeters = exif.getAltitude(Double.NaN).takeIf { !it.isNaN() },
                    timestampMillis = readExifTimestamp(exif),
                    source = "exif_tags"
                )
            }
        }
        return readMediaStoreLocation(uri)
    }

    private fun openExif(uri: Uri): ExifInterface? {
        val readableUri = uriWithOriginalLocation(uri)
        runCatching {
            context.contentResolver.openFileDescriptor(readableUri, "r")?.use { pfd ->
                return ExifInterface(pfd.fileDescriptor)
            }
        }
        runCatching {
            val temp = File(context.cacheDir, "exif_${System.currentTimeMillis()}.tmp")
            context.contentResolver.openInputStream(readableUri)?.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            return try {
                ExifInterface(temp.absolutePath)
            } finally {
                temp.delete()
            }
        }
        return runCatching {
            context.contentResolver.openInputStream(readableUri)?.use { input ->
                ExifInterface(input)
            }
        }.getOrNull()
    }

    private fun uriWithOriginalLocation(uri: Uri): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return uri
        return runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
    }

    private fun readManualGpsTags(exif: ExifInterface): Pair<Double, Double>? {
        val lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) ?: return null
        val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF) ?: return null
        val lon = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE) ?: return null
        val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF) ?: return null
        val latitude = dmsToDecimal(lat, latRef) ?: return null
        val longitude = dmsToDecimal(lon, lonRef) ?: return null
        return if (isValidLatLon(latitude, longitude)) latitude to longitude else null
    }

    private fun dmsToDecimal(dms: String, ref: String): Double? = runCatching {
        val parts = dms.split(',').map { part ->
            val ratio = part.trim().split('/')
            val num = ratio[0].toDouble()
            val den = ratio.getOrNull(1)?.toDouble()?.takeIf { it != 0.0 } ?: 1.0
            num / den
        }
        if (parts.size < 3) return null
        var value = parts[0] + parts[1] / 60.0 + parts[2] / 3600.0
        if (ref.equals("S", true) || ref.equals("W", true)) value = -value
        value
    }.getOrNull()

    private fun readMediaStoreLocation(uri: Uri): ResolvedPhotoLocation? {
        val resolver = context.contentResolver
        val originalUri = uriWithOriginalLocation(uri)
        val latitudeColumn = "latitude"
        val longitudeColumn = "longitude"
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            latitudeColumn,
            longitudeColumn
        )

        fun query(target: Uri): ResolvedPhotoLocation? = runCatching {
            resolver.query(target, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val latIndex = cursor.getColumnIndex(latitudeColumn)
                val lonIndex = cursor.getColumnIndex(longitudeColumn)
                if (latIndex < 0 || lonIndex < 0) return@use null
                if (cursor.isNull(latIndex) || cursor.isNull(lonIndex)) return@use null
                val latitude = cursor.getDouble(latIndex)
                val longitude = cursor.getDouble(lonIndex)
                if (!isValidLatLon(latitude, longitude)) return@use null
                val takenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val addedIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val taken = if (takenIndex >= 0 && !cursor.isNull(takenIndex)) {
                    cursor.getLong(takenIndex).takeIf { it > 0L }
                } else null
                val added = if (addedIndex >= 0 && !cursor.isNull(addedIndex)) {
                    cursor.getLong(addedIndex).takeIf { it > 0L }?.times(1000)
                } else null
                ResolvedPhotoLocation(
                    latitude = latitude,
                    longitude = longitude,
                    timestampMillis = taken ?: added,
                    source = "mediastore"
                )
            }
        }.getOrNull()

        query(originalUri)?.let { return it }
        if (originalUri != uri) query(uri)?.let { return it }
        uri.lastPathSegment?.toLongOrNull()?.let { mediaId ->
            query(
                ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    mediaId
                )
            )?.let { return it }
        }
        return null
    }

    private fun isValidLatLon(latitude: Double, longitude: Double): Boolean {
        if (latitude == 0.0 && longitude == 0.0) return false
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    private suspend fun findNearbyPoint(
        latitude: Double,
        longitude: Double,
        timestampMillis: Long
    ): LocationEntity? {
        val latDelta = 0.0007
        val lonDelta = latDelta / cos(Math.toRadians(latitude)).coerceAtLeast(0.2)
        return locationDao.findNearby(
            minLat = latitude - latDelta,
            maxLat = latitude + latDelta,
            minLon = longitude - lonDelta,
            maxLon = longitude + lonDelta,
            timestampMillis = timestampMillis,
            windowMillis = 2 * 60 * 60 * 1000L
        )
    }

    private fun readExifTimestamp(exif: ExifInterface?): Long? {
        if (exif == null) return null
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null
        return runCatching {
            LocalDateTime.parse(raw, EXIF_FORMAT)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    private fun stablePhotoId(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(8_192)
                var read = input.read(buffer)
                var total = 0
                while (read > 0 && total < 256_000) {
                    digest.update(buffer, 0, read)
                    total += read
                    read = input.read(buffer)
                }
            }
        }
        digest.update(uri.toString().toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }.take(24)
    }

    private fun createThumbnail(uri: Uri, sourceId: String): String? = runCatching {
        val bitmap = decodeBitmap(uri) ?: return null
        val scaled = scaleBitmap(bitmap, 512)
        val dir = File(context.filesDir, "photo_thumbs").apply { mkdirs() }
        val file = File(dir, "$sourceId.jpg")
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        }
        if (scaled !== bitmap) scaled.recycle()
        if (!bitmap.isRecycled) bitmap.recycle()
        Uri.fromFile(file).toString()
    }.getOrNull()

    private fun decodeBitmap(uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.setTargetSampleSize(2)
                decoder.isMutableRequired = false
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()

    private fun scaleBitmap(bitmap: Bitmap, maxSide: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun deleteLocalFileUri(uriString: String) {
        runCatching {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
            }
        }
    }

    private fun persistReadPermission(uri: Uri) {
        if (uri.scheme != "content") return
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.onFailure {
            Log.d(TAG, "Could not persist read permission for $uri")
        }
    }

    private fun mergeNotes(existing: String?, addition: String): String {
        if (existing.isNullOrBlank()) return addition
        return if (existing.contains(addition)) existing else "$existing · $addition"
    }

    companion object {
        private const val TAG = "PhotoTimelineImporter"
        private val EXIF_FORMAT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    }
}
