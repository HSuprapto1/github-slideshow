package com.roamatlas.presentation.screens.journey

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.roamatlas.data.journey.JourneyWithFlights
import com.roamatlas.data.local.entity.FlightEntity
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneysScreen(
    onOpenJourney: (Long) -> Unit,
    viewModel: JourneysViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var journeyName by remember { mutableStateOf("") }
    var journeyNotes by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf(LocalDate.now().plusDays(7)) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    val visibleJourneys = state.visibleJourneys
    val dateLabel = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    }

    LaunchedEffect(state.createdJourneyId) {
        state.createdJourneyId?.let { id ->
            viewModel.consumeCreatedJourneyId()
            onOpenJourney(id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Journeys") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    journeyName = ""
                    journeyNotes = ""
                    startDate = LocalDate.now()
                    endDate = LocalDate.now().plusDays(7)
                    viewModel.showCreateDialog()
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create journey")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                }
            }
            state.errorMessage?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            if (state.suggestedGroups.isNotEmpty()) {
                item {
                    Text(
                        "Suggested connected journeys",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "RoamAtlas found flights with a continuous route or matching booking reference. Confirm before combining them.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                items(state.suggestedGroups, key = { group -> group.joinToString { it.id.toString() } }) { group ->
                    SuggestedJourneyCard(group) {
                        journeyName = ""
                        journeyNotes = ""
                        viewModel.showCreateDialog(group)
                    }
                }
            }
            item {
                Text(
                    "Your journeys",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.selectedTab == JourneyListTab.UPCOMING,
                        onClick = { viewModel.selectTab(JourneyListTab.UPCOMING) },
                        label = { Text("Upcoming (${state.upcomingJourneys.size})") }
                    )
                    FilterChip(
                        selected = state.selectedTab == JourneyListTab.PAST,
                        onClick = { viewModel.selectTab(JourneyListTab.PAST) },
                        label = { Text("Past (${state.pastJourneys.size})") }
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search journeys") },
                    placeholder = { Text("Name, city, airline, flight, booking ref") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                )
            }
            if (state.destinationOptions.isNotEmpty()) {
                item {
                    Text(
                        "Destination",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.destinationOptions) { destination ->
                            FilterChip(
                                selected = state.destinationFilter == destination,
                                onClick = { viewModel.setDestinationFilter(destination) },
                                label = { Text(destination) }
                            )
                        }
                    }
                }
            }
            if (!state.isLoading && visibleJourneys.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            when {
                                state.searchQuery.isNotBlank() || state.destinationFilter != null ->
                                    "No journeys match your search or destination filter."
                                state.selectedTab == JourneyListTab.UPCOMING ->
                                    "No upcoming journeys. Tap + to create a trip shell, then add flights, trains, buses, or hotels."
                                else ->
                                    "No past journeys yet. Completed trips will appear here."
                            },
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            }
            if (state.selectedTab == JourneyListTab.PAST) {
                visibleJourneys
                    .groupBy { journeyYear(it) }
                    .toSortedMap(compareByDescending { it })
                    .forEach { (year, journeysInYear) ->
                        item {
                            Text(
                                year,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(journeysInYear, key = { it.journey.id }) { journey ->
                            JourneyCard(journey, isPast = true) {
                                onOpenJourney(journey.journey.id)
                            }
                        }
                    }
            } else {
                items(visibleJourneys, key = { it.journey.id }) { journey ->
                    JourneyCard(journey, isPast = false) {
                        onOpenJourney(journey.journey.id)
                    }
                }
            }
        }
    }

    if (state.showCreateDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideCreateDialog,
            title = { Text("Create journey") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Create the trip shell first. You can add flights, trains, buses, and hotels inside the journey.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = journeyName,
                        onValueChange = { journeyName = it },
                        label = { Text("Journey name") },
                        placeholder = { Text("Example: Europe trip") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    TextButton(onClick = { pickingStart = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Start: ${startDate.format(dateLabel)}")
                    }
                    TextButton(onClick = { pickingEnd = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("End: ${endDate.format(dateLabel)}")
                    }
                    OutlinedTextField(
                        value = journeyNotes,
                        onValueChange = { journeyNotes = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    if (state.unassignedFlights.isNotEmpty()) {
                        Text("Optional: attach unassigned legs now", fontWeight = FontWeight.Bold)
                        state.unassignedFlights.forEach { flight ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleFlight(flight.id) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = flight.id in state.selectedFlightIds,
                                    onCheckedChange = { viewModel.toggleFlight(flight.id) }
                                )
                                Column {
                                    Text(
                                        "${flight.departureAirportCode} → ${flight.arrivalAirportCode}",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        listOfNotNull(flight.flightNumber, formatFlightDate(flight.departureTimeMillis))
                                            .joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createJourney(journeyName, journeyNotes, startDate, endDate)
                    },
                    enabled = journeyName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideCreateDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    if (pickingStart) {
        JourneyDatePickerDialog(
            initialDate = startDate,
            onDismiss = { pickingStart = false },
            onConfirm = { picked ->
                startDate = picked
                if (endDate.isBefore(picked)) endDate = picked
                pickingStart = false
            }
        )
    }
    if (pickingEnd) {
        JourneyDatePickerDialog(
            initialDate = endDate,
            onDismiss = { pickingEnd = false },
            onConfirm = { picked ->
                endDate = picked
                pickingEnd = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JourneyDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onConfirm(
                            Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneOffset.UTC)
                                .toLocalDate()
                        )
                    } ?: onDismiss()
                }
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun SuggestedJourneyCard(flights: List<FlightEntity>, onCreate: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                flights.joinToString(" → ") { it.departureAirportCode } +
                    " → ${flights.last().arrivalAirportCode}",
                fontWeight = FontWeight.Bold
            )
            Text("${flights.size} flights · ${flights.size - 1} connections")
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text("Combine these flights")
            }
        }
    }
}

@Composable
private fun JourneyCard(
    journey: JourneyWithFlights,
    isPast: Boolean,
    onClick: () -> Unit
) {
    val cover = journey.journey.coverPhotoUri
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!cover.isNullOrBlank()) {
                AsyncImage(
                    model = Uri.parse(cover),
                    contentDescription = "Journey cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    journey.journey.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isPast) "Past journey" else "Upcoming",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (journey.flights.isNotEmpty()) {
                    JourneyRouteRiskStrip(journey)
                }
                Text(
                    journey.legSummaryText,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    formatJourneyDateRange(journey),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Open journey")
        }
    }
}

private fun journeyYear(journey: JourneyWithFlights): String {
    val day = journey.journey.endDateEpochDay
        ?: journey.journey.startDateEpochDay
    return LocalDate.ofEpochDay(day).year.toString()
}

private fun formatJourneyDateRange(journey: JourneyWithFlights): String {
    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val start = LocalDate.ofEpochDay(journey.journey.startDateEpochDay).format(fmt)
    val endDay = journey.journey.endDateEpochDay ?: journey.journey.startDateEpochDay
    val end = LocalDate.ofEpochDay(endDay).format(fmt)
    return if (start == end) start else "$start – $end"
}

private fun formatFlightDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))

internal fun formatDuration(totalMinutes: Long): String {
    val safe = totalMinutes.coerceAtLeast(0)
    val days = safe / (24 * 60)
    val hours = safe % (24 * 60) / 60
    val minutes = safe % 60
    return buildList {
        if (days > 0) add("${days}d")
        if (hours > 0 || days > 0) add("${hours}h")
        add("${minutes}m")
    }.joinToString(" ")
}
