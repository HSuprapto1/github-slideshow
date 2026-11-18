package com.roamatlas.presentation.screens.journey

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.roamatlas.data.airport.Airport
import com.roamatlas.data.flight.FlightPathAnalyzer
import com.roamatlas.data.flight.FlightScheduleTimes
import com.roamatlas.data.local.entity.FlightEntity
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightPathScreen(
    flight: FlightEntity,
    report: FlightPathAnalyzer.FlightPathReport,
    departureAirport: Airport?,
    arrivalAirport: Airport?,
    onBack: () -> Unit
) {
    val routeLabel = "${report.departureCode} → ${report.arrivalCode}"
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Flight path", fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(flight.airline, flight.flightNumber)
                                .joinToString(" · ")
                                .ifBlank { routeLabel },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FlightPathMap(
                report = report,
                departureAirport = departureAirport,
                arrivalAirport = arrivalAirport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .padding(horizontal = 16.dp)
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    routeLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                val scheduled = formatDurationMinutes(report.scheduledDurationMinutes)
                val actual = report.actualDurationMinutes?.let { formatDurationMinutes(it) }
                Text(
                    if (actual != null) {
                        "Scheduled $scheduled · Actual $actual"
                    } else {
                        "Scheduled $scheduled · Actual path incomplete"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                SummaryRow(
                    "Max altitude",
                    report.maxAltitudeM?.let { "${it.toInt()} m" } ?: "—"
                )
                SummaryRow(
                    "Speed",
                    buildString {
                        val avg = report.avgSpeedKmh?.toInt()
                        val max = report.maxSpeedKmh?.toInt()
                        when {
                            avg != null && max != null -> append("avg $avg · max $max km/h")
                            max != null -> append("max $max km/h")
                            avg != null -> append("avg $avg km/h")
                            else -> append("—")
                        }
                    }
                )
                SummaryRow(
                    "Distance flown",
                    report.distanceKm?.let { String.format(Locale.US, "%.0f km", it) } ?: "—"
                )
                if (report.gapCount > 0) {
                    Text(
                        "GPS gap · ${report.gapCount} break${if (report.gapCount == 1) "" else "s"} " +
                            "(dashed on map — phone likely offline / airplane mode).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Path color follows altitude (low → high). Takeoff and landing pins mark the recorded ends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FlightPathMap(
    report: FlightPathAnalyzer.FlightPathReport,
    departureAirport: Airport?,
    arrivalAirport: Airport?,
    modifier: Modifier = Modifier
) {
    val allLatLng = remember(report) {
        report.points.map { LatLng(it.latitude, it.longitude) }
    }
    val start = allLatLng.firstOrNull()
        ?: departureAirport?.let { LatLng(it.latitude, it.longitude) }
        ?: LatLng(0.0, 0.0)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(start, 4f)
    }
    LaunchedEffect(report.flightId, allLatLng.size) {
        if (allLatLng.size >= 2) {
            val bounds = LatLngBounds.builder().apply {
                allLatLng.forEach { include(it) }
                departureAirport?.let { include(LatLng(it.latitude, it.longitude)) }
                arrivalAirport?.let { include(LatLng(it.latitude, it.longitude)) }
            }.build()
            runCatching {
                cameraState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 80))
            }
        }
    }
    GoogleMap(modifier = modifier, cameraPositionState = cameraState) {
        departureAirport?.let {
            Marker(
                state = MarkerState(LatLng(it.latitude, it.longitude)),
                title = it.iataCode,
                snippet = "Departure airport"
            )
        }
        arrivalAirport?.let {
            Marker(
                state = MarkerState(LatLng(it.latitude, it.longitude)),
                title = it.iataCode,
                snippet = "Arrival airport"
            )
        }
        report.takeoff?.let {
            Marker(
                state = MarkerState(LatLng(it.latitude, it.longitude)),
                title = "Takeoff",
                snippet = FlightScheduleTimes.formatAirportLocal(
                    it.timestamp.toEpochMilli(),
                    departureAirport?.timeZoneId,
                    pattern = "HH:mm"
                )
            )
        }
        report.landing?.let {
            Marker(
                state = MarkerState(LatLng(it.latitude, it.longitude)),
                title = "Landing",
                snippet = FlightScheduleTimes.formatAirportLocal(
                    it.timestamp.toEpochMilli(),
                    arrivalAirport?.timeZoneId,
                    pattern = "HH:mm"
                )
            )
        }
        report.segments.forEach { segment ->
            if (segment.points.size < 2) return@forEach
            if (segment.isGap) {
                Polyline(
                    points = segment.points.map { LatLng(it.latitude, it.longitude) },
                    color = Color(0xFF90A4AE),
                    width = 5f,
                    pattern = listOf(Dash(24f), Gap(16f))
                )
            } else {
                // Paint by altitude in short chunks so color shifts along the climb/cruise.
                val pts = segment.points
                var i = 0
                while (i < pts.lastIndex) {
                    val end = minOf(i + 8, pts.lastIndex)
                    val chunk = pts.subList(i, end + 1)
                    val midAlt = chunk.mapNotNull { it.altitude }.average().takeIf { !it.isNaN() }
                    Polyline(
                        points = chunk.map { LatLng(it.latitude, it.longitude) },
                        color = Color(FlightPathAnalyzer.altitudeColorArgb(midAlt)),
                        width = 8f
                    )
                    i = end
                }
            }
        }
    }
}

private fun formatDurationMinutes(total: Long): String {
    val safe = total.coerceAtLeast(0)
    val hours = safe / 60
    val minutes = safe % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
