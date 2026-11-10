package com.roamatlas.domain.model

import java.time.Instant

enum class LocationSource {
    GPS_TRACKING,
    GOOGLE_TIMELINE,
    MANUAL_ENTRY
}

enum class DataConfidence {
    VERIFIED,
    UNCERTAIN,
    MISSING
}

data class LocationRecord(
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Instant,
    val source: LocationSource,
    val accuracy: Float? = null,
    val altitude: Double? = null,
    val speed: Float? = null,
    val countryCode: String? = null,
    val countryName: String? = null,
    val city: String? = null,
    val confidence: DataConfidence = DataConfidence.VERIFIED,
    val originalSourceId: String? = null,
    val notes: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
