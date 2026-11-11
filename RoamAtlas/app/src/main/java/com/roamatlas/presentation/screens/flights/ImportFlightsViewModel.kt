package com.roamatlas.presentation.screens.flights

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.roamatlas.data.email.GmailFlightImporter
import com.roamatlas.data.email.ParsedItinerary
import com.roamatlas.data.local.dao.FlightDao
import com.roamatlas.data.local.entity.FlightEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportFlightsUiState(
    val isSignedIn: Boolean = false,
    val signedInEmail: String? = null,
    val isScanning: Boolean = false,
    val parsedItineraries: List<ParsedItinerary> = emptyList(),
    val selectedItineraries: Set<ParsedItinerary> = emptySet(),
    val isImporting: Boolean = false,
    val importSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ImportFlightsViewModel @Inject constructor(
    private val gmailFlightImporter: GmailFlightImporter,
    private val flightDao: FlightDao
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ImportFlightsUiState())
    val uiState: StateFlow<ImportFlightsUiState> = _uiState.asStateFlow()
    
    init {
        checkSignInStatus()
    }
    
    private fun checkSignInStatus() {
        val isSignedIn = gmailFlightImporter.isSignedIn()
        val email = if (isSignedIn) gmailFlightImporter.getSignedInEmail() else null
        if (!isSignedIn) {
            _uiState.update { it.copy(isSignedIn = false, signedInEmail = null) }
            return
        }
        viewModelScope.launch {
            val restored = gmailFlightImporter.restoreSignedInSession()
            _uiState.update {
                it.copy(
                    isSignedIn = restored,
                    signedInEmail = email.takeIf { restored },
                    error = if (restored) null else "Please reconnect Gmail."
                )
            }
        }
    }
    
    fun getSignInIntent(): Intent {
        return gmailFlightImporter.getSignInIntent()
    }
    
    fun handleSignInResult(account: GoogleSignInAccount) {
        viewModelScope.launch {
            val success = gmailFlightImporter.handleSignInResult(account)
            if (success) {
                _uiState.update { 
                    it.copy(
                        isSignedIn = true,
                        signedInEmail = account.email
                    ) 
                }
            } else {
                _uiState.update { it.copy(error = "Failed to connect to Gmail") }
            }
        }
    }
    
    fun signOut() {
        viewModelScope.launch {
            gmailFlightImporter.signOut()
            _uiState.update { 
                it.copy(
                    isSignedIn = false,
                    signedInEmail = null,
                    parsedItineraries = emptyList(),
                    selectedItineraries = emptySet()
                ) 
            }
        }
    }
    
    fun scanForBookings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, error = null) }
            
            try {
                val itineraries = gmailFlightImporter.scanForBookingEmails()
                _uiState.update { 
                    it.copy(
                        isScanning = false,
                        parsedItineraries = itineraries
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isScanning = false,
                        error = "Failed to scan emails: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    fun searchBooking(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, error = null) }
            
            try {
                val itineraries = gmailFlightImporter.searchBooking(query)
                _uiState.update { 
                    it.copy(
                        isScanning = false,
                        parsedItineraries = itineraries
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isScanning = false,
                        error = "Search failed: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    fun toggleItinerarySelection(itinerary: ParsedItinerary) {
        _uiState.update { state ->
            val newSelection = if (state.selectedItineraries.contains(itinerary)) {
                state.selectedItineraries - itinerary
            } else {
                state.selectedItineraries + itinerary
            }
            state.copy(selectedItineraries = newSelection)
        }
    }
    
    fun importSelectedFlights() {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }
            
            try {
                val selectedItineraries = _uiState.value.selectedItineraries
                var importedCount = 0
                
                for (itinerary in selectedItineraries) {
                    for (flight in itinerary.flights) {
                        val now = System.currentTimeMillis()
                        val depTime = flight.departureTimeMillis ?: now
                        val arrTime = flight.arrivalTimeMillis ?: depTime
                        
                        val flightEntity = FlightEntity(
                            id = 0, // Auto-generated
                            departureAirportCode = flight.departureAirportCode,
                            departureAirportName = null,
                            departureCity = null,
                            departureCountryCode = null,
                            arrivalAirportCode = flight.arrivalAirportCode,
                            arrivalAirportName = null,
                            arrivalCity = null,
                            arrivalCountryCode = null,
                            departureTimeMillis = depTime,
                            arrivalTimeMillis = arrTime,
                            flightNumber = flight.flightNumber,
                            airline = flight.airline ?: itinerary.airline,
                            aircraftType = flight.aircraftType,
                            distanceKm = null,
                            durationMinutes = null,
                            seatNumber = flight.seatNumber,
                            bookingReference = flight.bookingReference ?: itinerary.bookingReference,
                            notes = null,
                            photoUri = null,
                            source = "EMAIL_IMPORT",
                            createdAtMillis = now,
                            updatedAtMillis = now
                        )
                        
                        flightDao.insert(flightEntity)
                        importedCount++
                    }
                }
                
                _uiState.update { 
                    it.copy(
                        isImporting = false,
                        importSuccess = true,
                        selectedItineraries = emptySet(),
                        parsedItineraries = it.parsedItineraries - selectedItineraries
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isImporting = false,
                        error = "Failed to import flights: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    fun clearImportSuccess() {
        _uiState.update { it.copy(importSuccess = false) }
    }
}
