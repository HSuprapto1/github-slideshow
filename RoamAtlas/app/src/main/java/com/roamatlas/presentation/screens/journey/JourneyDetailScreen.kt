package com.roamatlas.presentation.screens.journey

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.roamatlas.data.airport.Airport
import com.roamatlas.data.flight.FlightScheduleTimes
import com.roamatlas.data.journey.ConnectionRisk
import com.roamatlas.data.journey.JourneyConnection
import com.roamatlas.data.journey.JourneyShareFormatter
import com.roamatlas.data.journey.JourneyWithFlights
import com.roamatlas.data.local.entity.FlightEntity
import com.roamatlas.data.local.entity.JourneyStatus
import com.roamatlas.data.local.entity.TransportMode
import com.roamatlas.data.local.entity.TravelLegStatus
import com.roamatlas.domain.model.LocationRecord
import com.roamatlas.presentation.components.PhotoAttachmentSection
import com.roamatlas.presentation.components.PhotoFullScreenViewer
import com.roamatlas.presentation.util.createPlaceCameraPhotoUri
import com.roamatlas.presentation.util.publishCapturedPhotoToGallery
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyDetailScreen(
    onNavigateBack: () -> Unit,
    onOpenMap: (java.time.LocalDate, String?) -> Unit = { _, _ -> },
    onAddToJourney: (Long, String) -> Unit = { _, _ -> },
    viewModel: JourneyDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val journey = state.journey
    val context = LocalContext.current
    var showEditJourney by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showGroundLegDialog by remember { mutableStateOf(false) }
    var editingFlight by remember { mutableStateOf<FlightEntity?>(null) }
    var opsEditingFlight by remember { mutableStateOf<FlightEntity?>(null) }
    var removingFlight by remember { mutableStateOf<FlightEntity?>(null) }
    var fullscreenPhotos by remember { mutableStateOf<List<String>?>(null) }
    var fullscreenIndex by remember { mutableStateOf(0) }
    var previewPdfUri by remember { mutableStateOf<String?>(null) }

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
            state.errorMessage?.let { msg ->
                item {
                    Text(msg, color = MaterialTheme.colorScheme.error)
                    LaunchedEffect(msg) {
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
            state.message?.let { msg ->
                item {
                    Text(msg, color = MaterialTheme.colorScheme.primary)
                    LaunchedEffect(msg) {
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            journey?.let { value ->
                item {
                    JourneyHeader(
                        journey = value,
                        now = state.nowMillis,
                        airports = state.airports,
                        gpsLatitude = state.currentLatitude,
                        gpsLongitude = state.currentLongitude
                    )
                }
                state.suggestedArrivalPlace?.let { placeName ->
                    item {
                        SuggestedArrivalPlaceCard(
                            placeName = placeName,
                            onConfirm = viewModel::markJourneyCompleted,
                            onDismiss = viewModel::dismissSuggestedArrivalPlace
                        )
                    }
                }
                if (state.showPhotosSinceAirportPrompt && state.photosSinceAirport.isNotEmpty()) {
                    item {
                        PhotosSinceAirportCard(
                            count = state.photosSinceAirport.size,
                            onAdd = viewModel::importPhotosSinceAirport,
                            onDismiss = viewModel::dismissPhotosSinceAirportPrompt
                        )
                    }
                }
                item {
                    JourneyStatusActions(
                        journey = value,
                        onMarkCompleted = viewModel::markJourneyCompleted,
                        onMarkCancelled = viewModel::markJourneyCancelled,
                        onMarkInProgress = viewModel::markJourneyInProgress,
                        onMarkPlanned = viewModel::markJourneyPlanned,
                        onAddGroundLeg = { showGroundLegDialog = true }
                    )
                }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Flights & legs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Button(onClick = { showAddMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Add to journey")
                        }
                    }
                }
                if (value.orderedFlights.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "No legs yet. Add a flight, train, bus, or hotel to build this trip.",
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                item {
                    SimpleJourneyItinerary(
                        journey = value,
                        airports = state.airports,
                        onEditLeg = { editingFlight = it },
                        onRemoveLeg = { removingFlight = it }
                    )
                }
                items(
                    value.orderedFlights.filterNot { TransportMode.isHotel(it.transportMode) },
                    key = { "detail_${it.id}" }
                ) { flight ->
                    FlightLegActionsCard(
                        flight = flight,
                        onEdit = { editingFlight = flight },
                        onEditOps = { opsEditingFlight = flight },
                        onRemove = { removingFlight = flight },
                        onMarkDelayed = { viewModel.markLegDelayed(flight.id) },
                        onMarkMissed = { viewModel.markLegMissed(flight.id) },
                        onMarkDiverted = { code -> viewModel.markLegDiverted(flight.id, code) },
                        onMarkArrived = { viewModel.markLegArrived(flight.id) },
                        onNotThisJourney = { viewModel.markNotThisJourney(flight.id) },
                        onResetPlanned = { viewModel.resetLegPlanned(flight.id) },
                        onSetTicketPhoto = { uri ->
                            viewModel.setFlightTicketPhoto(flight.id, uri)
                        },
                        onClearTicketPhoto = {
                            viewModel.setFlightTicketPhoto(flight.id, null)
                        },
                        onOpenTicketPhoto = { uri ->
                            if (isPdfAttachment(uri)) {
                                previewPdfUri = uri
                            } else {
                                fullscreenPhotos = listOf(uri)
                                fullscreenIndex = 0
                            }
                        }
                    )
                }
                value.orderedFlights.filter { TransportMode.isHotel(it.transportMode) }.forEach { flight ->
                    item(key = "hotel_${flight.id}") {
                        FlightLegCard(
                            flight = flight,
                            departureAirport = state.airports[flight.departureAirportCode],
                            arrivalAirport = state.airports[flight.arrivalAirportCode],
                            onEdit = { editingFlight = flight },
                            onMarkDelayed = { viewModel.markLegDelayed(flight.id) },
                            onMarkMissed = { viewModel.markLegMissed(flight.id) },
                            onMarkDiverted = { code -> viewModel.markLegDiverted(flight.id, code) },
                            onMarkArrived = { viewModel.markLegArrived(flight.id) },
                            onNotThisJourney = { viewModel.markNotThisJourney(flight.id) },
                            onResetPlanned = { viewModel.resetLegPlanned(flight.id) },
                            onSetTicketPhoto = { uri ->
                                viewModel.setFlightTicketPhoto(flight.id, uri)
                            },
                            onClearTicketPhoto = {
                                viewModel.setFlightTicketPhoto(flight.id, null)
                            },
                            onOpenTicketPhoto = { uri ->
                                if (isPdfAttachment(uri)) {
                                    previewPdfUri = uri
                                } else {
                                    fullscreenPhotos = listOf(uri)
                                    fullscreenIndex = 0
                                }
                            }
                        )
                    }
                }
                if (state.journeyDays.isNotEmpty() || state.timelinePhotos.isNotEmpty()) {
                    item {
                        JourneyTimelineSection(
                            days = state.journeyDays,
                            trackedDayCount = state.trackedDayCount,
                            timelinePhotos = state.timelinePhotos,
                            onImportPhotos = viewModel::importTimelinePhotosToAlbum,
                            onOpenPhoto = { photos, index ->
                                fullscreenPhotos = photos
                                fullscreenIndex = index
                            },
                            onOpenDay = { day ->
                                Toast.makeText(
                                    context,
                                    "Opening ${day.date} on Map…",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onOpenMap(day.date, day.countryCode)
                            }
                        )
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
                item {
                    JourneyRouteMap(
                        journey = value,
                        airports = state.airports,
                        gpsTrack = state.gpsTrack,
                        showGpsTrack = state.showGpsTrack,
                        currentLatitude = state.currentLatitude,
                        currentLongitude = state.currentLongitude,
                        onToggleGpsTrack = viewModel::setShowGpsTrack,
                        onRefreshGps = viewModel::refreshCurrentGps
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

    previewPdfUri?.let { pdfUri ->
        com.roamatlas.presentation.components.PdfPreviewDialog(
            uriString = pdfUri,
            onDismiss = { previewPdfUri = null },
            onOpenExternal = {
                openTicketAttachment(context, pdfUri)
                previewPdfUri = null
            }
        )
    }

    if (showAddMenu && journey != null) {
        AlertDialog(
            onDismissRequest = { showAddMenu = false },
            title = { Text("Add to journey") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            showAddMenu = false
                            showGroundLegDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Ground leg (taxi / train / bus / other)") }
                    listOf(
                        TransportMode.FLIGHT to "Flight",
                        TransportMode.TRAIN to "Train",
                        TransportMode.BUS to "Bus",
                        TransportMode.HOTEL to "Hotel"
                    ).forEach { (mode, label) ->
                        TextButton(
                            onClick = {
                                showAddMenu = false
                                onAddToJourney(journey.journey.id, mode)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddMenu = false }) { Text("Cancel") }
            }
        )
    }

    if (showGroundLegDialog && journey != null) {
        AddGroundLegDialog(
            suggestedFrom = journey.lastSettledArrivalLabel
                ?: journey.orderedFlights
                    .filterNot { TransportMode.isHotel(it.transportMode) }
                    .lastOrNull()
                    ?.let { it.arrivalCity ?: it.arrivalAirportCode },
            onDismiss = { showGroundLegDialog = false },
            onSave = { mode, destination, from ->
                viewModel.addGroundLeg(mode, destination, from)
                showGroundLegDialog = false
            }
        )
    }

    if (showEditJourney && journey != null) {
        EditJourneyDialog(
            journey = journey,
            onDismiss = { showEditJourney = false },
            onSave = { name, notes, startDay, endDay ->
                viewModel.updateJourney(name, notes, startDay, endDay)
                showEditJourney = false
            }
        )
    }
    editingFlight?.let { flight ->
        EditFlightScheduleDialog(
            flight = flight,
            onDismiss = { editingFlight = null },
            onSave = { depCode, arrCode, depDate, depHour, depMinute, arrDate, arrHour, arrMinute, airline, number ->
                viewModel.updateFlightSchedule(
                    flight = flight,
                    departureAirportCode = depCode,
                    arrivalAirportCode = arrCode,
                    departureDate = depDate,
                    departureHour = depHour,
                    departureMinute = depMinute,
                    arrivalDate = arrDate,
                    arrivalHour = arrHour,
                    arrivalMinute = arrMinute,
                    airline = airline,
                    flightNumber = number
                )
                editingFlight = null
            }
        )
    }
    opsEditingFlight?.let { flight ->
        EditFlightStatusDialog(
            flight = flight,
            onDismiss = { opsEditingFlight = null },
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
                opsEditingFlight = null
            }
        )
    }
    removingFlight?.let { flight ->
        AlertDialog(
            onDismissRequest = { removingFlight = null },
            title = { Text("Remove flight?") },
            text = {
                Text(
                    "Remove ${listOfNotNull(flight.airline, flight.flightNumber).joinToString(" ").ifBlank { flight.departureAirportCode + " → " + flight.arrivalAirportCode }} " +
                        "from this journey? You can add it again anytime — legs sort by departure time."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeFlightFromJourney(flight.id)
                        removingFlight = null
                    }
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { removingFlight = null }) { Text("Cancel") }
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
private fun JourneyHeader(
    journey: JourneyWithFlights,
    now: Long,
    airports: Map<String, com.roamatlas.data.airport.Airport> = emptyMap(),
    gpsLatitude: Double? = null,
    gpsLongitude: Double? = null
) {
    val nextWait = FlightScheduleTimes.nextDepartureWait(
        flights = journey.orderedFlights,
        airports = airports,
        nowMillis = now,
        gpsLatitude = gpsLatitude,
        gpsLongitude = gpsLongitude
    )
    val final = journey.finalArrivalMillis
    val start = LocalDate.ofEpochDay(journey.journey.startDateEpochDay)
    val end = LocalDate.ofEpochDay(
        journey.journey.endDateEpochDay ?: journey.journey.startDateEpochDay
    )
    val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val status = JourneyStatus.label(journey.status)
    val settledLabel = journey.lastSettledArrivalLabel
    val headline = when {
        journey.isCancelled ->
            journey.journey.cancelReason?.let { "Cancelled · $it" } ?: "Cancelled"
        journey.isCompleted -> "Journey completed"
        journey.stillTravelingAfterBookedLegs ->
            if (settledLabel != null) {
                "Arrived at $settledLabel · still traveling to destination"
            } else {
                "Booked legs done · still traveling to destination"
            }
        journey.orderedFlights.isEmpty() -> "Trip shell · add your first leg"
        nextWait != null -> {
            val mode = TransportMode.label(nextWait.flight.transportMode).lowercase()
            buildString {
                if (nextWait.gpsConfirmsAtAirport) {
                    append("At ${nextWait.departureAirportCode} · ")
                }
                append("Next $mode in ${formatDuration(nextWait.minutesRemaining)}")
            }
        }
        final != null && now <= final -> "Journey in progress"
        journey.isPast -> "Past journey"
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
            AssistChip(onClick = {}, enabled = false, label = { Text(status) })
            Text(
                headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (start == end) start.format(dateFmt) else "${start.format(dateFmt)} – ${end.format(dateFmt)}",
                style = MaterialTheme.typography.titleMedium
            )
            if (journey.flights.isNotEmpty()) {
                val route = journey.orderedFlights
                    .filterNot { TransportMode.isHotel(it.transportMode) }
                if (route.isNotEmpty()) {
                    Text(
                        route.joinToString(" → ") { it.departureAirportCode } +
                            " → ${route.last().arrivalAirportCode}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Text(journey.legSummaryText, style = MaterialTheme.typography.bodyMedium)
            if (journey.stillTravelingAfterBookedLegs) {
                Text(
                    "GPS and photos keep attaching here until you mark that you’ve arrived.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun SuggestedArrivalPlaceCard(
    placeName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Near saved place · $placeName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "GPS matches one of your map places. Confirm if this is your final destination.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Text("I’ve arrived here")
                }
                TextButton(onClick = onDismiss) { Text("Not yet") }
            }
        }
    }
}

@Composable
private fun PhotosSinceAirportCard(
    count: Int,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "$count new photo${if (count == 1) "" else "s"} since the airport",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Add them to this trip album? They’re still in your timeline.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                    Text("Add to trip")
                }
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        }
    }
}

@Composable
private fun JourneyStatusActions(
    journey: JourneyWithFlights,
    onMarkCompleted: () -> Unit,
    onMarkCancelled: (String?) -> Unit,
    onMarkInProgress: () -> Unit,
    onMarkPlanned: () -> Unit,
    onAddGroundLeg: () -> Unit
) {
    var showCancelDialog by remember { mutableStateOf(false) }
    var cancelReason by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Journey status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Last airport is not automatically the end. Keep traveling (taxi, train, etc.) and " +
                    "tap “I’ve arrived at my destination” when you’re done. " +
                    "Per-leg Delayed / Missed / Diverted / Not this journey are on each leg below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!journey.isCompleted && !journey.isCancelled) {
                Button(
                    onClick = onMarkCompleted,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("I’ve arrived at my destination") }
                OutlinedButton(
                    onClick = onAddGroundLeg,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add ground leg") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!journey.isCancelled) {
                    OutlinedButton(
                        onClick = { showCancelDialog = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancelled") }
                }
                if (journey.isCompleted || journey.isCancelled) {
                    OutlinedButton(
                        onClick = onMarkInProgress,
                        modifier = Modifier.weight(1f)
                    ) { Text("In progress") }
                }
                OutlinedButton(
                    onClick = onMarkPlanned,
                    modifier = Modifier.weight(1f)
                ) { Text("Planned") }
            }
        }
    }
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel this journey?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("It will move to Past and won’t count as travelled.")
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        label = { Text("Reason (optional)") },
                        placeholder = { Text("Airline cancel, rebooked, personal…") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        onMarkCancelled(cancelReason)
                    }
                ) { Text("Cancel journey") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Keep") }
            }
        )
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
private fun JourneyTimelineSection(
    days: List<JourneyDaySummary>,
    trackedDayCount: Int,
    timelinePhotos: List<LocationRecord>,
    onImportPhotos: () -> Unit,
    onOpenPhoto: (List<String>, Int) -> Unit,
    onOpenDay: (JourneyDaySummary) -> Unit
) {
    val photoUris = timelinePhotos.mapNotNull {
        it.originalPhotoUri?.takeIf { uri -> uri.isNotBlank() }
            ?: it.photoUri?.takeIf { uri -> uri.isNotBlank() }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Days on this trip",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "$trackedDayCount GPS day${if (trackedDayCount == 1) "" else "s"} · " +
                    "${timelinePhotos.size} timeline photo${if (timelinePhotos.size == 1) "" else "s"} " +
                    "in this journey’s date range",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Tap a day to open it on the main Map.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            days.take(12).forEach { day ->
                OutlinedButton(
                    onClick = { onOpenDay(day) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        buildString {
                            append(
                                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                                    .format(day.date)
                            )
                            day.countryName?.let { append(" · ").append(it) }
                                ?: day.countryCode?.let { append(" · ").append(it) }
                            append(" · ${day.pointCount} points")
                            if (day.photoCount > 0) append(" · ${day.photoCount} photos")
                            append(" · Map")
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (days.size > 12) {
                Text("+${days.size - 12} more days", style = MaterialTheme.typography.labelMedium)
            }
            if (photoUris.isNotEmpty()) {
                Text("Timeline photos", fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photoUris.size) { index ->
                        AsyncImage(
                            model = Uri.parse(photoUris[index]),
                            contentDescription = "Timeline photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenPhoto(photoUris, index) }
                        )
                    }
                }
                OutlinedButton(
                    onClick = onImportPhotos,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add timeline photos to trip album")
                }
            }
        }
    }
}

@Composable
private fun JourneyRouteMap(
    journey: JourneyWithFlights,
    airports: Map<String, Airport>,
    gpsTrack: List<LocationRecord>,
    showGpsTrack: Boolean,
    currentLatitude: Double?,
    currentLongitude: Double?,
    onToggleGpsTrack: (Boolean) -> Unit,
    onRefreshGps: () -> Unit
) {
    val transportFlights = journey.orderedFlights.filterNot {
        TransportMode.isHotel(it.transportMode)
    }
    val airportPoints = transportFlights.flatMap { flight ->
        listOfNotNull(
            airports[flight.departureAirportCode]?.let { LatLng(it.latitude, it.longitude) },
            airports[
                flight.actualArrivalAirportCode?.takeIf { it.isNotBlank() }
                    ?: flight.arrivalAirportCode
            ]?.let { LatLng(it.latitude, it.longitude) }
        )
    }.distinct()
    val gpsPoints = gpsTrack.map { LatLng(it.latitude, it.longitude) }
    val currentGps = if (currentLatitude != null && currentLongitude != null) {
        LatLng(currentLatitude, currentLongitude)
    } else {
        null
    }
    val focus = currentGps ?: gpsPoints.lastOrNull() ?: airportPoints.firstOrNull()
    if (focus == null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Route map unavailable because airport coordinates and GPS points are missing.",
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            focus,
            when {
                currentGps != null -> 12f
                gpsPoints.isNotEmpty() -> 5f
                else -> 2.5f
            }
        )
    }
    LaunchedEffect(currentLatitude, currentLongitude) {
        val here = currentGps ?: return@LaunchedEffect
        cameraState.animate(CameraUpdateFactory.newLatLngZoom(here, 12f))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Journey route", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Green = arrived legs · Blue = remaining · Teal = GPS path · Pin = you now.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (gpsPoints.isNotEmpty()) {
                FilterChip(
                    selected = showGpsTrack,
                    onClick = { onToggleGpsTrack(!showGpsTrack) },
                    label = {
                        Text(
                            if (showGpsTrack) "GPS path on (${gpsPoints.size} pts)"
                            else "GPS path off"
                        )
                    }
                )
            }
            OutlinedButton(onClick = onRefreshGps) {
                Text(if (currentGps != null) "Center on me" else "Find my GPS")
            }
        }
        if (currentGps == null) {
            Text(
                "Waiting for current GPS… grant location permission if needed.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        GoogleMap(
            modifier = Modifier.fillMaxWidth().height(280.dp),
            cameraPositionState = cameraState
        ) {
            airports.values.filter { airport ->
                transportFlights.any {
                    it.departureAirportCode == airport.iataCode ||
                        it.arrivalAirportCode == airport.iataCode ||
                        it.actualArrivalAirportCode == airport.iataCode
                }
            }.forEach { airport ->
                Marker(
                    state = MarkerState(LatLng(airport.latitude, airport.longitude)),
                    title = "${airport.iataCode} · ${airport.name}",
                    snippet = airport.city
                )
            }
            currentGps?.let { here ->
                Marker(
                    state = MarkerState(here),
                    title = "You are here",
                    snippet = "Current GPS"
                )
            }
            transportFlights.forEach { flight ->
                val departure = airports[flight.departureAirportCode]
                val arrCode = flight.actualArrivalAirportCode?.takeIf { it.isNotBlank() }
                    ?: flight.arrivalAirportCode
                val arrival = airports[arrCode]
                if (departure != null && arrival != null) {
                    val arrived = TravelLegStatus.normalize(flight.travelLegStatus) ==
                        TravelLegStatus.ARRIVED
                    Polyline(
                        points = listOf(
                            LatLng(departure.latitude, departure.longitude),
                            LatLng(arrival.latitude, arrival.longitude)
                        ),
                        color = if (arrived) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                        width = if (arrived) 8f else 7f,
                        geodesic = true
                    )
                }
            }
            if (showGpsTrack && gpsPoints.size >= 2) {
                Polyline(
                    points = gpsPoints,
                    color = Color(0xFF00897B),
                    width = 6f
                )
            }
        }
    }
}

@Composable
private fun SimpleJourneyItinerary(
    journey: JourneyWithFlights,
    airports: Map<String, Airport>,
    onEditLeg: (FlightEntity) -> Unit,
    onRemoveLeg: (FlightEntity) -> Unit
) {
    val legs = journey.orderedFlights.filterNot { TransportMode.isHotel(it.transportMode) }
    if (legs.isEmpty()) return
    val flyMinutes = legs.map { flight ->
        val dep = airports[flight.departureAirportCode]
        val arrCode = flight.actualArrivalAirportCode?.takeIf { it.isNotBlank() }
            ?: flight.arrivalAirportCode
        FlightScheduleTimes.durationMinutes(flight, dep, airports[arrCode])
    }
    val transitMinutesList = legs.zipWithNext().map { (a, b) ->
        ((b.departureTimeMillis - a.arrivalTimeMillis) / 60_000L).coerceAtLeast(0)
    }
    val totalFly = flyMinutes.sum()
    val totalTransit = transitMinutesList.sum()
    val totalJourney = totalFly + totalTransit

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Itinerary",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Local ticket times · fly time uses each airport’s timezone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            legs.forEachIndexed { index, flight ->
                val arrCode = flight.actualArrivalAirportCode?.takeIf { it.isNotBlank() }
                    ?: flight.arrivalAirportCode
                val depMillis = flight.departureTimeMillis
                val arrMillis = flight.arrivalTimeMillis
                val duration = flyMinutes[index]
                val status = TravelLegStatus.normalize(flight.travelLegStatus)
                val arrived = status == TravelLegStatus.ARRIVED
                val legContainer = when (status) {
                    TravelLegStatus.ARRIVED -> Color(0xFFE8F5E9)
                    TravelLegStatus.DELAYED -> Color(0xFFFFF3E0)
                    TravelLegStatus.MISSED, TravelLegStatus.SKIPPED -> Color(0xFFFFEBEE)
                    TravelLegStatus.DIVERTED -> Color(0xFFFFF8E1)
                    else -> MaterialTheme.colorScheme.surface
                }
                val legAccent = when (status) {
                    TravelLegStatus.ARRIVED -> Color(0xFF1B5E20)
                    TravelLegStatus.DELAYED -> Color(0xFFE65100)
                    TravelLegStatus.MISSED, TravelLegStatus.SKIPPED -> Color(0xFFB71C1C)
                    TravelLegStatus.DIVERTED -> Color(0xFFF57F17)
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = legContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                buildString {
                                    append("${index + 1}. ")
                                    append(
                                        listOfNotNull(flight.airline, flight.flightNumber)
                                            .joinToString(" · ")
                                            .ifBlank { TransportMode.label(flight.transportMode) }
                                    )
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = legAccent
                            )
                            if (status != TravelLegStatus.PLANNED) {
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = {
                                        Text(
                                            TravelLegStatus.label(status),
                                            color = legAccent
                                        )
                                    }
                                )
                            }
                        }
                        Text(
                            "${flight.departureAirportCode} → $arrCode",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = legAccent
                        )
                        Text(
                            "${formatTicketStamp(depMillis)}  →  ${formatTicketStamp(arrMillis)}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Fly time · ${formatDuration(duration)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = legAccent
                        )
                        if (arrived) {
                            Text(
                                "Completed",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onEditLeg(flight) }) { Text("Edit") }
                            TextButton(onClick = { onRemoveLeg(flight) }) { Text("Remove") }
                        }
                    }
                }

                if (index < legs.lastIndex) {
                    val hub = arrCode
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "Transit at $hub · ${formatDuration(transitMinutesList[index])}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider()
            Text(
                "Totals",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Total journey (with transit) · ${formatDuration(totalJourney)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Total fly time · ${formatDuration(totalFly)}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "Total transit time · ${formatDuration(totalTransit)}",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun FlightLegActionsCard(
    flight: FlightEntity,
    onEdit: () -> Unit,
    onEditOps: () -> Unit,
    onRemove: () -> Unit,
    onMarkDelayed: () -> Unit,
    onMarkMissed: () -> Unit,
    onMarkDiverted: (String) -> Unit,
    onMarkArrived: () -> Unit,
    onNotThisJourney: () -> Unit,
    onResetPlanned: () -> Unit,
    onSetTicketPhoto: (Uri) -> Unit,
    onClearTicketPhoto: () -> Unit,
    onOpenTicketPhoto: (String) -> Unit
) {
    var showDivertDialog by remember { mutableStateOf(false) }
    var divertCode by remember { mutableStateOf("") }
    var showAttachDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(onSetTicketPhoto) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (saved && uri != null) onSetTicketPhoto(publishCapturedPhotoToGallery(context, uri))
        else if (uri != null) runCatching { context.contentResolver.delete(uri, null, null) }
    }
    fun launchCamera() {
        val uri = createPlaceCameraPhotoUri(context) ?: return
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCamera() }

    val status = TravelLegStatus.normalize(flight.travelLegStatus)
    val arrived = status == TravelLegStatus.ARRIVED
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (arrived) {
            CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${flight.departureAirportCode} → ${flight.arrivalAirportCode}",
                    fontWeight = FontWeight.SemiBold,
                    color = if (arrived) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                )
                Row {
                    TextButton(onClick = onEdit) { Text("Edit") }
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
            }
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        TravelLegStatus.label(status),
                        color = if (arrived) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                    )
                }
            )
            TextButton(onClick = onEditOps) { Text("Delays, seat & status") }
            TravelLegActionsRow(
                onMarkDelayed = onMarkDelayed,
                onMarkMissed = onMarkMissed,
                onMarkDiverted = { showDivertDialog = true },
                onMarkArrived = onMarkArrived,
                onNotThisJourney = onNotThisJourney,
                onResetPlanned = onResetPlanned
            )
            val ticketUri = flight.photoUri
            if (!ticketUri.isNullOrBlank()) {
                OutlinedButton(onClick = { onOpenTicketPhoto(ticketUri) }) {
                    Text("Open ticket")
                }
                TextButton(onClick = onClearTicketPhoto) { Text("Remove ticket") }
            } else {
                OutlinedButton(onClick = { showAttachDialog = true }) {
                    Text("Add ticket photo or PDF")
                }
            }
        }
    }
    if (showAttachDialog) {
        AlertDialog(
            onDismissRequest = { showAttachDialog = false },
            title = { Text("Attach ticket") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showAttachDialog = false
                            documentLauncher.launch(arrayOf("image/*", "application/pdf"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Gallery / Files") }
                    TextButton(
                        onClick = {
                            showAttachDialog = false
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) launchCamera()
                            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Camera") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAttachDialog = false }) { Text("Cancel") }
            }
        )
    }
    if (showDivertDialog) {
        AlertDialog(
            onDismissRequest = { showDivertDialog = false },
            title = { Text("Diverted to…") },
            text = {
                OutlinedTextField(
                    value = divertCode,
                    onValueChange = { divertCode = it.uppercase().take(4) },
                    label = { Text("Airport code") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val code = divertCode.trim()
                        if (code.length >= 3) {
                            showDivertDialog = false
                            onMarkDiverted(code)
                            divertCode = ""
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showDivertDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ConnectionBetweenLegsCard(connection: JourneyConnection) {
    val background = when (connection.risk) {
        ConnectionRisk.COMFORTABLE -> MaterialTheme.colorScheme.secondaryContainer
        ConnectionRisk.TIGHT -> Color(0xFFFFE0A3)
        ConnectionRisk.BELOW_RECOMMENDED -> MaterialTheme.colorScheme.errorContainer
    }
    val airport = connection.arrivingFlight.arrivalAirportCode
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Transit at $airport · ${formatDuration(connection.effectiveMinutes)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Between ${connection.arrivingFlight.departureAirportCode}→$airport and " +
                    "$airport→${connection.departingFlight.arrivalAirportCode}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Recommended minimum · ${formatDuration(connection.recommendedMinimumMinutes.toLong())}",
                style = MaterialTheme.typography.bodySmall
            )
            if (connection.scheduledMinutes != connection.effectiveMinutes) {
                Text(
                    "Originally scheduled · ${formatDuration(connection.scheduledMinutes)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (connection.airportChange) {
                Text("Airport change required", fontWeight = FontWeight.Bold)
            }
            if (connection.departingFlight.separateTicketFromPrevious == true) {
                Text(
                    "Separate ticket: missed-connection protection may not apply.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            when (connection.risk) {
                ConnectionRisk.COMFORTABLE -> Text(
                    "Comfortable connection buffer.",
                    style = MaterialTheme.typography.bodySmall
                )
                ConnectionRisk.TIGHT -> Text(
                    "Tight connection — check terminals, immigration, security, baggage.",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall
                )
                ConnectionRisk.BELOW_RECOMMENDED -> Text(
                    "Below recommended minimum — contact airline or booking agent.",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SimpleTransitStrip(airportCode: String, transitMinutes: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            "Transit at $airportCode · ${formatDuration(transitMinutes)}",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FlightLegCard(
    flight: FlightEntity,
    departureAirport: Airport?,
    arrivalAirport: Airport?,
    onEdit: () -> Unit,
    onMarkDelayed: () -> Unit,
    onMarkMissed: () -> Unit,
    onMarkDiverted: (String) -> Unit,
    onMarkArrived: () -> Unit,
    onNotThisJourney: () -> Unit,
    onResetPlanned: () -> Unit,
    onSetTicketPhoto: (Uri) -> Unit,
    onClearTicketPhoto: () -> Unit,
    onOpenTicketPhoto: (String) -> Unit
) {
    val context = LocalContext.current
    var showAttachDialog by remember { mutableStateOf(false) }
    var showDivertDialog by remember { mutableStateOf(false) }
    var divertCode by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let(onSetTicketPhoto)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (saved && uri != null) {
            val galleryUri = publishCapturedPhotoToGallery(context, uri)
            onSetTicketPhoto(galleryUri)
        } else if (uri != null) {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }
    fun launchCamera() {
        val uri = createPlaceCameraPhotoUri(context) ?: run {
            Toast.makeText(context, "Could not open camera", Toast.LENGTH_SHORT).show()
            return
        }
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(context, "Camera permission needed", Toast.LENGTH_SHORT).show()
    }
    fun startCamera() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                    buildString {
                        append(com.roamatlas.data.local.entity.TransportMode.label(flight.transportMode))
                        val detail = listOfNotNull(flight.airline, flight.flightNumber)
                            .joinToString(" · ")
                        if (detail.isNotBlank()) append(" · ").append(detail)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onEdit) { Text("Update") }
            }
            val isHotel = TransportMode.isHotel(flight.transportMode)
            if (isHotel) {
                Text(
                    flight.departureCity
                        ?: departureAirport?.city
                        ?: flight.departureAirportCode
                )
                Text(
                    "Check-in · " + formatAirportTime(
                        flight.estimatedDepartureTimeMillis ?: flight.departureTimeMillis,
                        departureAirport
                    )
                )
                Text(
                    "Check-out · " + formatAirportTime(
                        flight.estimatedArrivalTimeMillis ?: flight.arrivalTimeMillis,
                        arrivalAirport
                    )
                )
                flight.bookingReference?.takeIf { it.isNotBlank() }?.let {
                    Text("Booking ref · $it", style = MaterialTheme.typography.bodySmall)
                }
            } else {
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
                val durationLabel = formatDuration(
                    legDurationMinutes(flight, departureAirport, arrivalAirport)
                )
                Text(
                    if (TransportMode.isFlight(flight.transportMode)) {
                        "Flight time · $durationLabel"
                    } else {
                        "Travel time · $durationLabel"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                val legStatus = TravelLegStatus.normalize(flight.travelLegStatus)
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(TravelLegStatus.label(legStatus)) }
                )
                flight.actualArrivalAirportCode?.takeIf { it.isNotBlank() }?.let { code ->
                    Text("Actual arrival · $code", fontWeight = FontWeight.SemiBold)
                }
                flight.flightStatus?.let { Text("Ops status: $it", fontWeight = FontWeight.SemiBold) }
                if (TransportMode.isFlight(flight.transportMode)) {
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
                }
                flight.bookingReference?.takeIf { it.isNotBlank() }?.let {
                    Text("Booking ref · $it", style = MaterialTheme.typography.bodySmall)
                }
                TravelLegActionsRow(
                    onMarkDelayed = onMarkDelayed,
                    onMarkMissed = onMarkMissed,
                    onMarkDiverted = { showDivertDialog = true },
                    onMarkArrived = onMarkArrived,
                    onNotThisJourney = onNotThisJourney,
                    onResetPlanned = onResetPlanned
                )
            }
            Text(
                if (isHotel) "Confirmation / voucher" else "Ticket / boarding pass",
                fontWeight = FontWeight.SemiBold
            )
            val ticketUri = flight.photoUri
            if (!ticketUri.isNullOrBlank()) {
                if (isPdfAttachment(ticketUri)) {
                    OutlinedButton(
                        onClick = { onOpenTicketPhoto(ticketUri) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Preview PDF ticket")
                    }
                } else {
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
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showAttachDialog = true }) {
                        Text("Replace")
                    }
                    TextButton(onClick = onClearTicketPhoto) {
                        Text("Remove")
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { showAttachDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add ticket photo or PDF")
                }
            }
        }
    }

    if (showAttachDialog) {
        AlertDialog(
            onDismissRequest = { showAttachDialog = false },
            title = { Text("Attach ticket") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showAttachDialog = false
                            documentLauncher.launch(arrayOf("image/*", "application/pdf"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Gallery / Files (photo or PDF)") }
                    TextButton(
                        onClick = {
                            showAttachDialog = false
                            startCamera()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Camera") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAttachDialog = false }) { Text("Cancel") }
            }
        )
    }
    if (showDivertDialog) {
        AlertDialog(
            onDismissRequest = { showDivertDialog = false },
            title = { Text("Diverted to…") },
            text = {
                OutlinedTextField(
                    value = divertCode,
                    onValueChange = { divertCode = it.uppercase().take(4) },
                    label = { Text("Airport code") },
                    placeholder = { Text("e.g. CGK") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val code = divertCode.trim()
                        if (code.length >= 3) {
                            showDivertDialog = false
                            onMarkDiverted(code)
                            divertCode = ""
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showDivertDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TravelLegActionsRow(
    onMarkDelayed: () -> Unit,
    onMarkMissed: () -> Unit,
    onMarkDiverted: () -> Unit,
    onMarkArrived: () -> Unit,
    onNotThisJourney: () -> Unit,
    onResetPlanned: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Travel detection",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(onClick = onMarkDelayed, modifier = Modifier.weight(1f)) {
                Text("Delayed", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = onMarkMissed, modifier = Modifier.weight(1f)) {
                Text("Missed", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = onMarkDiverted, modifier = Modifier.weight(1f)) {
                Text("Diverted", style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(onClick = onMarkArrived, modifier = Modifier.weight(1f)) {
                Text("Arrived", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = onNotThisJourney, modifier = Modifier.weight(1f)) {
                Text("Not this journey", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onResetPlanned, modifier = Modifier.weight(1f)) {
                Text("Reset", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun isPdfAttachment(uriString: String): Boolean {
    val lower = uriString.lowercase(Locale.getDefault())
    return lower.endsWith(".pdf") || lower.contains(".pdf")
}

private fun openTicketAttachment(context: android.content.Context, uriString: String) {
    runCatching {
        val parsed = Uri.parse(uriString)
        val shareable = when (parsed.scheme) {
            "file" -> {
                val file = File(parsed.path ?: return)
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
            else -> parsed
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(shareable, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open ticket PDF"))
    }.onFailure {
        Toast.makeText(context, "Could not open PDF", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AddGroundLegDialog(
    suggestedFrom: String?,
    onDismiss: () -> Unit,
    onSave: (mode: String, destination: String, from: String?) -> Unit
) {
    var mode by remember { mutableStateOf(TransportMode.TAXI) }
    var destination by remember { mutableStateOf("") }
    var from by remember { mutableStateOf(suggestedFrom.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ground leg") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "After the airport (or any stop), continue to office, home, warehouse, port, hotel…",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        TransportMode.TAXI to "Taxi",
                        TransportMode.TRAIN to "Train",
                        TransportMode.BUS to "Bus",
                        TransportMode.OTHER to "Other"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = mode == value,
                            onClick = { mode = value },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = from,
                    onValueChange = { from = it },
                    label = { Text("From (optional)") },
                    placeholder = { Text(suggestedFrom ?: "Last airport / station") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Destination") },
                    placeholder = { Text("Office, home, warehouse…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        mode,
                        destination.trim(),
                        from.trim().takeIf { it.isNotBlank() }
                    )
                },
                enabled = destination.trim().isNotEmpty()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditJourneyDialog(
    journey: JourneyWithFlights,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, Long) -> Unit
) {
    var name by remember { mutableStateOf(journey.journey.name) }
    var notes by remember { mutableStateOf(journey.journey.notes.orEmpty()) }
    var startDate by remember {
        mutableStateOf(LocalDate.ofEpochDay(journey.journey.startDateEpochDay))
    }
    var endDate by remember {
        mutableStateOf(
            LocalDate.ofEpochDay(
                journey.journey.endDateEpochDay ?: journey.journey.startDateEpochDay
            )
        )
    }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    val dateFmt = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
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
                Text(
                    "Trip dates stay independent of flight times, so delays or early arrivals do not force a rewrite.",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { pickingStart = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Start: ${startDate.format(dateFmt)}")
                }
                TextButton(onClick = { pickingEnd = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("End: ${endDate.format(dateFmt)}")
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name, notes, startDate.toEpochDay(), endDate.toEpochDay())
                },
                enabled = !endDate.isBefore(startDate)
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
    if (pickingStart) {
        JourneyDetailDatePicker(
            initialDate = startDate,
            onDismiss = { pickingStart = false },
            onConfirm = {
                startDate = it
                if (endDate.isBefore(it)) endDate = it
                pickingStart = false
            }
        )
    }
    if (pickingEnd) {
        JourneyDetailDatePicker(
            initialDate = endDate,
            onDismiss = { pickingEnd = false },
            onConfirm = {
                endDate = it
                pickingEnd = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JourneyDetailDatePicker(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate
            .atStartOfDay(ZoneOffset.UTC)
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
                                .atZone(ZoneOffset.UTC)
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
private fun EditFlightScheduleDialog(
    flight: FlightEntity,
    onDismiss: () -> Unit,
    onSave: (
        departureAirportCode: String,
        arrivalAirportCode: String,
        departureDate: LocalDate,
        departureHour: Int,
        departureMinute: Int,
        arrivalDate: LocalDate,
        arrivalHour: Int,
        arrivalMinute: Int,
        airline: String,
        flightNumber: String
    ) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val depZdt = remember(flight.id, flight.departureTimeMillis) {
        Instant.ofEpochMilli(flight.departureTimeMillis).atZone(zone)
    }
    val arrZdt = remember(flight.id, flight.arrivalTimeMillis) {
        Instant.ofEpochMilli(flight.arrivalTimeMillis).atZone(zone)
    }
    var airline by remember(flight.id) { mutableStateOf(flight.airline.orEmpty()) }
    var flightNumber by remember(flight.id) { mutableStateOf(flight.flightNumber.orEmpty()) }
    var depCode by remember(flight.id) { mutableStateOf(flight.departureAirportCode) }
    var arrCode by remember(flight.id) { mutableStateOf(flight.arrivalAirportCode) }
    var depDate by remember(flight.id) { mutableStateOf(depZdt.toLocalDate()) }
    var arrDate by remember(flight.id) { mutableStateOf(arrZdt.toLocalDate()) }
    var depHour by remember(flight.id) { mutableStateOf(depZdt.hour.toString()) }
    var depMinute by remember(flight.id) {
        mutableStateOf(depZdt.minute.toString().padStart(2, '0'))
    }
    var arrHour by remember(flight.id) { mutableStateOf(arrZdt.hour.toString()) }
    var arrMinute by remember(flight.id) {
        mutableStateOf(arrZdt.minute.toString().padStart(2, '0'))
    }
    var pickingDepDate by remember { mutableStateOf(false) }
    var pickingArrDate by remember { mutableStateOf(false) }
    val dateFmt = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val depH = depHour.toIntOrNull()
    val depM = depMinute.toIntOrNull()
    val arrH = arrHour.toIntOrNull()
    val arrM = arrMinute.toIntOrNull()
    val canSave = depCode.trim().length >= 3 &&
        arrCode.trim().length >= 3 &&
        depH != null && depH in 0..23 &&
        depM != null && depM in 0..59 &&
        arrH != null && arrH in 0..23 &&
        arrM != null && arrM in 0..59

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit flight") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Fix ticket times, airports, or flight code. Legs re-sort by departure time after save.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = airline,
                        onValueChange = { airline = it },
                        label = { Text("Airline") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = flightNumber,
                        onValueChange = { flightNumber = it.uppercase().take(10) },
                        label = { Text("Flight #") },
                        modifier = Modifier.weight(0.7f),
                        singleLine = true
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = depCode,
                        onValueChange = { depCode = it.uppercase().take(4) },
                        label = { Text("From") },
                        placeholder = { Text("CGK") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = arrCode,
                        onValueChange = { arrCode = it.uppercase().take(4) },
                        label = { Text("To") },
                        placeholder = { Text("SIN") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Text("Departure", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { pickingDepDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Date · ${depDate.format(dateFmt)}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = depHour,
                        onValueChange = { depHour = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour (0–23)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = depMinute,
                        onValueChange = { depMinute = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minute") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Text("Arrival", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { pickingArrDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Date · ${arrDate.format(dateFmt)}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = arrHour,
                        onValueChange = { arrHour = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour (0–23)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = arrMinute,
                        onValueChange = { arrMinute = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minute") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!canSave) return@Button
                    onSave(
                        depCode.trim(),
                        arrCode.trim(),
                        depDate,
                        depH!!,
                        depM!!,
                        arrDate,
                        arrH!!,
                        arrM!!,
                        airline.trim(),
                        flightNumber.trim()
                    )
                },
                enabled = canSave
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
    if (pickingDepDate) {
        JourneyDetailDatePicker(
            initialDate = depDate,
            onDismiss = { pickingDepDate = false },
            onConfirm = {
                depDate = it
                if (arrDate.isBefore(it)) arrDate = it
                pickingDepDate = false
            }
        )
    }
    if (pickingArrDate) {
        JourneyDetailDatePicker(
            initialDate = arrDate,
            onDismiss = { pickingArrDate = false },
            onConfirm = {
                arrDate = it
                pickingArrDate = false
            }
        )
    }
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

private fun legDurationMinutes(
    flight: FlightEntity,
    departureAirport: Airport?,
    arrivalAirport: Airport?
): Long = FlightScheduleTimes.durationMinutes(flight, departureAirport, arrivalAirport)

private fun formatAirportTime(millis: Long, airport: Airport?): String =
    formatTicketStamp(millis)

/**
 * Ticket wall-clock in 24-hour format — same as [JourneyShareFormatter].
 * Do not convert into airport timezones here; that shifted CGK/SIN times and
 * made durations/transit look wrong.
 */
private fun formatTicketStamp(millis: Long): String =
    SimpleDateFormat("EEE, d MMM yyyy HH:mm", Locale.getDefault()).format(Date(millis))

private fun formatLocalAirportStamp(millis: Long, airport: Airport?): String =
    formatTicketStamp(millis)

private fun noteValue(notes: String?, label: String): String? =
    notes?.lineSequence()
        ?.firstOrNull { it.startsWith("$label:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
