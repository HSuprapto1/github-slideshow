package com.roamatlas.data.journey

import android.util.Log
import com.roamatlas.data.airport.AirportRepository
import com.roamatlas.data.geo.GeoDistance
import com.roamatlas.data.local.dao.FlightDao
import com.roamatlas.data.local.dao.MapPlaceDao
import com.roamatlas.data.local.dao.TripDao
import com.roamatlas.data.local.entity.FlightEntity
import com.roamatlas.data.local.entity.JourneyStatus
import com.roamatlas.data.local.entity.MapPlaceEntity
import com.roamatlas.data.local.entity.TransportMode
import com.roamatlas.data.local.entity.TravelLegStatus
import com.roamatlas.data.local.entity.TripEntity
import com.roamatlas.domain.model.LocationRecord
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Infers journey progress from GPS near departure/arrival airports,
 * including airplane-mode gaps (leave dep → appear at arr), soft nudges
 * while still traveling, and saved-place arrival suggestions.
 */
@Singleton
class JourneyTravelDetector @Inject constructor(
    private val tripDao: TripDao,
    private val flightDao: FlightDao,
    private val mapPlaceDao: MapPlaceDao,
    private val airportRepository: AirportRepository,
    private val notifier: JourneyTravelNotifier
) {
    private val mutex = Mutex()

    suspend fun onNewLocation(location: LocationRecord) = mutex.withLock {
        runCatching { process(location) }
            .onFailure { Log.w(TAG, "Journey detection failed", it) }
    }

    /** Call on app start / journey open to catch recovery without a brand-new GPS point. */
    suspend fun recheckWithLastKnown(
        latitude: Double,
        longitude: Double,
        timestampMillis: Long,
        isAirborne: Boolean = false
    ) = mutex.withLock {
        runCatching {
            process(
                LocationRecord(
                    latitude = latitude,
                    longitude = longitude,
                    timestamp = java.time.Instant.ofEpochMilli(timestampMillis),
                    source = com.roamatlas.domain.model.LocationSource.GPS_TRACKING,
                    isAirborne = isAirborne
                )
            )
        }.onFailure { Log.w(TAG, "Journey recheck failed", it) }
    }

    fun suggestedArrivalPlace(journeyId: Long): String? =
        notifier.getSuggestedArrivalPlace(journeyId)

    fun clearSuggestedArrivalPlace(journeyId: Long) {
        notifier.clearSuggestedArrivalPlace(journeyId)
    }

    private suspend fun process(location: LocationRecord) {
        val now = location.timestamp.toEpochMilli()
        val journeys = tripDao.getActiveJourneys()
        if (journeys.isEmpty()) return
        val savedPlaces = mapPlaceDao.getAllSnapshot()

        journeys.forEach { trip ->
            val legs = flightDao.getFlightsForJourneySnapshot(trip.id)
                .filter { !TransportMode.isHotel(it.transportMode) }
                .sortedWith(
                    compareBy<FlightEntity> { it.journeySequence ?: Int.MAX_VALUE }
                        .thenBy { it.departureTimeMillis }
                )
            if (legs.isEmpty()) {
                maybeSoftNudgeStillTraveling(trip, emptyList(), now)
                return@forEach
            }
            processJourney(
                trip = trip,
                legs = legs,
                lat = location.latitude,
                lon = location.longitude,
                now = now,
                isAirborne = location.isAirborne,
                savedPlaces = savedPlaces
            )
        }
    }

    private suspend fun processJourney(
        trip: TripEntity,
        legs: List<FlightEntity>,
        lat: Double,
        lon: Double,
        now: Long,
        isAirborne: Boolean,
        savedPlaces: List<MapPlaceEntity>
    ) {
        var journeyTouched = false
        legs.forEachIndexed { index, leg ->
            val status = TravelLegStatus.normalize(leg.travelLegStatus)
            if (status == TravelLegStatus.SKIPPED || status == TravelLegStatus.MISSED) return@forEachIndexed
            val dismissedUntil = leg.detectionDismissedUntilMillis ?: 0L
            if (dismissedUntil > now) return@forEachIndexed

            // Ground legs use place names, not airport DB codes — skip airport geofence.
            if (TransportMode.isGround(leg.transportMode)) return@forEachIndexed

            val dep = airportRepository.findByCode(leg.departureAirportCode) ?: return@forEachIndexed
            val arrCode = leg.actualArrivalAirportCode?.takeIf { it.isNotBlank() }
                ?: leg.arrivalAirportCode
            val arr = airportRepository.findByCode(arrCode) ?: return@forEachIndexed

            val depKm = GeoDistance.haversineKm(lat, lon, dep.latitude, dep.longitude)
            val arrKm = GeoDistance.haversineKm(lat, lon, arr.latitude, arr.longitude)
            val nearDep = depKm <= AT_AIRPORT_KM
            val nearArr = arrKm <= AT_AIRPORT_KM
            val scheduledDep = leg.estimatedDepartureTimeMillis ?: leg.departureTimeMillis
            val scheduledArr = leg.estimatedArrivalTimeMillis ?: leg.arrivalTimeMillis
            val inWindow = now in (scheduledDep - PRE_WINDOW_MS)..(scheduledArr + POST_WINDOW_MS)
            val journeyInProgress =
                JourneyStatus.normalize(trip.journeyStatus) == JourneyStatus.IN_PROGRESS

            if (!inWindow && !journeyInProgress && status == TravelLegStatus.PLANNED) {
                return@forEachIndexed
            }

            if (isAirborne &&
                journeyInProgress &&
                status in setOf(
                    TravelLegStatus.AT_DEPARTURE,
                    TravelLegStatus.DEPARTED,
                    TravelLegStatus.DELAYED,
                    TravelLegStatus.DIVERTED,
                    TravelLegStatus.PLANNED
                )
            ) {
                updateLeg(
                    leg,
                    travelLegStatus = TravelLegStatus.IN_AIR,
                    actualDepartureMillis = leg.actualDepartureMillis ?: now
                )
                journeyTouched = true
                return@forEachIndexed
            }

            if (index > 0 && !isAirborne) {
                val prev = legs[index - 1]
                val prevStatus = TravelLegStatus.normalize(prev.travelLegStatus)
                val prevArrCode = prev.actualArrivalAirportCode?.takeIf { it.isNotBlank() }
                    ?: prev.arrivalAirportCode
                val prevArr = airportRepository.findByCode(prevArrCode)
                val nearPrevArr = prevArr != null &&
                    GeoDistance.haversineKm(lat, lon, prevArr.latitude, prevArr.longitude) <= AT_AIRPORT_KM
                val arrivedLateForNext = prev.actualArrivalMillis != null &&
                    prev.actualArrivalMillis > scheduledDep
                if (
                    prevStatus == TravelLegStatus.ARRIVED &&
                    status in setOf(
                        TravelLegStatus.PLANNED,
                        TravelLegStatus.AT_DEPARTURE,
                        TravelLegStatus.DELAYED
                    ) &&
                    (now > scheduledDep + MISSED_AFTER_MS || arrivedLateForNext) &&
                    nearPrevArr
                ) {
                    if (status != TravelLegStatus.MISSED) {
                        updateLeg(leg, travelLegStatus = TravelLegStatus.MISSED)
                        ensureJourneyInProgress(trip)
                        notifier.notifyOnce(
                            key = "missed_${leg.id}",
                            title = "Missed connection? · ${trip.name}",
                            text = "Still near $prevArrCode after ${leg.departureAirportCode} departure. " +
                                "Confirm Missed connection or Not this journey on the journey screen.",
                            journeyId = trip.id
                        )
                        journeyTouched = true
                    }
                    return@forEachIndexed
                }
            }

            if (!isAirborne && nearDep && now > scheduledDep + DELAY_AFTER_MS &&
                status !in setOf(
                    TravelLegStatus.ARRIVED,
                    TravelLegStatus.IN_AIR,
                    TravelLegStatus.DEPARTED,
                    TravelLegStatus.MISSED
                )
            ) {
                if (status != TravelLegStatus.DELAYED) {
                    updateLeg(leg, travelLegStatus = TravelLegStatus.DELAYED)
                    ensureJourneyInProgress(trip)
                    notifier.notifyOnce(
                        key = "delay_${leg.id}",
                        title = "Possible delay · ${trip.name}",
                        text = "Still near ${leg.departureAirportCode} after departure time. " +
                            "Mark delayed, cancelled, or Not this journey in the journey screen.",
                        journeyId = trip.id
                    )
                    journeyTouched = true
                }
                return@forEachIndexed
            }

            if (!isAirborne && nearDep &&
                status in setOf(TravelLegStatus.PLANNED, TravelLegStatus.DIVERTED)
            ) {
                updateLeg(leg, travelLegStatus = TravelLegStatus.AT_DEPARTURE)
                ensureJourneyInProgress(trip)
                notifier.notifyOnce(
                    key = "atdep_${leg.id}",
                    title = "At ${leg.departureAirportCode}",
                    text = "Looks like you’re starting “${trip.name}”. " +
                        "Open the journey to confirm, mark Not this journey, or cancel.",
                    journeyId = trip.id
                )
                journeyTouched = true
                return@forEachIndexed
            }

            val canArrive = status in setOf(
                TravelLegStatus.AT_DEPARTURE,
                TravelLegStatus.DEPARTED,
                TravelLegStatus.IN_AIR,
                TravelLegStatus.DELAYED,
                TravelLegStatus.DIVERTED
            ) || (journeyInProgress && now >= scheduledDep)
            if (!isAirborne && nearArr && canArrive && status != TravelLegStatus.ARRIVED) {
                val recovered = status == TravelLegStatus.PLANNED ||
                    status == TravelLegStatus.AT_DEPARTURE ||
                    status == TravelLegStatus.DELAYED
                updateLeg(
                    leg,
                    travelLegStatus = TravelLegStatus.ARRIVED,
                    actualDepartureMillis = leg.actualDepartureMillis
                        ?: (if (recovered) scheduledDep else null),
                    actualArrivalMillis = now
                )
                ensureJourneyInProgress(trip)
                notifier.notifyOnce(
                    key = "arr_${leg.id}",
                    title = if (recovered) "Flight recovered · ${trip.name}" else "Arrived · ${trip.name}",
                    text = if (recovered) {
                        "Phone location jumped to ${arrCode}. Marked ${leg.departureAirportCode}→$arrCode arrived " +
                            "(airplane-mode style gap). Review the journey summary."
                    } else {
                        "Near $arrCode — leg ${leg.departureAirportCode}→$arrCode marked arrived."
                    },
                    journeyId = trip.id
                )
                journeyTouched = true
                notifyBookedLegsDoneIfNeeded(trip)
            }
        }

        maybeSoftNudgeStillTraveling(trip, legs, now)
        if (!isAirborne) {
            maybeSuggestSavedPlaceArrival(trip, legs, lat, lon, now, savedPlaces)
        }

        if (journeyTouched) {
            Log.i(TAG, "Updated travel detection for journey ${trip.id}")
        }
    }

    private suspend fun notifyBookedLegsDoneIfNeeded(trip: TripEntity) {
        if (JourneyStatus.normalize(trip.journeyStatus) == JourneyStatus.COMPLETED ||
            JourneyStatus.normalize(trip.journeyStatus) == JourneyStatus.CANCELLED
        ) {
            return
        }
        val refreshed = flightDao.getFlightsForJourneySnapshot(trip.id)
            .filter { !TransportMode.isHotel(it.transportMode) }
        if (refreshed.isEmpty()) return
        val allSettled = refreshed.all {
            val s = TravelLegStatus.normalize(it.travelLegStatus)
            s == TravelLegStatus.ARRIVED || s == TravelLegStatus.MISSED || s == TravelLegStatus.SKIPPED
        }
        if (!allSettled) return
        val lastCode = refreshed.lastOrNull()?.let {
            it.actualArrivalAirportCode?.takeIf { code -> code.isNotBlank() } ?: it.arrivalAirportCode
        }
        ensureJourneyInProgress(trip)
        notifier.notifyOnce(
            key = "legs_done_${trip.id}",
            title = "Booked legs done · ${trip.name}",
            text = buildString {
                if (!lastCode.isNullOrBlank()) {
                    append("Arrived at $lastCode. ")
                }
                append("Journey stays in progress so GPS and photos still count. ")
                append("Add a ground leg if you like, then tap “I’ve arrived at my destination” when done.")
            },
            journeyId = trip.id
        )
    }

    private suspend fun maybeSoftNudgeStillTraveling(
        trip: TripEntity,
        legs: List<FlightEntity>,
        now: Long
    ) {
        val status = JourneyStatus.normalize(trip.journeyStatus)
        if (status != JourneyStatus.IN_PROGRESS && status != JourneyStatus.PLANNED) return
        if (legs.isEmpty()) return
        val allSettled = legs.all {
            val s = TravelLegStatus.normalize(it.travelLegStatus)
            s == TravelLegStatus.ARRIVED || s == TravelLegStatus.MISSED || s == TravelLegStatus.SKIPPED
        }
        if (!allSettled) return
        val lastArrival = legs.mapNotNull { leg ->
            leg.actualArrivalMillis
                ?: if (TravelLegStatus.normalize(leg.travelLegStatus) == TravelLegStatus.ARRIVED) {
                    leg.estimatedArrivalTimeMillis ?: leg.arrivalTimeMillis
                } else {
                    null
                }
        }.maxOrNull() ?: return
        if (now < lastArrival + STILL_TRAVELING_NUDGE_MS) return
        ensureJourneyInProgress(trip)
        notifier.notifyOnce(
            key = "still_traveling_${trip.id}",
            title = "Still traveling? · ${trip.name}",
            text = "Booked legs finished a while ago. GPS and photos are still on this journey. " +
                "Tap “I’ve arrived at my destination” when you’re done, or add a ground leg.",
            journeyId = trip.id
        )
    }

    private suspend fun maybeSuggestSavedPlaceArrival(
        trip: TripEntity,
        legs: List<FlightEntity>,
        lat: Double,
        lon: Double,
        now: Long,
        savedPlaces: List<MapPlaceEntity>
    ) {
        if (savedPlaces.isEmpty()) return
        val status = JourneyStatus.normalize(trip.journeyStatus)
        if (status != JourneyStatus.IN_PROGRESS && status != JourneyStatus.PLANNED) return
        val allSettled = legs.isNotEmpty() && legs.all {
            val s = TravelLegStatus.normalize(it.travelLegStatus)
            s == TravelLegStatus.ARRIVED || s == TravelLegStatus.MISSED || s == TravelLegStatus.SKIPPED
        }
        // Prefer after booked legs settle; also allow if any flight already arrived.
        val anyFlightArrived = legs.any {
            TransportMode.isFlight(it.transportMode) &&
                TravelLegStatus.normalize(it.travelLegStatus) == TravelLegStatus.ARRIVED
        }
        if (!allSettled && !anyFlightArrived) return

        val match = savedPlaces.mapNotNull { place ->
            val distM = GeoDistance.haversineMeters(lat, lon, place.latitude, place.longitude)
            val radius = (place.radiusMeters?.toDouble() ?: DEFAULT_PLACE_RADIUS_M)
                .coerceAtLeast(MIN_PLACE_RADIUS_M)
            if (distM <= radius) place to distM else null
        }.minByOrNull { it.second }?.first ?: return

        notifier.setSuggestedArrivalPlace(trip.id, match.name, match.id)
        ensureJourneyInProgress(trip)
        notifier.notifyOnce(
            key = "place_${trip.id}_${match.id}",
            title = "Near ${match.name} · ${trip.name}",
            text = "Looks like you reached a saved place. Open the journey and confirm " +
                "“I’ve arrived at my destination” if this is the end.",
            journeyId = trip.id
        )
    }

    private suspend fun ensureJourneyInProgress(trip: TripEntity) {
        if (JourneyStatus.normalize(trip.journeyStatus) == JourneyStatus.PLANNED) {
            tripDao.update(
                trip.copy(
                    journeyStatus = JourneyStatus.IN_PROGRESS,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun updateLeg(
        leg: FlightEntity,
        travelLegStatus: String,
        actualDepartureMillis: Long? = leg.actualDepartureMillis,
        actualArrivalMillis: Long? = leg.actualArrivalMillis,
        actualArrivalAirportCode: String? = leg.actualArrivalAirportCode
    ) {
        flightDao.update(
            leg.copy(
                travelLegStatus = travelLegStatus,
                actualDepartureMillis = actualDepartureMillis,
                actualArrivalMillis = actualArrivalMillis,
                actualArrivalAirportCode = actualArrivalAirportCode,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    companion object {
        private const val TAG = "JourneyTravelDetector"
        private const val AT_AIRPORT_KM = 12.0
        private const val PRE_WINDOW_MS = 8L * 60L * 60L * 1000L
        private const val POST_WINDOW_MS = 10L * 60L * 60L * 1000L
        private const val DELAY_AFTER_MS = 90L * 60L * 1000L
        private const val MISSED_AFTER_MS = 45L * 60L * 1000L
        private const val STILL_TRAVELING_NUDGE_MS = 6L * 60L * 60L * 1000L
        private const val DEFAULT_PLACE_RADIUS_M = 250.0
        private const val MIN_PLACE_RADIUS_M = 80.0
    }
}
