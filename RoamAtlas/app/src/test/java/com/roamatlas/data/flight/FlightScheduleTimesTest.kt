package com.roamatlas.data.flight

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class FlightScheduleTimesTest {
    @Test
    fun `singapore to paris uses airport timezones not wall clock`() {
        // Classic bug: both times stored as the same zone's wall clock.
        // SIN 23:00 / CDG 06:00 next morning looks like ~7h, but is ~13h real block time.
        val depWrong = LocalDateTime.of(2024, 6, 1, 23, 0)
            .atZone(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val arrWrong = LocalDateTime.of(2024, 6, 2, 6, 0)
            .atZone(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val rawHours = (arrWrong - depWrong) / 3_600_000.0
        assertTrue("precondition: naive duration ~7h, was $rawHours", rawHours in 6.5..7.5)

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
}
