package com.roamatlas.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.domain.model.CountingMode
import com.roamatlas.domain.model.DayCountingRule
import com.roamatlas.domain.model.DayTracker
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

data class DashboardUiState(
    val trackerResults: List<TrackerResult> = emptyList(),
    val locationCount: Int = 0,
    val countriesVisited: Int = 0,
    val earliestDate: LocalDate? = null,
    val isLoading: Boolean = true,
    val isDemoData: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val dayTrackerRepository: DayTrackerRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    init {
        loadDashboardData()
    }
    
    private fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val locationCount = locationRepository.getLocationCount().first()
            val countryCodes = locationRepository.getDistinctCountryCodes().first()
            val earliestTimestamp = locationRepository.getEarliestTimestamp()
            
            val trackers = dayTrackerRepository.getAllTrackers().first()
            
            val trackerResults = if (trackers.isEmpty() && locationCount == 0) {
                generateDemoTrackerResults()
            } else {
                calculateTrackerResults(trackers)
            }
            
            val isDemoData = locationCount == 0
            
            _uiState.value = DashboardUiState(
                trackerResults = trackerResults,
                locationCount = if (isDemoData) 1247 else locationCount,
                countriesVisited = if (isDemoData) 12 else countryCodes.size,
                earliestDate = if (isDemoData) {
                    LocalDate.of(2020, 1, 15)
                } else {
                    earliestTimestamp?.let { 
                        Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() 
                    }
                },
                isLoading = false,
                isDemoData = isDemoData
            )
        }
    }
    
    private fun generateDemoTrackerResults(): List<TrackerResult> {
        val now = Instant.now()
        return listOf(
            TrackerResult(
                tracker = DayTracker(
                    id = 1,
                    name = "UK Tax Year 2026",
                    countryCode = "GB",
                    countryName = "United Kingdom",
                    countingMode = CountingMode.CUSTOM_DATE_RANGE,
                    startDate = LocalDate.of(2026, 4, 6),
                    endDate = LocalDate.of(2027, 4, 5),
                    thresholdDays = 183,
                    countingRule = DayCountingRule.ANY_PRESENCE
                ),
                totalDays = 156,
                verifiedDays = 142,
                uncertainDays = 14,
                missingDays = 0,
                daysInRange = 365,
                percentageComplete = 85.0f,
                thresholdExceeded = false,
                lastCalculated = now
            ),
            TrackerResult(
                tracker = DayTracker(
                    id = 2,
                    name = "Days Outside US 2026",
                    countryCode = "US",
                    countryName = "United States",
                    countingMode = CountingMode.CALENDAR_YEAR,
                    year = 2026,
                    thresholdDays = 330,
                    countInsideCountry = false,
                    countingRule = DayCountingRule.ANY_PRESENCE
                ),
                totalDays = 289,
                verifiedDays = 275,
                uncertainDays = 14,
                missingDays = 5,
                daysInRange = 365,
                percentageComplete = 79.2f,
                thresholdExceeded = false,
                lastCalculated = now
            ),
            TrackerResult(
                tracker = DayTracker(
                    id = 3,
                    name = "Rolling 12 Months - Portugal",
                    countryCode = "PT",
                    countryName = "Portugal",
                    countingMode = CountingMode.ROLLING_12_MONTHS,
                    thresholdDays = 183,
                    countingRule = DayCountingRule.OVERNIGHT_STAY
                ),
                totalDays = 198,
                verifiedDays = 190,
                uncertainDays = 8,
                missingDays = 0,
                daysInRange = 365,
                percentageComplete = 100f,
                thresholdExceeded = true,
                lastCalculated = now
            )
        )
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
            CountingMode.CALENDAR_YEAR -> {
                val year = tracker.year ?: now.year
                val start = LocalDate.of(year, 1, 1)
                val end = LocalDate.of(year, 12, 31)
                start.toEpochMilli() to end.toEpochMilli()
            }
            CountingMode.CUSTOM_DATE_RANGE -> {
                val start = tracker.startDate ?: now.minusYears(1)
                val end = tracker.endDate ?: now
                start.toEpochMilli() to end.toEpochMilli()
            }
            CountingMode.ROLLING_12_MONTHS -> {
                val end = now
                val start = now.minusYears(1)
                start.toEpochMilli() to end.toEpochMilli()
            }
            CountingMode.CUSTOM_START_UNTIL_TODAY -> {
                val start = tracker.startDate ?: now.minusYears(1)
                start.toEpochMilli() to now.toEpochMilli()
            }
        }
    }
    
    private fun calculateDaysInRange(startMillis: Long, endMillis: Long): Int {
        return ((endMillis - startMillis) / (24 * 60 * 60 * 1000)).toInt() + 1
    }
    
    private fun LocalDate.toEpochMilli(): Long {
        return this.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    
    fun refresh() {
        loadDashboardData()
    }
}
