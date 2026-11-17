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
 * Flight times on tickets are **local at each airport**.
 *
 * RoamAtlas often stores those wall-clock values as millis in the phone/default
 * zone (so Share text shows the ticket times). Duration must still compare real
 * instants: read the printed local time back out, then apply each airport's TZ.
 *
 * Never rewrite stored millis from display code — that made times flip.
 */
object FlightScheduleTimes {

    data class NormalizedSchedule(
        val departureMillis: Long,
        val arrivalMillis: Long,
        val durationMinutes: Long
    )

    /**
     * Real block / fly time. Does not mutate stored ticket times.
     *
     * Ticket clocks are local at each airport. Convert both to real instants
     * with airport timezones, then subtract:
     * - CGK 18:10 (UTC+7) → SIN 20:55 (UTC+8) = 1h 45m
     * - CDG 10:55 (UTC+1) → GIG 18:30 (UTC−3) = 11h 35m
     */
    fun durationMinutes(
        flight: FlightEntity,
        departureAirport: Airport? = null,
        arrivalAirport: Airport? = null
    ): Long {
        val dep = flight.departureTimeMillis
        val arr = flight.arrivalTimeMillis
        val raw = ((arr - dep) / 60_000L).coerceAtLeast(0)
        val depZone = departureAirport?.timeZoneId
        val arrZone = arrivalAirport?.timeZoneId
        if (depZone.isNullOrBlank() || arrZone.isNullOrBlank()) {
            return flight.durationMinutes?.takeIf { it > 0 }?.toLong() ?: raw
        }
        val distance = flight.distanceKm ?: haversineKm(
            departureAirport?.latitude,
            departureAirport?.longitude,
            arrivalAirport?.latitude,
            arrivalAirport?.longitude
        )
        val normalized = normalize(
            departureMillis = dep,
            arrivalMillis = arr,
            departureTimeZoneId = depZone,
            arrivalTimeZoneId = arrZone,
            distanceKm = distance
        ).durationMinutes
        // Guard: short hop must never become an overnight ~17h glitch.
        if (distance != null && distance < 2_500.0 && normalized > 6 * 60) {
            return if (raw in 30..(6 * 60)) raw else normalized
        }
        val stored = flight.durationMinutes?.takeIf { it > 0 }?.toLong()
        if (stored != null && abs(stored - normalized) <= 20) return stored
        return normalized
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
        val stored = storedDurationMinutes?.takeIf { it > 0 }?.toLong()
        if (stored != null && abs(stored - normalized.durationMinutes) <= 15) {
            return stored
        }
        return normalized.durationMinutes
    }

    /**
     * Re-interpret stored millis as airport-local schedules when needed.
     * For display/duration only — do not write results back into Room from UI.
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

        return selectBest(candidates, rawMinutes, expected)
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
        val date = Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }

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
        val plausible = candidates.filter { it.durationMinutes in 20..(22 * 60) }
        if (plausible.isEmpty()) return candidates.first()
        val raw = plausible.firstOrNull { it.durationMinutes == rawMinutes }
        if (raw != null && plausible.size == 1) return raw
        val shortest = plausible.minOf { it.durationMinutes }
        return if (rawMinutes == shortest && plausible.any { it.durationMinutes > rawMinutes + 90 }) {
            plausible.maxBy { it.durationMinutes }
        } else {
            plausible.firstOrNull { it.durationMinutes == rawMinutes } ?: plausible.first()
        }
    }

    private fun expectedMinutes(distanceKm: Double?): Long? {
        if (distanceKm == null || distanceKm < 200) return null
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
