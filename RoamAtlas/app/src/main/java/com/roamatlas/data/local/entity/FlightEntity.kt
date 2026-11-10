package com.roamatlas.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "flights",
    indices = [
        Index(value = ["departureTimeMillis"]),
        Index(value = ["departureAirportCode"]),
        Index(value = ["arrivalAirportCode"])
    ]
)
data class FlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val departureAirportCode: String,
    val departureAirportName: String?,
    val departureCity: String?,
    val departureCountryCode: String?,
    val arrivalAirportCode: String,
    val arrivalAirportName: String?,
    val arrivalCity: String?,
    val arrivalCountryCode: String?,
    val departureTimeMillis: Long,
    val arrivalTimeMillis: Long,
    val flightNumber: String?,
    val airline: String?,
    val aircraftType: String?,
    val distanceKm: Double?,
    val durationMinutes: Int?,
    val seatNumber: String?,
    val bookingReference: String?,
    val notes: String?,
    val photoUri: String?,
    val source: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long?,
    val destinationCountryCode: String?,
    val destinationCity: String?,
    val notes: String?,
    val coverPhotoUri: String?,
    val photoUrisJson: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    val key: String,
    val value: String
) {
    companion object {
        const val KEY_HOME_COUNTRY_CODE = "home_country_code"
        const val KEY_HOME_COUNTRY_NAME = "home_country_name"
        const val KEY_DISTANCE_UNIT = "distance_unit"
        const val KEY_DATE_FORMAT = "date_format"
    }
}
