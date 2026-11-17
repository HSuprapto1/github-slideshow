package com.roamatlas.presentation.screens.journey

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.roamatlas.data.airport.Airport
import com.roamatlas.data.airport.AirportRepository
import com.roamatlas.data.flight.FlightScheduleTimes
import com.roamatlas.data.journey.JourneyRepository
import com.roamatlas.data.journey.JourneyTravelDetector
import com.roamatlas.data.journey.JourneyWithFlights
import com.roamatlas.data.local.dao.LocationDao
import com.roamatlas.data.local.entity.FlightEntity
import com.roamatlas.data.local.entity.TravelLegStatus
import com.roamatlas.domain.model.LocationRecord
import com.roamatlas.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class JourneyDaySummary(
    val date: LocalDate,
    val countryCode: String?,
    val countryName: String?,
    val pointCount: Int,
    val photoCount: Int
)

data class JourneyDetailUiState(
    val journey: JourneyWithFlights? = null,
    val airports: Map<String, Airport> = emptyMap(),
    val gpsTrack: List<LocationRecord> = emptyList(),
    val timelinePhotos: List<LocationRecord> = emptyList(),
    val photosSinceAirport: List<LocationRecord> = emptyList(),
    val showPhotosSinceAirportPrompt: Boolean = false,
    val suggestedArrivalPlace: String? = null,
    val journeyDays: List<JourneyDaySummary> = emptyList(),
    val trackedDayCount: Int = 0,
    val showGpsTrack: Boolean = true,
    /** Live / last-known device GPS for centering the journey map. */
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val nowMillis: Long = System.currentTimeMillis(),
    val isLoading: Boolean = true,
    val wasDeleted: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null
)

