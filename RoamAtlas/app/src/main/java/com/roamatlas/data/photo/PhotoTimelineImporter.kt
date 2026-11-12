package com.roamatlas.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
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
    val failed: Int = 0
) {
    val processed: Int
        get() = added + attachedToExisting + skippedDuplicate + skippedNoGps + failed

    fun summaryMessage(): String {
        val parts = buildList {
            if (added > 0) add("added $added new timeline point${if (added == 1) "" else "s"}")
            if (attachedToExisting > 0) {
                add("linked $attachedToExisting photo${if (attachedToExisting == 1) "" else "s"} to existing points")
            }
            if (skippedDuplicate > 0) add("skipped $skippedDuplicate already imported")
            if (skippedNoGps > 0) add("skipped $skippedNoGps without GPS")
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
    val timestampMillis: Long = System.currentTimeMillis()
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
        uris.forEach { uri ->
            when (val outcome = importOne(uri, fallbackLocation = null)) {
                ImportOutcome.ADDED -> added++
                ImportOutcome.ATTACHED -> attached++
                ImportOutcome.DUPLICATE -> skippedDuplicate++
                ImportOutcome.NO_GPS -> skippedNoGps++
                ImportOutcome.FAILED -> failed++
            }
        }
        return PhotoImportResult(added, attached, skippedDuplicate, skippedNoGps, failed)
    }

    suspend fun importCapturedPhoto(
        uri: Uri,
        captureLocation: CapturedPhotoLocation
    ): PhotoImportResult {
        return when (importOne(uri, captureLocation)) {
            ImportOutcome.ADDED -> PhotoImportResult(added = 1)
            ImportOutcome.ATTACHED -> PhotoImportResult(attachedToExisting = 1)
            ImportOutcome.DUPLICATE -> PhotoImportResult(skippedDuplicate = 1)
            ImportOutcome.NO_GPS -> PhotoImportResult(skippedNoGps = 1)
            ImportOutcome.FAILED -> PhotoImportResult(failed = 1)
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

            val exif = openExif(uri)
            val latLon = exif?.latLong
            val latitude = latLon?.getOrNull(0) ?: fallbackLocation?.latitude
            val longitude = latLon?.getOrNull(1) ?: fallbackLocation?.longitude
            if (latitude == null || longitude == null) return ImportOutcome.NO_GPS

            val timestampMillis = readExifTimestamp(exif)
                ?: fallbackLocation?.timestampMillis
                ?: System.currentTimeMillis()
            val altitude = exif?.getAltitude(Double.NaN)?.takeIf { !it.isNaN() }
                ?: fallbackLocation?.altitudeMeters
            val accuracy = fallbackLocation?.accuracyMeters

            val thumbnailUri = createThumbnail(uri, sourceId) ?: return ImportOutcome.FAILED

            val nearby = findNearbyPoint(latitude, longitude, timestampMillis)
            if (nearby != null) {
                if (nearby.photoUri.isNullOrBlank()) {
                    locationDao.update(
                        nearby.copy(
                            photoUri = thumbnailUri,
                            notes = mergeNotes(nearby.notes, "Photo thumbnail attached"),
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    )
                    return ImportOutcome.ATTACHED
                }
                // Point already there with a photo — treat as already present.
                return ImportOutcome.DUPLICATE
            }

            val place = geoResolver.resolve(latitude, longitude)
            val now = System.currentTimeMillis()
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
                    notes = "Imported from photo",
                    photoUri = thumbnailUri,
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

    private suspend fun findNearbyPoint(
        latitude: Double,
        longitude: Double,
        timestampMillis: Long
    ): LocationEntity? {
        // ~75 meters latitude delta; longitude scaled by cos(lat).
        val latDelta = 0.0007
        val lonDelta = latDelta / cos(Math.toRadians(latitude)).coerceAtLeast(0.2)
        return locationDao.findNearby(
            minLat = latitude - latDelta,
            maxLat = latitude + latDelta,
            minLon = longitude - lonDelta,
            maxLon = longitude + lonDelta,
            timestampMillis = timestampMillis,
            windowMillis = 2 * 60 * 60 * 1000L // 2 hours
        )
    }

    private fun openExif(uri: Uri): ExifInterface? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input)
            }
        }.getOrNull()
    }

    private fun readExifTimestamp(exif: ExifInterface?): Long? {
        if (exif == null) return null
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null
        return runCatching {
            val local = LocalDateTime.parse(raw, EXIF_FORMAT)
            local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
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

    private fun createThumbnail(uri: Uri, sourceId: String): String? {
        return runCatching {
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
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.setTargetSampleSize(2)
                    decoder.isMutableRequired = false
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }
        }.getOrNull()
    }

    private fun scaleBitmap(bitmap: Bitmap, maxSide: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / largest
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun mergeNotes(existing: String?, addition: String): String {
        if (existing.isNullOrBlank()) return addition
        return if (existing.contains(addition)) existing else "$existing · $addition"
    }

    companion object {
        private const val TAG = "PhotoTimelineImporter"
        private val EXIF_FORMAT =
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    }
}
