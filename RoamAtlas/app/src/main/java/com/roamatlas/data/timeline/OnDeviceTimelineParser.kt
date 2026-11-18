package com.roamatlas.data.timeline

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Reads the unlocked Timeline file created by the phone itself.
 *
 * Android: Settings → Location → Location services → Timeline → Export Timeline data
 * produces Timeline.json with semanticSegments and rawSignals.
 * iPhone: Google Maps → Location & Privacy → Export Timeline data
 * produces location-history.json, a list of the same segments.
 *
 * The encrypted Takeout backup is a different file and cannot be read.
 */
object OnDeviceTimelineParser {

    enum class Kind {
        ON_DEVICE,
        ENCRYPTED,
        NOT_THIS_FORMAT,
        EMPTY
    }

    data class Point(
        val latitude: Double,
        val longitude: Double,
        val timestampMillis: Long,
        val accuracy: Float? = null,
        val altitude: Double? = null,
        val speed: Float? = null,
        val city: String? = null,
        val confidence: String,
        val originalSourceId: String,
        val notes: String? = null
    )

    data class Parsed(
        val kind: Kind,
        val points: List<Point> = emptyList(),
        val places: Int = 0,
        val skipped: Int = 0,
        val message: String = ""
    )

    data class SegmentPoints(
        val points: List<Point>,
        val places: Int
    )