@HiltViewModel
class JourneyDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val journeyRepository: JourneyRepository,
    private val airportRepository: AirportRepository,
    private val locationRepository: LocationRepository,
    private val locationDao: LocationDao,
    private val journeyTravelDetector: JourneyTravelDetector
) : ViewModel() {
    private val journeyId = savedStateHandle.get<String>("journeyId")?.toLongOrNull() ?: 0L
    private val _uiState = MutableStateFlow(JourneyDetailUiState())
    val uiState: StateFlow<JourneyDetailUiState> = _uiState.asStateFlow()
    private var timelineJob: Job? = null

    init {
        viewModelScope.launch {
            // Re-run detection when opening a journey (airplane-mode recovery).
            runCatching {
                locationDao.getLatestLocation()?.let { latest ->
                    journeyTravelDetector.recheckWithLastKnown(
                        latitude = latest.latitude,
                        longitude = latest.longitude,
                        timestampMillis = latest.timestampMillis,
                        isAirborne = latest.isAirborne
                    )
                }
            }
        }
        viewModelScope.launch {
            refreshCurrentGps()
        }
        viewModelScope.launch {
            // One-shot restore if an older build pushed SIN arrival to the next day (~17h).
            runCatching { journeyRepository.repairCorruptedTicketSchedules(journeyId) }
            journeyRepository.getJourney(journeyId).collect { journey ->
                val codes = journey?.flights.orEmpty().flatMap {
                    listOf(
                        it.departureAirportCode,
                        it.arrivalAirportCode,
                        it.actualArrivalAirportCode
                    )
                }.filterNotNull()
                val airports = airportRepository.getByIataCodes(codes)
                // Do not rewrite stored flight times here — that made times keep changing.
                val suggested = journey?.let {
                    if (!it.isCompleted && !it.isCancelled) {
                        journeyTravelDetector.suggestedArrivalPlace(journeyId)
                    } else {
                        null
                    }
                }
                _uiState.value = _uiState.value.copy(
                    journey = journey,
                    airports = airports,
                    suggestedArrivalPlace = suggested,
                    isLoading = false,
                    errorMessage = if (journey == null) "Journey not found." else null
                )
                if (journey != null) {
                    loadTimelineForJourney(journey)
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                _uiState.value = _uiState.value.copy(nowMillis = System.currentTimeMillis())
                refreshCurrentGps()
            }
        }
    }

    fun setShowGpsTrack(show: Boolean) {
        _uiState.value = _uiState.value.copy(showGpsTrack = show)
    }

    fun refreshCurrentGps() {
        viewModelScope.launch {
            val device = readDeviceLocation()
            if (device != null) {
                _uiState.value = _uiState.value.copy(
                    currentLatitude = device.latitude,
                    currentLongitude = device.longitude
                )
                return@launch
            }
            // Fallback: latest point already stored by GPS tracking.
            locationDao.getLatestLocation()?.let { latest ->
                _uiState.value = _uiState.value.copy(
                    currentLatitude = latest.latitude,
                    currentLongitude = latest.longitude
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun readDeviceLocation(): android.location.Location? {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        return runCatching {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cancellation = CancellationTokenSource()
            val current = try {
                client.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellation.token
                ).await()
            } catch (_: Exception) {
                null
            }
            current ?: client.lastLocation.await()
        }.getOrNull()
    }

    fun importTimelinePhotosToAlbum() {
        val photos = _uiState.value.timelinePhotos
        if (photos.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                message = "No timeline photos in this journey’s dates."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(message = "Adding timeline photos…")
            val album = _uiState.value.journey?.photoUris.orEmpty().toSet()
            var added = 0
            var skipped = 0
            var failed = 0
            photos.forEach { location ->
                val uriString = location.originalPhotoUri?.takeIf { it.isNotBlank() }
                    ?: location.photoUri?.takeIf { it.isNotBlank() }
                if (uriString.isNullOrBlank()) {
                    failed++
                    return@forEach
                }
                if (uriString in album) {
                    skipped++
                    return@forEach
                }
                val ok = journeyRepository.addJourneyPhoto(journeyId, Uri.parse(uriString))
                if (ok) added++ else failed++
            }
            _uiState.value = _uiState.value.copy(
                message = when {
                    added > 0 && failed == 0 ->
                        "Added $added timeline photo${if (added == 1) "" else "s"} to the trip album."
                    added > 0 ->
                        "Added $added photo${if (added == 1) "" else "s"} · $failed could not be copied."
                    skipped > 0 && failed == 0 ->
                        "Those timeline photos are already in the trip album."
                    else ->
                        "Could not add timeline photos. Check photo access and try again."
                }
            )
        }
    }

    fun updateJourney(
        name: String,
        notes: String,
        startDateEpochDay: Long,
        endDateEpochDay: Long
    ) {
        viewModelScope.launch {
            runCatching {
                journeyRepository.updateJourney(
                    journeyId = journeyId,
                    name = name,
                    notes = notes,
                    startDateEpochDay = startDateEpochDay,
                    endDateEpochDay = endDateEpochDay
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message ?: "Could not update journey dates."
                )
            }
        }
    }

    fun addJourneyPhoto(uri: Uri) {
        viewModelScope.launch {
            val ok = journeyRepository.addJourneyPhoto(journeyId, uri)
            _uiState.value = _uiState.value.copy(
                message = if (ok) "Photo added to journey." else "Could not add photo.",
                errorMessage = if (ok) null else "Could not add journey photo."
            )
        }
    }

    fun removeJourneyPhoto(photoUri: String) {
        viewModelScope.launch {
            journeyRepository.removeJourneyPhoto(journeyId, photoUri)
        }
    }

    fun setCoverPhoto(photoUri: String) {
        viewModelScope.launch {
            journeyRepository.setJourneyCoverPhoto(journeyId, photoUri)
        }
    }

    fun setFlightTicketPhoto(flightId: Long, uri: Uri?) {
        viewModelScope.launch {
            val ok = journeyRepository.setFlightTicketPhoto(flightId, uri)
            _uiState.value = _uiState.value.copy(
                message = when {
                    !ok -> null
                    uri == null -> "Ticket attachment removed."
                    else -> "Ticket / boarding pass saved."
                },
                errorMessage = if (ok) null else "Could not save ticket attachment."
            )
        }
    }

    fun updateFlight(
        flight: FlightEntity,
        departureDelayMinutes: Int?,
        arrivalDelayMinutes: Int?,
        status: String,
        minimumConnectionMinutes: Int?,
        separateTicketFromPrevious: Boolean?,
        aircraftType: String,
        seatNumber: String,
        terminal: String,
        gate: String
    ) {
        viewModelScope.launch {
            journeyRepository.updateFlightOperationalDetails(
                flight = flight,
                estimatedDepartureTimeMillis = departureDelayMinutes?.let {
                    flight.departureTimeMillis + it * 60_000L
                },
                estimatedArrivalTimeMillis = arrivalDelayMinutes?.let {
                    flight.arrivalTimeMillis + it * 60_000L
                },
                status = status,
                minimumConnectionMinutes = minimumConnectionMinutes,
                separateTicketFromPrevious = separateTicketFromPrevious,
                aircraftType = aircraftType,
                seatNumber = seatNumber,
                terminal = terminal,
                gate = gate
            )
        }
    }

    fun updateFlightSchedule(
        flight: FlightEntity,
        departureAirportCode: String,
        arrivalAirportCode: String,
        departureDate: LocalDate,
        departureHour: Int,
        departureMinute: Int,
        arrivalDate: LocalDate,
        arrivalHour: Int,
        arrivalMinute: Int,
        airline: String,
        flightNumber: String
    ) {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val departureMillis = departureDate.atTime(departureHour, departureMinute)
                .atZone(zone).toInstant().toEpochMilli()
            val arrivalMillis = arrivalDate.atTime(arrivalHour, arrivalMinute)
                .atZone(zone).toInstant().toEpochMilli()
            if (arrivalMillis < departureMillis) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Arrival must be after departure."
                )
                return@launch
            }
            val depAirport = airportRepository.findByCode(departureAirportCode)
            val arrAirport = airportRepository.findByCode(arrivalAirportCode)
            val distance = FlightScheduleTimes.haversineKm(
                depAirport?.latitude,
                depAirport?.longitude,
                arrAirport?.latitude,
                arrAirport?.longitude
            )
            val duration = FlightScheduleTimes.durationMinutes(
                departureMillis = departureMillis,
                arrivalMillis = arrivalMillis,
                departureTimeZoneId = depAirport?.timeZoneId,
                arrivalTimeZoneId = arrAirport?.timeZoneId,
                distanceKm = distance
            ).toInt()
            val ok = journeyRepository.updateFlightSchedule(
                flightId = flight.id,
                departureAirportCode = departureAirportCode,
                arrivalAirportCode = arrivalAirportCode,
                departureAirportName = depAirport?.name ?: flight.departureAirportName,
                arrivalAirportName = arrAirport?.name ?: flight.arrivalAirportName,
                departureCity = depAirport?.city ?: flight.departureCity,
                arrivalCity = arrAirport?.city ?: flight.arrivalCity,
                departureCountryCode = depAirport?.countryCode ?: flight.departureCountryCode,
                arrivalCountryCode = arrAirport?.countryCode ?: flight.arrivalCountryCode,
                departureTimeMillis = departureMillis,
                arrivalTimeMillis = arrivalMillis,
                airline = airline,
                flightNumber = flightNumber,
                durationMinutes = duration.takeIf { it > 0 },
                distanceKm = distance
            )
            _uiState.value = _uiState.value.copy(
                message = if (ok) "Flight updated. Legs re-sorted by departure time." else null,
                errorMessage = if (ok) null else "Could not update flight. Check airports and times."
            )
        }
    }

    fun removeFlightFromJourney(flightId: Long) {
        viewModelScope.launch {
            val ok = journeyRepository.removeFlightFromJourney(journeyId, flightId)
            _uiState.value = _uiState.value.copy(
                message = if (ok) "Flight removed from journey." else null,
                errorMessage = if (ok) null else "Could not remove flight."
            )
        }
    }

    fun markJourneyCompleted() {
        viewModelScope.launch {
            journeyRepository.setJourneyStatus(journeyId, "COMPLETED")
            journeyTravelDetector.clearSuggestedArrivalPlace(journeyId)
            _uiState.value = _uiState.value.copy(
                suggestedArrivalPlace = null,
                showPhotosSinceAirportPrompt = false,
                message = "Arrived at destination · journey completed. GPS after this won’t attach here."
            )
        }
    }

    fun dismissSuggestedArrivalPlace() {
        journeyTravelDetector.clearSuggestedArrivalPlace(journeyId)
        _uiState.value = _uiState.value.copy(suggestedArrivalPlace = null)
    }

    fun dismissPhotosSinceAirportPrompt() {
        _uiState.value = _uiState.value.copy(showPhotosSinceAirportPrompt = false)
    }

    fun importPhotosSinceAirport() {
        val photos = _uiState.value.photosSinceAirport
        if (photos.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                showPhotosSinceAirportPrompt = false,
                message = "No new photos since the airport."
            )
            return
        }
        viewModelScope.launch {
            var added = 0
            photos.forEach { location ->
                val uriString = location.originalPhotoUri?.takeIf { it.isNotBlank() }
                    ?: location.photoUri?.takeIf { it.isNotBlank() }
                if (uriString != null) {
                    val ok = journeyRepository.addJourneyPhoto(journeyId, Uri.parse(uriString))
                    if (ok) added++
                }
            }
            _uiState.value = _uiState.value.copy(
                showPhotosSinceAirportPrompt = false,
                photosSinceAirport = emptyList(),
                message = if (added > 0) {
                    "Added $added photo${if (added == 1) "" else "s"} taken since the airport."
                } else {
                    "No new photos could be added."
                }
            )
        }
    }

    fun addGroundLeg(transportMode: String, destinationName: String, fromName: String?) {
        viewModelScope.launch {
            val id = journeyRepository.addGroundLeg(
                journeyId = journeyId,
                transportMode = transportMode,
                destinationName = destinationName,
                fromName = fromName
            )
            _uiState.value = _uiState.value.copy(
                message = if (id != null) {
                    "Ground leg added. GPS and photos stay on this journey until you arrive."
                } else {
                    null
                },
                errorMessage = if (id == null) "Could not add ground leg. Enter a destination." else null
            )
        }
    }

    fun markJourneyCancelled(reason: String?) {
        viewModelScope.launch {
            journeyRepository.setJourneyStatus(
                journeyId = journeyId,
                status = "CANCELLED",
                cancelReason = reason
            )
            _uiState.value = _uiState.value.copy(
                message = "Journey cancelled. It stays in Past and is not counted as travelled."
            )
        }
    }

    fun markJourneyPlanned() {
        viewModelScope.launch {
            journeyRepository.setJourneyStatus(journeyId, "PLANNED")
            _uiState.value = _uiState.value.copy(message = "Journey set back to planned.")
        }
    }

    fun markJourneyInProgress() {
        viewModelScope.launch {
            journeyRepository.setJourneyStatus(journeyId, "IN_PROGRESS")
            _uiState.value = _uiState.value.copy(message = "Journey marked in progress.")
        }
    }

    fun markLegDelayed(flightId: Long) {
        viewModelScope.launch {
            journeyRepository.setTravelLegStatus(flightId, TravelLegStatus.DELAYED)
            _uiState.value = _uiState.value.copy(message = "Leg marked delayed.")
        }
    }

    fun markLegMissed(flightId: Long) {
        viewModelScope.launch {
            journeyRepository.setTravelLegStatus(flightId, TravelLegStatus.MISSED)
            _uiState.value = _uiState.value.copy(message = "Marked missed connection.")
        }
    }

    fun markLegDiverted(flightId: Long, airportCode: String) {
        viewModelScope.launch {
            journeyRepository.setTravelLegStatus(
                flightId = flightId,
                status = TravelLegStatus.DIVERTED,
                actualArrivalAirportCode = airportCode
            )
            _uiState.value = _uiState.value.copy(
                message = "Leg marked diverted to ${airportCode.uppercase()}."
            )
        }
    }

    fun markLegArrived(flightId: Long) {
        viewModelScope.launch {
            journeyRepository.setTravelLegStatus(flightId, TravelLegStatus.ARRIVED)
            _uiState.value = _uiState.value.copy(message = "Leg marked arrived.")
        }
    }

    fun markNotThisJourney(flightId: Long) {
        viewModelScope.launch {
            journeyRepository.markNotThisJourney(flightId)
            _uiState.value = _uiState.value.copy(
                message = "Ignored for GPS matching (~36h). Not counted as this journey’s travel."
            )
        }
    }

    fun resetLegPlanned(flightId: Long) {
        viewModelScope.launch {
            journeyRepository.setTravelLegStatus(flightId, TravelLegStatus.PLANNED)
            _uiState.value = _uiState.value.copy(message = "Leg reset to planned.")
        }
    }

    fun deleteJourney() {
        viewModelScope.launch {
            journeyRepository.deleteJourney(journeyId)
            _uiState.value = _uiState.value.copy(wasDeleted = true)
        }
    }

    private fun loadTimelineForJourney(journey: JourneyWithFlights) {
        timelineJob?.cancel()
        timelineJob = viewModelScope.launch {
            // While still traveling, keep the date shell open through today so post-airport
            // GPS and photos remain part of this journey.
            if (!journey.isCompleted && !journey.isCancelled) {
                journeyRepository.ensureOpenThroughToday(journeyId)
            }
            val zone = ZoneId.systemDefault()
            val refreshed = journeyRepository.getJourney(journeyId).first() ?: journey
            // Prefer editable journey date range so early arrivals / late departures stay in scope.
            val endDay = refreshed.journey.endDateEpochDay
                ?: refreshed.journey.startDateEpochDay
            val effectiveEndDay = if (!refreshed.isCompleted && !refreshed.isCancelled) {
                maxOf(endDay, LocalDate.now(zone).toEpochDay())
            } else {
                endDay
            }
            val startMillis = LocalDate.ofEpochDay(refreshed.journey.startDateEpochDay)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
            val endExclusive = LocalDate.ofEpochDay(effectiveEndDay)
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()

            val sampleEvery = 1
            val gps = runCatching {
                locationRepository.getMapSample(
                    startMillis = startMillis,
                    endMillis = endExclusive,
                    countryCode = null,
                    sampleEvery = sampleEvery,
                    limit = 2_500
                ).first()
            }.getOrDefault(emptyList())

            val photos = runCatching {
                locationRepository.getLocationsWithPhotos(
                    startMillis = startMillis,
                    endMillis = endExclusive,
                    countryCode = null,
                    limit = 300
                ).first()
            }.getOrDefault(emptyList())

            val evidence = runCatching {
                locationRepository.getDailyCountryEvidence(startMillis, endExclusive)
            }.getOrDefault(emptyList())

            val photosByDay = photos.groupBy {
                it.timestamp.atZone(zone).toLocalDate()
            }
            val days = evidence
                .groupBy { LocalDate.parse(it.date) }
                .map { (date, rows) ->
                    val top = rows.maxByOrNull { it.pointCount }
                    JourneyDaySummary(
                        date = date,
                        countryCode = top?.countryCode,
                        countryName = top?.countryName,
                        pointCount = rows.sumOf { it.pointCount },
                        photoCount = photosByDay[date]?.size ?: 0
                    )
                }
                .sortedBy { it.date }

            val trackedDays = runCatching {
                locationRepository.countTrackedDays(startMillis, endExclusive)
            }.getOrDefault(days.size)

            val album = refreshed.photoUris.toSet()
            val airportMillis = refreshed.lastAirportArrivalMillis
            val newSinceAirport = if (
                airportMillis != null &&
                !refreshed.isCompleted &&
                !refreshed.isCancelled
            ) {
                photos.filter { location ->
                    if (location.timestamp.toEpochMilli() < airportMillis) return@filter false
                    val uri = location.originalPhotoUri?.takeIf { it.isNotBlank() }
                        ?: location.photoUri?.takeIf { it.isNotBlank() }
                    uri != null && uri !in album
                }.sortedBy { it.timestamp }
            } else {
                emptyList()
            }

            _uiState.value = _uiState.value.copy(
                gpsTrack = gps.sortedBy { it.timestamp },
                timelinePhotos = photos.sortedBy { it.timestamp },
                photosSinceAirport = newSinceAirport,
                showPhotosSinceAirportPrompt = newSinceAirport.isNotEmpty(),
                journeyDays = days,
                trackedDayCount = trackedDays,
                suggestedArrivalPlace = if (!refreshed.isCompleted && !refreshed.isCancelled) {
                    journeyTravelDetector.suggestedArrivalPlace(journeyId)
                } else {
                    null
                }
            )
        }
    }
}
