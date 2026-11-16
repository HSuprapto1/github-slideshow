package com.roamatlas.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "flights",
    indices = [
        Index(value = ["departureTimeMillis"]),
        Index(value = ["departureAirportCode"]),
        Index(value = ["arrivalAirportCode"]),
        Index(value = ["journeyId"])
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
    val updatedAtMillis: Long,
    val journeyId: Long? = null,
    val journeySequence: Int? = null,
    val estimatedDepartureTimeMillis: Long? = null,
    val estimatedArrivalTimeMillis: Long? = null,
    val flightStatus: String? = null,
    val separateTicketFromPrevious: Boolean? = null,
    val minimumConnectionMinutes: Int? = null,
    /** FLIGHT, TRAIN, BUS, or HOTEL — stations/cities reuse airport code fields. */
    val transportMode: String = TransportMode.FLIGHT
)

object TransportMode {
    const val FLIGHT = "FLIGHT"
    const val TRAIN = "TRAIN"
    const val BUS = "BUS"
    const val HOTEL = "HOTEL"

    fun label(mode: String?): String = when (mode?.uppercase()) {
        TRAIN -> "Train"
        BUS -> "Bus"
        HOTEL -> "Hotel"
        else -> "Flight"
    }

    fun isFlight(mode: String?): Boolean =
        mode.isNullOrBlank() || mode.equals(FLIGHT, ignoreCase = true)

    fun isHotel(mode: String?): Boolean =
        mode.equals(HOTEL, ignoreCase = true)

    fun isStay(mode: String?): Boolean = isHotel(mode)
}

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
    /** PLANNED, IN_PROGRESS, COMPLETED, CANCELLED — see [JourneyStatus]. */
    val journeyStatus: String = JourneyStatus.PLANNED,
    val cancelReason: String? = null,
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
