package com.roamatlas.presentation.screens.flights

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.roamatlas.data.email.GmailFlightImporter
import com.roamatlas.data.email.ParsedItinerary
import com.roamatlas.data.journey.JourneyRepository
import com.roamatlas.data.local.dao.FlightDao
import com.roamatlas.data.local.entity.FlightEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val importMessage: String? = null,
    val connectionMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class ImportFlightsViewModel @Inject constructor(
    private val gmailFlightImporter: GmailFlightImporter,
    private val flightDao: FlightDao,
    private val journeyRepository: JourneyRepository
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
                        signedInEmail = account.email,
                        connectionMessage = "Connected successfully as ${account.email ?: "Google account"}",
                        error = null
                    ) 
                }
            } else {
                _uiState.update { it.copy(error = "Failed to connect to Gmail") }
            }
        }
    }

    fun handleSignInFailure(message: String) {
        _uiState.update {
            it.copy(
                isSignedIn = false,
                signedInEmail = null,
                connectionMessage = null,
                error = "Gmail connection failed: $message"
            )
        }
    }
    
    fun signOut() {
        viewModelScope.launch {
            try {
                gmailFlightImporter.signOut()
                _uiState.update {
                    it.copy(
                        isSignedIn = false,
                        signedInEmail = null,
                        parsedItineraries = emptyList(),
                        selectedItineraries = emptySet(),
                        connectionMessage = "Gmail connection removed",
                        error = null
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(error = "Could not remove Gmail connection: ${error.message}")
                }
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
                var matchedCount = 0
                var skippedCount = 0
                var airportDetailConflictCount = 0
                val existingFlights = flightDao.getAllFlights().first().toMutableList()
                
                for (itinerary in selectedItineraries) {
                    for (flight in itinerary.flights) {
                        val now = System.currentTimeMillis()
                        val matchingFlight = findMatchingFlight(existingFlights, flight, now)
                        if (matchingFlight != null) {
                            if (
                                hasAirportDetailConflict(
                                    matchingFlight.notes,
                                    flight.departureGate,
                                    flight.departureTerminal
                                )
                            ) {
                                airportDetailConflictCount++
                            }
                            val merged = matchingFlight.copy(
                                flightNumber = matchingFlight.flightNumber ?: flight.flightNumber,
                                airline = matchingFlight.airline ?: flight.airline ?: itinerary.airline,
                                aircraftType = matchingFlight.aircraftType ?: flight.aircraftType,
                                seatNumber = matchingFlight.seatNumber ?: flight.seatNumber,
                                bookingReference = matchingFlight.bookingReference
                                    ?: flight.bookingReference
                                    ?: itinerary.bookingReference,
                                estimatedDepartureTimeMillis = flight.departureTimeMillis
                                    ?.takeIf {
                                        kotlin.math.abs(
                                            it - matchingFlight.departureTimeMillis
                                        ) >= 5 * 60_000L
                                    }
                                    ?: matchingFlight.estimatedDepartureTimeMillis,
                                estimatedArrivalTimeMillis = flight.arrivalTimeMillis
                                    ?.takeIf {
                                        kotlin.math.abs(
                                            it - matchingFlight.arrivalTimeMillis
                                        ) >= 5 * 60_000L
                                    }
                                    ?: matchingFlight.estimatedArrivalTimeMillis,
                                notes = mergeEmailDetails(
                                    matchingFlight.notes,
                                    flight.departureGate,
                                    flight.departureTerminal,
                                    includeCombinedSource = true
                                ),
                                source = if (matchingFlight.source.contains("EMAIL")) {
                                    matchingFlight.source
                                } else {
                                    "${matchingFlight.source}+EMAIL_IMPORT"
                                },
                                updatedAtMillis = now
                            )
                            flightDao.update(merged)
                            existingFlights[existingFlights.indexOf(matchingFlight)] = merged
                            matchedCount++
                            continue
                        }

                        val depTime = flight.departureTimeMillis
                        if (depTime == null) {
                            skippedCount++
                            continue
                        }
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
                            notes = mergeEmailDetails(
                                existingNotes = null,
                                gate = flight.departureGate,
                                terminal = flight.departureTerminal,
                                includeCombinedSource = false
                            ),
                            photoUri = null,
                            source = "EMAIL_IMPORT",
                            createdAtMillis = now,
                            updatedAtMillis = now
                        )
                        
                        val id = flightDao.insert(flightEntity)
                        existingFlights.add(flightEntity.copy(id = id))
                        importedCount++
                    }
                }

                // Auto-create journeys from unassigned connected flights
                // (prefer same booking reference; otherwise continuous route ≤48h).
                var journeysCreated = 0
                val unassigned = flightDao.getUnassignedFlights().first()
                val suggested = journeyRepository.suggestConnectedGroups(unassigned)
                suggested.forEach { group ->
                    val sameBooking = group.mapNotNull { it.bookingReference?.takeIf(String::isNotBlank) }
                        .distinctBy { it.lowercase() }
                        .size <= 1 &&
                        group.any { !it.bookingReference.isNullOrBlank() }
                    runCatching {
                        journeyRepository.createJourney(
                            name = "",
                            notes = if (sameBooking) {
                                "Auto-created from Gmail booking"
                            } else {
                                "Auto-created from connected Gmail flights"
                            },
                            flights = group
                        )
                        journeysCreated++
                    }
                }

                val resultParts = buildList {
                    if (matchedCount > 0) {
                        add("$matchedCount flight${if (matchedCount == 1) "" else "s"} matched your manual entry and ${if (matchedCount == 1) "was" else "were"} updated")
                    }
                    if (importedCount > 0) {
                        add("$importedCount new flight${if (importedCount == 1) "" else "s"} imported")
                    }
                    if (journeysCreated > 0) {
                        add(
                            "$journeysCreated journey${if (journeysCreated == 1) "" else "s"} " +
                                "auto-created from connected flights"
                        )
                    }
                    if (skippedCount > 0) {
                        add("$skippedCount skipped because the email had no travel date; add it manually")
                    }
                    if (airportDetailConflictCount > 0) {
                        add(
                            "$airportDetailConflictCount gate or terminal update differs from your manual entry. " +
                                "Open Flight Reminders and check the nearest airport board"
                        )
                    }
                }
                
                _uiState.update { 
                    it.copy(
                        isImporting = false,
                        importSuccess = true,
                        importMessage = resultParts.joinToString(". "),
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

    private fun findMatchingFlight(
        existingFlights: List<FlightEntity>,
        parsed: com.roamatlas.data.email.ParsedFlight,
        now: Long
    ): FlightEntity? {
        val parsedNumber = normalizedFlightNumber(parsed.flightNumber)
        val routeMatches = existingFlights.filter { existing ->
            existing.departureAirportCode.equals(parsed.departureAirportCode, ignoreCase = true) &&
                existing.arrivalAirportCode.equals(parsed.arrivalAirportCode, ignoreCase = true)
        }
        parsed.departureTimeMillis?.let { parsedTime ->
            val candidates = routeMatches.filter { existing ->
                val existingNumber = normalizedFlightNumber(existing.flightNumber)
                (parsedNumber != null && existingNumber == parsedNumber) ||
                    kotlin.math.abs(existing.departureTimeMillis - parsedTime) <= 6 * 60 * 60 * 1000L
            }
            return candidates.minByOrNull {
                kotlin.math.abs(it.departureTimeMillis - parsedTime)
            }?.takeIf {
                val sameNumber = normalizedFlightNumber(it.flightNumber) == parsedNumber
                kotlin.math.abs(it.departureTimeMillis - parsedTime) <=
                    (if (sameNumber) 24 else 6) * 60 * 60 * 1000L
            }
        }
        if (parsedNumber == null) return null
        val relevantMatches = routeMatches.filter {
            normalizedFlightNumber(it.flightNumber) == parsedNumber &&
            it.departureTimeMillis in (now - 7 * 24 * 60 * 60 * 1000L)..(now + 366L * 24 * 60 * 60 * 1000L)
        }
        return relevantMatches.singleOrNull()
    }

    private fun normalizedFlightNumber(value: String?): String? =
        value?.filter(Char::isLetterOrDigit)?.uppercase()?.takeIf { it.isNotEmpty() }

    private fun mergeEmailDetails(
        existingNotes: String?,
        gate: String?,
        terminal: String?,
        includeCombinedSource: Boolean
    ): String? {
        val hasManualAirportDetails = existingNotes.orEmpty().lineSequence().any {
            it.equals(
                "Airport details source: Manually entered from airport board",
                ignoreCase = true
            )
        }
        val additions = buildList {
            if (hasManualAirportDetails) {
                gate?.let { add("Automatic departure gate: $it") }
                terminal?.let { add("Automatic departure terminal: $it") }
            } else {
                gate?.let { add("Departure gate: $it") }
                terminal?.let { add("Departure terminal: $it") }
            }
            if (includeCombinedSource) add("Sources: Manual entry + Gmail")
        }
        if (additions.isEmpty()) return existingNotes
        val lines = existingNotes.orEmpty().lines().filter { it.isNotBlank() }.toMutableList()
        additions.forEach { addition ->
            val label = addition.substringBefore(':')
            lines.removeAll { it.startsWith("$label:", ignoreCase = true) }
            lines.add(addition)
        }
        return lines.joinToString("\n")
    }

    private fun hasAirportDetailConflict(
        existingNotes: String?,
        automaticGate: String?,
        automaticTerminal: String?
    ): Boolean {
        val hasManualDetails = noteValue(existingNotes, "Airport details source")
            ?.equals("Manually entered from airport board", ignoreCase = true) == true
        if (!hasManualDetails) return false
        val manualGate = noteValue(existingNotes, "Departure gate")
        val manualTerminal = noteValue(existingNotes, "Departure terminal")
        return valuesDiffer(manualGate, automaticGate) ||
            valuesDiffer(manualTerminal, automaticTerminal)
    }

    private fun valuesDiffer(manual: String?, automatic: String?): Boolean =
        !manual.isNullOrBlank() &&
            !automatic.isNullOrBlank() &&
            !manual.equals(automatic, ignoreCase = true)

    private fun noteValue(notes: String?, label: String): String? =
        notes?.lineSequence()
            ?.firstOrNull { it.startsWith("$label:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    fun clearImportSuccess() {
        _uiState.update { it.copy(importSuccess = false, importMessage = null) }
    }

    fun clearConnectionMessage() {
        _uiState.update { it.copy(connectionMessage = null) }
    }
}
