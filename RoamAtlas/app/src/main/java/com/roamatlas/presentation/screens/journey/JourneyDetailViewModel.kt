package com.roamatlas.presentation.screens.journey

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.airport.Airport
import com.roamatlas.data.airport.AirportRepository
import com.roamatlas.data.journey.JourneyRepository
import com.roamatlas.data.journey.JourneyWithFlights
import com.roamatlas.data.local.entity.FlightEntity
import com.roamatlas.domain.model.LocationRecord
import com.roamatlas.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
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
    val journeyDays: List<JourneyDaySummary> = emptyList(),
    val trackedDayCount: Int = 0,
    val showGpsTrack: Boolean = true,
    val nowMillis: Long = System.currentTimeMillis(),
    val isLoading: Boolean = true,
    val wasDeleted: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null
)

@HiltViewModel
class JourneyDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val journeyRepository: JourneyRepository,
    private val airportRepository: AirportRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {
    private val journeyId = savedStateHandle.get<String>("journeyId")?.toLongOrNull() ?: 0L
    private val _uiState = MutableStateFlow(JourneyDetailUiState())
    val uiState: StateFlow<JourneyDetailUiState> = _uiState.asStateFlow()
    private var timelineJob: Job? = null

    init {
        viewModelScope.launch {
            journeyRepository.getJourney(journeyId).collect { journey ->
                val codes = journey?.flights.orEmpty().flatMap {
                    listOf(it.departureAirportCode, it.arrivalAirportCode)
                }
                _uiState.value = _uiState.value.copy(
                    journey = journey,
                    airports = airportRepository.getByIataCodes(codes),
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
            }
        }
    }

    fun setShowGpsTrack(show: Boolean) {
        _uiState.value = _uiState.value.copy(showGpsTrack = show)
    }

    fun importTimelinePhotosToAlbum() {
        val photos = _uiState.value.timelinePhotos
        if (photos.isEmpty()) {
            _uiState.value = _uiState.value.copy(message = "No timeline photos in this journey’s dates.")
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
                message = if (added > 0) {
                    "Added $added timeline photo${if (added == 1) "" else "s"} to the trip album."
                } else {
                    "No new timeline photos could be added."
                }
            )
        }
    }

    fun updateJourney(name: String, notes: String) {
        viewModelScope.launch {
            journeyRepository.updateJourney(journeyId, name, notes)
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

    fun deleteJourney() {
        viewModelScope.launch {
            journeyRepository.deleteJourney(journeyId)
            _uiState.value = _uiState.value.copy(wasDeleted = true)
        }
    }

    private fun loadTimelineForJourney(journey: JourneyWithFlights) {
        timelineJob?.cancel()
        timelineJob = viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val startMillis = journey.firstDepartureMillis
                ?: journey.journey.startDateEpochDay.let {
                    LocalDate.ofEpochDay(it).atStartOfDay(zone).toInstant().toEpochMilli()
                }
            val endExclusive = journey.finalArrivalMillis?.let { end ->
                // Include the arrival day fully.
                Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
                    .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            } ?: journey.journey.endDateEpochDay?.let {
                LocalDate.ofEpochDay(it).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            } ?: (startMillis + 24L * 60 * 60 * 1000)

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

            _uiState.value = _uiState.value.copy(
                gpsTrack = gps.sortedBy { it.timestamp },
                timelinePhotos = photos.sortedBy { it.timestamp },
                journeyDays = days,
                trackedDayCount = trackedDays
            )
        }
    }
}
