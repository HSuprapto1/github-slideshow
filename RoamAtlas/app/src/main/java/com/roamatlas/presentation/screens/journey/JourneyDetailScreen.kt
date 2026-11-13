package com.roamatlas.presentation.screens.journey

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.roamatlas.data.airport.Airport
import com.roamatlas.data.journey.ConnectionRisk
import com.roamatlas.data.journey.JourneyConnection
import com.roamatlas.data.journey.JourneyShareFormatter
import com.roamatlas.data.journey.JourneyWithFlights
import com.roamatlas.data.local.entity.FlightEntity
import com.roamatlas.presentation.components.PhotoAttachmentSection
import com.roamatlas.presentation.components.PhotoFullScreenViewer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: JourneyDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val journey = state.journey
    val context = LocalContext.current
    var showEditJourney by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var editingFlight by remember { mutableStateOf<FlightEntity?>(null) }
    var fullscreenPhotos by remember { mutableStateOf<List<String>?>(null) }
    var fullscreenIndex by remember { mutableStateOf(0) }

    LaunchedEffect(state.wasDeleted) {
        if (state.wasDeleted) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(journey?.journey?.name ?: "Journey") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (journey != null) {
                        IconButton(onClick = { showEditJourney = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit journey")
                        }
                        IconButton(onClick = { showShare = true }) {
                            Icon(Icons.Default.Share, contentDescription = "Share journey")
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete journey")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (state.isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                }
            }
            state.errorMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
            journey?.let { value ->
                item { JourneyHeader(value, state.nowMillis) }
                if (value.journey.notes != null) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Journey notes", fontWeight = FontWeight.Bold)
                                Text(value.journey.notes)
                            }
                        }
                    }
                }
                item {
                    JourneyPhotosSection(
                        photos = value.photoUris,
                        coverUri = value.journey.coverPhotoUri,
                        onAddPhoto = viewModel::addJourneyPhoto,
                        onRemovePhoto = viewModel::removeJourneyPhoto,
                        onSetCover = viewModel::setCoverPhoto,
                        onOpenPhoto = { photos, index ->
                            fullscreenPhotos = photos
                            fullscreenIndex = index
                        }
                    )
                }
                item { JourneyRouteMap(value, state.airports) }
                if (value.connections.isNotEmpty()) {
                    item {
                        Text(
                            "Connections",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Warnings use your chosen minimum, or a conservative default of 2 hours at the same airport and 4 hours for an airport change.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    items(value.connections) { connection ->
                        ConnectionCard(connection)
                    }
                }
                item {
                    Text(
                        "Flights",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(value.orderedFlights, key = { it.id }) { flight ->
                    FlightLegCard(
                        flight = flight,
                        departureAirport = state.airports[flight.departureAirportCode],
                        arrivalAirport = state.airports[flight.arrivalAirportCode],
                        onEdit = { editingFlight = flight },
                        onSetTicketPhoto = { uri ->
                            viewModel.setFlightTicketPhoto(flight.id, uri)
                        },
                        onClearTicketPhoto = {
                            viewModel.setFlightTicketPhoto(flight.id, null)
                        },
                        onOpenTicketPhoto = { uri ->
                            fullscreenPhotos = listOf(uri)
                            fullscreenIndex = 0
                        }
                    )
                }
                item { Spacer(Modifier.height(50.dp)) }
            }
        }
    }

    fullscreenPhotos?.let { photos ->
        PhotoFullScreenViewer(
            photos = photos,
            initialIndex = fullscreenIndex,
            onDismiss = { fullscreenPhotos = null }
        )
    }

    if (showEditJourney && journey != null) {
        EditJourneyDialog(
            journey = journey,
            onDismiss = { showEditJourney = false },
            onSave = { name, notes ->
                viewModel.updateJourney(name, notes)
                showEditJourney = false
            }
        )
    }
    editingFlight?.let { flight ->
        EditFlightStatusDialog(
            flight = flight,
            onDismiss = { editingFlight = null },
            onSave = {
                    departureDelay,
                    arrivalDelay,
                    status,
                    minimum,
                    separate,
                    aircraft,
                    seat,
                    terminal,
                    gate ->
                viewModel.updateFlight(
                    flight,
                    departureDelay,
                    arrivalDelay,
                    status,
                    minimum,
                    separate,
                    aircraft,
                    seat,
                    terminal,
                    gate
                )
                editingFlight = null
            }
        )
    }
    if (showShare && journey != null) {
        ShareJourneyDialog(
            onDismiss = { showShare = false },
            onShareText = { includeReference, includeNotes ->
                JourneyShareFormatter.shareText(
                    context,
                    journey,
                    state.airports,
                    includeReference,
                    includeNotes
                )
                showShare = false
            },
            onShareCard = { includeReference, includeNotes ->
                JourneyShareFormatter.shareCard(
                    context,
                    journey,
                    state.airports,
                    includeReference,
                    includeNotes
                )
                showShare = false
            }
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete this journey?") },
            text = {
                Text("The flights will remain saved and can be combined into another journey.")
            },
            confirmButton = {
                Button(onClick = viewModel::deleteJourney) { Text("Delete journey") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun JourneyHeader(journey: JourneyWithFlights, now: Long) {
    val first = journey.firstDepartureMillis
    val final = journey.finalArrivalMillis
    val nextFlight = journey.orderedFlights.firstOrNull {
        (it.estimatedDepartureTimeMillis ?: it.departureTimeMillis) > now
    }
    val headline = when {
        first == null -> "No flights"
        now < first -> "Departs in ${formatDuration((first - now) / 60_000)}"
        final != null && now <= final && nextFlight != null ->
            "Next flight in ${formatDuration(((nextFlight.estimatedDepartureTimeMillis ?: nextFlight.departureTimeMillis) - now) / 60_000)}"
        final != null && now > final -> "Past journey · completed"
        else -> "Journey in progress"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            journey.journey.coverPhotoUri?.takeIf { it.isNotBlank() }?.let { cover ->
                AsyncImage(
                    model = Uri.parse(cover),
                    contentDescription = "Journey cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            Text(headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (journey.flights.isNotEmpty()) {
                Text(
                    journey.orderedFlights.joinToString(" → ") { it.departureAirportCode } +
                        " → ${journey.orderedFlights.last().arrivalAirportCode}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                "${journey.flights.size} flights · ${formatDuration(journey.totalJourneyMinutes)} journey · " +
                    "${formatDuration(journey.totalTransitMinutes)} current transit estimate"
            )
        }
    }
}

@Composable
private fun JourneyPhotosSection(
    photos: List<String>,
    coverUri: String?,
    onAddPhoto: (Uri) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onSetCover: (String) -> Unit,
    onOpenPhoto: (List<String>, Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Trip photos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Add tickets, boarding passes, or trip memories. Tap a photo to view full size. Long-press isn’t needed — use × to remove, or Set cover below.",
                style = MaterialTheme.typography.bodySmall
            )
            PhotoAttachmentSection(
                photos = photos,
                onAddPhoto = onAddPhoto,
                onRemovePhoto = onRemovePhoto,
                title = "Album",
                maxPhotos = 20,
                onPhotoClick = { uri ->
                    val index = photos.indexOf(uri).coerceAtLeast(0)
                    onOpenPhoto(photos, index)
                }
            )
            if (photos.isNotEmpty()) {
                Text("Cover photo", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photos.take(5).forEach { uri ->
                        val selected = uri == coverUri
                        AsyncImage(
                            model = Uri.parse(uri),
                            contentDescription = "Set as cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSetCover(uri) }
                                .then(
                                    if (selected) {
                                        Modifier.padding(0.dp)
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
                Text(
                    if (coverUri != null) "Tap another photo above to change the cover."
                    else "Tap a photo to set it as the journey cover.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun JourneyRouteMap(
    journey: JourneyWithFlights,
    airports: Map<String, Airport>
) {
    val points = journey.orderedFlights.flatMap { flight ->
        listOfNotNull(
            airports[flight.departureAirportCode]?.let { LatLng(it.latitude, it.longitude) },
            airports[flight.arrivalAirportCode]?.let { LatLng(it.latitude, it.longitude) }
        )
    }.distinct()
    if (points.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Route map unavailable because airport coordinates are missing.",
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(points.first(), 2.5f)
    }
    Column {
        Text("Journey route", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Lines show estimated great-circle routes, not the aircraft’s actual track.",
            style = MaterialTheme.typography.bodySmall
        )
        GoogleMap(
            modifier = Modifier.fillMaxWidth().height(280.dp),
            cameraPositionState = cameraState
        ) {
            airports.values.filter { airport ->
                journey.orderedFlights.any {
                    it.departureAirportCode == airport.iataCode ||
                        it.arrivalAirportCode == airport.iataCode
                }
            }.forEach { airport ->
                Marker(
                    state = MarkerState(LatLng(airport.latitude, airport.longitude)),
                    title = "${airport.iataCode} · ${airport.name}",
                    snippet = airport.city
                )
            }
            journey.orderedFlights.forEach { flight ->
                val departure = airports[flight.departureAirportCode]
                val arrival = airports[flight.arrivalAirportCode]
                if (departure != null && arrival != null) {
                    Polyline(
                        points = listOf(
                            LatLng(departure.latitude, departure.longitude),
                            LatLng(arrival.latitude, arrival.longitude)
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        width = 7f,
                        geodesic = true
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(connection: JourneyConnection) {
    val background = when (connection.risk) {
        ConnectionRisk.COMFORTABLE -> MaterialTheme.colorScheme.secondaryContainer
        ConnectionRisk.TIGHT -> Color(0xFFFFE0A3)
        ConnectionRisk.BELOW_RECOMMENDED -> MaterialTheme.colorScheme.errorContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "${connection.arrivingFlight.arrivalAirportCode} connection",
                fontWeight = FontWeight.Bold
            )
            Text(
                "${formatDuration(connection.effectiveMinutes)} effective transit · " +
                    "${formatDuration(connection.recommendedMinimumMinutes.toLong())} recommended minimum"
            )
            if (connection.scheduledMinutes != connection.effectiveMinutes) {
                Text("Originally scheduled: ${formatDuration(connection.scheduledMinutes)}")
            }
            if (connection.airportChange) {
                Text("Airport change required", fontWeight = FontWeight.Bold)
            }
            if (connection.departingFlight.separateTicketFromPrevious == true) {
                Text("Separate ticket: missed-connection protection may not apply.")
            }
            when (connection.risk) {
                ConnectionRisk.COMFORTABLE -> Text("Connection has a comfortable calculated buffer.")
                ConnectionRisk.TIGHT -> Text(
                    "Potentially tight connection. Check terminals, immigration, security, and baggage.",
                    fontWeight = FontWeight.SemiBold
                )
                ConnectionRisk.BELOW_RECOMMENDED -> Text(
                    "Below the recommended minimum. Contact the airline or booking agent to discuss alternatives.",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FlightLegCard(
    flight: FlightEntity,
    departureAirport: Airport?,
    arrivalAirport: Airport?,
    onEdit: () -> Unit,
    onSetTicketPhoto: (Uri) -> Unit,
    onClearTicketPhoto: () -> Unit,
    onOpenTicketPhoto: (String) -> Unit
) {
    val ticketLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let(onSetTicketPhoto)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    listOfNotNull(flight.airline, flight.flightNumber)
                        .joinToString(" · ")
                        .ifBlank { "Flight" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onEdit) { Text("Update") }
            }
            Text(
                "${flight.departureAirportCode} · ${departureAirport?.name ?: flight.departureAirportName ?: "Unknown airport"}"
            )
            Text(
                formatAirportTime(
                    flight.estimatedDepartureTimeMillis ?: flight.departureTimeMillis,
                    departureAirport
                )
            )
            Text(
                "↓  ${flight.arrivalAirportCode} · ${arrivalAirport?.name ?: flight.arrivalAirportName ?: "Unknown airport"}"
            )
            Text(
                formatAirportTime(
                    flight.estimatedArrivalTimeMillis ?: flight.arrivalTimeMillis,
                    arrivalAirport
                )
            )
            flight.flightStatus?.let { Text("Status: $it", fontWeight = FontWeight.SemiBold) }
            Text(
                "Aircraft: ${flight.aircraftType ?: "Not available yet"} · Seat: ${flight.seatNumber ?: "Not available yet"}",
                style = MaterialTheme.typography.bodySmall
            )
            val gate = noteValue(flight.notes, "Departure gate")
            val terminal = noteValue(flight.notes, "Departure terminal")
            Text(
                listOfNotNull(
                    terminal?.let { "Terminal $it" },
                    gate?.let { "Gate $it" }
                ).joinToString(" · ").ifBlank {
                    "Gate not available—check the airline or airport display."
                },
                style = MaterialTheme.typography.bodySmall
            )
            Text("Ticket / boarding pass", fontWeight = FontWeight.SemiBold)
            val ticketUri = flight.photoUri
            if (!ticketUri.isNullOrBlank()) {
                AsyncImage(
                    model = Uri.parse(ticketUri),
                    contentDescription = "Ticket photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpenTicketPhoto(ticketUri) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { ticketLauncher.launch("image/*") }) {
                        Text("Replace")
                    }
                    TextButton(onClick = onClearTicketPhoto) {
                        Text("Remove")
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { ticketLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add ticket or boarding pass photo")
                }
            }
        }
    }
}

@Composable
private fun EditJourneyDialog(
    journey: JourneyWithFlights,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(journey.journey.name) }
    var notes by remember { mutableStateOf(journey.journey.notes.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit journey") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Journey name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 4
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, notes) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditFlightStatusDialog(
    flight: FlightEntity,
    onDismiss: () -> Unit,
    onSave: (
        Int?,
        Int?,
        String,
        Int?,
        Boolean?,
        String,
        String,
        String,
        String
    ) -> Unit
) {
    var departureDelay by remember {
        mutableStateOf(
            flight.estimatedDepartureTimeMillis?.let {
                ((it - flight.departureTimeMillis) / 60_000).toString()
            }.orEmpty()
        )
    }
    var arrivalDelay by remember {
        mutableStateOf(
            flight.estimatedArrivalTimeMillis?.let {
                ((it - flight.arrivalTimeMillis) / 60_000).toString()
            }.orEmpty()
        )
    }
    var status by remember { mutableStateOf(flight.flightStatus.orEmpty()) }
    var minimum by remember { mutableStateOf(flight.minimumConnectionMinutes?.toString().orEmpty()) }
    var separateTicket by remember { mutableStateOf(flight.separateTicketFromPrevious == true) }
    var aircraft by remember { mutableStateOf(flight.aircraftType.orEmpty()) }
    var seat by remember { mutableStateOf(flight.seatNumber.orEmpty()) }
    var terminal by remember {
        mutableStateOf(noteValue(flight.notes, "Departure terminal").orEmpty())
    }
    var gate by remember {
        mutableStateOf(noteValue(flight.notes, "Departure gate").orEmpty())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update flight information") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Keep the original schedule and record changes separately.")
                OutlinedTextField(
                    value = departureDelay,
                    onValueChange = { departureDelay = it.filter { char -> char.isDigit() || char == '-' } },
                    label = { Text("Departure delay in minutes") },
                    placeholder = { Text("Example: 45") }
                )
                OutlinedTextField(
                    value = arrivalDelay,
                    onValueChange = { arrivalDelay = it.filter { char -> char.isDigit() || char == '-' } },
                    label = { Text("Arrival delay in minutes") },
                    placeholder = { Text("Example: 30") }
                )
                OutlinedTextField(
                    value = status,
                    onValueChange = { status = it },
                    label = { Text("Status") },
                    placeholder = { Text("Scheduled, delayed, boarding, arrived…") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = aircraft,
                        onValueChange = { aircraft = it },
                        label = { Text("Aircraft (optional)") },
                        placeholder = { Text("Boeing or Airbus model") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = seat,
                        onValueChange = { seat = it },
                        label = { Text("Seat") },
                        placeholder = { Text("After check-in") },
                        modifier = Modifier.weight(0.55f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = terminal,
                        onValueChange = { terminal = it },
                        label = { Text("Terminal") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = gate,
                        onValueChange = { gate = it },
                        label = { Text("Gate") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = minimum,
                    onValueChange = { minimum = it.filter(Char::isDigit) },
                    label = { Text("Minimum connection time after previous flight") },
                    suffix = { Text("min") }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = separateTicket,
                        onCheckedChange = { separateTicket = it }
                    )
                    Text("This flight is on a separate ticket")
                }
                Text(
                    "Delay information is manual unless an updated email or live provider supplies it. Always confirm with the airline.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        departureDelay.toIntOrNull(),
                        arrivalDelay.toIntOrNull(),
                        status,
                        minimum.toIntOrNull(),
                        separateTicket,
                        aircraft,
                        seat,
                        terminal,
                        gate
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ShareJourneyDialog(
    onDismiss: () -> Unit,
    onShareText: (Boolean, Boolean) -> Unit,
    onShareCard: (Boolean, Boolean) -> Unit
) {
    var includeBooking by remember { mutableStateOf(false) }
    var includeNotes by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share journey") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Share to WhatsApp, email, messages, or any compatible app.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeBooking, onCheckedChange = { includeBooking = it })
                    Text("Include booking references")
                }
                if (includeBooking) {
                    Text(
                        "Warning: a booking reference combined with a passenger name can allow access to a reservation.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeNotes, onCheckedChange = { includeNotes = it })
                    Text("Include journey notes")
                }
                OutlinedButton(
                    onClick = { onShareText(includeBooking, includeNotes) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Share as text") }
                Button(
                    onClick = { onShareCard(includeBooking, includeNotes) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Share visual card") }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatAirportTime(millis: Long, airport: Airport?): String {
    val formatter = SimpleDateFormat("EEE, d MMM yyyy · HH:mm", Locale.getDefault())
    airport?.timeZoneId?.let { zone ->
        runCatching { formatter.timeZone = TimeZone.getTimeZone(zone) }
    }
    return formatter.format(Date(millis)) +
        airport?.timeZoneId?.let { " (${it.substringAfterLast('/')})" }.orEmpty()
}

private fun noteValue(notes: String?, label: String): String? =
    notes?.lineSequence()
        ?.firstOrNull { it.startsWith("$label:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
