package com.roamatlas.data.flight

import com.roamatlas.data.airport.Airport
import com.roamatlas.data.local.entity.FlightEntity
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Flight times on tickets are **local to each airport**. Duration must compare
 * real instants (local wall-clock + airport timezone), not same-zone clock math.
 *
 * Older imports often stored both times in the phone/UTC zone, which under-counts
 * long-haul legs (e.g. SIN→CDG showing ~7h instead of ~13h).
 */
object FlightScheduleTimes {

    data class NormalizedSchedule(
        val departureMillis: Long,
        val arrivalMillis: Long,
        val durationMinutes: Long
    )

    fun durationMinutes(
        flight: FlightEntity,
        departureAirport: Airport?,
        arrivalAirport: Airport?
    ): Long {
        val distance = flight.distanceKm
            ?: haversineKm(
                departureAirport?.latitude,
                departureAirport?.longitude,
                arrivalAirport?.latitude,
                arrivalAirport?.longitude
            )
        return durationMinutes(
            departureMillis = flight.estimatedDepartureTimeMillis ?: flight.departureTimeMillis,
            arrivalMillis = flight.estimatedArrivalTimeMillis ?: flight.arrivalTimeMillis,
            departureTimeZoneId = departureAirport?.timeZoneId,
            arrivalTimeZoneId = arrivalAirport?.timeZoneId,
            distanceKm = distance,
            storedDurationMinutes = flight.durationMinutes
        )
    }

    fun durationMinutes(
        departureMillis: Long,
        arrivalMillis: Long,
        departureTimeZoneId: String?,
        arrivalTimeZoneId: String?,
        distanceKm: Double? = null,
        storedDurationMinutes: Int? = null
    ): Long {
        val normalized = normalize(
            departureMillis = departureMillis,
            arrivalMillis = arrivalMillis,
            departureTimeZoneId = departureTimeZoneId,
            arrivalTimeZoneId = arrivalTimeZoneId,
            distanceKm = distanceKm
        )
        // Prefer timezone-aware result; only keep stored duration if it matches closely.
        val stored = storedDurationMinutes?.takeIf { it > 0 }?.toLong()
        if (stored != null && abs(stored - normalized.durationMinutes) <= 15) {
            return stored
        }
        return normalized.durationMinutes
    }

    /**
     * Re-interpret stored millis as airport-local schedules when needed.
     */
    fun normalize(
        departureMillis: Long,
        arrivalMillis: Long,
        departureTimeZoneId: String?,
        arrivalTimeZoneId: String?,
        distanceKm: Double? = null
    ): NormalizedSchedule {
        val rawMinutes = ((arrivalMillis - departureMillis) / 60_000L).coerceAtLeast(0)
        val depZone = parseZone(departureTimeZoneId)
        val arrZone = parseZone(arrivalTimeZoneId)
        if (depZone == null || arrZone == null) {
            return NormalizedSchedule(departureMillis, arrivalMillis, rawMinutes)
        }

        val expected = expectedMinutes(distanceKm)
        val candidates = linkedSetOf(
            ZoneId.systemDefault(),
            ZoneOffset.UTC,
            depZone,
            arrZone
        ).map { sourceZone ->
            reinterpret(departureMillis, arrivalMillis, sourceZone, depZone, arrZone)
        }.distinctBy { it.durationMinutes }

        val best = selectBest(candidates, rawMinutes, expected)
        return best
    }

    /**
     * Convert a date + local hour/minute into an Instant in the airport timezone.
     */
    fun combineLocalDateAndTimeMillis(
        dateMillis: Long,
        hour: Int,
        minute: Int,
        airportTimeZoneId: String?
    ): Long {
        val zone = parseZone(airportTimeZoneId) ?: ZoneId.systemDefault()
        // Date pickers usually encode the chosen calendar day at UTC midnight.
        val date = Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }

    /**
     * Take millis that were built in [sourceZone] wall time and rebuild in [airportZone].
     */
    fun reinterpretMillisAsAirportLocal(
        millis: Long,
        sourceZone: ZoneId = ZoneId.systemDefault(),
        airportTimeZoneId: String?
    ): Long {
        val airportZone = parseZone(airportTimeZoneId) ?: return millis
        val local = Instant.ofEpochMilli(millis).atZone(sourceZone).toLocalDateTime()
        return local.atZone(airportZone).toInstant().toEpochMilli()
    }

    private fun reinterpret(
        departureMillis: Long,
        arrivalMillis: Long,
        sourceZone: ZoneId,
        depZone: ZoneId,
        arrZone: ZoneId
    ): NormalizedSchedule {
        val depLocal = Instant.ofEpochMilli(departureMillis).atZone(sourceZone).toLocalDateTime()
        var arrLocal = Instant.ofEpochMilli(arrivalMillis).atZone(sourceZone).toLocalDateTime()
        var depInstant = depLocal.atZone(depZone).toInstant()
        var arrInstant = arrLocal.atZone(arrZone).toInstant()
        // Overnight arrival printed on next local morning in destination TZ.
        var guard = 0
        while (!arrInstant.isAfter(depInstant) && guard < 2) {
            arrLocal = arrLocal.plusDays(1)
            arrInstant = arrLocal.atZone(arrZone).toInstant()
            guard++
        }
        val minutes = Duration.between(depInstant, arrInstant).toMinutes().coerceAtLeast(0)
        return NormalizedSchedule(
            departureMillis = depInstant.toEpochMilli(),
            arrivalMillis = arrInstant.toEpochMilli(),
            durationMinutes = minutes
        )
    }

    private fun selectBest(
        candidates: List<NormalizedSchedule>,
        rawMinutes: Long,
        expectedMinutes: Long?
    ): NormalizedSchedule {
        if (candidates.isEmpty()) {
            return NormalizedSchedule(0, 0, rawMinutes)
        }
        if (expectedMinutes != null) {
            return candidates.minBy { abs(it.durationMinutes - expectedMinutes) }
        }
        // When zones differ and we have no distance, prefer the candidate that
        // is not the naive under-count if a longer plausible one exists.
        val plausible = candidates.filter { it.durationMinutes in 20..(22 * 60) }
        if (plausible.isEmpty()) return candidates.first()
        val raw = plausible.firstOrNull { it.durationMinutes == rawMinutes }
        if (raw != null && plausible.size == 1) return raw
        // Prefer longer among plausible when raw is the shortest (classic TZ bug).
        val shortest = plausible.minOf { it.durationMinutes }
        return if (rawMinutes == shortest && plausible.any { it.durationMinutes > rawMinutes + 90 }) {
            plausible.maxBy { it.durationMinutes }
        } else {
            plausible.firstOrNull { it.durationMinutes == rawMinutes } ?: plausible.first()
        }
    }

    private fun expectedMinutes(distanceKm: Double?): Long? {
        if (distanceKm == null || distanceKm < 200) return null
        // Typical jet block time ≈ distance/850 km/h + 40 min taxi/climb buffer.
        return ((distanceKm / 850.0) * 60.0 + 40.0).toLong().coerceIn(45, 20 * 60)
    }

    fun haversineKm(
        lat1: Double?,
        lon1: Double?,
        lat2: Double?,
        lon2: Double?
    ): Double? {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return null
        val earth = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return earth * c
    }

    private fun parseZone(id: String?): ZoneId? =
        id?.takeIf { it.isNotBlank() }?.let { runCatching { ZoneId.of(it) }.getOrNull() }
}
