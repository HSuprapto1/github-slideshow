package com.roamatlas.data.timeline

import com.google.gson.annotations.SerializedName

/**
 * Google Takeout Location History JSON models.
 * Based on documented format from: https://locationhistoryformat.com/
 * 
 * Supports:
 * - Records.json (raw location records)
 * - Semantic Location History files (YYYY_MONTH.json)
 */

// ============================================
// Records.json Format (Raw Location History)
// ============================================

data class RecordsFile(
    @SerializedName("locations")
    val locations: List<LocationRecord>? = null
)

data class LocationRecord(
    @SerializedName("timestamp")
    val timestamp: String? = null,
    
    @SerializedName("timestampMs")
    val timestampMs: String? = null,
    
    @SerializedName("latitudeE7")
    val latitudeE7: Long? = null,
    
    @SerializedName("longitudeE7")
    val longitudeE7: Long? = null,
    
    @SerializedName("accuracy")
    val accuracy: Int? = null,
    
    @SerializedName("altitude")
    val altitude: Int? = null,
    
    @SerializedName("verticalAccuracy")
    val verticalAccuracy: Int? = null,
    
    @SerializedName("velocity")
    val velocity: Int? = null,
    
    @SerializedName("heading")
    val heading: Int? = null,
    
    @SerializedName("source")
    val source: String? = null,
    
    @SerializedName("deviceTag")
    val deviceTag: Long? = null,
    
    @SerializedName("platformType")
    val platformType: String? = null,
    
    @SerializedName("placeId")
    val placeId: String? = null,
    
    @SerializedName("osLevel")
    val osLevel: Int? = null,
    
    @SerializedName("formFactor")
    val formFactor: String? = null,
    
    @SerializedName("activity")
    val activity: List<ActivityRecord>? = null
)

data class ActivityRecord(
    @SerializedName("timestamp")
    val timestamp: String? = null,
    
    @SerializedName("timestampMs")
    val timestampMs: String? = null,
    
    @SerializedName("activity")
    val activity: List<ActivityInfo>? = null
)

data class ActivityInfo(
    @SerializedName("type")
    val type: String? = null,
    
    @SerializedName("confidence")
    val confidence: Int? = null
)

// ============================================
// Semantic Location History Format
// ============================================

data class SemanticLocationFile(
    @SerializedName("timelineObjects")
    val timelineObjects: List<TimelineObject>? = null
)

data class TimelineObject(
    @SerializedName("placeVisit")
    val placeVisit: PlaceVisit? = null,
    
    @SerializedName("activitySegment")
    val activitySegment: ActivitySegment? = null
)

data class PlaceVisit(
    @SerializedName("location")
    val location: SemanticLocation? = null,
    
    @SerializedName("duration")
    val duration: Duration? = null,
    
    @SerializedName("placeConfidence")
    val placeConfidence: String? = null,
    
    @SerializedName("visitConfidence")
    val visitConfidence: Int? = null,
    
    @SerializedName("centerLatE7")
    val centerLatE7: Long? = null,
    
    @SerializedName("centerLngE7")
    val centerLngE7: Long? = null,
    
    @SerializedName("editConfirmationStatus")
    val editConfirmationStatus: String? = null,
    
    @SerializedName("otherCandidateLocations")
    val otherCandidateLocations: List<SemanticLocation>? = null
)

data class SemanticLocation(
    @SerializedName("latitudeE7")
    val latitudeE7: Long? = null,
    
    @SerializedName("longitudeE7")
    val longitudeE7: Long? = null,
    
    @SerializedName("placeId")
    val placeId: String? = null,
    
    @SerializedName("address")
    val address: String? = null,
    
    @SerializedName("name")
    val name: String? = null,
    
    @SerializedName("sourceInfo")
    val sourceInfo: SourceInfo? = null,
    
    @SerializedName("locationConfidence")
    val locationConfidence: Double? = null
)

data class SourceInfo(
    @SerializedName("deviceTag")
    val deviceTag: Long? = null
)

data class ActivitySegment(
    @SerializedName("startLocation")
    val startLocation: SemanticLocation? = null,
    
    @SerializedName("endLocation")
    val endLocation: SemanticLocation? = null,
    
    @SerializedName("duration")
    val duration: Duration? = null,
    
    @SerializedName("distance")
    val distance: Int? = null,
    
    @SerializedName("activityType")
    val activityType: String? = null,
    
    @SerializedName("confidence")
    val confidence: String? = null,
    
    @SerializedName("activities")
    val activities: List<ActivityProbability>? = null,
    
    @SerializedName("waypointPath")
    val waypointPath: WaypointPath? = null,
    
    @SerializedName("simplifiedRawPath")
    val simplifiedRawPath: SimplifiedRawPath? = null,
    
    @SerializedName("editConfirmationStatus")
    val editConfirmationStatus: String? = null
)

data class Duration(
    @SerializedName("startTimestamp")
    val startTimestamp: String? = null,
    
    @SerializedName("startTimestampMs")
    val startTimestampMs: String? = null,
    
    @SerializedName("endTimestamp")
    val endTimestamp: String? = null,
    
    @SerializedName("endTimestampMs")
    val endTimestampMs: String? = null
)

data class ActivityProbability(
    @SerializedName("activityType")
    val activityType: String? = null,
    
    @SerializedName("probability")
    val probability: Double? = null
)

data class WaypointPath(
    @SerializedName("waypoints")
    val waypoints: List<Waypoint>? = null,
    
    @SerializedName("source")
    val source: String? = null,
    
    @SerializedName("distanceMeters")
    val distanceMeters: Double? = null
)

data class Waypoint(
    @SerializedName("latE7")
    val latE7: Long? = null,
    
    @SerializedName("lngE7")
    val lngE7: Long? = null
)

data class SimplifiedRawPath(
    @SerializedName("points")
    val points: List<PathPoint>? = null,
    
    @SerializedName("source")
    val source: String? = null,
    
    @SerializedName("distanceMeters")
    val distanceMeters: Double? = null
)

data class PathPoint(
    @SerializedName("latE7")
    val latE7: Long? = null,
    
    @SerializedName("lngE7")
    val lngE7: Long? = null,
    
    @SerializedName("timestamp")
    val timestamp: String? = null,
    
    @SerializedName("timestampMs")
    val timestampMs: String? = null,
    
    @SerializedName("accuracyMeters")
    val accuracyMeters: Int? = null
)
