package com.roamatlas.data.flight

import com.roamatlas.data.airport.Airport
import com.roamatlas.data.local.entity.FlightEntity
import com.roamatlas.domain.model.DataConfidence
import com.roamatlas.domain.model.LocationRecord
import com.roamatlas.domain.model.LocationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class FlightPathAnalyzerTest {

    private val sin = Airport(
        iataCode = "SIN",
        icaoCode = "",
        name = "Changi",
        city = "Singapore",
        countryCode = "SG",
        latitude = 1.35,
        longitude = 103.99,
        timeZoneId = "Asia/Singapore",
        type = "large_airport"
    )
    private val cdg = Airport(
        iataCode = "CDG",
        icaoCode = "",
        name = "CDG",
        city = "Paris",
        countryCode = "FR",
        latitude = 49.01,
        longitude = 2.55,
        timeZoneId = "Europe/Paris",
        type = "large_airport"
    )

    @Test
    fun `actual duration altitude speed and gap are computed`() {
        val zone = ZoneId.of("Asia/Singapore")
        val t0 = LocalDateTime.of(2026, 11, 17, 22, 55).atZone(zone).toInstant()
        val points = mutableListOf(
            point(1.36, 104.0, t0, alt = 200.0, speedMs = 80f, airborne = true),
            point(3.0, 102.0, t0.plusSeconds(20 * 60), alt = 4_000.0, speedMs = 150f, airborne = true),
            point(5.0, 100.0, t0.plusSeconds(40 * 60), alt = 11_000.0, speedMs = 250f, airborne = true),
            // 50 min gap (> 45 min threshold)
            point(
                20.0,
                80.0,
                t0.plusSeconds(40 * 60 + 50 * 60),
                alt = 11_200.0,
                speedMs = 245f,
                airborne = true
            ),
            point(35.0, 40.0, t0.plusSeconds(40 * 60 + 50 * 60 + 20 * 60), alt = 10_800.0, speedMs = 240f, airborne = true),
            point(49.0, 2.6, t0.plusSeconds(14 * 3_600), alt = 150.0, speedMs = 70f, airborne = true)
        )
        // Fill last stretch so only one intentional gap remains before cruise-end.
        // Replace sparse end with a 20-min step from the post-gap point toward landing.
        points.removeAt(points.lastIndex)
        var cursor = points.last().timestamp
        var lat = points.last().latitude
        var lon = points.last().longitude
        val end = t0.plusSeconds(14 * 3_600)
        while (cursor.plusSeconds(20 * 60).isBefore(end)) {
            cursor = cursor.plusSeconds(20 * 60)
            lat += (49.0 - lat) * 0.15
            lon += (2.6 - lon) * 0.15
            points.add(point(lat, lon, cursor, alt = 10_500.0, speedMs = 240f, airborne = true))
        }
        points.add(point(49.0, 2.6, end, alt = 150.0, speedMs = 70f, airborne = true))

        val dep = LocalDateTime.of(2026, 11, 17, 22, 50).atZone(zone).toInstant().toEpochMilli()
        val arr = LocalDateTime.of(2026, 11, 18, 6, 5)
            .atZone(ZoneId.of("Europe/Paris")).toInstant().toEpochMilli()
        val flight = FlightEntity(
            id = 2,
            departureAirportCode = "SIN",
            departureAirportName = null,
            departureCity = null,
            departureCountryCode = null,
            arrivalAirportCode = "CDG",
            arrivalAirportName = null,
            arrivalCity = null,
            arrivalCountryCode = null,
            departureTimeMillis = dep,
            arrivalTimeMillis = arr,
            flightNumber = "AF257",
            airline = "Air France",
            aircraftType = null,
            distanceKm = 10_700.0,
            durationMinutes = 855,
            seatNumber = null,
            bookingReference = null,
            notes = null,
            photoUri = null,
            source = "TEST",
            createdAtMillis = 0,
            updatedAtMillis = 0
        )
        val report = FlightPathAnalyzer.reportForFlight(
            flight = flight,
            gpsTrack = points,
            departureAirport = sin,
            arrivalAirport = cdg,
            scheduledDurationMinutes = 855
        )
        assertTrue("points=${report.points.size}", report.hasPath)
        assertEquals(1, report.gapCount)
        assertEquals(14 * 60L, report.actualDurationMinutes)
        assertEquals(11_200.0, report.maxAltitudeM!!, 0.1)
        assertTrue(report.maxSpeedKmh!! in 800.0..950.0)
        assertTrue(report.distanceKm != null && report.distanceKm!! > 1_000)
    }

    private fun point(
        lat: Double,
        lon: Double,
        instant: Instant,
        alt: Double,
        speedMs: Float,
        airborne: Boolean
    ) = LocationRecord(
        id = 0,
        latitude = lat,
        longitude = lon,
        timestamp = instant,
        source = LocationSource.GPS_TRACKING,
        altitude = alt,
        speed = speedMs,
        confidence = DataConfidence.VERIFIED,
        isAirborne = airborne
    )
}
