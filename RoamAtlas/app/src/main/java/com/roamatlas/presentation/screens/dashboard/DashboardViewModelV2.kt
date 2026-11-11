package com.roamatlas.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.repository.TravelStatisticsRepository
import com.roamatlas.domain.model.CountryVisitStats
import com.roamatlas.domain.model.DayTracker
import com.roamatlas.domain.model.TravelStatistics
import com.roamatlas.domain.model.TrackerResult
import com.roamatlas.domain.repository.DayTrackerRepository
import com.roamatlas.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class DashboardUiStateV2(
    val travelStats: TravelStatistics = TravelStatistics(),
    val customTrackers: List<TrackerResult> = emptyList(),
    val homeCountryCode: String? = null,
    val homeCountryName: String? = null,
    val isLoading: Boolean = true,
    val hasData: Boolean = false,
    val showAddTrackerDialog: Boolean = false
)

@HiltViewModel
class DashboardViewModelV2 @Inject constructor(
    private val locationRepository: LocationRepository,
    private val dayTrackerRepository: DayTrackerRepository,
    private val travelStatisticsRepository: TravelStatisticsRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DashboardUiStateV2())
    val uiState: StateFlow<DashboardUiStateV2> = _uiState.asStateFlow()
    
    init {
        loadDashboardData()
    }
    
    private fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val locationCount = locationRepository.getLocationCount().first()
            val homeCountryCode = travelStatisticsRepository.getHomeCountryCode()
            
            if (locationCount == 0) {
                // No data yet - show empty state (no demo data)
                _uiState.value = DashboardUiStateV2(
                    travelStats = TravelStatistics(),
                    customTrackers = emptyList(),
                    homeCountryCode = homeCountryCode,
                    isLoading = false,
                    hasData = false
                )
            } else {
                // Real data from user's imported locations
                travelStatisticsRepository.getTravelStatistics().collect { stats ->
                    val trackers = dayTrackerRepository.getAllTrackers().first()
                    val trackerResults = calculateTrackerResults(trackers)
                    
                    _uiState.value = DashboardUiStateV2(
                        travelStats = stats,
                        customTrackers = trackerResults,
                        homeCountryCode = homeCountryCode,
                        isLoading = false,
                        hasData = true
                    )
                }
            }
        }
    }
    
    private suspend fun calculateTrackerResults(trackers: List<DayTracker>): List<TrackerResult> {
        return trackers.map { tracker ->
            val (startMillis, endMillis) = getDateRangeForTracker(tracker)
            val daysCount = locationRepository.countDaysInCountry(
                tracker.countryCode,
                startMillis,
                endMillis
            )
            
            TrackerResult(
                tracker = tracker,
                totalDays = daysCount,
                verifiedDays = daysCount,
                uncertainDays = 0,
                missingDays = 0,
                daysInRange = calculateDaysInRange(startMillis, endMillis),
                percentageComplete = if (daysCount > 0) 100f else 0f,
                thresholdExceeded = tracker.thresholdDays?.let { daysCount >= it },
                lastCalculated = Instant.now()
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
        return ((endMillis - startMillis) / (24 * 60 * 60 * 1000)).toInt() + 1
    }
    
    private fun LocalDate.toEpochMilli(): Long {
        return this.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun LocalDate.inclusiveEndMillis(): Long = plusDays(1).toEpochMilli() - 1
    
    fun refresh() {
        loadDashboardData()
    }
    
    fun showAddTrackerDialog() {
        _uiState.value = _uiState.value.copy(showAddTrackerDialog = true)
    }
    
    fun hideAddTrackerDialog() {
        _uiState.value = _uiState.value.copy(showAddTrackerDialog = false)
    }
}
