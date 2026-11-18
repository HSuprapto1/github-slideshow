package com.roamatlas.domain.model

import java.time.Instant
import java.time.LocalDate

data class Flight(
    val id: Long = 0,
    val departureAirportCode: String,
    val departureAirportName: String? = null,
    val departureCity: String? = null,
    val departureCountryCode: String? = null,
    val arrivalAirportCode: String,
    val arrivalAirportName: String? = null,
    val arrivalCity: String? = null,
    val arrivalCountryCode: String? = null,
    val departureTime: Instant,
    val arrivalTime: Instant,
    val flightNumber: String? = null,
    val airline: String? = null,
    val aircraftType: String? = null,
    val distanceKm: Double? = null,
    val durationMinutes: Int? = null,
    val seatNumber: String? = null,
    val bookingReference: String? = null,
    val notes: String? = null,
    val photoUri: String? = null,
    val source: FlightSource = FlightSource.MANUAL_ENTRY,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

enum class FlightSource {
    MANUAL_ENTRY,
    IMPORTED,
    INFERRED_FROM_TIMELINE
}

data class TravelStatistics(
    val totalCountriesVisited: Int = 0,
    val totalCitiesVisited: Int = 0,
    val totalLocationsRecorded: Int = 0,
    val totalFlights: Int = 0,
    val totalFlightDistanceKm: Double = 0.0,
    val totalFlightDurationMinutes: Int = 0,
    val longestFlightKm: Double? = null,
    val longestFlightRoute: String? = null,
    val mostVisitedCountry: String? = null,
    val mostVisitedCountryDays: Int = 0,
    val daysOutsideHomeCountry: Int = 0,
    val daysInsideHomeCountry: Int = 0,
    val totalDaysTracked: Int = 0,
    val earliestRecordDate: LocalDate? = null,
    val latestRecordDate: LocalDate? = null,
    val countriesVisitedList: List<CountryVisitStats> = emptyList(),
    val yearlyStats: List<YearlyTravelStats> = emptyList()
)

data class CountryVisitStats(
    val countryCode: String,
    val countryName: String,
    val daysSpent: Int,
    val visitCount: Int,
    val firstVisit: LocalDate? = null,
    val lastVisit: LocalDate? = null,
    val classification: CountryVisitClassification = CountryVisitClassification.VISITED,
    val classificationReason: String? = null
)

enum class CountryVisitClassification {
    VISITED,
    TRANSIT,
    UNCERTAIN
}

data class YearlyTravelStats(
    val year: Int,
    val countriesVisited: Int,
    val daysTracked: Int,
    val flights: Int,
    val flightDistanceKm: Double
)

data class Trip(
    val id: Long = 0,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val destinationCountryCode: String? = null,
    val destinationCity: String? = null,
    val notes: String? = null,
    val coverPhotoUri: String? = null,
    val photoUris: List<String> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
