package com.roamatlas.data.timeline

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.roamatlas.domain.model.DataConfidence
import com.roamatlas.domain.model.LocationRecord
import com.roamatlas.domain.model.LocationSource
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

sealed class ParseResult {
    data class Success(
        val locations: List<LocationRecord>,
        val skippedCount: Int,
        val errors: List<ParseError>
    ) : ParseResult()
    
    data class Error(val message: String, val exception: Exception? = null) : ParseResult()
}

data class ParseError(
    val index: Int,
    val reason: String,
    val rawData: String? = null
)

@Singleton
class GoogleTimelineParser @Inject constructor() {
    
    private val gson = Gson()
    
    /**
     * Parse a Google Takeout Location History file.
     * Automatically detects format (Records.json or Semantic Location History).
     */
    fun parse(inputStream: InputStream, fileName: String): ParseResult {
        return try {
            val reader = InputStreamReader(inputStream)
            val content = reader.readText()
            reader.close()
            
            parseContent(content, fileName)
        } catch (e: Exception) {
            ParseResult.Error("Failed to read file: ${e.message}", e)
        }
    }
    
    fun parseContent(content: String, fileName: String): ParseResult {
        return try {
            val onDevice = OnDeviceTimelineParser.parse(content)
            if (onDevice.kind == OnDeviceTimelineParser.Kind.ENCRYPTED) {
                return ParseResult.Error(onDevice.message)
            }
            if (onDevice.kind == OnDeviceTimelineParser.Kind.ON_DEVICE) {
                val locations = onDevice.points.map { point ->
                    LocationRecord(
                        latitude = point.latitude,
                        longitude = point.longitude,
                        timestamp = Instant.ofEpochMilli(point.timestampMillis),
                        source = LocationSource.GOOGLE_TIMELINE,
                        accuracy = point.accuracy,
                        altitude = point.altitude,
                        speed = point.speed,
                        confidence = if (point.confidence == "VERIFIED") {
                            DataConfidence.VERIFIED
                        } else {
                            DataConfidence.UNCERTAIN
                        },
                        originalSourceId = point.originalSourceId,
                        notes = point.notes
                    )
                }
                return ParseResult.Success(locations, onDevice.skipped, emptyList())
            }
            when {
                content.contains("\"locations\"") && content.contains("\"latitudeE7\"") -> {
                    parseRecordsJson(content)
                }
                content.contains("\"timelineObjects\"") -> {
                    parseSemanticLocationHistory(content)
                }
                content.contains("\"latitudeE7\"") -> {
                    parseLegacyFormat(content)
                }
                else -> {
                    ParseResult.Error(
                        "Unsupported file format. Expected Records.json or Semantic Location History format. " +
                        "File: $fileName"
                    )
                }
            }
        } catch (e: JsonSyntaxException) {
            ParseResult.Error("Invalid JSON format: ${e.message}", e)
        } catch (e: Exception) {
            ParseResult.Error("Parse error: ${e.message}", e)
        }
    }
    
    private fun parseRecordsJson(content: String): ParseResult {
        val recordsFile = gson.fromJson(content, RecordsFile::class.java)
        val rawLocations = recordsFile.locations ?: return ParseResult.Success(emptyList(), 0, emptyList())
        
        val locations = mutableListOf<LocationRecord>()
        val errors = mutableListOf<ParseError>()
        var skippedCount = 0
        
        rawLocations.forEachIndexed { index, record ->
            try {
                val location = convertRawRecord(record, index)
                if (location != null) {
                    locations.add(location)
                } else {
                    skippedCount++
                    errors.add(ParseError(index, "Missing required coordinates", null))
                }
            } catch (e: Exception) {
                skippedCount++
                errors.add(ParseError(index, "Parse error: ${e.message}", null))
            }
        }
        
        return ParseResult.Success(locations, skippedCount, errors)
    }
    