    fun parse(content: String): Parsed {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return Parsed(Kind.EMPTY, message = "That file is empty.")
        }
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return Parsed(
                Kind.ENCRYPTED,
                message = "This file is locked. It is the Google Takeout backup, and Google does not give you the key. " +
                    "On your phone, open Settings, then Location, then Location services, then Timeline, then Export Timeline data."
            )
        }

        return try {
            val element = JsonParser.parseString(trimmed)
            when {
                element.isJsonObject -> parseObject(element.asJsonObject)
                element.isJsonArray -> parseArray(element.asJsonArray)
                else -> Parsed(Kind.NOT_THIS_FORMAT, message = "Could not read this file.")
            }
        } catch (_: Exception) {
            Parsed(Kind.NOT_THIS_FORMAT, message = "Could not read this file.")
        }
    }

    private fun parseObject(json: JsonObject): Parsed {
        val hasSegments = json.has("semanticSegments")
        val hasRaw = json.has("rawSignals")
        if (!hasSegments && !hasRaw) {
            return Parsed(Kind.NOT_THIS_FORMAT)
        }

        val points = mutableListOf<Point>()
        var places = 0
        var skipped = 0

        if (hasSegments) {
            val segments = json.array("semanticSegments")
            if (segments != null) {
                for (element in segments) {
                    val segment = element.asObjectOrNull() ?: continue
                    val before = points.size
                    val addedPlaces = appendSegment(segment, points)
                    if (points.size == before && addedPlaces == 0) skipped++
                    places += addedPlaces
                }
            }
        }

        val usedSegments = points.isNotEmpty()
        if (!usedSegments && hasRaw) {
            val signals = json.array("rawSignals")
            if (signals != null) {
                for (element in signals) {
                    val signal = element.asObjectOrNull() ?: continue
                    if (appendRawSignal(signal, points) == 0) skipped++
                }
            }
        }

        val message = when {
            points.isEmpty() -> "No places were found in that Timeline file."
            usedSegments && hasRaw -> "Read the visits and routes from your phone Timeline. The extra raw GPS readings were left out because the visits already cover them."
            usedSegments -> "Read the visits and routes from your phone Timeline."
            else -> "Read the GPS readings from your phone Timeline."
        }
        return Parsed(Kind.ON_DEVICE, points, places, skipped, message)
    }

    private fun parseArray(array: JsonArray): Parsed {
        if (array.size() == 0) {
            return Parsed(Kind.EMPTY, message = "That file is empty.")
        }
        val first = array[0].asObjectOrNull() ?: return Parsed(Kind.NOT_THIS_FORMAT)
        val looksLikeSegment = first.has("visit") || first.has("activity") ||
            first.has("timelinePath") || first.has("startTime")
        if (!looksLikeSegment) {
            return Parsed(Kind.NOT_THIS_FORMAT)
        }

        val points = mutableListOf<Point>()
        var places = 0
        var skipped = 0
        for (element in array) {
            val segment = element.asObjectOrNull() ?: continue
            val before = points.size
            val addedPlaces = appendSegment(segment, points)
            if (points.size == before && addedPlaces == 0) skipped++
            places += addedPlaces
        }
        return Parsed(
            Kind.ON_DEVICE,
            points,
            places,
            skipped,
            if (points.isEmpty()) "No places were found in that Timeline file."
            else "Read the visits and routes from your phone Timeline."
        )
    }

    private fun appendSegment(segment: JsonObject, out: MutableList<Point>): Int {
        val start = parseTime(segment.string("startTime"))
        val end = parseTime(segment.string("endTime"))
        var places = 0

        segment.obj("visit")?.let { visit ->
            val top = visit.obj("topCandidate")
            val coords = extractLatLng(top?.get("placeLocation"))
                ?: extractLatLng(visit.get("placeLocation"))
            val whenMillis = start ?: end
            if (coords != null && whenMillis != null) {
                val semanticType = top?.string("semanticType").orEmpty()
                val probability = visit.double("probability")
                    ?: top?.double("probability")
                    ?: 1.0
                out.add(
                    point(
                        coords = coords,
                        timestampMillis = whenMillis,
                        confidence = if (probability >= 0.5) "VERIFIED" else "UNCERTAIN",
                        kind = "visit",
                        notes = semanticType.takeIf { it.isNotBlank() && !it.equals("UNKNOWN", true) }
                    )
                )
                places++
            }
        }

        segment.obj("activity")?.let { activity ->
            val mode = activity.obj("topCandidate")?.string("type").orEmpty()
                .ifBlank { activity.string("activityType").orEmpty() }
            val note = mode.takeIf { it.isNotBlank() }?.let {
                "Trip: ${it.replace('_', ' ').lowercase().replaceFirstChar { c -> c.uppercase() }}"
            }
            val startCoords = extractLatLng(activity.get("start"))
            val endCoords = extractLatLng(activity.get("end"))
            if (startCoords != null && start != null) {
                out.add(point(startCoords, start, "UNCERTAIN", "trip_start", note))
            }
            val endTime = end ?: start
            if (endCoords != null && endTime != null) {
                out.add(point(endCoords, endTime, "UNCERTAIN", "trip_end", note))
            }
        }

        val path = segment.array("timelinePath")
        if (path != null && start != null) {
            for (element in path) {
                val item = element.asObjectOrNull() ?: continue
                val coords = extractLatLng(item.get("point"))
                    ?: extractLatLng(item.get("latLng"))
                    ?: extractLatLng(item)
                val whenMillis = pathTime(item, start) ?: continue
                if (coords != null) {
                    out.add(point(coords, whenMillis, "UNCERTAIN", "path", null))
                }
            }
        } else if (path != null) {
            for (element in path) {
                val item = element.asObjectOrNull() ?: continue
                val coords = extractLatLng(item.get("point")) ?: continue
                val whenMillis = parseTime(item.string("time")) ?: continue
                out.add(point(coords, whenMillis, "UNCERTAIN", "path", null))
            }
        }
        return places
    }

    fun parseSegment(segment: JsonObject): SegmentPoints {
        val points = mutableListOf<Point>()
        val places = appendSegment(segment, points)
        return SegmentPoints(points, places)
    }

    fun parseRawSignal(signal: JsonObject): Point? {
        val points = mutableListOf<Point>()
        appendRawSignal(signal, points)
        return points.singleOrNull()
    }

    private fun appendRawSignal(signal: JsonObject, out: MutableList<Point>): Int {
        val position = signal.obj("position") ?: return 0
        val coords = extractLatLng(position.get("LatLng"))
            ?: extractLatLng(position.get("latLng"))
            ?: return 0
        val whenMillis = parseTime(position.string("timestamp")) ?: return 0
        val accuracy = position.double("accuracyMeters")?.toFloat()
        val altitude = position.double("altitudeMeters")
        val speed = position.double("speedMetersPerSecond")?.toFloat()
        out.add(
            point(
                coords = coords,
                timestampMillis = whenMillis,
                confidence = if (accuracy != null && accuracy <= 50f) "VERIFIED" else "UNCERTAIN",
                kind = "raw",
                notes = position.string("source")?.takeIf { it.isNotBlank() },
                accuracy = accuracy,
                altitude = altitude,
                speed = speed
            )
        )
        return 1
    }

    private fun pathTime(item: JsonObject, start: Long): Long? {
        val explicit = parseTime(item.string("time"))
        if (explicit != null) return explicit
        val minutes = item.double("durationMinutesOffsetFromStartTime")?.toLong()
            ?: item.string("durationMinutesOffsetFromStartTime")?.toLongOrNull()
            ?: return null
        return start + minutes * 60_000L
    }

    private fun point(
        coords: Pair<Double, Double>,
        timestampMillis: Long,
        confidence: String,
        kind: String,
        notes: String?,
        accuracy: Float? = null,
        altitude: Double? = null,
        speed: Float? = null
    ): Point {
        val sourceId = stableSourceId(kind, timestampMillis, coords.first, coords.second)
        return Point(
            latitude = coords.first,
            longitude = coords.second,
            timestampMillis = timestampMillis,
            accuracy = accuracy,
            altitude = altitude,
            speed = speed,
            confidence = confidence,
            originalSourceId = sourceId,
            notes = notes
        )
    }

    private fun stableSourceId(
        kind: String,
        timestampMillis: Long,
        latitude: Double,
        longitude: Double
    ): String {
        val value = "$kind|$timestampMillis|${latitude.toString()}|${longitude.toString()}"
        val hash = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return "gmaps_" + hash.joinToString("") { "%02x".format(it) }
    }

    internal fun extractLatLng(value: JsonElement?): Pair<Double, Double>? {
        if (value == null || value.isJsonNull) return null
        if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            return parseLatLng(value.asString)
        }
        val obj = value.asObjectOrNull() ?: return null
        if (obj.has("latLng")) return extractLatLng(obj.get("latLng"))
        if (obj.has("LatLng")) return extractLatLng(obj.get("LatLng"))
        if (obj.has("placeLocation")) return extractLatLng(obj.get("placeLocation"))
        val lat = obj.double("latitude") ?: obj.double("lat")
        val lon = obj.double("longitude") ?: obj.double("lng") ?: obj.double("lon")
        if (lat != null && lon != null) return validPair(lat, lon)
        return null
    }

    internal fun parseLatLng(raw: String?): Pair<Double, Double>? {
        if (raw.isNullOrBlank()) return null
        var text = raw.trim()
        if (text.startsWith("geo:", ignoreCase = true)) {
            text = text.substring(4)
        }
        text = text.replace("°", "").replace("º", "")
        val parts = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        return validPair(lat, lon)
    }

    private fun validPair(lat: Double, lon: Double): Pair<Double, Double>? {
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        if (lat == 0.0 && lon == 0.0) return null
        return lat to lon
    }

    internal fun parseTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val ts = raw.trim()
        return try {
            if (ts.all { it.isDigit() }) {
                val n = ts.toLong()
                if (n < 4_102_444_800L) n * 1000 else n
            } else {
                try {
                    Instant.parse(ts).toEpochMilli()
                } catch (_: Exception) {
                    OffsetDateTime.parse(ts).toInstant().toEpochMilli()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

    private fun JsonObject.obj(name: String): JsonObject? = get(name)?.asObjectOrNull()

    private fun JsonObject.array(name: String): JsonArray? {
        val value = get(name) ?: return null
        return if (value.isJsonArray) value.asJsonArray else null
    }

    private fun JsonObject.string(name: String): String? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
        return value.asString
    }

    private fun JsonObject.double(name: String): Double? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive) return null
        val primitive = value.asJsonPrimitive
        return when {
            primitive.isNumber -> primitive.asDouble
            primitive.isString -> primitive.asString.replace("°", "").trim().toDoubleOrNull()
            else -> null
        }
    }
}
