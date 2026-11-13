package com.roamatlas.data.journey

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.roamatlas.data.local.dao.FlightDao
import com.roamatlas.data.local.dao.TripDao
import com.roamatlas.data.local.entity.FlightEntity
import com.roamatlas.data.local.entity.TripEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import java.io.File
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionRisk {
    COMFORTABLE,
    TIGHT,
    BELOW_RECOMMENDED
}

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

    val isPast: Boolean
        get() {
            val end = finalArrivalMillis ?: firstDepartureMillis ?: return false
            return end < System.currentTimeMillis()
        }

    val connections: List<JourneyConnection>
        get() = orderedFlights.zipWithNext().map { (arriving, departing) ->
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
        flights: List<FlightEntity>
    ): Long {
        require(flights.isNotEmpty()) { "Choose at least one flight." }
        val ordered = flights.sortedBy { it.departureTimeMillis }
        val now = System.currentTimeMillis()
        val firstDate = Instant.ofEpochMilli(ordered.first().departureTimeMillis)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val lastDate = Instant.ofEpochMilli(ordered.last().arrivalTimeMillis)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val journeyId = tripDao.insert(
            TripEntity(
                name = name.ifBlank {
                    "${ordered.first().departureAirportCode} to ${ordered.last().arrivalAirportCode}"
                },
                startDateEpochDay = firstDate.toEpochDay(),
                endDateEpochDay = lastDate.toEpochDay(),
                destinationCountryCode = ordered.last().arrivalCountryCode,
                destinationCity = ordered.last().arrivalCity,
                notes = notes.ifBlank { null },
                coverPhotoUri = null,
                photoUrisJson = null,
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

    suspend fun updateJourney(journeyId: Long, name: String, notes: String) {
        val journey = tripDao.getById(journeyId) ?: return
        tripDao.update(
            journey.copy(
                name = name.ifBlank { journey.name },
                notes = notes.ifBlank { null },
                updatedAtMillis = System.currentTimeMillis()
            )
        )
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
        val out = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        Uri.fromFile(out).toString()
    }.getOrNull()

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
