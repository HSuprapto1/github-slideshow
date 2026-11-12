package com.roamatlas.presentation.screens.flights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightRemindersScreen(
    onNavigateBack: () -> Unit,
    viewModel: FlightRemindersViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val upcoming by viewModel.upcomingFlight.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flight Reminders") },
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UpcomingFlightCard(
                state = upcoming,
                onSaveAirportDetails = viewModel::updateAirportBoardDetails
            )

            // Main toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.isEnabled) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = if (settings.isEnabled) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Smart Flight Reminders",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (settings.isEnabled) "Active - Monitoring your flights" 
                                   else "Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = settings.isEnabled,
                        onCheckedChange = { viewModel.setEnabled(it) }
                    )
                }
            }
            
            // Description
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "How it works",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Monitors your location before upcoming flights\n" +
                               "• Gives a distance-based departure estimate\n" +
                               "• Opens Google Maps for current traffic\n" +
                               "• Warns if your location suggests you may miss the flight\n" +
                               "• Links to live airline status; it does not guess cancellations",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            if (settings.isEnabled) {
                // Time Settings
                SectionHeader(
                    icon = Icons.Default.Schedule,
                    title = "Timing"
                )
                
                // Hours before departure
                SettingSlider(
                    title = "Airport arrival time",
                    description = "Be at airport ${settings.hoursBeforeDeparture} hours before departure",
                    value = settings.hoursBeforeDeparture.toFloat(),
                    valueRange = 1f..4f,
                    steps = 2,
                    onValueChange = { viewModel.updateHoursBeforeDeparture(it.toInt()) },
                    valueLabel = "${settings.hoursBeforeDeparture}h"
                )
                
                // Advance warning
                SettingSlider(
                    title = "Start monitoring",
                    description = "Begin checking ${settings.advanceWarningHours} hours before flight",
                    value = settings.advanceWarningHours.toFloat(),
                    valueRange = 4f..12f,
                    steps = 7,
                    onValueChange = { viewModel.updateAdvanceWarningHours(it.toInt()) },
                    valueLabel = "${settings.advanceWarningHours}h"
                )
                
                // Urgent warning
                SettingSlider(
                    title = "Urgent alerts",
                    description = "Send urgent warnings ${settings.urgentWarningHours} hours before",
                    value = settings.urgentWarningHours.toFloat(),
                    valueRange = 1f..5f,
                    steps = 3,
                    onValueChange = { viewModel.updateUrgentWarningHours(it.toInt()) },
                    valueLabel = "${settings.urgentWarningHours}h"
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Notification Settings
                SectionHeader(
                    icon = Icons.Default.NotificationsActive,
                    title = "Notifications"
                )
                
                SettingToggle(
                    title = "Arrival confirmation",
                    description = "Notify when you arrive at the airport",
                    checked = settings.notifyWhenAtAirport,
                    onCheckedChange = { viewModel.updateNotifyAtAirport(it) }
                )
                
                SettingToggle(
                    title = "Missed flight detection",
                    description = "Alert if flight departed and you weren't at airport",
                    checked = settings.notifyPossibleMissed,
                    onCheckedChange = { viewModel.updateNotifyPossibleMissed(it) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Warning info
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = "Battery usage",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "This feature uses background location to monitor your position. " +
                                       "It only activates when you have upcoming flights within 24 hours.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UpcomingFlightCard(
    state: UpcomingFlightState,
    onSaveAirportDetails: (Long, String, String) -> Unit
) {
    val flight = state.flight
    var showAirportDetailsDialog by remember { mutableStateOf(false) }
    var terminalInput by remember { mutableStateOf("") }
    var gateInput by remember { mutableStateOf("") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = if (state.isTransit) "Your next transit flight" else "Your next flight",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (flight == null) {
                Text("No future flight is saved yet.")
                Text(
                    "Add a flight manually or import one from Gmail.",
                    style = MaterialTheme.typography.bodySmall
                )
                return@Column
            }
            val time = SimpleDateFormat("EEE, d MMM yyyy • HH:mm", Locale.getDefault())
                .format(Date(flight.departureTimeMillis))
            val flightLabel = listOfNotNull(flight.airline, flight.flightNumber)
                .joinToString(" • ")
                .ifBlank { "Flight details not supplied" }
            Text(flightLabel, fontWeight = FontWeight.SemiBold)
            Text("${flight.departureAirportCode} → ${flight.arrivalAirportCode}")
            Text("Departure: $time")
            Text("Leaves in ${formatRemainingTime(state.minutesUntilDeparture)}")
            if (state.isTransit && state.transitMinutesRemaining != null) {
                Text(
                    "Transit time remaining: ${formatRemainingTime(state.transitMinutesRemaining)}",
                    fontWeight = FontWeight.SemiBold
                )
            }
            flight.seatNumber?.takeIf { it.isNotBlank() }?.let { Text("Seat: $it") }
            val gate = noteValue(flight.notes, "Departure gate")
            val terminal = noteValue(flight.notes, "Departure terminal")
            val airportDetailsSource = noteValue(flight.notes, "Airport details source")
            Text(
                buildString {
                    if (terminal != null) append("Terminal $terminal • ")
                    append(gate?.let { "Gate $it" }
                        ?: "Gate not available—check the airline or airport display")
                },
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                airportDetailsSource?.let { "Source: $it" }
                    ?: "Gate information is shown only when found in your booking email or entered from the airport board.",
                style = MaterialTheme.typography.labelSmall
            )
            OutlinedButton(
                onClick = {
                    terminalInput = terminal.orEmpty()
                    gateInput = gate.orEmpty()
                    showAirportDetailsDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Update from airport board")
            }
        }
    }

    if (showAirportDetailsDialog && flight != null) {
        AlertDialog(
            onDismissRequest = { showAirportDetailsDialog = false },
            title = { Text("Update airport details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter exactly what you see on the airport departure board. You can leave either field empty."
                    )
                    OutlinedTextField(
                        value = terminalInput,
                        onValueChange = { terminalInput = it },
                        label = { Text("Terminal") },
                        placeholder = { Text("Example: 3") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = gateInput,
                        onValueChange = { gateInput = it },
                        label = { Text("Gate") },
                        placeholder = { Text("Example: A12") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveAirportDetails(flight.id, terminalInput, gateInput)
                        showAirportDetailsDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAirportDetailsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatRemainingTime(minutes: Long): String {
    val days = minutes / (24 * 60)
    val hours = (minutes % (24 * 60)) / 60
    val remainingMinutes = minutes % 60
    return buildList {
        if (days > 0) add("${days}d")
        if (hours > 0 || days > 0) add("${hours}h")
        add("${remainingMinutes}m")
    }.joinToString(" ")
}

private fun noteValue(notes: String?, label: String): String? =
    notes?.lineSequence()
        ?.firstOrNull { it.startsWith("$label:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun SettingSlider(
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    valueLabel: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
