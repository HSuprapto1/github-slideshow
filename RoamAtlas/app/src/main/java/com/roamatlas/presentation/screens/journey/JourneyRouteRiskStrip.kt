package com.roamatlas.presentation.screens.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roamatlas.data.journey.ConnectionRisk
import com.roamatlas.data.journey.JourneyWithFlights

/**
 * Route airports as chips. Transit airports are tinted by connection risk so
 * short/tight connections are visible on the card without opening detail.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JourneyRouteRiskStrip(journey: JourneyWithFlights) {
    if (journey.orderedFlights.isEmpty()) return
    val connectionByAirport = journey.connections.associateBy {
        it.departingFlight.departureAirportCode.uppercase()
    }
    val airports = buildList {
        add(journey.orderedFlights.first().departureAirportCode.uppercase())
        journey.orderedFlights.forEach { add(it.arrivalAirportCode.uppercase()) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            airports.forEachIndexed { index, airport ->
                if (index > 0) {
                    Text(
                        "→",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
                AirportRiskChip(
                    code = airport,
                    risk = connectionByAirport[airport]?.risk
                )
            }
        }
        val risky = journey.connections.filter { it.risk != ConnectionRisk.COMFORTABLE }
        if (risky.isNotEmpty()) {
            Text(
                risky.joinToString(" · ") { connection ->
                    val airport = connection.departingFlight.departureAirportCode.uppercase()
                    val duration = formatDuration(connection.effectiveMinutes)
                    when (connection.risk) {
                        ConnectionRisk.TIGHT ->
                            "$airport transit $duration · tight"
                        ConnectionRisk.BELOW_RECOMMENDED ->
                            "$airport transit $duration · short"
                        ConnectionRisk.COMFORTABLE -> ""
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (risky.any { it.risk == ConnectionRisk.BELOW_RECOMMENDED }) {
                    MaterialTheme.colorScheme.error
                } else {
                    Color(0xFF8A5A00)
                }
            )
        }
    }
}

@Composable
private fun AirportRiskChip(code: String, risk: ConnectionRisk?) {
    val background = when (risk) {
        ConnectionRisk.BELOW_RECOMMENDED -> MaterialTheme.colorScheme.errorContainer
        ConnectionRisk.TIGHT -> Color(0xFFFFE0A3)
        ConnectionRisk.COMFORTABLE -> MaterialTheme.colorScheme.secondaryContainer
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when (risk) {
        ConnectionRisk.BELOW_RECOMMENDED -> MaterialTheme.colorScheme.onErrorContainer
        ConnectionRisk.TIGHT -> Color(0xFF5C3B00)
        ConnectionRisk.COMFORTABLE -> MaterialTheme.colorScheme.onSecondaryContainer
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = code,
            color = textColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
