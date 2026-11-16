package com.roamatlas.data.journey

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.roamatlas.data.local.dao.FlightDao
import com.roamatlas.data.local.dao.TripDao
import com.roamatlas.data.local.entity.FlightEntity
import com.roamatlas.data.local.entity.JourneyStatus
import com.roamatlas.data.local.entity.TransportMode
import com.roamatlas.data.local.entity.TravelLegStatus
import com.roamatlas.data.local.entity.TripEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionRisk {
    COMFORTABLE,
    TIGHT,
    BELOW_RECOMMENDED
}

/** Auto summary of scheduled vs GPS-inferred travel for a journey. */
data class JourneyTravelSummary(
    val scheduledDoorToDoorMinutes: Long,
    val actualDoorToDoorMinutes: Long?,
    val scheduledTransitMinutes: Long,
    val actualAirOrTransitMinutes: Long?,
    val scheduledLayoverMinutes: Long,
    val actualLayoverMinutes: Long?,
    val legsArrived: Int,
    val legsMissed: Int,
    val legsSkipped: Int,
    val legsDelayed: Int,
    val legsDiverted: Int,
    val recoveredGapLegs: Int,
    val highlightLines: List<String>
)

data class JourneyConnection(
    val arrivingFlight: FlightEntity,
    val departingFlight: FlightEntity,
    val scheduledMinutes: Long,
    val effectiveMinutes: Long,
    val recommendedMinimumMinutes: Int,
    val risk: ConnectionRisk,
    val airportChange: Boolean
)

