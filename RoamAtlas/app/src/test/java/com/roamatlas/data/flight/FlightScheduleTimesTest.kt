package com.roamatlas.data.flight

import com.roamatlas.data.airport.Airport
import com.roamatlas.data.local.entity.FlightEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class FlightScheduleTimesTest {

    private val cgk = airport("CGK", "Asia/Jakarta", -6.12557, 106.655998)
    private val sin = airport("SIN", "Asia/Singapore", 1.35019, 103.994003)
    private val cdg = airport("CDG", "Europe/Paris", 49.00896, 2.554117)
    private val gig = airport("GIG", "America/Sao_Paulo", -22.809999, -43.250557)

    @Test
    fun `brazil crew CGK to SIN is about 2h45m not 14h`() {
        // Ticket wall clocks stored in a single zone (how Share displays them).
        val zone = ZoneId.of("Asia/Jakarta")
        val dep = LocalDateTime.of(2026, 11, 17, 18, 10).atZone(zone).toInstant().toEpochMilli()
        val arr = LocalDateTime.of(2026, 11, 17, 20, 55).atZone(zone).toInstant().toEpochMilli()
        val flight = flight("CGK", "SIN", dep, arr)
        val minutes = FlightScheduleTimes.durationMinutes(flight, cgk, sin)
        // Ticket local clocks: 18:10 → 20:55 = 2h 45m (do not TZ-inflate).
        assertEquals(165L, minutes)
    }

    @Test
    fun `brazil crew SIN transit is 1h55m`() {
        val zone = ZoneId.of("Asia/Jakarta")
        val sinArrive = LocalDateTime.of(2026, 11, 17, 20, 55).atZone(zone).toInstant().toEpochMilli()
        val sinDepart = LocalDateTime.of(2026, 11, 17, 22, 50).atZone(zone).toInstant().toEpochMilli()
        val transit = (sinDepart - sinArrive) / 60_000L
        assertEquals(115L, transit)
    }

    @Test
    fun `brazil crew SIN to CDG is about 13h not 7h`() {
        val zone = ZoneId.of("Asia/Jakarta")
        val dep = LocalDateTime.of(2026, 11, 17, 22, 50).atZone(zone).toInstant().toEpochMilli()
        val arr = LocalDateTime.of(2026, 11, 18, 6, 5).atZone(zone).toInstant().toEpochMilli()
        val rawHours = (arr - dep) / 3_600_000.0
        assertTrue("precondition: naive wall ~7h, was $rawHours", rawHours in 6.5..8.0)

        val flight = flight("SIN", "CDG", dep, arr).copy(distanceKm = 10_700.0)
        val minutes = FlightScheduleTimes.durationMinutes(flight, sin, cdg)
        assertTrue(
            "SIN→CDG expected ~12–15h, got ${minutes / 60.0}h",
            minutes in (12 * 60)..(15 * 60)
        )
    }

    @Test
    fun `brazil crew CDG to GIG is a long haul block`() {
        val zone = ZoneId.of("Asia/Jakarta")
        val dep = LocalDateTime.of(2026, 11, 18, 10, 55).atZone(zone).toInstant().toEpochMilli()
        val arr = LocalDateTime.of(2026, 11, 18, 18, 30).atZone(zone).toInstant().toEpochMilli()
        val flight = flight("CDG", "GIG", dep, arr).copy(distanceKm = 9_200.0)
        val minutes = FlightScheduleTimes.durationMinutes(flight, cdg, gig)
        assertTrue(
            "CDG→GIG expected ~10–13h, got ${minutes / 60.0}h",
            minutes in (10 * 60)..(13 * 60)
        )
    }

    @Test
    fun `singapore to paris uses airport timezones not wall clock`() {
        val depWrong = LocalDateTime.of(2024, 6, 1, 23, 0)
            .atZone(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val arrWrong = LocalDateTime.of(2024, 6, 2, 6, 0)
            .atZone(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val normalized = FlightScheduleTimes.normalize(
            departureMillis = depWrong,
            arrivalMillis = arrWrong,
            departureTimeZoneId = "Asia/Singapore",
            arrivalTimeZoneId = "Europe/Paris",
            distanceKm = 10_700.0
        )
        assertTrue(
            "expected ~12–15h for SIN→CDG, got ${normalized.durationMinutes / 60.0}h",
            normalized.durationMinutes in (12 * 60)..(15 * 60)
        )
    }

    @Test
    fun `same timezone short hop keeps simple difference`() {
        val dep = LocalDateTime.of(2024, 6, 1, 10, 0)
            .atZone(ZoneOffset.ofHours(8))
            .toInstant()
            .toEpochMilli()
        val arr = LocalDateTime.of(2024, 6, 1, 12, 15)
            .atZone(ZoneOffset.ofHours(8))
            .toInstant()
            .toEpochMilli()
        val minutes = FlightScheduleTimes.durationMinutes(
            departureMillis = dep,
            arrivalMillis = arr,
            departureTimeZoneId = "Asia/Singapore",
            arrivalTimeZoneId = "Asia/Singapore",
            distanceKm = 700.0
        )
        assertTrue(minutes in 130..140)
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

    private fun flight(dep: String, arr: String, depMillis: Long, arrMillis: Long) = FlightEntity(
        id = 1,
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
