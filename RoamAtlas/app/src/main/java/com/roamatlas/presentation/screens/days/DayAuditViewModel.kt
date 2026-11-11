package com.roamatlas.presentation.screens.days

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.repository.TravelStatisticsRepository
import com.roamatlas.data.repository.DayOverrideRepository
import com.roamatlas.data.repository.ManualDayClassification
import com.roamatlas.domain.model.CountingMode
import com.roamatlas.domain.model.DayTracker
import com.roamatlas.domain.repository.DailyCountryRecord
import com.roamatlas.domain.repository.DayTrackerRepository
import com.roamatlas.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

data class AuditedDay(
    val date: LocalDate,
    val evidence: List<DailyCountryRecord>,
    val manualClassification: ManualDayClassification? = null
) {
    val hasRecordedPoint: Boolean get() = evidence.isNotEmpty()
    val hasKnownCountry: Boolean get() = evidence.any { it.countryCode != null }
}

data class DayAuditUiState(
    val startDate: LocalDate = LocalDate.now().withDayOfYear(1),
    val endDate: LocalDate = LocalDate.now(),
    val selectedCountryCode: String = "ID",
    val selectedCountryName: String = "Indonesia",
    val countInsideCountry: Boolean = false,
    val countries: List<Pair<String, String>> = emptyList(),
    val days: List<AuditedDay> = emptyList(),
    val insideDays: Int = 0,
    val outsideDays: Int = 0,
    val unknownCountryDays: Int = 0,
    val missingDays: Int = 0,
    val isLoading: Boolean = true,
    val savedMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class DayAuditViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val dayTrackerRepository: DayTrackerRepository,
    private val travelStatisticsRepository: TravelStatisticsRepository,
    private val dayOverrideRepository: DayOverrideRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DayAuditUiState())
    val uiState: StateFlow<DayAuditUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            val countries = TravelStatisticsRepository.getAllCountries()
            val homeCode = travelStatisticsRepository.getHomeCountryCode() ?: "ID"
            _uiState.value = _uiState.value.copy(
                countries = countries,
                selectedCountryCode = homeCode,
                selectedCountryName = countries.firstOrNull { it.first == homeCode }?.second
                    ?: homeCode
            )
            loadEvidence()
        }
    }

    fun setRange(startDate: LocalDate, endDate: LocalDate) {
        if (endDate < startDate) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "The end date must be on or after the start date."
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            startDate = startDate,
            endDate = endDate,
            savedMessage = null,
            errorMessage = null
        )
        loadEvidence()
    }

    fun selectCountry(code: String, name: String) {
        _uiState.value = _uiState.value.copy(
            selectedCountryCode = code,
            selectedCountryName = name,
            savedMessage = null
        )
        loadEvidence()
    }

    fun setCountInside(countInside: Boolean) {
        _uiState.value = _uiState.value.copy(countInsideCountry = countInside)
    }

    fun markDayOutside(date: LocalDate) {
        setDayClassification(date, ManualDayClassification.OUTSIDE)
    }

    fun markDayInside(date: LocalDate) {
        setDayClassification(date, ManualDayClassification.INSIDE)
    }

    private fun setDayClassification(
        date: LocalDate,
        classification: ManualDayClassification
    ) {
        val countryCode = _uiState.value.selectedCountryCode
        viewModelScope.launch {
            dayOverrideRepository.setOverride(
                countryCode,
                date,
                classification
            )
            _uiState.value = _uiState.value.copy(
                days = _uiState.value.days.map { day ->
                    if (day.date == date) {
                        day.copy(manualClassification = classification)
                    } else {
                        day
                    }
                }
            )
            calculateSummary()
        }
    }

    fun removeDayOverride(date: LocalDate) {
        val countryCode = _uiState.value.selectedCountryCode
        viewModelScope.launch {
            dayOverrideRepository.removeOverride(countryCode, date)
            _uiState.value = _uiState.value.copy(
                days = _uiState.value.days.map { day ->
                    if (day.date == date) {
                        day.copy(manualClassification = null)
                    } else {
                        day
                    }
                }
            )
            calculateSummary()
        }
    }

    fun saveRangeAsCounter() {
        val state = _uiState.value
        viewModelScope.launch {
            val direction = if (state.countInsideCountry) "in" else "outside"
            dayTrackerRepository.insertTracker(
                DayTracker(
                    name = "Days $direction ${state.selectedCountryName} " +
                        "${state.startDate} to ${state.endDate}",
                    countryCode = state.selectedCountryCode,
                    countryName = state.selectedCountryName,
                    countingMode = CountingMode.CUSTOM_DATE_RANGE,
                    startDate = state.startDate,
                    endDate = state.endDate,
                    countInsideCountry = state.countInsideCountry,
                    displayOrder = dayTrackerRepository.getNextDisplayOrder()
                )
            )
            _uiState.value = _uiState.value.copy(
                savedMessage = "Saved. You can see and edit this counter on Dashboard."
            )
        }
    }

    private fun loadEvidence() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val state = _uiState.value
                // Include a wide UTC margin; the SQL query converts each point to its local day.
                val queryStart = state.startDate.minusDays(1)
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                val queryEnd = state.endDate.plusDays(2)
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1
                val evidence = locationRepository.getDailyCountryEvidence(queryStart, queryEnd)
                    .groupBy { LocalDate.parse(it.date) }
                val overrides = dayOverrideRepository.getOverrides(state.selectedCountryCode)
                val days = generateSequence(state.startDate) { current ->
                    current.plusDays(1).takeIf { it <= state.endDate }
                }.map { date ->
                    AuditedDay(
                        date = date,
                        evidence = evidence[date].orEmpty(),
                        manualClassification = overrides[date]
                    )
                }.toList()
                _uiState.value = _uiState.value.copy(days = days, isLoading = false)
                calculateSummary()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Could not load daily records."
                )
            }
        }
    }

    private fun calculateSummary() {
        val state = _uiState.value
        val inside = state.days.count { day ->
            when (day.manualClassification) {
                ManualDayClassification.INSIDE -> true
                ManualDayClassification.OUTSIDE -> false
                null -> day.evidence.any { it.countryCode == state.selectedCountryCode }
            }
        }
        val outside = state.days.count { day ->
            when (day.manualClassification) {
                ManualDayClassification.OUTSIDE -> true
                ManualDayClassification.INSIDE -> false
                null -> day.evidence.any {
                    it.countryCode != null && it.countryCode != state.selectedCountryCode
                }
            }
        }
        val unknown = state.days.count { day ->
            day.hasRecordedPoint && !day.hasKnownCountry
        }
        val missing = state.days.count { !it.hasRecordedPoint }
        _uiState.value = state.copy(
            insideDays = inside,
            outsideDays = outside,
            unknownCountryDays = unknown,
            missingDays = missing
        )
    }
}