    private fun parseSemanticLocationHistory(content: String): ParseResult {
        val semanticFile = gson.fromJson(content, SemanticLocationFile::class.java)
        val timelineObjects = semanticFile.timelineObjects ?: return ParseResult.Success(emptyList(), 0, emptyList())
        
        val locations = mutableListOf<LocationRecord>()
        val errors = mutableListOf<ParseError>()
        var skippedCount = 0
        
        timelineObjects.forEachIndexed { index, obj ->
            try {
                when {
                    obj.placeVisit != null -> {
                        val placeLocations = convertPlaceVisit(obj.placeVisit, index)
                        locations.addAll(placeLocations)
                    }
                    obj.activitySegment != null -> {
                        val segmentLocations = convertActivitySegment(obj.activitySegment, index)
                        locations.addAll(segmentLocations)
                    }
                    else -> {
                        skippedCount++
                        errors.add(ParseError(index, "Unknown timeline object type", null))
                    }
                }
            } catch (e: Exception) {
                skippedCount++
                errors.add(ParseError(index, "Parse error: ${e.message}", null))
            }
        }
        
        return ParseResult.Success(locations, skippedCount, errors)
    }
    
    private fun parseLegacyFormat(content: String): ParseResult {
        return try {
            val records = gson.fromJson(content, Array<com.roamatlas.data.timeline.LocationRecord>::class.java)
            val locations = mutableListOf<LocationRecord>()
            val errors = mutableListOf<ParseError>()
            var skippedCount = 0
            
            records.forEachIndexed { index, record ->
                try {
                    val location = convertRawRecord(record, index)
                    if (location != null) {
                        locations.add(location)
                    } else {
                        skippedCount++
                    }
                } catch (e: Exception) {
                    skippedCount++
                    errors.add(ParseError(index, "Parse error: ${e.message}", null))
                }
            }
            
            ParseResult.Success(locations, skippedCount, errors)
        } catch (e: Exception) {
            ParseResult.Error("Failed to parse legacy format: ${e.message}", e)
        }
    }
    
    private fun convertRawRecord(record: com.roamatlas.data.timeline.LocationRecord, index: Int): LocationRecord? {
        val latE7 = record.latitudeE7 ?: return null
        val lngE7 = record.longitudeE7 ?: return null
        
        val timestamp = parseTimestamp(record.timestamp, record.timestampMs) ?: return null
        
        val sourceId = buildSourceId("records", timestamp, latE7, lngE7)
        
        return LocationRecord(
            latitude = latE7 / 1e7,
            longitude = lngE7 / 1e7,
            timestamp = timestamp,
            source = LocationSource.GOOGLE_TIMELINE,
            accuracy = record.accuracy?.toFloat(),
            altitude = record.altitude?.toDouble(),
            speed = record.velocity?.toFloat(),
            confidence = DataConfidence.VERIFIED,
            originalSourceId = sourceId,
            notes = buildNotes(record)
        )
    }
    
    private fun convertPlaceVisit(placeVisit: PlaceVisit, index: Int): List<LocationRecord> {
        val locations = mutableListOf<LocationRecord>()
        
        val lat = placeVisit.location?.latitudeE7 ?: placeVisit.centerLatE7
        val lng = placeVisit.location?.longitudeE7 ?: placeVisit.centerLngE7
        
        if (lat == null || lng == null) return locations
        
        val startTimestamp = parseTimestamp(
            placeVisit.duration?.startTimestamp,
            placeVisit.duration?.startTimestampMs
        ) ?: return locations
        
        val sourceId = buildSourceId("place", startTimestamp, lat, lng)
        
        val confidence = when (placeVisit.placeConfidence) {
            "HIGH_CONFIDENCE" -> DataConfidence.VERIFIED
            "MEDIUM_CONFIDENCE" -> DataConfidence.UNCERTAIN
            else -> DataConfidence.UNCERTAIN
        }
        
        val placeName = placeVisit.location?.name
        val address = placeVisit.location?.address
        
        locations.add(
            LocationRecord(
                latitude = lat / 1e7,
                longitude = lng / 1e7,
                timestamp = startTimestamp,
                source = LocationSource.GOOGLE_TIMELINE,
                confidence = confidence,
                originalSourceId = sourceId,
                city = placeName,
                notes = buildPlaceNotes(placeName, address, placeVisit.placeConfidence)
            )
        )
        
        return locations
    }
    