data class JourneyWithFlights(
    val journey: TripEntity,
    val flights: List<FlightEntity>
) {
    val orderedFlights: List<FlightEntity>
        get() = flights.sortedWith(
            compareBy<FlightEntity> { it.journeySequence ?: Int.MAX_VALUE }
                .thenBy { it.departureTimeMillis }
        )

    val firstDepartureMillis: Long?
        get() = orderedFlights.firstOrNull()?.let {
            it.estimatedDepartureTimeMillis ?: it.departureTimeMillis
        }

    val finalArrivalMillis: Long?
        get() = orderedFlights.lastOrNull()?.let {
            it.estimatedArrivalTimeMillis ?: it.arrivalTimeMillis
        }

    val totalJourneyMinutes: Long
        get() {
            val first = firstDepartureMillis ?: return 0
            val last = finalArrivalMillis ?: return 0
            return ((last - first) / 60_000).coerceAtLeast(0)
        }

    val totalTransitMinutes: Long
        get() = connections.sumOf { it.effectiveMinutes.coerceAtLeast(0) }

    val photoUris: List<String>
        get() = decodePhotoUris(journey.photoUrisJson)

    val journeyDayCount: Int
        get() {
            val start = journey.startDateEpochDay
            val end = journey.endDateEpochDay ?: start
            return (end - start + 1).toInt().coerceAtLeast(1)
        }

    val flightCount: Int
        get() = flights.count { TransportMode.isFlight(it.transportMode) }

    val groundTransportCount: Int
        get() = flights.count {
            it.transportMode.equals(TransportMode.TRAIN, ignoreCase = true) ||
                it.transportMode.equals(TransportMode.BUS, ignoreCase = true)
        }

    val hotelCount: Int
        get() = flights.count { TransportMode.isHotel(it.transportMode) }

    val legSummaryText: String
        get() = buildList {
            if (flightCount > 0) add("$flightCount flight${if (flightCount == 1) "" else "s"}")
            if (groundTransportCount > 0) {
                add("$groundTransportCount train${if (groundTransportCount == 1) "" else "s"}/bus${if (groundTransportCount == 1) "" else "es"}")
            }
            if (hotelCount > 0) add("$hotelCount hotel${if (hotelCount == 1) "" else "s"}")
            add("$journeyDayCount day${if (journeyDayCount == 1) "" else "s"}")
        }.joinToString(" · ").ifBlank { "$journeyDayCount days" }

    val status: String
        get() = JourneyStatus.normalize(journey.journeyStatus)

    val isCancelled: Boolean
        get() = status == JourneyStatus.CANCELLED

    val isCompleted: Boolean
        get() = status == JourneyStatus.COMPLETED

    val travelLegs: List<FlightEntity>
        get() = orderedFlights.filterNot { TransportMode.isHotel(it.transportMode) }

    val travelSummary: JourneyTravelSummary
        get() {
            val legs = travelLegs
            val scheduledDoor = totalJourneyMinutes
            val firstActualDep = legs.mapNotNull { it.actualDepartureMillis }.minOrNull()
            val lastActualArr = legs.mapNotNull { it.actualArrivalMillis }.maxOrNull()
            val actualDoor = if (firstActualDep != null && lastActualArr != null && lastActualArr >= firstActualDep) {
                ((lastActualArr - firstActualDep) / 60_000).coerceAtLeast(0)
            } else {
                null
            }
            val scheduledTransit = legs.sumOf { leg ->
                val dep = leg.estimatedDepartureTimeMillis ?: leg.departureTimeMillis
                val arr = leg.estimatedArrivalTimeMillis ?: leg.arrivalTimeMillis
                ((arr - dep) / 60_000).coerceAtLeast(0)
            }
            val actualTransitParts = legs.mapNotNull { leg ->
                val dep = leg.actualDepartureMillis ?: return@mapNotNull null
                val arr = leg.actualArrivalMillis ?: return@mapNotNull null
                if (arr < dep) null else ((arr - dep) / 60_000).coerceAtLeast(0)
            }
            val actualTransit = if (actualTransitParts.size == legs.count {
                    TravelLegStatus.normalize(it.travelLegStatus) == TravelLegStatus.ARRIVED
                } && actualTransitParts.isNotEmpty()
            ) {
                actualTransitParts.sum()
            } else if (actualTransitParts.isNotEmpty()) {
                actualTransitParts.sum()
            } else {
                null
            }
            val scheduledLayover = connections.sumOf { it.scheduledMinutes.coerceAtLeast(0) }
            val actualLayovers = legs.zipWithNext().mapNotNull { (a, b) ->
                if (TransportMode.isStay(a.transportMode) || TransportMode.isStay(b.transportMode)) {
                    return@mapNotNull null
                }
                val arr = a.actualArrivalMillis ?: return@mapNotNull null
                val dep = b.actualDepartureMillis ?: return@mapNotNull null
                ((dep - arr) / 60_000)
            }
            val actualLayover = actualLayovers.takeIf { it.isNotEmpty() }?.sum()
            val arrived = legs.count {
                TravelLegStatus.normalize(it.travelLegStatus) == TravelLegStatus.ARRIVED
            }
            val missed = legs.count {
                TravelLegStatus.normalize(it.travelLegStatus) == TravelLegStatus.MISSED
            }
            val skipped = legs.count {
                TravelLegStatus.normalize(it.travelLegStatus) == TravelLegStatus.SKIPPED
            }
            val delayed = legs.count {
                TravelLegStatus.normalize(it.travelLegStatus) == TravelLegStatus.DELAYED
            }
            val diverted = legs.count {
                TravelLegStatus.normalize(it.travelLegStatus) == TravelLegStatus.DIVERTED ||
                    !it.actualArrivalAirportCode.isNullOrBlank()
            }
            // Airplane-mode style: arrival GPS without a real airborne stretch recorded.
            val recovered = legs.count { leg ->
                TravelLegStatus.normalize(leg.travelLegStatus) == TravelLegStatus.ARRIVED &&
                    leg.actualArrivalMillis != null &&
                    (
                        leg.actualDepartureMillis == null ||
                            leg.actualDepartureMillis ==
                            (leg.estimatedDepartureTimeMillis ?: leg.departureTimeMillis)
                    )
            }
            val lines = buildList {
                if (legs.isEmpty()) {
                    add("No travel legs yet.")
                    return@buildList
                }
                add("Scheduled door-to-door · ${formatSummaryMinutes(scheduledDoor)}")
                actualDoor?.let { add("Actual door-to-door · ${formatSummaryMinutes(it)}") }
                add("Scheduled in-transit · ${formatSummaryMinutes(scheduledTransit)}")
                actualTransit?.let { add("GPS in-transit · ${formatSummaryMinutes(it)}") }
                if (scheduledLayover > 0) {
                    add("Scheduled layovers · ${formatSummaryMinutes(scheduledLayover)}")
                }
                actualLayover?.let { add("Actual layovers · ${formatSummaryMinutes(it)}") }
                if (arrived > 0 || missed > 0 || skipped > 0 || delayed > 0 || diverted > 0) {
                    add(
                        buildList {
                            if (arrived > 0) add("$arrived arrived")
                            if (delayed > 0) add("$delayed delayed")
                            if (missed > 0) add("$missed missed")
                            if (diverted > 0) add("$diverted diverted")
                            if (skipped > 0) add("$skipped not this journey")
                        }.joinToString(" · ")
                    )
                }
                if (recovered > 0) {
                    add("$recovered leg${if (recovered == 1) "" else "s"} recovered after GPS gap (airplane mode)")
                }
            }
            return JourneyTravelSummary(
                scheduledDoorToDoorMinutes = scheduledDoor,
                actualDoorToDoorMinutes = actualDoor,
                scheduledTransitMinutes = scheduledTransit,
                actualAirOrTransitMinutes = actualTransit,
                scheduledLayoverMinutes = scheduledLayover,
                actualLayoverMinutes = actualLayover,
                legsArrived = arrived,
                legsMissed = missed,
                legsSkipped = skipped,
                legsDelayed = delayed,
                legsDiverted = diverted,
                recoveredGapLegs = recovered,
                highlightLines = lines
            )
        }

    val isPast: Boolean
        get() {
            if (isCancelled || isCompleted) return true
            val zone = ZoneId.systemDefault()
            val endDay = journey.endDateEpochDay
            if (endDay != null) {
                val endMillis = LocalDate.ofEpochDay(endDay)
                    .plusDays(1)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
                return endMillis <= System.currentTimeMillis()
            }
            val end = finalArrivalMillis ?: firstDepartureMillis ?: return false
            return end < System.currentTimeMillis()
        }

    val connections: List<JourneyConnection>
        get() = orderedFlights.zipWithNext().mapNotNull { (arriving, departing) ->
            if (TransportMode.isStay(arriving.transportMode) ||
                TransportMode.isStay(departing.transportMode)
            ) {
                return@mapNotNull null
            }
            val scheduled = (departing.departureTimeMillis - arriving.arrivalTimeMillis) / 60_000
            val effectiveArrival = arriving.estimatedArrivalTimeMillis ?: arriving.arrivalTimeMillis
            val effectiveDeparture =
                departing.estimatedDepartureTimeMillis ?: departing.departureTimeMillis
            val effective = (effectiveDeparture - effectiveArrival) / 60_000
            val airportChange = !arriving.arrivalAirportCode.equals(
                departing.departureAirportCode,
                ignoreCase = true
            )
            val recommended = departing.minimumConnectionMinutes
                ?: if (airportChange) 240 else 120
            JourneyConnection(
                arrivingFlight = arriving,
                departingFlight = departing,
                scheduledMinutes = scheduled,
                effectiveMinutes = effective,
                recommendedMinimumMinutes = recommended,
                risk = when {
                    effective < recommended -> ConnectionRisk.BELOW_RECOMMENDED
                    effective < recommended + 60 -> ConnectionRisk.TIGHT
                    else -> ConnectionRisk.COMFORTABLE
                },
                airportChange = airportChange
            )
        }
}

