package com.roamatlas.domain.model

import java.time.LocalDate

enum class CountingMode {
    CALENDAR_YEAR,
    CUSTOM_DATE_RANGE,
    ROLLING_12_MONTHS,
    CUSTOM_START_UNTIL_TODAY
}

enum class DayCountingRule {
    ANY_PRESENCE,
    MAJORITY_OF_DAY,
    OVERNIGHT_STAY,
    FULL_24_HOURS
}

data class DayTracker(
    val id: Long = 0,
    val name: String,
    val countryCode: String,
    val countryName: String,
    val countingMode: CountingMode,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val year: Int? = null,
    val thresholdDays: Int? = null,
    val countingRule: DayCountingRule = DayCountingRule.ANY_PRESENCE,
    val countInsideCountry: Boolean = true,
    val displayOrder: Int = 0,
    val createdAt: java.time.Instant = java.time.Instant.now(),
    val updatedAt: java.time.Instant = java.time.Instant.now()
)

data class TrackerResult(
    val tracker: DayTracker,
    val totalDays: Int,
    val verifiedDays: Int,
    val uncertainDays: Int,
    val missingDays: Int,
    val daysInRange: Int,
    val percentageComplete: Float,
    val thresholdExceeded: Boolean?,
    val lastCalculated: java.time.Instant,
    val daysUntilRangeEnd: Int = 0,
    val daysNeededForThreshold: Int? = null
)
