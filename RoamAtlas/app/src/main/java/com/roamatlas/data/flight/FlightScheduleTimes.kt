package com.roamatlas.data.flight

import com.roamatlas.data.airport.Airport
import com.roamatlas.data.local.entity.FlightEntity
import com.roamatlas.data.local.entity.TransportMode
import com.roamatlas.data.local.entity.TravelLegStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Flight times on tickets are **local at each airport**.
 *
 * RoamAtlas often stores those wall-clock values as millis in the phone/default
 * zone (so Share text shows the ticket times). Duration must still compare real
 * instants: read the printed local time back out, then apply each airport's TZ.
 *
 * Waiting / countdown must use the **departure airport timezone** (SIN now,
 * CDG later), not the phone zone. GPS only confirms you are at that airport.
 *
 * Never rewrite stored millis from display code — that made times flip.
 */
object FlightScheduleTimes {

    data class NormalizedSchedule(
        val departureMillis: Long,
        val arrivalMillis: Long,
        val durationMinutes: Long
    )

    data class NextDepartureWait(
        val flight: FlightEntity,
        val departureInstantMillis: Long,
        val minutesRemaining: Long,
        val departureAirportCode: String,
        val gpsConfirmsAtAirport: Boolean
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
        // Identity: millis already true UTC (ATPI airport-TZ stamps).
        // Also try common "typed in one zone" sources. Do not collapse by
        // duration alone — UTC reinterpret of Jakarta-typed times can keep
        // ~14h fly time while shifting SIN 22:50 → 15:50.
        val sourceZones = linkedSetOf(
            ZoneId.systemDefault(),
            ZoneOffset.UTC,
            depZone,
            arrZone,
            ZoneId.of("Asia/Jakarta"),
            ZoneId.of("Asia/Singapore"),
            ZoneId.of("Europe/Paris")
        )
        val candidates = buildList {
            add(identityCandidate(departureMillis, arrivalMillis, rawMinutes, depZone))
            sourceZones.forEach { sourceZone ->
                add(
                    scored(
                        reinterpret(departureMillis, arrivalMillis, sourceZone, depZone, arrZone),
                        sourceZone,
                        depZone
                    )
                )
            }
        }
        return selectBest(candidates, rawMinutes, expected)
    }

    private data class ScoredSchedule(
        val schedule: NormalizedSchedule,
        val sourceOffsetHours: Int,
        val depOffsetHours: Int
    )

    private fun identityCandidate(
        departureMillis: Long,
        arrivalMillis: Long,
        rawMinutes: Long,
        depZone: ZoneId
    ): ScoredSchedule {
        val depOffset = depZone.rules.getOffset(Instant.ofEpochMilli(departureMillis)).totalSeconds / 3600
        return ScoredSchedule(
            schedule = NormalizedSchedule(departureMillis, arrivalMillis, rawMinutes),
            sourceOffsetHours = depOffset,
            depOffsetHours = depOffset
        )
    }

