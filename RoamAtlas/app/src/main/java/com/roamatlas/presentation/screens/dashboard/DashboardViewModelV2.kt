package com.roamatlas.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.journey.JourneyRepository
import com.roamatlas.data.journey.JourneyWithFlights
import com.roamatlas.data.photo.CapturedPhotoLocation
import com.roamatlas.data.photo.PhotoTimelineImporter
import android.net.Uri
import java.time.LocalDate
import com.roamatlas.data.repository.TravelStatisticsRepository
import com.roamatlas.data.repository.DayOverrideRepository
import com.roamatlas.data.repository.ManualDayClassification
import com.roamatlas.domain.model.CountingMode
import com.roamatlas.domain.model.DayTracker
import com.roamatlas.domain.model.TravelStatistics
import com.roamatlas.domain.model.TrackerResult
import com.roamatlas.domain.repository.DayTrackerRepository
import com.roamatlas.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class DashboardUiStateV2(
    val travelStats: TravelStatistics = TravelStatistics(),
    val customTrackers: List<TrackerResult> = emptyList(),
    val homeCountryCode: String? = null,
    val homeCountryName: String? = null,
    val isLoading: Boolean = true,
    val hasData: Boolean = false,
    val showAddMenu: Boolean = false,
    val showAddTrackerDialog: Boolean = false,
    val showAddPhotoDialog: Boolean = false,
    val photoImporting: Boolean = false,
    val photoImportMessage: String? = null,
    val pendingNoGpsUris: List<Uri> = emptyList(),
    val showNoGpsResolver: Boolean = false,
    val showLocationPicker: Boolean = false,
    val resolvingNoGpsUri: Uri? = null,
    val todayPointCount: Int = 0,
    val todayCities: List<String> = emptyList(),
    val editingTracker: DayTracker? = null,
    val trackerPendingDelete: DayTracker? = null,
    val countries: List<Pair<String, String>> = emptyList(),
    val upcomingJourney: JourneyWithFlights? = null,
    val nowMillis: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

private data class DashboardData(
    val stats: TravelStatistics,
    val trackers: List<DayTracker>,
    val homeCountryCode: String?,
    val journeys: List<JourneyWithFlights>,
    val now: Long
)

@HiltViewModel
class DashboardViewModelV2 @Inject constructor(
    private val locationRepository: LocationRepository,
    private val dayTrackerRepository: DayTrackerRepository,
    private val travelStatisticsRepository: TravelStatisticsRepository,
    private val dayOverrideRepository: DayOverrideRepository,
    private val journeyRepository: JourneyRepository,
    private val photoTimelineImporter: PhotoTimelineImporter
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DashboardUiStateV2())
    val uiState: StateFlow<DashboardUiStateV2> = _uiState.asStateFlow()
    private val clock = MutableStateFlow(System.currentTimeMillis())
    
    init {
        loadDashboardData()
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                clock.value = System.currentTimeMillis()
            }
        }
    }
    
    private fun loadDashboardData() {
        viewModelScope.launch {
            try {
                combine(
                    travelStatisticsRepository.getTravelStatistics(),
                    dayTrackerRepository.getAllTrackers(),
                    travelStatisticsRepository.getHomeCountryCodeFlow(),
                    journeyRepository.getJourneys(),
                    clock
                ) { stats, trackers, homeCountryCode, journeys, now ->
                    DashboardData(stats, trackers, homeCountryCode, journeys, now)
                }.collectLatest { data ->
                    val stats = data.stats
                    val trackers = data.trackers
                    val homeCountryCode = data.homeCountryCode
                    val trackerResults = calculateTrackerResults(trackers)
                    val countries = TravelStatisticsRepository.getAllCountries()
                    val upcomingJourney = data.journeys
                        .filter { (it.finalArrivalMillis ?: Long.MIN_VALUE) >= data.now }
                        .minByOrNull { it.firstDepartureMillis ?: Long.MAX_VALUE }
                    val today = LocalDate.now()
                    val todayPoints = runCatching {
                        locationRepository.getLocationsForLocalDate(today).first()
                    }.getOrDefault(emptyList())
                    val todayCount = runCatching {
                        locationRepository.countForLocalDate(today)
                    }.getOrDefault(todayPoints.size)
                    val todayCities = todayPoints.mapNotNull { it.city?.takeIf(String::isNotBlank) }
                        .distinct()
                        .take(4)
                    _uiState.value = _uiState.value.copy(
                        travelStats = stats,
                        customTrackers = trackerResults,
                        homeCountryCode = homeCountryCode,
                        homeCountryName = countries.firstOrNull { it.first == homeCountryCode }?.second,
                        isLoading = false,
                        hasData = stats.totalLocationsRecorded > 0 || stats.totalFlights > 0,
                        countries = countries,
                        todayPointCount = todayCount,
                        todayCities = todayCities,
                        upcomingJourney = upcomingJourney,
                        nowMillis = data.now,
                        errorMessage = null
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Dashboard could not be loaded."
                )
            }
        }
    }
    
    private suspend fun calculateTrackerResults(trackers: List<DayTracker>): List<TrackerResult> {
        return trackers.map { tracker ->
            val (startMillis, endMillis) = getDateRangeForTracker(tracker)
            val trackedDays = locationRepository.countTrackedDays(startMillis, endMillis)
            val zone = java.time.ZoneId.systemDefault()
            val startDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
            val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
            val overrides = dayOverrideRepository.getOverrides(tracker.countryCode)
                .filterKeys { it in startDate..endDate }
            val manualOutside = overrides.filterValues {
                it == ManualDayClassification.OUTSIDE
            }.keys
            val manualInside = overrides.filterValues {
                it == ManualDayClassification.INSIDE
            }.keys
            val resultDays = if (tracker.countInsideCountry) {
                val evidenceDates = locationRepository.getDaysInCountry(
                    tracker.countryCode,
                    startMillis,
                    endMillis
                )
                ((evidenceDates + manualInside) - manualOutside).size
            } else {
                val evidenceDates = locationRepository.getDaysOutsideCountry(
                    tracker.countryCode,
                    startMillis,
                    endMillis
                )
                ((evidenceDates + manualOutside) - manualInside).size
            }
            val daysUntilRangeEnd = ChronoUnit.DAYS
                .between(LocalDate.now(), endDate)
                .coerceAtLeast(0)
                .toInt()
            val daysNeededForThreshold = tracker.thresholdDays?.let {
                (it - resultDays).coerceAtLeast(0)
            }
            
            TrackerResult(
                tracker = tracker,
                totalDays = resultDays,
                verifiedDays = resultDays,
                uncertainDays = 0,
                missingDays = (calculateDaysInRange(startMillis, endMillis) - trackedDays)
                    .coerceAtLeast(0),
                daysInRange = calculateDaysInRange(startMillis, endMillis),
                percentageComplete = if (trackedDays > 0) {
                    resultDays.toFloat() / trackedDays * 100f
                } else 0f,
                thresholdExceeded = tracker.thresholdDays?.let { resultDays >= it },
                lastCalculated = Instant.now(),
                daysUntilRangeEnd = daysUntilRangeEnd,
                daysNeededForThreshold = daysNeededForThreshold
            )
        }
    }
    
    private fun getDateRangeForTracker(tracker: DayTracker): Pair<Long, Long> {
        val now = LocalDate.now()
        return when (tracker.countingMode) {
            com.roamatlas.domain.model.CountingMode.CALENDAR_YEAR -> {
                val year = tracker.year ?: now.year
                val start = LocalDate.of(year, 1, 1)
                val end = LocalDate.of(year, 12, 31)
                start.toEpochMilli() to end.inclusiveEndMillis()
            }
            com.roamatlas.domain.model.CountingMode.CUSTOM_DATE_RANGE -> {
                val start = tracker.startDate ?: now.minusYears(1)
                val end = tracker.endDate ?: now
                start.toEpochMilli() to end.inclusiveEndMillis()
            }
            com.roamatlas.domain.model.CountingMode.ROLLING_12_MONTHS -> {
                val end = now
                val start = now.minusYears(1)
                start.toEpochMilli() to end.inclusiveEndMillis()
            }
            com.roamatlas.domain.model.CountingMode.CUSTOM_START_UNTIL_TODAY -> {
                val start = tracker.startDate ?: now.minusYears(1)
                start.toEpochMilli() to now.inclusiveEndMillis()
            }
        }
    }
    
    private fun calculateDaysInRange(startMillis: Long, endMillis: Long): Int {
        val zone = java.time.ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        val end = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(start, end).toInt() + 1
    }
    
    private fun LocalDate.toEpochMilli(): Long {
        return this.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun LocalDate.inclusiveEndMillis(): Long = plusDays(1).toEpochMilli() - 1
    
    fun refresh() {
        // Room flows refresh this screen automatically after imports and setting changes.
    }
    
    fun showAddMenu() {
        _uiState.value = _uiState.value.copy(showAddMenu = true)
    }

    fun hideAddMenu() {
        _uiState.value = _uiState.value.copy(showAddMenu = false)
    }

    fun showAddTrackerDialog() {
        _uiState.value = _uiState.value.copy(
            showAddMenu = false,
            showAddTrackerDialog = true,
            editingTracker = null
        )
    }
    
    fun hideAddTrackerDialog() {
        _uiState.value = _uiState.value.copy(
            showAddTrackerDialog = false,
            editingTracker = null
        )
    }

    fun showAddPhotoDialog() {
        _uiState.value = _uiState.value.copy(
            showAddMenu = false,
            showAddPhotoDialog = true,
            photoImportMessage = null
        )
    }

    fun hideAddPhotoDialog() {
        _uiState.value = _uiState.value.copy(
            showAddPhotoDialog = false,
            photoImporting = false,
            showNoGpsResolver = false,
            showLocationPicker = false,
            resolvingNoGpsUri = null
        )
    }

    fun importGalleryPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(photoImporting = true, photoImportMessage = null)
            val result = runCatching {
                photoTimelineImporter.importGalleryPhotos(uris)
            }.getOrElse {
                _uiState.value = _uiState.value.copy(
                    photoImporting = false,
                    photoImportMessage = it.message ?: "Could not import photos."
                )
                return@launch
            }
            refreshTodaySummary()
            _uiState.value = _uiState.value.copy(
                photoImporting = false,
                photoImportMessage = result.summaryMessage(),
                pendingNoGpsUris = result.pendingNoGpsUris,
                showNoGpsResolver = result.pendingNoGpsUris.isNotEmpty(),
                resolvingNoGpsUri = result.pendingNoGpsUris.firstOrNull()
            )
        }
    }

    fun importCapturedPhoto(uri: Uri, capture: CapturedPhotoLocation) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(photoImporting = true, photoImportMessage = null)
            val result = runCatching {
                photoTimelineImporter.importCapturedPhoto(uri, capture)
            }.getOrElse {
                _uiState.value = _uiState.value.copy(
                    photoImporting = false,
                    photoImportMessage = it.message ?: "Could not import photo."
                )
                return@launch
            }
            refreshTodaySummary()
            _uiState.value = _uiState.value.copy(
                photoImporting = false,
                photoImportMessage = result.summaryMessage(),
                pendingNoGpsUris = result.pendingNoGpsUris,
                showNoGpsResolver = result.pendingNoGpsUris.isNotEmpty(),
                resolvingNoGpsUri = result.pendingNoGpsUris.firstOrNull()
            )
        }
    }

    fun resolvePendingWithCurrentLocation(location: CapturedPhotoLocation) {
        val uri = _uiState.value.resolvingNoGpsUri ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(photoImporting = true)
            val result = runCatching {
                photoTimelineImporter.importWithChosenLocation(
                    uri,
                    location.copy(label = location.label ?: "Current location")
                )
            }.getOrElse {
                _uiState.value = _uiState.value.copy(
                    photoImporting = false,
                    photoImportMessage = it.message ?: "Could not place photo."
                )
                return@launch
            }
            advanceNoGpsQueue(result.summaryMessage())
        }
    }

    fun openLocationPickerForPending() {
        if (_uiState.value.resolvingNoGpsUri == null) return
        _uiState.value = _uiState.value.copy(
            showLocationPicker = true,
            showAddPhotoDialog = false,
            showNoGpsResolver = false
        )
    }

    fun hideLocationPicker() {
        _uiState.value = _uiState.value.copy(
            showLocationPicker = false,
            showAddPhotoDialog = true,
            showNoGpsResolver = _uiState.value.pendingNoGpsUris.isNotEmpty()
        )
    }

    fun resolvePendingWithMapLocation(location: CapturedPhotoLocation) {
        val uri = _uiState.value.resolvingNoGpsUri ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(photoImporting = true, showLocationPicker = false)
            val result = runCatching {
                photoTimelineImporter.importWithChosenLocation(uri, location)
            }.getOrElse {
                _uiState.value = _uiState.value.copy(
                    photoImporting = false,
                    showAddPhotoDialog = true,
                    showNoGpsResolver = true,
                    photoImportMessage = it.message ?: "Could not place photo."
                )
                return@launch
            }
            advanceNoGpsQueue(result.summaryMessage())
        }
    }

    fun skipCurrentNoGpsPhoto() {
        viewModelScope.launch {
            advanceNoGpsQueue("Skipped one photo without choosing a location.")
        }
    }

    private suspend fun advanceNoGpsQueue(message: String) {
        val remaining = _uiState.value.pendingNoGpsUris.drop(1)
        refreshTodaySummary()
        _uiState.value = _uiState.value.copy(
            photoImporting = false,
            photoImportMessage = message,
            pendingNoGpsUris = remaining,
            resolvingNoGpsUri = remaining.firstOrNull(),
            showNoGpsResolver = remaining.isNotEmpty(),
            showAddPhotoDialog = true,
            showLocationPicker = false
        )
    }

    private suspend fun refreshTodaySummary() {
        val today = LocalDate.now()
        val todayPoints = runCatching {
            locationRepository.getLocationsForLocalDate(today).first()
        }.getOrDefault(emptyList())
        val todayCount = runCatching {
            locationRepository.countForLocalDate(today)
        }.getOrDefault(todayPoints.size)
        _uiState.value = _uiState.value.copy(
            todayPointCount = todayCount,
            todayCities = todayPoints.mapNotNull { it.city?.takeIf(String::isNotBlank) }
                .distinct()
                .take(4)
        )
    }

    fun editTracker(tracker: DayTracker) {
        _uiState.value = _uiState.value.copy(
            showAddMenu = false,
            showAddTrackerDialog = true,
            editingTracker = tracker
        )
    }

    fun requestDeleteTracker(tracker: DayTracker) {
        _uiState.value = _uiState.value.copy(trackerPendingDelete = tracker)
    }

    fun dismissDeleteTracker() {
        _uiState.value = _uiState.value.copy(trackerPendingDelete = null)
    }

    fun confirmDeleteTracker() {
        val tracker = _uiState.value.trackerPendingDelete ?: return
        viewModelScope.launch {
            dayTrackerRepository.deleteTrackerById(tracker.id)
            dismissDeleteTracker()
        }
    }

    fun saveTracker(
        name: String,
        countryCode: String,
        countryName: String,
        countingMode: CountingMode,
        countInsideCountry: Boolean,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        year: Int? = null,
        thresholdDays: Int? = null
    ) {
        viewModelScope.launch {
            val existing = _uiState.value.editingTracker
            val tracker = DayTracker(
                    id = existing?.id ?: 0,
                    name = name.ifBlank {
                        if (countInsideCountry) "Days in $countryName" else "Days outside $countryName"
                    },
                    countryCode = countryCode,
                    countryName = countryName,
                    countingMode = countingMode,
                    startDate = startDate,
                    endDate = endDate,
                    year = year,
                    thresholdDays = thresholdDays,
                    countInsideCountry = countInsideCountry,
                    displayOrder = existing?.displayOrder
                        ?: dayTrackerRepository.getNextDisplayOrder(),
                    createdAt = existing?.createdAt ?: Instant.now(),
                    updatedAt = Instant.now()
                )
            if (existing == null) {
                dayTrackerRepository.insertTracker(tracker)
            } else {
                dayTrackerRepository.updateTracker(tracker)
            }
            hideAddTrackerDialog()
        }
    }
}
