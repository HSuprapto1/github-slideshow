package com.roamatlas.data.timeline

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.roamatlas.data.geo.OfflineGeoResolver
import com.roamatlas.data.local.RoamAtlasDatabase
import com.roamatlas.data.local.dao.LocationDao
import com.roamatlas.data.local.entity.LocationEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalTimelineImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: RoamAtlasDatabase,
    private val locationDao: LocationDao,
    private val geoResolver: OfflineGeoResolver
) {
    
    data class ImportResult(
        val success: Boolean,
        val locationsImported: Int,
        val placesFound: Int,
        val errors: List<String>,
        val message: String
    )

    data class ImportProgress(
        val percent: Int,
        val processed: Int,
        val imported: Int,
        val message: String
    )
    
    suspend fun importFromFolder(
        folderUri: Uri,
        onProgress: (ImportProgress) -> Unit = {}
    ): ImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        var locationsImported = 0
        var placesFound = 0
        
        try {
            val folder = DocumentFile.fromTreeUri(context, folderUri)
            
            if (folder == null || !folder.canRead()) {
                return@withContext ImportResult(false, 0, 0, listOf("Cannot access folder"), "Failed to access folder")
            }
            
            // Find all JSON files
            val jsonFiles = mutableListOf<DocumentFile>()
            scanForJsonFiles(folder, jsonFiles)
            
            Log.d("LocalTimelineImporter", "Found ${jsonFiles.size} JSON files")
            
            for (jsonFile in jsonFiles) {
                try {
                    val result = processJsonFile(jsonFile, onProgress)
                    locationsImported += result.first
                    placesFound += result.second
                } catch (e: Exception) {
                    errors.add("Error: ${e.message}")
                }
            }
            
            ImportResult(
                success = locationsImported > 0,
                locationsImported = locationsImported,
                placesFound = placesFound,
                errors = errors,
                message = if (locationsImported > 0) {
                    "Saved $locationsImported places from the phone Timeline."
                } else {
                    "No places were found. Export Timeline data from phone Settings, then choose that file."
                }
            )
        } catch (e: Exception) {
            ImportResult(false, 0, 0, listOf(e.message ?: "Unknown error"), "Failed: ${e.message}")
        }
    }
    
    suspend fun importFromFile(
        fileUri: Uri,
        onProgress: (ImportProgress) -> Unit = {}
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            importStream(fileUri, onProgress)
        } catch (_: LegacyTimelineFormatException) {
            importLegacyFile(fileUri)
        } catch (e: Exception) {
            ImportResult(false, 0, 0, listOf(e.message ?: "Unknown"), "Failed: ${e.message}")
        }
    }
    
    private fun scanForJsonFiles(folder: DocumentFile, result: MutableList<DocumentFile>) {
        val children = folder.listFiles()
        for (child in children) {
            if (child.isDirectory) {
                scanForJsonFiles(child, result)
            } else if (child.name?.lowercase()?.endsWith(".json") == true) {
                result.add(child)
            }
        }
    }
    
    private suspend fun processJsonFile(
        file: DocumentFile,
        onProgress: (ImportProgress) -> Unit
    ): Pair<Int, Int> {
        val result = importStream(file.uri, onProgress)
        return Pair(result.locationsImported, result.placesFound)
    }

    private suspend fun importStream(
        uri: Uri,
        onProgress: (ImportProgress) -> Unit
    ): ImportResult {
        val totalBytes = querySize(uri)
        val input = context.contentResolver.openInputStream(uri)
            ?: return ImportResult(false, 0, 0, listOf("Cannot open file"), "Failed")
        val counting = CountingInputStream(input)
        var processed = 0
        var imported = 0
        var places = 0
        var skipped = 0
        val batch = ArrayList<LocationEntity>(INSERT_BATCH_SIZE)

        fun progress(message: String) {
            val percent = if (totalBytes > 0) {
                ((counting.bytesRead * 100L) / totalBytes).toInt().coerceIn(0, 99)
            } else 0
            onProgress(ImportProgress(percent, processed, imported, message))
        }

        suspend fun flush() {
            if (batch.isEmpty()) return
            imported += locationDao.insertAll(batch).count { it != -1L }
            batch.clear()
            progress("Saving and identifying countries…")
        }

        database.withTransaction {
            counting.use { stream ->
                JsonReader(InputStreamReader(stream)).use { reader ->
                    reader.isLenient = true

                    suspend fun addPoints(points: List<OnDeviceTimelineParser.Point>) {
                        for (point in points) {
                            processed++
                            batch.add(point.toEntity())
                            if (batch.size >= INSERT_BATCH_SIZE) flush()
                        }
                    }

                    when (reader.peek()) {
                        JsonToken.BEGIN_OBJECT -> {
                            var recognized = false
                            var semanticPointsFound = false
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "semanticSegments" -> {
                                        recognized = true
                                        reader.beginArray()
                                        while (reader.hasNext()) {
                                            val segment = JsonParser.parseReader(reader).asJsonObject
                                            val parsed = OnDeviceTimelineParser.parseSegment(segment)
                                            if (parsed.points.isEmpty()) skipped++
                                            if (parsed.points.isNotEmpty()) semanticPointsFound = true
                                            places += parsed.places
                                            addPoints(parsed.points)
                                        }
                                        reader.endArray()
                                    }
                                    "rawSignals" -> {
                                        recognized = true
                                        if (semanticPointsFound) {
                                            reader.skipValue()
                                        } else {
                                            reader.beginArray()
                                            while (reader.hasNext()) {
                                                val signal = JsonParser.parseReader(reader).asJsonObject
                                                val point = OnDeviceTimelineParser.parseRawSignal(signal)
                                                if (point == null) skipped++ else addPoints(listOf(point))
                                            }
                                            reader.endArray()
                                        }
                                    }
                                    "userLocationProfile" -> reader.skipValue()
                                    "locations", "timelineObjects" ->
                                        throw LegacyTimelineFormatException()
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                            if (!recognized) throw LegacyTimelineFormatException()
                        }
                        JsonToken.BEGIN_ARRAY -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                val segment = JsonParser.parseReader(reader).asJsonObject
                                val parsed = OnDeviceTimelineParser.parseSegment(segment)
                                if (parsed.points.isEmpty()) skipped++
                                places += parsed.places
                                addPoints(parsed.points)
                            }
                            reader.endArray()
                        }
                        else -> throw LegacyTimelineFormatException()
                    }
                    flush()
                }
            }
        }
        onProgress(ImportProgress(100, processed, imported, "Import complete"))
        val duplicates = processed - imported
        val message = if (imported > 0) {
            "Saved $imported locations with country and city information." +
                if (duplicates > 0) " Skipped $duplicates already saved locations." else ""
        } else if (processed > 0) {
            "All $processed locations were already saved in RoamAtlas."
        } else {
            "No locations were found in that Timeline file."
        }
        return ImportResult(
            success = processed > 0,
            locationsImported = imported,
            placesFound = places,
            errors = if (skipped > 0) listOf("$skipped unsupported Timeline records skipped") else emptyList(),
            message = message
        )
    }

    private suspend fun importLegacyFile(uri: Uri): ImportResult {
        val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return ImportResult(false, 0, 0, listOf("Cannot open file"), "Failed")
        val saved = saveParsedContent(content)
        return ImportResult(
            success = saved.inserted > 0,
            locationsImported = saved.inserted,
            placesFound = saved.places,
            errors = emptyList(),
            message = saved.message
        )
    }

    private fun querySize(uri: Uri): Long {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else -1L
            } ?: -1L
    }

    private data class Saved(
        val inserted: Int,
        val places: Int,
        val message: String
    )

    private suspend fun saveParsedContent(content: String): Saved {
        val onDevice = OnDeviceTimelineParser.parse(content)
        if (onDevice.kind == OnDeviceTimelineParser.Kind.ENCRYPTED ||
            onDevice.kind == OnDeviceTimelineParser.Kind.EMPTY
        ) {
            return Saved(0, 0, onDevice.message)
        }
        if (onDevice.kind == OnDeviceTimelineParser.Kind.ON_DEVICE) {
            val inserted = insertEntities(onDevice.points.map { it.toEntity() })
            val message = when {
                inserted > 0 -> "${onDevice.message} Saved $inserted new places."
                onDevice.points.isEmpty() -> onDevice.message
                else -> "Those places are already saved in RoamAtlas."
            }
            return Saved(inserted, onDevice.places, message)
        }

        val (locations, places) = parseLegacyContent(content)
        val inserted = insertEntities(locations)
        val message = when {
            inserted > 0 -> "Saved $inserted places from the older Google file."
            locations.isEmpty() -> "No places were found in that file. Use the Timeline file from phone Settings, not the locked Takeout download."
            else -> "Those places are already saved in RoamAtlas."
        }
        return Saved(inserted, places, message)
    }

    private suspend fun insertEntities(locations: List<LocationEntity>): Int {
        if (locations.isEmpty()) return 0
        var inserted = 0
        for (chunk in locations.chunked(400)) {
            inserted += locationDao.insertAll(chunk).count { it != -1L }
        }
        return inserted
    }

    private fun OnDeviceTimelineParser.Point.toEntity(): LocationEntity {
        val now = System.currentTimeMillis()
        val place = geoResolver.resolve(latitude, longitude)
        return LocationEntity(
            id = 0,
            latitude = latitude,
            longitude = longitude,
            timestampMillis = timestampMillis,
            source = "GOOGLE_TIMELINE",
            accuracy = accuracy,
            altitude = altitude,
            speed = speed,
            countryCode = place.countryCode,
            countryName = place.countryName,
            city = city ?: place.city,
            confidence = confidence,
            originalSourceId = originalSourceId,
            notes = notes,
            photoUri = null,
            originalPhotoUri = null,
            createdAtMillis = now,
            updatedAtMillis = now
        )
    }
    
    private fun parseLegacyContent(content: String): Pair<List<LocationEntity>, Int> {
        val locations = mutableListOf<LocationEntity>()
        var placesCount = 0
        val now = System.currentTimeMillis()
        
        try {
            // Try as object first
            if (content.trim().startsWith("{")) {
                val json = JSONObject(content)
                
                // Semantic Location History format
                if (json.has("timelineObjects")) {
                    val arr = json.getJSONArray("timelineObjects")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        if (obj.has("placeVisit")) {
                            parsePlaceVisit(obj.getJSONObject("placeVisit"), now)?.let {
                                locations.add(it)
                                placesCount++
                            }
                        }
                        if (obj.has("activitySegment")) {
                            parseActivitySegment(obj.getJSONObject("activitySegment"), now).forEach {
                                locations.add(it)
                            }
                        }
                    }
                }
                
                // Records.json format
                if (json.has("locations")) {
                    val arr = json.getJSONArray("locations")
                    for (i in 0 until arr.length()) {
                        parseRecordLocation(arr.getJSONObject(i), now)?.let { locations.add(it) }
                    }
                }
            }
            
            // Try as array
            if (content.trim().startsWith("[")) {
                val arr = JSONArray(content)
                for (i in 0 until arr.length()) {
                    parseGenericLocation(arr.getJSONObject(i), now)?.let { locations.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e("LocalTimelineImporter", "Parse error", e)
        }
        
        return Pair(locations, placesCount)
    }
    
    private fun parsePlaceVisit(pv: JSONObject, now: Long): LocationEntity? {
        val loc = pv.optJSONObject("location") ?: return null
        val lat = loc.optLong("latitudeE7", 0) / 1e7
        val lon = loc.optLong("longitudeE7", 0) / 1e7
        if (lat == 0.0 && lon == 0.0) return null
        
        val dur = pv.optJSONObject("duration")
        val ts = parseTimestamp(dur?.optString("startTimestamp") ?: dur?.optString("startTimestampMs"))
        
        val place = geoResolver.resolve(lat, lon)
        return LocationEntity(
            id = 0, latitude = lat, longitude = lon, timestampMillis = ts,
            source = "GOOGLE_TIMELINE", accuracy = null, altitude = null, speed = null,
            countryCode = place.countryCode, countryName = place.countryName,
            city = loc.optString("name", null) ?: place.city,
            confidence = "VERIFIED", originalSourceId = "pv_$ts",
            notes = loc.optString("address", null), photoUri = null,
            originalPhotoUri = null,
            createdAtMillis = now, updatedAtMillis = now
        )
    }
    
    private fun parseActivitySegment(seg: JSONObject, now: Long): List<LocationEntity> {
        val result = mutableListOf<LocationEntity>()
        
        seg.optJSONObject("startLocation")?.let { sl ->
            val lat = sl.optLong("latitudeE7", 0) / 1e7
            val lon = sl.optLong("longitudeE7", 0) / 1e7
            if (lat != 0.0 || lon != 0.0) {
                val dur = seg.optJSONObject("duration")
                val ts = parseTimestamp(dur?.optString("startTimestamp"))
                val place = geoResolver.resolve(lat, lon)
                result.add(LocationEntity(
                    id = 0, latitude = lat, longitude = lon, timestampMillis = ts,
                    source = "GOOGLE_TIMELINE", accuracy = null, altitude = null, speed = null,
                    countryCode = place.countryCode, countryName = place.countryName, city = place.city,
                    confidence = "UNCERTAIN", originalSourceId = "seg_$ts",
                    notes = null, photoUri = null,
            originalPhotoUri = null,
                    createdAtMillis = now, updatedAtMillis = now
                ))
            }
        }
        
        return result
    }
    
    private fun parseRecordLocation(loc: JSONObject, now: Long): LocationEntity? {
        var lat = loc.optLong("latitudeE7", 0) / 1e7
        var lon = loc.optLong("longitudeE7", 0) / 1e7
        if (lat == 0.0 && lon == 0.0) {
            lat = loc.optDouble("latitude", 0.0)
            lon = loc.optDouble("longitude", 0.0)
        }
        if (lat == 0.0 && lon == 0.0) return null
        
        val ts = parseTimestamp(loc.optString("timestamp", null) ?: loc.optString("timestampMs", null))
        val acc = loc.optInt("accuracy", -1).takeIf { it > 0 }?.toFloat()
        val place = geoResolver.resolve(lat, lon)
        
        return LocationEntity(
            id = 0, latitude = lat, longitude = lon, timestampMillis = ts,
            source = "GOOGLE_TIMELINE", accuracy = acc, altitude = loc.optDouble("altitude", Double.NaN).takeIf { !it.isNaN() },
            speed = null, countryCode = place.countryCode, countryName = place.countryName, city = place.city,
            confidence = if (acc != null && acc < 50) "VERIFIED" else "UNCERTAIN",
            originalSourceId = "rec_$ts", notes = null, photoUri = null,
            originalPhotoUri = null,
            createdAtMillis = now, updatedAtMillis = now
        )
    }
    
    private fun parseGenericLocation(obj: JSONObject, now: Long): LocationEntity? {
        val lat = obj.optDouble("lat", 0.0).takeIf { it != 0.0 }
            ?: obj.optDouble("latitude", 0.0).takeIf { it != 0.0 }
            ?: (obj.optLong("latitudeE7", 0) / 1e7)
        val lon = obj.optDouble("lng", 0.0).takeIf { it != 0.0 }
            ?: obj.optDouble("lon", 0.0).takeIf { it != 0.0 }
            ?: obj.optDouble("longitude", 0.0).takeIf { it != 0.0 }
            ?: (obj.optLong("longitudeE7", 0) / 1e7)
        
        if (lat == 0.0 && lon == 0.0) return null
        
        val ts = parseTimestamp(obj.optString("timestamp", null) ?: obj.optString("time", null))
        val place = geoResolver.resolve(lat, lon)
        
        return LocationEntity(
            id = 0, latitude = lat, longitude = lon, timestampMillis = ts,
            source = "GOOGLE_TIMELINE", accuracy = null, altitude = null, speed = null,
            countryCode = place.countryCode, countryName = place.countryName,
            city = obj.optString("name", null) ?: place.city,
            confidence = "UNCERTAIN", originalSourceId = "gen_$ts",
            notes = obj.optString("address", null), photoUri = null,
            originalPhotoUri = null,
            createdAtMillis = now, updatedAtMillis = now
        )
    }
    
    private fun parseTimestamp(ts: String?): Long {
        if (ts.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            if (ts.all { it.isDigit() }) {
                val n = ts.toLong()
                if (n < 4102444800L) n * 1000 else n
            } else {
                Instant.parse(ts).toEpochMilli()
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private class LegacyTimelineFormatException : Exception()

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var bytesRead: Long = 0
            private set

        override fun read(): Int {
            return super.read().also { if (it >= 0) bytesRead++ }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return super.read(buffer, offset, length).also {
                if (it > 0) bytesRead += it
            }
        }
    }

    companion object {
        private const val INSERT_BATCH_SIZE = 500
    }
}
