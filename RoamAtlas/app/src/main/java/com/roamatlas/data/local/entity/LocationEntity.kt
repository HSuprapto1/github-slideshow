package com.roamatlas.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "locations",
    indices = [
        Index(value = ["timestampMillis"]),
        Index(value = ["countryCode"]),
        Index(value = ["source"]),
        Index(value = ["latitude", "longitude"]),
        Index(value = ["originalSourceId"], unique = true)
    ]
)
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val source: String,
    val accuracy: Float?,
    val altitude: Double?,
    val speed: Float?,
    val countryCode: String?,
    val countryName: String?,
    val city: String?,
    val confidence: String,
    val originalSourceId: String?,
    val notes: String?,
    val photoUri: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
