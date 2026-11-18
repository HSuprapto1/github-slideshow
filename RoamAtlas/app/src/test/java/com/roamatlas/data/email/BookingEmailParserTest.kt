package com.roamatlas.data.email

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Calendar

class BookingEmailParserTest {

    @Test
    fun `parses dated flight with gate and terminal`() {
        val result = BookingEmailParser().parseEmail(
            """
            Booking reference: ABC123
            Flight SQ 950
            SIN → CGK
            18 November 2026
            Departure 09:30 Arrival 10:20
            Terminal 3 Gate A12
            """.trimIndent()
        )

        val flight = result.flights.single()
        assertEquals("SIN", flight.departureAirportCode)
        assertEquals("CGK", flight.arrivalAirportCode)
        assertEquals("SQ950", flight.flightNumber)
        assertEquals("3", flight.departureTerminal)
        assertEquals("A12", flight.departureGate)
        assertNotNull(flight.departureTimeMillis)
        assertEquals(9, Calendar.getInstance().apply {
            timeInMillis = flight.departureTimeMillis!!
        }.get(Calendar.HOUR_OF_DAY))
    }
}
