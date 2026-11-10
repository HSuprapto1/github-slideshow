package com.roamatlas.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "day_trackers")
data class DayTrackerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val countryCode: String,
    val countryName: String,
    val countingMode: String,
    val startDateEpochDay: Long?,
    val endDateEpochDay: Long?,
    val year: Int?,
    val thresholdDays: Int?,
    val countingRule: String,
    val countInsideCountry: Boolean,
    val displayOrder: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
