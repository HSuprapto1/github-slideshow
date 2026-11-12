package com.roamatlas.data.journey

import com.roamatlas.data.local.entity.FlightEntity
import com.roamatlas.data.local.entity.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class JourneyWithFlightsTest {
    @Test
    fun `arrival delay reduces connection and triggers warning`() {
        val first = flight(
            id = 1,
            departure = "CGK",
            arrival = "SIN",
            departureTime = 1_000_000,
            arrivalTime = 4_600_000
        ).copy(estimatedArrivalTimeMillis = 8_200_000)
        val second = flight(
            id = 2,
            departure = "SIN",
            arrival = "HND",
            departureTime = 11_800_000,
            arrivalTime = 25_000_000
        ).copy(minimumConnectionMinutes = 90)
        val journey = JourneyWithFlights(
            journey = TripEntity(
                id = 1,
                name = "Test journey",
                startDateEpochDay = 0,
                endDateEpochDay = 1,
                destinationCountryCode = "JP",
                destinationCity = "Tokyo",
                notes = null,
                coverPhotoUri = null,
                photoUrisJson = null,
                createdAtMillis = 0,
                updatedAtMillis = 0
            ),
            flights = listOf(first, second)
        )

        val connection = journey.connections.single()
        assertEquals(120, connection.scheduledMinutes)
        assertEquals(60, connection.effectiveMinutes)
        assertEquals(ConnectionRisk.BELOW_RECOMMENDED, connection.risk)
    }

    private fun flight(
        id: Long,
        departure: String,
        arrival: String,
        departureTime: Long,
        arrivalTime: Long
    ) = FlightEntity(
        id = id,
        departureAirportCode = departure,
        departureAirportName = null,
        departureCity = null,
        departureCountryCode = null,
        arrivalAirportCode = arrival,
        arrivalAirportName = null,
        arrivalCity = null,
        arrivalCountryCode = null,
        departureTimeMillis = departureTime,
        arrivalTimeMillis = arrivalTime,
        flightNumber = null,
        airline = null,
        aircraftType = null,
        distanceKm = null,
        durationMinutes = null,
        seatNumber = null,
        bookingReference = null,
        notes = null,
        photoUri = null,
        source = "TEST",
        createdAtMillis = 0,
        updatedAtMillis = 0
    )
}
