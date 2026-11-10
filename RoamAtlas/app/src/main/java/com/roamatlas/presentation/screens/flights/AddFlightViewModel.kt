package com.roamatlas.presentation.screens.flights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamatlas.data.local.dao.FlightDao
import com.roamatlas.data.local.entity.FlightEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

data class AddFlightUiState(
    // Departure
    val departureAirportCode: String = "",
    val departureAirportName: String = "",
    val departureCity: String = "",
    val departureCountryCode: String = "",
    val departureDateMillis: Long? = null,
    val departureHour: Int = 12,
    val departureMinute: Int = 0,
    
    // Arrival
    val arrivalAirportCode: String = "",
    val arrivalAirportName: String = "",
    val arrivalCity: String = "",
    val arrivalCountryCode: String = "",
    val arrivalDateMillis: Long? = null,
    val arrivalHour: Int = 14,
    val arrivalMinute: Int = 0,
    
    // Flight details
    val flightNumber: String = "",
    val airline: String = "",
    val aircraftType: String = "",
    val seatNumber: String = "",
    val bookingReference: String = "",
    val notes: String = "",
    
    // Calculated
    val calculatedDistanceKm: Double? = null,
    
    // State
    val isSaving: Boolean = false,
    val flightSaved: Boolean = false,
    val error: String? = null
) {
    val isValid: Boolean
        get() = departureAirportCode.length == 3 &&
                arrivalAirportCode.length == 3 &&
                departureDateMillis != null &&
                arrivalDateMillis != null
}

