package com.roamatlas.data.journey

import com.roamatlas.data.airport.Airport
import com.roamatlas.data.local.entity.FlightEntity
import com.roamatlas.data.local.entity.TripEntity
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class JourneyShareFormatterTest {
    @Test
    fun `share text includes fly times transit and totals`() {
        val zone = ZoneId.of("Asia/Jakarta")
        fun t(y: Int, m: Int, d: Int, h: Int, min: Int) =
            LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

        val airports = mapOf(
            "CGK" to airport("CGK", "Asia/Jakarta", -6.12557, 106.655998),
            "SIN" to airport("SIN", "Asia/Singapore", 1.35019, 103.994003),
            "CDG" to airport("CDG", "Europe/Paris", 49.00896, 2.554117),
            "GIG" to airport("GIG", "America/Sao_Paulo", -22.809999, -43.250557)
        )
        val journey = JourneyWithFlights(
            journey = TripEntity(
                id = 1,
                name = "Brazil Crew-X to Island Challenger",
                startDateEpochDay = LocalDateTime.of(2026, 11, 17, 0, 0).toLocalDate().toEpochDay(),
                endDateEpochDay = LocalDateTime.of(2026, 11, 18, 0, 0).toLocalDate().toEpochDay(),
                destinationCountryCode = "BR",
                destinationCity = "Rio",
                notes = null,
                coverPhotoUri = null,
                photoUrisJson = null,
                createdAtMillis = 0,
                updatedAtMillis = 0
            ),
            flights = listOf(
                flight(1, "CGK", "SIN", "SQ963", "Singapore Airlines",
                    t(2026, 11, 17, 18, 10), t(2026, 11, 17, 20, 55)),
                flight(2, "SIN", "CDG", "AF257", "Air France",
                    LocalDateTime.of(2026, 11, 17, 22, 50)
                        .atZone(ZoneId.of("Asia/Singapore")).toInstant().toEpochMilli(),
                    LocalDateTime.of(2026, 11, 18, 6, 5)
                        .atZone(ZoneId.of("Europe/Paris")).toInstant().toEpochMilli()),
                flight(3, "CDG", "GIG", "AF484", "Air France",
                    t(2026, 11, 18, 10, 55), t(2026, 11, 18, 18, 30))
            )
        )

        val text = JourneyShareFormatter.formatText(
            journey = journey,
            airports = airports,
            includeBookingReferences = false,
            includeNotes = false
        )
        assertTrue(text.contains("Fly time ·"))
        assertTrue(text.contains("Transit at SIN ·"))
        assertTrue(text.contains("Transit at CDG ·"))
        assertTrue(text.contains("Total journey (with transit)"))
        assertTrue(text.contains("Total fly time"))
        assertTrue(text.contains("Total transit time"))
        assertTrue(text.contains("SQ963"))
        assertTrue(text.contains("AF257"))
        assertTrue(text.contains("AF484"))
    }

    private fun airport(iata: String, tz: String, lat: Double, lon: Double) = Airport(
        iataCode = iata,
        icaoCode = "",
        name = iata,
        city = iata,
        countryCode = "",
        latitude = lat,
        longitude = lon,
        timeZoneId = tz,
        type = "large_airport"
    )

    private fun flight(
        id: Long,
        dep: String,
        arr: String,
        number: String,
        airline: String,
        depMillis: Long,
        arrMillis: Long
    ) = FlightEntity(
        id = id,
        departureAirportCode = dep,
        departureAirportName = null,
        departureCity = null,
        departureCountryCode = null,
        arrivalAirportCode = arr,
        arrivalAirportName = null,
        arrivalCity = null,
        arrivalCountryCode = null,
        departureTimeMillis = depMillis,
        arrivalTimeMillis = arrMillis,
        flightNumber = number,
        airline = airline,
        aircraftType = null,
        distanceKm = null,
        durationMinutes = null,
        seatNumber = null,
        bookingReference = null,
        notes = null,
        photoUri = null,
        source = "TEST",
        createdAtMillis = 0,
        updatedAtMillis = 0,
        journeySequence = id.toInt()
    )
}
