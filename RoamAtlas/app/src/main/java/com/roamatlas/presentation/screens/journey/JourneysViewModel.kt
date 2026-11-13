package com.roamatlas.presentation.screens.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.journey.JourneyRepository
import com.roamatlas.data.journey.JourneyWithFlights
import com.roamatlas.data.local.entity.FlightEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class JourneyListTab {
    UPCOMING,
    PAST
}

data class JourneysUiState(
    val journeys: List<JourneyWithFlights> = emptyList(),
    val upcomingJourneys: List<JourneyWithFlights> = emptyList(),
    val pastJourneys: List<JourneyWithFlights> = emptyList(),
    val visibleJourneys: List<JourneyWithFlights> = emptyList(),
    val destinationOptions: List<String> = emptyList(),
    val selectedTab: JourneyListTab = JourneyListTab.UPCOMING,
    val searchQuery: String = "",
    val destinationFilter: String? = null,
    val unassignedFlights: List<FlightEntity> = emptyList(),
    val suggestedGroups: List<List<FlightEntity>> = emptyList(),
    val showCreateDialog: Boolean = false,
    val selectedFlightIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class JourneysViewModel @Inject constructor(
    private val journeyRepository: JourneyRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(JourneysUiState())
    val uiState: StateFlow<JourneysUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                journeyRepository.getJourneys(),
                journeyRepository.getUnassignedFlights()
            ) { journeys, unassigned ->
                journeys to unassigned
            }.collect { (journeys, unassigned) ->
                val upcoming = journeys
                    .filterNot { it.isPast }
                    .sortedBy { it.firstDepartureMillis ?: Long.MAX_VALUE }
                val past = journeys
                    .filter { it.isPast }
                    .sortedByDescending { it.firstDepartureMillis ?: 0L }
                val preferredTab = when {
                    upcoming.isNotEmpty() -> JourneyListTab.UPCOMING
                    past.isNotEmpty() -> JourneyListTab.PAST
                    else -> _uiState.value.selectedTab
                }
                val destinations = journeys.mapNotNull { journey ->
                    journey.journey.destinationCity?.takeIf { it.isNotBlank() }
                        ?: journey.journey.destinationCountryCode?.takeIf { it.isNotBlank() }
                }.distinct().sorted()
                val current = _uiState.value
                val nextTab = if (current.journeys.isEmpty()) preferredTab else current.selectedTab
                _uiState.value = current.copy(
                    journeys = journeys,
                    upcomingJourneys = upcoming,
                    pastJourneys = past,
                    destinationOptions = destinations,
                    selectedTab = nextTab,
                    unassignedFlights = unassigned,
                    suggestedGroups = journeyRepository.suggestConnectedGroups(unassigned),
                    isLoading = false,
                    visibleJourneys = computeVisible(
                        tab = nextTab,
                        upcoming = upcoming,
                        past = past,
                        query = current.searchQuery,
                        destination = current.destinationFilter
                    )
                )
            }
        }
    }

    fun selectTab(tab: JourneyListTab) {
        val state = _uiState.value
        _uiState.value = state.copy(
            selectedTab = tab,
            visibleJourneys = computeVisible(
                tab = tab,
                upcoming = state.upcomingJourneys,
                past = state.pastJourneys,
                query = state.searchQuery,
                destination = state.destinationFilter
            )
        )
    }

    fun setSearchQuery(query: String) {
        val state = _uiState.value
        _uiState.value = state.copy(
            searchQuery = query,
            visibleJourneys = computeVisible(
                tab = state.selectedTab,
                upcoming = state.upcomingJourneys,
                past = state.pastJourneys,
                query = query,
                destination = state.destinationFilter
            )
        )
    }

    fun setDestinationFilter(destination: String?) {
        val state = _uiState.value
        val next = if (state.destinationFilter == destination) null else destination
        _uiState.value = state.copy(
            destinationFilter = next,
            visibleJourneys = computeVisible(
                tab = state.selectedTab,
                upcoming = state.upcomingJourneys,
                past = state.pastJourneys,
                query = state.searchQuery,
                destination = next
            )
        )
    }

    fun showCreateDialog(preselected: List<FlightEntity> = emptyList()) {
        _uiState.value = _uiState.value.copy(
            showCreateDialog = true,
            selectedFlightIds = preselected.map { it.id }.toSet(),
            errorMessage = null
        )
    }

    fun hideCreateDialog() {
        _uiState.value = _uiState.value.copy(
            showCreateDialog = false,
            selectedFlightIds = emptySet()
        )
    }

    fun toggleFlight(flightId: Long) {
        val selected = _uiState.value.selectedFlightIds
        _uiState.value = _uiState.value.copy(
            selectedFlightIds = if (flightId in selected) {
                selected - flightId
            } else {
                selected + flightId
            }
        )
    }

    fun createJourney(name: String, notes: String) {
        val selected = _uiState.value.unassignedFlights.filter {
            it.id in _uiState.value.selectedFlightIds
        }
        if (selected.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Select at least one flight."
            )
            return
        }
        viewModelScope.launch {
            runCatching {
                journeyRepository.createJourney(name, notes, selected)
            }.onSuccess {
                hideCreateDialog()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message ?: "Could not create journey."
                )
            }
        }
    }

    private fun computeVisible(
        tab: JourneyListTab,
        upcoming: List<JourneyWithFlights>,
        past: List<JourneyWithFlights>,
        query: String,
        destination: String?
    ): List<JourneyWithFlights> {
        val base = when (tab) {
            JourneyListTab.UPCOMING -> upcoming
            JourneyListTab.PAST -> past
        }
        return base.filter { journeyMatches(it, query, destination) }
    }

    private fun journeyMatches(
        journey: JourneyWithFlights,
        query: String,
        destination: String?
    ): Boolean {
        if (!destination.isNullOrBlank()) {
            val destOk = listOfNotNull(
                journey.journey.destinationCity,
                journey.journey.destinationCountryCode
            ).any { it.equals(destination, ignoreCase = true) } ||
                journey.flights.any {
                    it.arrivalCity.equals(destination, ignoreCase = true) ||
                        it.arrivalCountryCode.equals(destination, ignoreCase = true)
                }
            if (!destOk) return false
        }
        val q = query.trim()
        if (q.isEmpty()) return true
        val haystack = buildList {
            add(journey.journey.name)
            journey.journey.destinationCity?.let(::add)
            journey.journey.destinationCountryCode?.let(::add)
            journey.journey.notes?.let(::add)
            journey.flights.forEach { flight ->
                flight.airline?.let(::add)
                flight.flightNumber?.let(::add)
                flight.bookingReference?.let(::add)
                flight.departureAirportCode.let(::add)
                flight.arrivalAirportCode.let(::add)
                flight.departureCity?.let(::add)
                flight.arrivalCity?.let(::add)
            }
        }.joinToString(" ").lowercase()
        return q.lowercase().split(Regex("\\s+")).all { token -> haystack.contains(token) }
    }
}