    private fun convertActivitySegment(segment: ActivitySegment, index: Int): List<LocationRecord> {
        val locations = mutableListOf<LocationRecord>()
        
        val startTimestamp = parseTimestamp(
            segment.duration?.startTimestamp,
            segment.duration?.startTimestampMs
        )
        
        val endTimestamp = parseTimestamp(
            segment.duration?.endTimestamp,
            segment.duration?.endTimestampMs
        )
        
        segment.startLocation?.let { loc ->
            val lat = loc.latitudeE7 ?: return@let
            val lng = loc.longitudeE7 ?: return@let
            val ts = startTimestamp ?: return@let
            
            locations.add(
                LocationRecord(
                    latitude = lat / 1e7,
                    longitude = lng / 1e7,
                    timestamp = ts,
                    source = LocationSource.GOOGLE_TIMELINE,
                    confidence = mapConfidence(segment.confidence),
                    originalSourceId = buildSourceId("segment_start", ts, lat, lng),
                    notes = "Activity: ${segment.activityType ?: "UNKNOWN"}"
                )
            )
        }
        
        segment.endLocation?.let { loc ->
            val lat = loc.latitudeE7 ?: return@let
            val lng = loc.longitudeE7 ?: return@let
            val ts = endTimestamp ?: startTimestamp ?: return@let
            
            locations.add(
                LocationRecord(
                    latitude = lat / 1e7,
                    longitude = lng / 1e7,
                    timestamp = ts,
                    source = LocationSource.GOOGLE_TIMELINE,
                    confidence = mapConfidence(segment.confidence),
                    originalSourceId = buildSourceId("segment_end", ts, lat, lng),
                    notes = "Activity: ${segment.activityType ?: "UNKNOWN"}"
                )
            )
        }
        
        segment.waypointPath?.waypoints?.forEach { waypoint ->
            val lat = waypoint.latE7 ?: return@forEach
            val lng = waypoint.lngE7 ?: return@forEach
            val ts = startTimestamp ?: return@forEach
            
            locations.add(
                LocationRecord(
                    latitude = lat / 1e7,
                    longitude = lng / 1e7,
                    timestamp = ts,
                    source = LocationSource.GOOGLE_TIMELINE,
                    confidence = DataConfidence.UNCERTAIN,
                    originalSourceId = buildSourceId("waypoint", ts, lat, lng),
                    notes = "Waypoint from ${segment.activityType ?: "activity"}"
                )
            )
        }
        
        segment.simplifiedRawPath?.points?.forEach { point ->
            val lat = point.latE7 ?: return@forEach
            val lng = point.lngE7 ?: return@forEach
            val ts = parseTimestamp(point.timestamp, point.timestampMs) ?: startTimestamp ?: return@forEach
            
            locations.add(
                LocationRecord(
                    latitude = lat / 1e7,
                    longitude = lng / 1e7,
                    timestamp = ts,
                    source = LocationSource.GOOGLE_TIMELINE,
                    accuracy = point.accuracyMeters?.toFloat(),
                    confidence = DataConfidence.VERIFIED,
                    originalSourceId = buildSourceId("path", ts, lat, lng),
                    notes = null
                )
            )
        }
        
        return locations
    }
    
    private fun parseTimestamp(isoTimestamp: String?, millisTimestamp: String?): Instant? {
        isoTimestamp?.let {
            try {
                return Instant.parse(it)
            } catch (e: DateTimeParseException) {
                try {
                    return DateTimeFormatter.ISO_DATE_TIME.parse(it, Instant::from)
                } catch (e2: Exception) {
                    // Fall through to try milliseconds
                }
            }
        }
        
        millisTimestamp?.let {
            try {
                return Instant.ofEpochMilli(it.toLong())
            } catch (e: NumberFormatException) {
                // Invalid milliseconds
            }
        }
        
        return null
    }
    
    private fun buildSourceId(prefix: String, timestamp: Instant, latE7: Long, lngE7: Long): String {
        return "${prefix}_${timestamp.toEpochMilli()}_${latE7}_${lngE7}"
    }
    
    private fun buildNotes(record: com.roamatlas.data.timeline.LocationRecord): String? {
        val parts = mutableListOf<String>()
        record.source?.let { parts.add("Source: $it") }
        record.platformType?.let { parts.add("Platform: $it") }
        record.formFactor?.let { parts.add("Device: $it") }
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }
    
    private fun buildPlaceNotes(name: String?, address: String?, confidence: String?): String? {
        val parts = mutableListOf<String>()
        name?.let { parts.add("Place: $it") }
        address?.let { parts.add("Address: $it") }
        confidence?.let { parts.add("Confidence: $it") }
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }
    
    private fun mapConfidence(confidence: String?): DataConfidence {
        return when (confidence?.uppercase()) {
            "HIGH" -> DataConfidence.VERIFIED
            "MEDIUM" -> DataConfidence.UNCERTAIN
            "LOW" -> DataConfidence.UNCERTAIN
            else -> DataConfidence.UNCERTAIN
        }
    }
}
