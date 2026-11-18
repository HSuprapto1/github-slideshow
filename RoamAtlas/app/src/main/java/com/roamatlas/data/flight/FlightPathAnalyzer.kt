package com.roamatlas.data.flight

import com.roamatlas.data.airport.Airport
import com.roamatlas.data.local.entity.FlightEntity
import com.roamatlas.domain.model.LocationRecord
import java.util.Locale
import kotlin.math.abs

/**
 * Builds an actual takeoff→landing path report from phone GPS for one leg.
 * Speeds in storage are m/s (Android Location); reports use km/h and altitude in m.
 */
object FlightPathAnalyzer {

    /** Time gap that counts as a GPS recording hole (airplane mode, etc.). */
    const val GAP_THRESHOLD_MS = 45L * 60_000L

    data class PathSegment(
        val points: List<LocationRecord>,
        /** False = solid recorded track; true = straight connector across a gap. */
        val isGap: Boolean
    )

    data class FlightPathReport(
        val flightId: Long,
        val departureCode: String,
        val arrivalCode: String,
        val points: List<LocationRecord>,
        val segments: List<PathSegment>,
        val gapCount: Int,
        val takeoff: LocationRecord?,
        val landing: LocationRecord?,
        val actualDurationMinutes: Long?,
        val scheduledDurationMinutes: Long,
        val maxAltitudeM: Double?,
        val avgSpeedKmh: Double?,
        val maxSpeedKmh: Double?,
        val distanceKm: Double?
    ) {
        val hasPath: Boolean get() = points.size >= 2
    }

    fun reportForFlight(
        flight: FlightEntity,
        gpsTrack: List<LocationRecord>,
        departureAirport: Airport?,
        arrivalAirport: Airport?,
        scheduledDurationMinutes: Long
    ): FlightPathReport {
        val points = selectPoints(flight, gpsTrack, departureAirport, arrivalAirport)
        val segments = splitSegments(points)
        val recorded = segments.filterNot { it.isGap }.flatMap { it.points }
        val takeoff = recorded.firstOrNull()
        val landing = recorded.lastOrNull()
        val actualMinutes = if (takeoff != null && landing != null) {
            ((landing.timestamp.toEpochMilli() - takeoff.timestamp.toEpochMilli()) / 60_000L)
                .coerceAtLeast(0)
        } else {
            null
        }
        val altitudes = recorded.mapNotNull { it.altitude }
        val speedsKmh = recorded.mapNotNull { it.speed?.times(3.6)?.toDouble() }
            .filter { it in 50.0..1_200.0 }
        val distance = distanceOfRecorded(segments)
        return FlightPathReport(
            flightId = flight.id,
            departureCode = flight.departureAirportCode.uppercase(Locale.US),
            arrivalCode = (
                flight.actualArrivalAirportCode?.takeIf { it.isNotBlank() }
                    ?: flight.arrivalAirportCode
                ).uppercase(Locale.US),
            points = points,
            segments = segments,
            gapCount = segments.count { it.isGap },
            takeoff = takeoff,
            landing = landing,
            actualDurationMinutes = actualMinutes,
            scheduledDurationMinutes = scheduledDurationMinutes,
            maxAltitudeM = altitudes.maxOrNull(),
            avgSpeedKmh = speedsKmh.takeIf { it.isNotEmpty() }?.average(),
            maxSpeedKmh = speedsKmh.maxOrNull(),
            distanceKm = distance
        )
    }

    fun reportsForJourney(
        flights: List<FlightEntity>,
        gpsTrack: List<LocationRecord>,
        airports: Map<String, Airport>
    ): Map<Long, FlightPathReport> =
        flights.associate { flight ->
            val dep = airports[flight.departureAirportCode.uppercase(Locale.US)]
                ?: airports[flight.departureAirportCode]
            val arrCode = flight.actualArrivalAirportCode?.takeIf { it.isNotBlank() }
                ?: flight.arrivalAirportCode
            val arr = airports[arrCode.uppercase(Locale.US)] ?: airports[arrCode]
            val scheduled = FlightScheduleTimes.durationMinutes(flight, dep, arr)
            flight.id to reportForFlight(flight, gpsTrack, dep, arr, scheduled)
        }

