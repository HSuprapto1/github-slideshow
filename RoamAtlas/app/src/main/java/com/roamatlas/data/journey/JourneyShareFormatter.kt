package com.roamatlas.data.journey

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import com.roamatlas.data.airport.Airport
import com.roamatlas.data.flight.FlightScheduleTimes
import com.roamatlas.data.local.entity.TransportMode
import java.io.File
import java.io.FileOutputStream

object JourneyShareFormatter {
    fun shareText(
        context: Context,
        journey: JourneyWithFlights,
        airports: Map<String, Airport>,
        includeBookingReferences: Boolean,
        includeNotes: Boolean
    ) {
        val text = formatText(
            journey,
            airports,
            includeBookingReferences,
            includeNotes
        )
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "${journey.journey.name} flight itinerary")
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Share journey"
            )
        )
    }

    fun shareCard(
        context: Context,
        journey: JourneyWithFlights,
        airports: Map<String, Airport>,
        includeBookingReferences: Boolean,
        includeNotes: Boolean
    ) {
        val text = formatText(
            journey,
            airports,
            includeBookingReferences,
            includeNotes
        )
        val bitmap = renderCard(
            journey,
            airports,
            includeBookingReferences,
            includeNotes
        )
        val directory = File(context.cacheDir, "shared_journeys").apply { mkdirs() }
        val file = File(directory, "RoamAtlas_${journey.journey.id}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }
        bitmap.recycle()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_SUBJECT, "${journey.journey.name} flight itinerary")
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, "Journey card", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Share journey card"
            )
        )
    }

    fun formatText(
        journey: JourneyWithFlights,
        airports: Map<String, Airport>,
        includeBookingReferences: Boolean,
        includeNotes: Boolean
    ): String {
        val legs = journey.orderedFlights.filterNot { TransportMode.isHotel(it.transportMode) }
        val flyMinutes = legs.map { flight ->
            val dep = airports[flight.departureAirportCode]
            val arrCode = flight.actualArrivalAirportCode?.takeIf { it.isNotBlank() }
                ?: flight.arrivalAirportCode
            FlightScheduleTimes.durationMinutes(flight, dep, airports[arrCode])
        }
        val transitMinutes = legs.zipWithNext().map { (a, b) ->
            FlightScheduleTimes.connectionMinutes(a, b, airports)
        }
        val totalFly = flyMinutes.sum()
        val totalTransit = transitMinutes.sum()
        val totalJourney = totalFly + totalTransit

        return buildString {
            appendLine(journey.journey.name)
            if (legs.isNotEmpty()) {
                appendLine(
                    legs.joinToString(" → ") { it.departureAirportCode } +
                        " → ${legs.last().arrivalAirportCode}"
                )
            }
            appendLine(
                "${legs.size} flight${if (legs.size == 1) "" else "s"} · " +
                    "${transitMinutes.size} transit${if (transitMinutes.size == 1) "" else "s"}"
            )
            appendLine()

            legs.forEachIndexed { index, flight ->
                val departure = airports[flight.departureAirportCode]
                val arrCode = flight.actualArrivalAirportCode?.takeIf { it.isNotBlank() }
                    ?: flight.arrivalAirportCode
                val arrival = airports[arrCode]
                appendLine(
                    "${index + 1}. ${
                        listOfNotNull(flight.airline, flight.flightNumber)
                            .joinToString(" · ")
                            .ifBlank { TransportMode.label(flight.transportMode) }
                    }"
                )
                appendLine(
                    "   ${flight.departureAirportCode} ${departure?.name ?: flight.departureAirportName.orEmpty()}"
                )
                val depInstant = FlightScheduleTimes.departureInstantMillis(
                    flight,
                    departure,
                    arrival
                )
                val arrInstant = FlightScheduleTimes.arrivalInstantMillis(
                    flight,
                    departure,
                    arrival
                )
                appendLine(
                    "   ${
                        FlightScheduleTimes.formatAirportLocal(
                            depInstant,
                            departure?.timeZoneId
                        )
                    }"
                )
                appendLine(
                    "   → $arrCode ${arrival?.name ?: flight.arrivalAirportName.orEmpty()}"
                )
                appendLine(
                    "   ${
                        FlightScheduleTimes.formatAirportLocal(
                            arrInstant,
                            arrival?.timeZoneId
                        )
                    }"
                )
                appendLine("   Fly time · ${formatMinutes(flyMinutes[index])}")
                if (flight.estimatedDepartureTimeMillis != null ||
                    flight.estimatedArrivalTimeMillis != null
                ) {
                    appendLine(
                        "   Latest estimate: ${
                            FlightScheduleTimes.formatAirportLocal(
                                depInstant,
                                departure?.timeZoneId
                            )
                        }" +
                            " → ${
                                FlightScheduleTimes.formatAirportLocal(
                                    arrInstant,
                                    arrival?.timeZoneId
                                )
                            }"
                    )
                }
                val terminal = noteValue(flight.notes, "Departure terminal")
                val gate = noteValue(flight.notes, "Departure gate")
                if (terminal != null || gate != null) {
                    appendLine(
                        "   " + listOfNotNull(
                            terminal?.let { "Terminal $it" },
                            gate?.let { "Gate $it" }
                        ).joinToString(" · ")
                    )
                }
                if (flight.aircraftType != null || flight.seatNumber != null) {
                    appendLine(
                        "   " + listOfNotNull(
                            flight.aircraftType?.let { "Aircraft: $it" },
                            flight.seatNumber?.let { "Seat: $it" }
                        ).joinToString(" · ")
                    )
                }
                flight.flightStatus?.let { appendLine("   Status: $it") }
                if (includeBookingReferences) {
                    flight.bookingReference?.let { appendLine("   Booking reference: $it") }
                }
                appendLine()
                if (index < transitMinutes.size) {
                    appendLine(
                        "   Transit at $arrCode · ${formatMinutes(transitMinutes[index])}"
                    )
                    appendLine()
                }
            }

            appendLine("Totals")
            appendLine("   Total journey (with transit) · ${formatMinutes(totalJourney)}")
            appendLine("   Total fly time · ${formatMinutes(totalFly)}")
            appendLine("   Total transit time · ${formatMinutes(totalTransit)}")
            appendLine()

            if (includeNotes) {
                journey.journey.notes?.let {
                    appendLine("Notes")
                    appendLine(it)
                    appendLine()
                }
            }
            appendLine("Shared from RoamAtlas")
        }
    }

    private fun renderCard(
        journey: JourneyWithFlights,
        airports: Map<String, Airport>,
        includeBookingReferences: Boolean,
        includeNotes: Boolean
    ): Bitmap {
        val lines = formatText(
            journey,
            airports,
            includeBookingReferences,
            includeNotes
        ).lines()
        val width = 1200
        val lineHeight = 48
        val height = (lines.size * lineHeight + 180).coerceAtLeast(600)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(246, 249, 255))
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(24, 55, 95)
            textSize = 54f
            isFakeBoldText = true
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 30, 35)
            textSize = 32f
        }
        canvas.drawText("RoamAtlas", 54f, 72f, titlePaint)
        var y = 140f
        lines.forEachIndexed { index, line ->
            val paint = if (index == 0) titlePaint else bodyPaint
            canvas.drawText(line.take(68), 54f, y, paint)
            y += lineHeight
        }
        return bitmap
    }

    private fun formatMinutes(total: Long): String {
        val safe = total.coerceAtLeast(0)
        val days = safe / 1440
        val hours = safe % 1440 / 60
        val minutes = safe % 60
        return buildList {
            if (days > 0) add("${days}d")
            if (hours > 0 || days > 0) add("${hours}h")
            add("${minutes}m")
        }.joinToString(" ")
    }

    private fun noteValue(notes: String?, label: String): String? =
        notes?.lineSequence()
            ?.firstOrNull { it.startsWith("$label:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}
