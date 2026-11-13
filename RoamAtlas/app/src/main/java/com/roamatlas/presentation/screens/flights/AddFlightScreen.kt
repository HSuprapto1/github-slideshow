package com.roamatlas.presentation.screens.flights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.roamatlas.data.airport.Airport
import com.roamatlas.data.local.entity.TransportMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFlightScreen(
    onNavigateBack: () -> Unit,
    onFlightAdded: () -> Unit = {},
    viewModel: AddFlightViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val modeLabel = TransportMode.label(uiState.transportMode)
    
    LaunchedEffect(uiState.flightSaved) {
        if (uiState.flightSaved) {
            snackbarHostState.showSnackbar("$modeLabel saved successfully!")
            onFlightAdded()
            onNavigateBack()
        }
    }
    
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add $modeLabel") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Transport", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    TransportMode.FLIGHT,
                    TransportMode.TRAIN,
                    TransportMode.BUS
                ).forEach { mode ->
                    FilterChip(
                        selected = uiState.transportMode == mode,
                        onClick = { viewModel.setTransportMode(mode) },
                        label = { Text(TransportMode.label(mode)) }
                    )
                }
            }

            // Departure Section
            SectionCard(
                title = "Departure",
                icon = Icons.Default.FlightTakeoff
            ) {
                AirportInput(
                    searchQuery = uiState.departureAirportSearch,
                    onSearchQueryChange = viewModel::searchDepartureAirport,
                    suggestions = uiState.departureAirportSuggestions,
                    onSelectAirport = viewModel::selectDepartureAirport,
                    airportCode = uiState.departureAirportCode,
                    onAirportCodeChange = viewModel::updateDepartureAirportCode,
                    airportName = uiState.departureAirportName,
                    onAirportNameChange = viewModel::updateDepartureAirportName,
                    city = uiState.departureCity,
                    onCityChange = viewModel::updateDepartureCity,
                    countryCode = uiState.departureCountryCode,
                    onCountryCodeChange = viewModel::updateDepartureCountryCode
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                DateTimeInput(
                    label = "Departure",
                    dateMillis = uiState.departureDateMillis,
                    onDateChange = viewModel::updateDepartureDate,
                    hour = uiState.departureHour,
                    minute = uiState.departureMinute,
                    onTimeChange = viewModel::updateDepartureTime
                )
            }
            
            // Arrival Section
            SectionCard(
                title = "Arrival",
                icon = Icons.Default.FlightLand
            ) {
                AirportInput(
                    searchQuery = uiState.arrivalAirportSearch,
                    onSearchQueryChange = viewModel::searchArrivalAirport,
                    suggestions = uiState.arrivalAirportSuggestions,
                    onSelectAirport = viewModel::selectArrivalAirport,
                    airportCode = uiState.arrivalAirportCode,
                    onAirportCodeChange = viewModel::updateArrivalAirportCode,
                    airportName = uiState.arrivalAirportName,
                    onAirportNameChange = viewModel::updateArrivalAirportName,
                    city = uiState.arrivalCity,
                    onCityChange = viewModel::updateArrivalCity,
                    countryCode = uiState.arrivalCountryCode,
                    onCountryCodeChange = viewModel::updateArrivalCountryCode
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                DateTimeInput(
                    label = "Arrival",
                    dateMillis = uiState.arrivalDateMillis,
                    onDateChange = viewModel::updateArrivalDate,
                    hour = uiState.arrivalHour,
                    minute = uiState.arrivalMinute,
                    onTimeChange = viewModel::updateArrivalTime
                )
            }
            
            // Flight Details Section
            SectionCard(
                title = "Flight Details",
                icon = Icons.Default.Schedule
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.airline,
                        onValueChange = viewModel::updateAirline,
                        label = {
                            Text(
                                when (uiState.transportMode) {
                                    TransportMode.TRAIN -> "Operator"
                                    TransportMode.BUS -> "Operator"
                                    else -> "Airline"
                                }
                            )
                        },
                        placeholder = {
                            Text(
                                when (uiState.transportMode) {
                                    TransportMode.TRAIN -> "e.g. Deutsche Bahn"
                                    TransportMode.BUS -> "e.g. FlixBus"
                                    else -> "e.g. Singapore Airlines"
                                }
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.flightNumber,
                        onValueChange = viewModel::updateFlightNumber,
                        label = {
                            Text(
                                when (uiState.transportMode) {
                                    TransportMode.TRAIN -> "Train #"
                                    TransportMode.BUS -> "Bus #"
                                    else -> "Flight #"
                                }
                            )
                        },
                        placeholder = {
                            Text(
                                when (uiState.transportMode) {
                                    TransportMode.TRAIN -> "ICE 123"
                                    TransportMode.BUS -> "FX 456"
                                    else -> "SQ123"
                                }
                            )
                        },
                        modifier = Modifier.weight(0.6f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.aircraftType,
                        onValueChange = viewModel::updateAircraftType,
                        label = { Text("Aircraft (optional)") },
                        placeholder = { Text("Add later if known") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.seatNumber,
                        onValueChange = viewModel::updateSeatNumber,
                        label = { Text("Seat (optional)") },
                        placeholder = { Text("After check-in") },
                        modifier = Modifier.weight(0.5f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = uiState.bookingReference,
                    onValueChange = viewModel::updateBookingReference,
                    label = { Text("Booking Reference") },
                    placeholder = { Text("ABC123") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    )
                )
                Text(
                    "Aircraft and seat can be left empty and updated after online or airport check-in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Notes Section
            SectionCard(
                title = "Notes",
                icon = Icons.Default.Add
            ) {
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = viewModel::updateNotes,
                    label = { Text("Notes") },
                    placeholder = { Text("Any additional notes...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    maxLines = 4
                )
            }
            
            // Distance (calculated or manual)
            if (uiState.calculatedDistanceKm != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Estimated Distance",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${String.format("%.0f", uiState.calculatedDistanceKm)} km",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            // Save Button
            Button(
                onClick = viewModel::saveFlight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = uiState.isValid && !uiState.isSaving
            ) {
                Text(if (uiState.isSaving) "Saving..." else "Save $modeLabel")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            content()
        }
    }
}

@Composable
private fun AirportInput(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    suggestions: List<Airport>,
    onSelectAirport: (Airport) -> Unit,
    airportCode: String,
    onAirportCodeChange: (String) -> Unit,
    airportName: String,
    onAirportNameChange: (String) -> Unit,
    city: String,
    onCityChange: (String) -> Unit,
    countryCode: String,
    onCountryCodeChange: (String) -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        label = { Text("Find airport") },
        placeholder = { Text("Search code, airport name, city, or country") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    if (suggestions.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column {
                suggestions.forEach { airport ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectAirport(airport) }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(
                            "${airport.iataCode} · ${airport.name}",
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            listOf(airport.city, airport.countryName)
                                .filter { it.isNotBlank() }
                                .joinToString(", "),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = airportCode,
            onValueChange = { if (it.length <= 3) onAirportCodeChange(it.uppercase()) },
            label = { Text("Code") },
            placeholder = { Text("SIN") },
            modifier = Modifier.weight(0.4f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters
            )
        )
        OutlinedTextField(
            value = airportName,
            onValueChange = onAirportNameChange,
            label = { Text("Airport Name") },
            placeholder = { Text("Changi Airport") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
    }
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = city,
            onValueChange = onCityChange,
            label = { Text("City") },
            placeholder = { Text("Singapore") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        OutlinedTextField(
            value = countryCode,
            onValueChange = { if (it.length <= 2) onCountryCodeChange(it.uppercase()) },
            label = { Text("Country") },
            placeholder = { Text("SG") },
            modifier = Modifier.weight(0.4f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeInput(
    label: String,
    dateMillis: Long?,
    onDateChange: (Long) -> Unit,
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (dateMillis != null) {
                    dateFormat.format(Date(dateMillis))
                } else {
                    "$label Date"
                }
            )
        }
        
        OutlinedButton(
            onClick = { showTimePicker = true },
            modifier = Modifier.weight(0.6f)
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            Text(text = timeFormat.format(cal.time))
        }
    }
    
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { onDateChange(it) }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true
        )
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Select $label Time",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("Cancel")
                        }
                        TextButton(
                            onClick = {
                                onTimeChange(timePickerState.hour, timePickerState.minute)
                                showTimePicker = false
                            }
                        ) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}