    /**
     * Color for altitude (m): low teal → cruise indigo. Used to paint path segments.
     */
    fun altitudeColorArgb(altitudeM: Double?): Long {
        val alt = altitudeM ?: return 0xFF00897BL
        val t = ((alt - 500.0) / 11_500.0).coerceIn(0.0, 1.0)
        // teal 0x00897B → deep indigo 0x3949AB
        val r = (0x00 + (0x39 - 0x00) * t).toInt()
        val g = (0x89 + (0x49 - 0x89) * t).toInt()
        val b = (0x7B + (0xAB - 0x7B) * t).toInt()
        return 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    /** Distinct leg colors for multi-flight actual paths on the journey map. */
    fun legColorArgb(index: Int): Long {
        val palette = longArrayOf(
            0xFF00897B,
            0xFF5E35B1,
            0xFFEF6C00,
            0xFF1565C0,
            0xFFC62828,
            0xFF2E7D32
        )
        return palette[abs(index) % palette.size]
    }

    private fun selectPoints(
        flight: FlightEntity,
        gpsTrack: List<LocationRecord>,
        departureAirport: Airport?,
        arrivalAirport: Airport?
    ): List<LocationRecord> {
        if (gpsTrack.isEmpty()) return emptyList()
        val scheduledDep = flight.estimatedDepartureTimeMillis ?: flight.departureTimeMillis
        val scheduledArr = flight.estimatedArrivalTimeMillis ?: flight.arrivalTimeMillis
        val windowStart = (flight.actualDepartureMillis ?: scheduledDep) - 45L * 60_000L
        val windowEnd = (flight.actualArrivalMillis ?: scheduledArr) + 45L * 60_000L
        val inWindow = gpsTrack
            .filter {
                val t = it.timestamp.toEpochMilli()
                t in windowStart..windowEnd
            }
            .sortedBy { it.timestamp }
        if (inWindow.isEmpty()) return emptyList()

        val airborne = inWindow.filter { it.isAirborne }
        val core = if (airborne.size >= 2) {
            airborne
        } else {
            inWindow.filter { point ->
                point.isAirborne ||
                    (point.altitude != null && point.altitude >= 1_500) ||
                    (point.speed != null && point.speed >= 40f) ||
                    nearAirport(point, departureAirport, 80.0) ||
                    nearAirport(point, arrivalAirport, 80.0)
            }.ifEmpty { inWindow }
        }
        return core.sortedBy { it.timestamp }
    }

    private fun splitSegments(points: List<LocationRecord>): List<PathSegment> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return listOf(PathSegment(points, isGap = false))
        val result = mutableListOf<PathSegment>()
        var current = mutableListOf(points.first())
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val next = points[i]
            val dt = next.timestamp.toEpochMilli() - prev.timestamp.toEpochMilli()
            if (dt > GAP_THRESHOLD_MS) {
                if (current.isNotEmpty()) {
                    result.add(PathSegment(current.toList(), isGap = false))
                }
                result.add(PathSegment(listOf(prev, next), isGap = true))
                current = mutableListOf(next)
            } else {
                current.add(next)
            }
        }
        if (current.isNotEmpty()) {
            result.add(PathSegment(current.toList(), isGap = false))
        }
        return result
    }

    private fun distanceOfRecorded(segments: List<PathSegment>): Double? {
        var total = 0.0
        var any = false
        segments.filterNot { it.isGap }.forEach { segment ->
            val pts = segment.points
            for (i in 1 until pts.size) {
                val d = FlightScheduleTimes.haversineKm(
                    pts[i - 1].latitude,
                    pts[i - 1].longitude,
                    pts[i].latitude,
                    pts[i].longitude
                ) ?: continue
                total += d
                any = true
            }
        }
        return if (any) total else null
    }

    private fun nearAirport(
        point: LocationRecord,
        airport: Airport?,
        radiusKm: Double
    ): Boolean {
        if (airport == null) return false
        val d = FlightScheduleTimes.haversineKm(
            point.latitude,
            point.longitude,
            airport.latitude,
            airport.longitude
        ) ?: return false
        return d <= radiusKm
    }
}