@Singleton
class JourneyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tripDao: TripDao,
    private val flightDao: FlightDao
) {
    fun getJourneys(): Flow<List<JourneyWithFlights>> =
        combine(tripDao.getAll(), flightDao.getAllFlights()) { journeys, flights ->
            journeys.map { journey ->
                JourneyWithFlights(
                    journey = journey,
                    flights = flights.filter { it.journeyId == journey.id }
                )
            }.sortedBy { it.firstDepartureMillis ?: Long.MAX_VALUE }
        }

    fun getJourney(journeyId: Long): Flow<JourneyWithFlights?> =
        combine(tripDao.getAll(), flightDao.getFlightsForJourney(journeyId)) { journeys, flights ->
            journeys.firstOrNull { it.id == journeyId }?.let {
                JourneyWithFlights(it, flights)
            }
        }

    fun getUnassignedFlights(): Flow<List<FlightEntity>> = flightDao.getUnassignedFlights()

    suspend fun createJourney(
        name: String,
        notes: String,
        flights: List<FlightEntity>,
        startDateEpochDay: Long? = null,
        endDateEpochDay: Long? = null
    ): Long {
        val ordered = flights.sortedBy { it.departureTimeMillis }
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val firstDate = startDateEpochDay?.let(LocalDate::ofEpochDay)
            ?: ordered.firstOrNull()?.let {
                Instant.ofEpochMilli(it.departureTimeMillis).atZone(zone).toLocalDate()
            }
            ?: LocalDate.now(zone)
        val lastDate = endDateEpochDay?.let(LocalDate::ofEpochDay)
            ?: ordered.lastOrNull()?.let {
                Instant.ofEpochMilli(it.arrivalTimeMillis).atZone(zone).toLocalDate()
            }
            ?: firstDate
        require(!lastDate.isBefore(firstDate)) { "End date must be on or after start date." }
        val journeyId = tripDao.insert(
            TripEntity(
                name = name.ifBlank {
                    if (ordered.isNotEmpty()) {
                        "${ordered.first().departureAirportCode} to ${ordered.last().arrivalAirportCode}"
                    } else {
                        "New journey"
                    }
                },
                startDateEpochDay = firstDate.toEpochDay(),
                endDateEpochDay = lastDate.toEpochDay(),
                destinationCountryCode = ordered.lastOrNull()?.arrivalCountryCode,
                destinationCity = ordered.lastOrNull()?.arrivalCity,
                notes = notes.ifBlank { null },
                coverPhotoUri = null,
                photoUrisJson = null,
                journeyStatus = JourneyStatus.PLANNED,
                cancelReason = null,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
        ordered.forEachIndexed { index, flight ->
            flightDao.update(
                flight.copy(
                    journeyId = journeyId,
                    journeySequence = index,
                    updatedAtMillis = now
                )
            )
        }
        return journeyId
    }

    suspend fun createEmptyJourney(
        name: String,
        notes: String,
        startDateEpochDay: Long,
        endDateEpochDay: Long
    ): Long = createJourney(
        name = name,
        notes = notes,
        flights = emptyList(),
        startDateEpochDay = startDateEpochDay,
        endDateEpochDay = endDateEpochDay
    )

    suspend fun updateJourney(
        journeyId: Long,
        name: String,
        notes: String,
        startDateEpochDay: Long? = null,
        endDateEpochDay: Long? = null
    ) {
        val journey = tripDao.getById(journeyId) ?: return
        val start = startDateEpochDay ?: journey.startDateEpochDay
        val end = endDateEpochDay ?: journey.endDateEpochDay ?: start
        require(end >= start) { "End date must be on or after start date." }
        tripDao.update(
            journey.copy(
                name = name.ifBlank { journey.name },
                notes = notes.ifBlank { null },
                startDateEpochDay = start,
                endDateEpochDay = end,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun setJourneyStatus(
        journeyId: Long,
        status: String,
        cancelReason: String? = null
    ) {
        val journey = tripDao.getById(journeyId) ?: return
        val normalized = JourneyStatus.normalize(status)
        tripDao.update(
            journey.copy(
                journeyStatus = normalized,
                cancelReason = if (normalized == JourneyStatus.CANCELLED) {
                    cancelReason?.takeIf { it.isNotBlank() } ?: journey.cancelReason
                } else {
                    null
                },
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun setTravelLegStatus(
        flightId: Long,
        status: String,
        actualArrivalAirportCode: String? = null,
        dismissDetectionHours: Int? = null
    ) {
        val flight = flightDao.getFlightById(flightId) ?: return
        val normalized = TravelLegStatus.normalize(status)
        val now = System.currentTimeMillis()
        val dismissedUntil = when {
            dismissDetectionHours != null && dismissDetectionHours > 0 ->
                now + dismissDetectionHours * 60L * 60L * 1000L
            normalized == TravelLegStatus.SKIPPED ->
                now + 36L * 60L * 60L * 1000L
            else -> flight.detectionDismissedUntilMillis
        }
        flightDao.update(
            flight.copy(
                travelLegStatus = normalized,
                actualArrivalAirportCode = when (normalized) {
                    TravelLegStatus.DIVERTED ->
                        actualArrivalAirportCode?.uppercase()?.takeIf { it.isNotBlank() }
                            ?: flight.actualArrivalAirportCode
                    TravelLegStatus.ARRIVED ->
                        actualArrivalAirportCode?.uppercase()?.takeIf { it.isNotBlank() }
                            ?: flight.actualArrivalAirportCode
                    TravelLegStatus.SKIPPED, TravelLegStatus.MISSED, TravelLegStatus.PLANNED ->
                        if (normalized == TravelLegStatus.PLANNED) null
                        else flight.actualArrivalAirportCode
                    else -> flight.actualArrivalAirportCode
                },
                actualArrivalMillis = when (normalized) {
                    TravelLegStatus.ARRIVED -> flight.actualArrivalMillis ?: now
                    TravelLegStatus.MISSED, TravelLegStatus.SKIPPED, TravelLegStatus.PLANNED ->
                        if (normalized == TravelLegStatus.PLANNED) null else flight.actualArrivalMillis
                    else -> flight.actualArrivalMillis
                },
                actualDepartureMillis = when (normalized) {
                    TravelLegStatus.DEPARTED, TravelLegStatus.IN_AIR, TravelLegStatus.ARRIVED ->
                        flight.actualDepartureMillis ?: now
                    TravelLegStatus.PLANNED -> null
                    else -> flight.actualDepartureMillis
                },
                detectionDismissedUntilMillis = dismissedUntil,
                updatedAtMillis = now
            )
        )
        val journeyId = flight.journeyId ?: return
        when (normalized) {
            TravelLegStatus.AT_DEPARTURE,
            TravelLegStatus.DEPARTED,
            TravelLegStatus.IN_AIR,
            TravelLegStatus.DELAYED,
            TravelLegStatus.DIVERTED,
            TravelLegStatus.ARRIVED,
            TravelLegStatus.MISSED -> {
                val journey = tripDao.getById(journeyId) ?: return
                if (JourneyStatus.normalize(journey.journeyStatus) == JourneyStatus.PLANNED) {
                    tripDao.update(
                        journey.copy(
                            journeyStatus = JourneyStatus.IN_PROGRESS,
                            updatedAtMillis = now
                        )
                    )
                }
            }
        }
        if (normalized == TravelLegStatus.ARRIVED) {
            val legs = flightDao.getFlightsForJourneySnapshot(journeyId)
                .filter { !TransportMode.isHotel(it.transportMode) }
            val allDone = legs.isNotEmpty() && legs.all {
                val s = TravelLegStatus.normalize(it.travelLegStatus)
                s == TravelLegStatus.ARRIVED || s == TravelLegStatus.MISSED || s == TravelLegStatus.SKIPPED
            }
            if (allDone) {
                val journey = tripDao.getById(journeyId) ?: return
                tripDao.update(
                    journey.copy(
                        journeyStatus = JourneyStatus.COMPLETED,
                        updatedAtMillis = now
                    )
                )
            }
        }
    }

    /** Soft-dismiss GPS matching for this leg (“Not this journey”). */
    suspend fun markNotThisJourney(flightId: Long) {
        setTravelLegStatus(
            flightId = flightId,
            status = TravelLegStatus.SKIPPED,
            dismissDetectionHours = 36
        )
    }

    suspend fun attachFlightToJourney(journeyId: Long, flightId: Long): Boolean {
        val journey = tripDao.getById(journeyId) ?: return false
        val flight = flightDao.getFlightById(flightId) ?: return false
        val existing = flightDao.getFlightsForJourney(journeyId).first()
        val nextSequence = (existing.maxOfOrNull { it.journeySequence ?: -1 } ?: -1) + 1
        val now = System.currentTimeMillis()
        flightDao.update(
            flight.copy(
                journeyId = journeyId,
                journeySequence = nextSequence,
                updatedAtMillis = now
            )
        )
        // Expand trip dates if the new leg falls outside the shell.
        val zone = ZoneId.systemDefault()
        val depDay = Instant.ofEpochMilli(flight.departureTimeMillis)
            .atZone(zone).toLocalDate().toEpochDay()
        val arrDay = Instant.ofEpochMilli(flight.arrivalTimeMillis)
            .atZone(zone).toLocalDate().toEpochDay()
        val start = minOf(journey.startDateEpochDay, depDay)
        val end = maxOf(journey.endDateEpochDay ?: journey.startDateEpochDay, arrDay)
        if (start != journey.startDateEpochDay || end != journey.endDateEpochDay) {
            tripDao.update(
                journey.copy(
                    startDateEpochDay = start,
                    endDateEpochDay = end,
                    updatedAtMillis = now
                )
            )
        }
        return true
    }

    suspend fun addJourneyPhoto(journeyId: Long, uri: Uri): Boolean {
        val journey = tripDao.getById(journeyId) ?: return false
        val stored = persistPhoto(uri, "journey_${journeyId}") ?: return false
        val photos = decodePhotoUris(journey.photoUrisJson).toMutableList()
        if (stored in photos) return true
        photos.add(stored)
        tripDao.update(
            journey.copy(
                photoUrisJson = encodePhotoUris(photos),
                coverPhotoUri = journey.coverPhotoUri ?: stored,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        return true
    }

    suspend fun removeJourneyPhoto(journeyId: Long, photoUri: String) {
        val journey = tripDao.getById(journeyId) ?: return
        val photos = decodePhotoUris(journey.photoUrisJson).filterNot { it == photoUri }
        val cover = when {
            journey.coverPhotoUri == photoUri -> photos.firstOrNull()
            else -> journey.coverPhotoUri
        }
        tripDao.update(
            journey.copy(
                photoUrisJson = encodePhotoUris(photos),
                coverPhotoUri = cover,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        deleteLocalFileUri(photoUri)
    }

    suspend fun setJourneyCoverPhoto(journeyId: Long, photoUri: String?) {
        val journey = tripDao.getById(journeyId) ?: return
        tripDao.update(
            journey.copy(
                coverPhotoUri = photoUri,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun setFlightTicketPhoto(flightId: Long, uri: Uri?): Boolean {
        val flight = flightDao.getFlightById(flightId) ?: return false
        val previous = flight.photoUri
        val stored = uri?.let { persistPhoto(it, "ticket_${flightId}") }
        flightDao.update(
            flight.copy(
                photoUri = stored,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        if (!previous.isNullOrBlank() && previous != stored) {
            deleteLocalFileUri(previous)
        }
        return true
    }

    suspend fun updateFlightOperationalDetails(
        flight: FlightEntity,
        estimatedDepartureTimeMillis: Long?,
        estimatedArrivalTimeMillis: Long?,
        status: String?,
        minimumConnectionMinutes: Int?,
        separateTicketFromPrevious: Boolean?,
        aircraftType: String?,
        seatNumber: String?,
        terminal: String?,
        gate: String?
    ) {
        flightDao.update(
            flight.copy(
                estimatedDepartureTimeMillis = estimatedDepartureTimeMillis,
                estimatedArrivalTimeMillis = estimatedArrivalTimeMillis,
                flightStatus = status?.takeIf { it.isNotBlank() },
                minimumConnectionMinutes = minimumConnectionMinutes,
                separateTicketFromPrevious = separateTicketFromPrevious,
                aircraftType = aircraftType?.takeIf { it.isNotBlank() },
                seatNumber = seatNumber?.takeIf { it.isNotBlank() },
                notes = replaceAirportDetails(flight.notes, terminal, gate),
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteJourney(journeyId: Long) {
        flightDao.clearJourney(journeyId)
        tripDao.getById(journeyId)?.let { tripDao.delete(it) }
    }

    fun suggestConnectedGroups(flights: List<FlightEntity>): List<List<FlightEntity>> {
        val sorted = flights.sortedBy { it.departureTimeMillis }
        if (sorted.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<FlightEntity>>()
        sorted.forEach { flight ->
            val current = groups.lastOrNull()
            val previous = current?.lastOrNull()
            val gap = previous?.let { flight.departureTimeMillis - it.arrivalTimeMillis }
            val sameBooking = previous?.bookingReference?.takeIf { it.isNotBlank() }
                ?.equals(flight.bookingReference, ignoreCase = true) == true
            val routeContinues = previous?.arrivalAirportCode
                ?.equals(flight.departureAirportCode, ignoreCase = true) == true
            if (
                previous != null &&
                gap != null &&
                gap in 0..(48L * 60 * 60 * 1000) &&
                (sameBooking || routeContinues)
            ) {
                current.add(flight)
            } else {
                groups.add(mutableListOf(flight))
            }
        }
        return groups.filter { it.size >= 2 }
    }

    private fun persistPhoto(uri: Uri, prefix: String): String? = runCatching {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        val dir = File(context.filesDir, "journey_photos").apply { mkdirs() }
        val extension = resolveExtension(uri)
        val out = File(dir, "${prefix}_${System.currentTimeMillis()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        Uri.fromFile(out).toString()
    }.getOrNull()

    private fun resolveExtension(uri: Uri): String {
        val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        val name = uri.lastPathSegment?.lowercase().orEmpty()
        return when {
            mime.contains("pdf") || name.endsWith(".pdf") -> "pdf"
            mime.contains("png") || name.endsWith(".png") -> "png"
            mime.contains("webp") || name.endsWith(".webp") -> "webp"
            mime.contains("gif") || name.endsWith(".gif") -> "gif"
            else -> "jpg"
        }
    }

    private fun deleteLocalFileUri(uriString: String) {
        runCatching {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
            }
        }
    }

    private fun replaceAirportDetails(
        notes: String?,
        terminal: String?,
        gate: String?
    ): String? {
        val retained = notes.orEmpty().lineSequence()
            .filterNot {
                it.startsWith("Departure terminal:", ignoreCase = true) ||
                    it.startsWith("Departure gate:", ignoreCase = true) ||
                    it.startsWith("Airport details source:", ignoreCase = true)
            }
            .filter { it.isNotBlank() }
            .toMutableList()
        terminal?.trim()?.takeIf { it.isNotBlank() }?.let {
            retained.add("Departure terminal: ${it.uppercase()}")
        }
        gate?.trim()?.takeIf { it.isNotBlank() }?.let {
            retained.add("Departure gate: ${it.uppercase()}")
        }
        if (!terminal.isNullOrBlank() || !gate.isNullOrBlank()) {
            retained.add("Airport details source: Manually entered")
        }
        return retained.joinToString("\n").takeIf { it.isNotBlank() }
    }
}

internal fun formatSummaryMinutes(totalMinutes: Long): String {
    val minutes = totalMinutes.coerceAtLeast(0)
    val hours = minutes / 60
    val rem = minutes % 60
    return when {
        hours == 0L -> "${rem}m"
        rem == 0L -> "${hours}h"
        else -> "${hours}h ${rem}m"
    }
}

internal fun decodePhotoUris(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }.getOrDefault(emptyList())
}

internal fun encodePhotoUris(uris: List<String>): String? {
    val unique = uris.filter { it.isNotBlank() }.distinct()
    if (unique.isEmpty()) return null
    return JSONArray(unique).toString()
}