    private fun scored(
        schedule: NormalizedSchedule,
        sourceZone: ZoneId,
        depZone: ZoneId
    ): ScoredSchedule {
        val at = Instant.ofEpochMilli(schedule.departureMillis)
        return ScoredSchedule(
            schedule = schedule,
            sourceOffsetHours = sourceZone.rules.getOffset(at).totalSeconds / 3600,
            depOffsetHours = depZone.rules.getOffset(at).totalSeconds / 3600
        )
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

    fun wallClockMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        airportTimeZoneId: String
    ): Long {
        val zone = parseZone(airportTimeZoneId) ?: ZoneId.systemDefault()
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
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

    /** True departure instant for a leg (airport TZ), including estimates when set. */
    fun departureInstantMillis(
        flight: FlightEntity,
        departureAirport: Airport?,
        arrivalAirport: Airport?
    ): Long {
        val dep = flight.estimatedDepartureTimeMillis ?: flight.departureTimeMillis
        val arr = flight.estimatedArrivalTimeMillis ?: flight.arrivalTimeMillis
        val distance = flight.distanceKm ?: haversineKm(
            departureAirport?.latitude,
            departureAirport?.longitude,
            arrivalAirport?.latitude,
            arrivalAirport?.longitude
        )
        return normalize(
            departureMillis = dep,
            arrivalMillis = arr,
            departureTimeZoneId = departureAirport?.timeZoneId,
            arrivalTimeZoneId = arrivalAirport?.timeZoneId,
            distanceKm = distance
        ).departureMillis
    }

    /** True arrival instant for a leg (airport TZ), including estimates when set. */
    fun arrivalInstantMillis(
        flight: FlightEntity,
        departureAirport: Airport?,
        arrivalAirport: Airport?
    ): Long {
        val dep = flight.estimatedDepartureTimeMillis ?: flight.departureTimeMillis
        val arr = flight.estimatedArrivalTimeMillis ?: flight.arrivalTimeMillis
        val distance = flight.distanceKm ?: haversineKm(
            departureAirport?.latitude,
            departureAirport?.longitude,
            arrivalAirport?.latitude,
            arrivalAirport?.longitude
        )
        return normalize(
            departureMillis = dep,
            arrivalMillis = arr,
            departureTimeZoneId = departureAirport?.timeZoneId,
            arrivalTimeZoneId = arrivalAirport?.timeZoneId,
            distanceKm = distance
        ).arrivalMillis
    }

    /**
     * Next open leg waiting time. Departure airport TZ owns the clock;
     * optional GPS only confirms you are at that airport (SIN now, CDG later).
     */
    fun nextDepartureWait(
        flights: List<FlightEntity>,
        airports: Map<String, Airport>,
        nowMillis: Long,
        gpsLatitude: Double? = null,
        gpsLongitude: Double? = null,
        nearAirportRadiusKm: Double = 80.0
    ): NextDepartureWait? {
        val next = flights
            .asSequence()
            .filterNot { TransportMode.isHotel(it.transportMode) }
            .filterNot {
                val status = TravelLegStatus.normalize(it.travelLegStatus)
                status == TravelLegStatus.ARRIVED ||
                    status == TravelLegStatus.SKIPPED ||
                    status == TravelLegStatus.MISSED
            }
            .map { flight ->
                val depAirport = airports[flight.departureAirportCode.uppercase(Locale.US)]
                    ?: airports[flight.departureAirportCode]
                val arrCode = flight.actualArrivalAirportCode?.takeIf { it.isNotBlank() }
                    ?: flight.arrivalAirportCode
                val arrAirport = airports[arrCode.uppercase(Locale.US)] ?: airports[arrCode]
                flight to departureInstantMillis(flight, depAirport, arrAirport)
            }
            .firstOrNull { (_, depInstant) -> depInstant > nowMillis }
            ?: return null

        val (flight, depInstant) = next
        val code = flight.departureAirportCode.uppercase(Locale.US)
        val depAirport = airports[code] ?: airports[flight.departureAirportCode]
        val near = if (
            gpsLatitude != null &&
            gpsLongitude != null &&
            depAirport != null
        ) {
            isNearAirport(
                gpsLatitude,
                gpsLongitude,
                depAirport,
                nearAirportRadiusKm
            )
        } else {
            false
        }
        return NextDepartureWait(
            flight = flight,
            departureInstantMillis = depInstant,
            minutesRemaining = ((depInstant - nowMillis) / 60_000L).coerceAtLeast(0),
            departureAirportCode = code,
            gpsConfirmsAtAirport = near
        )
    }

    fun connectionMinutes(
        arriving: FlightEntity,
        departing: FlightEntity,
        airports: Map<String, Airport>
    ): Long {
        val arrCode = arriving.actualArrivalAirportCode?.takeIf { it.isNotBlank() }
            ?: arriving.arrivalAirportCode
        val arriveAt = arrivalInstantMillis(
            arriving,
            airports[arriving.departureAirportCode.uppercase(Locale.US)]
                ?: airports[arriving.departureAirportCode],
            airports[arrCode.uppercase(Locale.US)] ?: airports[arrCode]
        )
        val leaveAt = departureInstantMillis(
            departing,
            airports[departing.departureAirportCode.uppercase(Locale.US)]
                ?: airports[departing.departureAirportCode],
            airports[departing.arrivalAirportCode.uppercase(Locale.US)]
                ?: airports[departing.arrivalAirportCode]
        )
        return ((leaveAt - arriveAt) / 60_000L).coerceAtLeast(0)
    }

    fun isNearAirport(
        latitude: Double,
        longitude: Double,
        airport: Airport,
        radiusKm: Double = 80.0
    ): Boolean {
        val distance = haversineKm(
            latitude,
            longitude,
            airport.latitude,
            airport.longitude
        ) ?: return false
        return distance <= radiusKm
    }

    fun formatAirportLocal(
        millis: Long,
        airportTimeZoneId: String?,
        pattern: String = "EEE, d MMM yyyy HH:mm"
    ): String {
        val zone = parseZone(airportTimeZoneId) ?: ZoneId.systemDefault()
        return Instant.ofEpochMilli(millis)
            .atZone(zone)
            .format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
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
        candidates: List<ScoredSchedule>,
        rawMinutes: Long,
        expectedMinutes: Long?
    ): NormalizedSchedule {
        if (candidates.isEmpty()) {
            return NormalizedSchedule(0, 0, rawMinutes)
        }
        val plausible = candidates.filter { it.schedule.durationMinutes in 20..(22 * 60) }
            .ifEmpty { candidates }
        if (expectedMinutes != null) {
            return plausible.minWith(
                compareBy<ScoredSchedule> {
                    abs(it.schedule.durationMinutes - expectedMinutes)
                }.thenBy {
                    // Prefer "typed in a nearby zone" (e.g. Jakarta +7 while
                    // departing SIN +8) over re-reading through the departure
                    // zone itself, which shifts 22:50 → 23:50 after the phone
                    // moves to Singapore.
                    val delta = abs(it.sourceOffsetHours - it.depOffsetHours)
                    when (delta) {
                        1, 2 -> 0
                        0 -> 1
                        else -> 2 + delta
                    }
                }.thenBy {
                    it.schedule.departureMillis
                }
            ).schedule
        }
        val schedules = plausible.map { it.schedule }
        val raw = schedules.firstOrNull { it.durationMinutes == rawMinutes }
        if (raw != null && schedules.size == 1) return raw
        val shortest = schedules.minOf { it.durationMinutes }
        return if (rawMinutes == shortest && schedules.any { it.durationMinutes > rawMinutes + 90 }) {
            schedules.maxBy { it.durationMinutes }
        } else {
            schedules.firstOrNull { it.durationMinutes == rawMinutes } ?: schedules.first()
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
