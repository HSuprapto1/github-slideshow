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

data class JourneysUiState(
    val journeys: List<JourneyWithFlights> = emptyList(),
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
                _uiState.value = _uiState.value.copy(
                    journeys = journeys,
                    unassignedFlights = unassigned,
                    suggestedGroups = journeyRepository.suggestConnectedGroups(unassigned),
                    isLoading = false
                )
            }
        }
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
}
