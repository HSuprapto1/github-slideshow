package com.roamatlas.data.local

import com.roamatlas.data.local.entity.DayTrackerEntity
import com.roamatlas.data.local.entity.LocationEntity
import com.roamatlas.domain.model.CountingMode
import com.roamatlas.domain.model.DataConfidence
import com.roamatlas.domain.model.DayCountingRule
import com.roamatlas.domain.model.DayTracker
import com.roamatlas.domain.model.LocationRecord
import com.roamatlas.domain.model.LocationSource
import java.time.Instant
import java.time.LocalDate

fun LocationEntity.toDomain(): LocationRecord {
    return LocationRecord(
        id = id,
        latitude = latitude,
        longitude = longitude,
        timestamp = Instant.ofEpochMilli(timestampMillis),
        source = LocationSource.valueOf(source),
        accuracy = accuracy,
        altitude = altitude,
        speed = speed,
        countryCode = countryCode,
        countryName = countryName,
        city = city,
        confidence = DataConfidence.valueOf(confidence),
        originalSourceId = originalSourceId,
        notes = notes,
        createdAt = Instant.ofEpochMilli(createdAtMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtMillis)
    )
}

fun LocationRecord.toEntity(): LocationEntity {
    return LocationEntity(
        id = id,
        latitude = latitude,
        longitude = longitude,
        timestampMillis = timestamp.toEpochMilli(),
        source = source.name,
        accuracy = accuracy,
        altitude = altitude,
        speed = speed,
        countryCode = countryCode,
        countryName = countryName,
        city = city,
        confidence = confidence.name,
        originalSourceId = originalSourceId,
        notes = notes,
        createdAtMillis = createdAt.toEpochMilli(),
        updatedAtMillis = updatedAt.toEpochMilli()
    )
}

fun DayTrackerEntity.toDomain(): DayTracker {
    return DayTracker(
        id = id,
        name = name,
        countryCode = countryCode,
        countryName = countryName,
        countingMode = CountingMode.valueOf(countingMode),
        startDate = startDateEpochDay?.let { LocalDate.ofEpochDay(it) },
        endDate = endDateEpochDay?.let { LocalDate.ofEpochDay(it) },
        year = year,
        thresholdDays = thresholdDays,
        countingRule = DayCountingRule.valueOf(countingRule),
        countInsideCountry = countInsideCountry,
        displayOrder = displayOrder,
        createdAt = Instant.ofEpochMilli(createdAtMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtMillis)
    )
}

fun DayTracker.toEntity(): DayTrackerEntity {
    return DayTrackerEntity(
        id = id,
        name = name,
        countryCode = countryCode,
        countryName = countryName,
        countingMode = countingMode.name,
        startDateEpochDay = startDate?.toEpochDay(),
        endDateEpochDay = endDate?.toEpochDay(),
        year = year,
        thresholdDays = thresholdDays,
        countingRule = countingRule.name,
        countInsideCountry = countInsideCountry,
        displayOrder = displayOrder,
        createdAtMillis = createdAt.toEpochMilli(),
        updatedAtMillis = updatedAt.toEpochMilli()
    )
}