@HiltViewModel
class AddFlightViewModel @Inject constructor(
    private val flightDao: FlightDao
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AddFlightUiState())
    val uiState: StateFlow<AddFlightUiState> = _uiState.asStateFlow()
    
    // Airport coordinates for distance calculation (major airports)
    private val airportCoordinates = mapOf(
        "SIN" to Pair(1.3644, 103.9915),    // Singapore Changi
        "CGK" to Pair(-6.1256, 106.6558),   // Jakarta
        "DPS" to Pair(-8.7467, 115.1672),   // Bali
        "KUL" to Pair(2.7456, 101.7072),    // Kuala Lumpur
        "BKK" to Pair(13.6900, 100.7501),   // Bangkok
        "HKG" to Pair(22.3080, 113.9185),   // Hong Kong
        "NRT" to Pair(35.7720, 140.3929),   // Tokyo Narita
        "HND" to Pair(35.5494, 139.7798),   // Tokyo Haneda
        "ICN" to Pair(37.4602, 126.4407),   // Seoul Incheon
        "PVG" to Pair(31.1443, 121.8083),   // Shanghai Pudong
        "PEK" to Pair(40.0799, 116.6031),   // Beijing
        "SYD" to Pair(-33.9399, 151.1753),  // Sydney
        "MEL" to Pair(-37.6690, 144.8410),  // Melbourne
        "LHR" to Pair(51.4700, -0.4543),    // London Heathrow
        "CDG" to Pair(49.0097, 2.5479),     // Paris CDG
        "FRA" to Pair(50.0379, 8.5622),     // Frankfurt
        "AMS" to Pair(52.3105, 4.7683),     // Amsterdam
        "DXB" to Pair(25.2532, 55.3657),    // Dubai
        "DOH" to Pair(25.2731, 51.6081),    // Doha
        "JFK" to Pair(40.6413, -73.7781),   // New York JFK
        "LAX" to Pair(33.9416, -118.4085),  // Los Angeles
        "SFO" to Pair(37.6213, -122.3790),  // San Francisco
        "ORD" to Pair(41.9742, -87.9073),   // Chicago O'Hare
        "MIA" to Pair(25.7959, -80.2870),   // Miami
        "YYZ" to Pair(43.6777, -79.6248),   // Toronto
        "DEL" to Pair(28.5562, 77.1000),    // Delhi
        "BOM" to Pair(19.0896, 72.8656),    // Mumbai
        "MNL" to Pair(14.5086, 121.0197),   // Manila
        "SUB" to Pair(-7.3798, 112.7868),   // Surabaya
        "JOG" to Pair(-7.7882, 110.4317),   // Yogyakarta
        "UPG" to Pair(-5.0617, 119.5540),   // Makassar
    )
    
    fun updateDepartureAirportCode(code: String) {
        _uiState.update { it.copy(departureAirportCode = code) }
        calculateDistance()
    }
    
    fun updateDepartureAirportName(name: String) {
        _uiState.update { it.copy(departureAirportName = name) }
    }
    
    fun updateDepartureCity(city: String) {
        _uiState.update { it.copy(departureCity = city) }
    }
    
    fun updateDepartureCountryCode(code: String) {
        _uiState.update { it.copy(departureCountryCode = code) }
    }
    
    fun updateDepartureDate(millis: Long) {
        _uiState.update { it.copy(departureDateMillis = millis) }
    }
    
    fun updateDepartureTime(hour: Int, minute: Int) {
        _uiState.update { it.copy(departureHour = hour, departureMinute = minute) }
    }
    
    fun updateArrivalAirportCode(code: String) {
        _uiState.update { it.copy(arrivalAirportCode = code) }
        calculateDistance()
    }
    
    fun updateArrivalAirportName(name: String) {
        _uiState.update { it.copy(arrivalAirportName = name) }
    }
    
    fun updateArrivalCity(city: String) {
        _uiState.update { it.copy(arrivalCity = city) }
    }
    
    fun updateArrivalCountryCode(code: String) {
        _uiState.update { it.copy(arrivalCountryCode = code) }
    }
    
    fun updateArrivalDate(millis: Long) {
        _uiState.update { it.copy(arrivalDateMillis = millis) }
    }
    
    fun updateArrivalTime(hour: Int, minute: Int) {
        _uiState.update { it.copy(arrivalHour = hour, arrivalMinute = minute) }
    }
    
    fun updateFlightNumber(number: String) {
        _uiState.update { it.copy(flightNumber = number) }
    }
    
    fun updateAirline(airline: String) {
        _uiState.update { it.copy(airline = airline) }
    }
    
    fun updateAircraftType(type: String) {
        _uiState.update { it.copy(aircraftType = type) }
    }
    
    fun updateSeatNumber(seat: String) {
        _uiState.update { it.copy(seatNumber = seat) }
    }
    
    fun updateBookingReference(ref: String) {
        _uiState.update { it.copy(bookingReference = ref) }
    }
    
    fun updateNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    private fun calculateDistance() {
        val state = _uiState.value
        val depCoords = airportCoordinates[state.departureAirportCode]
        val arrCoords = airportCoordinates[state.arrivalAirportCode]
        
        if (depCoords != null && arrCoords != null) {
            val distance = haversineDistance(
                depCoords.first, depCoords.second,
                arrCoords.first, arrCoords.second
            )
            _uiState.update { it.copy(calculatedDistanceKm = distance) }
        } else {
            _uiState.update { it.copy(calculatedDistanceKm = null) }
        }
    }
    
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * acos(kotlin.math.sqrt(1 - a).coerceIn(0.0, 1.0))
        return earthRadiusKm * c
    }
    
    fun saveFlight() {
        val state = _uiState.value
        if (!state.isValid) {
            _uiState.update { it.copy(error = "Please fill in all required fields") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            
            try {
                val departureTime = combineDateAndTime(
                    state.departureDateMillis!!,
                    state.departureHour,
                    state.departureMinute
                )
                val arrivalTime = combineDateAndTime(
                    state.arrivalDateMillis!!,
                    state.arrivalHour,
                    state.arrivalMinute
                )
                
                val durationMinutes = ((arrivalTime - departureTime) / 60000).toInt()
                
                val flight = FlightEntity(
                    id = 0, // Auto-generated
                    departureAirportCode = state.departureAirportCode,
                    departureAirportName = state.departureAirportName.ifBlank { null },
                    departureCity = state.departureCity.ifBlank { null },
                    departureCountryCode = state.departureCountryCode.ifBlank { null },
                    arrivalAirportCode = state.arrivalAirportCode,
                    arrivalAirportName = state.arrivalAirportName.ifBlank { null },
                    arrivalCity = state.arrivalCity.ifBlank { null },
                    arrivalCountryCode = state.arrivalCountryCode.ifBlank { null },
                    departureTimeMillis = departureTime,
                    arrivalTimeMillis = arrivalTime,
                    flightNumber = state.flightNumber.ifBlank { null },
                    airline = state.airline.ifBlank { null },
                    aircraftType = state.aircraftType.ifBlank { null },
                    distanceKm = state.calculatedDistanceKm,
                    durationMinutes = if (durationMinutes > 0) durationMinutes else null,
                    seatNumber = state.seatNumber.ifBlank { null },
                    bookingReference = state.bookingReference.ifBlank { null },
                    notes = state.notes.ifBlank { null },
                    photoUri = null,
                    source = "MANUAL",
                    createdAtMillis = System.currentTimeMillis(),
                    updatedAtMillis = System.currentTimeMillis()
                )
                
                flightDao.insert(flight)
                _uiState.update { it.copy(isSaving = false, flightSaved = true) }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isSaving = false, 
                        error = "Failed to save flight: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    private fun combineDateAndTime(dateMillis: Long, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
    
    // For pre-filling from parsed email
    fun prefillFromParsedFlight(
        departureCode: String,
        arrivalCode: String,
        departureTime: Long,
        arrivalTime: Long,
        flightNumber: String?,
        airline: String?,
        bookingRef: String?
    ) {
        val depCal = Calendar.getInstance().apply { timeInMillis = departureTime }
        val arrCal = Calendar.getInstance().apply { timeInMillis = arrivalTime }
        
        _uiState.update {
            it.copy(
                departureAirportCode = departureCode,
                arrivalAirportCode = arrivalCode,
                departureDateMillis = departureTime,
                departureHour = depCal.get(Calendar.HOUR_OF_DAY),
                departureMinute = depCal.get(Calendar.MINUTE),
                arrivalDateMillis = arrivalTime,
                arrivalHour = arrCal.get(Calendar.HOUR_OF_DAY),
                arrivalMinute = arrCal.get(Calendar.MINUTE),
                flightNumber = flightNumber ?: "",
                airline = airline ?: "",
                bookingReference = bookingRef ?: ""
            )
        }
        calculateDistance()
    }
}
