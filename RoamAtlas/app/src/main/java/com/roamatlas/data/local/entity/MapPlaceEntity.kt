package com.roamatlas.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Named map favorite (Home, Mom's house, office pin, etc.).
 * Radius is optional for later “near this place” matching.
 */
@Entity(
    tableName = "map_places",
    indices = [Index(value = ["name"])]
)
data class MapPlaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** Meters; null = point only. */
    val radiusMeters: Float? = null,
    val notes: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

object JourneyStatus {
    const val PLANNED = "PLANNED"
    const val IN_PROGRESS = "IN_PROGRESS"
    const val COMPLETED = "COMPLETED"
    const val CANCELLED = "CANCELLED"

    fun label(status: String?): String = when (status?.uppercase()) {
        IN_PROGRESS -> "In progress"
        COMPLETED -> "Completed"
        CANCELLED -> "Cancelled"
        else -> "Planned"
    }

    fun normalize(status: String?): String =
        when (status?.uppercase()) {
            IN_PROGRESS -> IN_PROGRESS
            COMPLETED -> COMPLETED
            CANCELLED -> CANCELLED
            else -> PLANNED
        }
}
