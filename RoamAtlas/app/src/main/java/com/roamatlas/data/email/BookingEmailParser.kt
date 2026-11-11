package com.roamatlas.data.email

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

/**
 * Parser for extracting flight information from booking confirmation emails.
 * Supports various airline formats and common booking systems.
 */
data class ParsedFlight(
    val departureAirportCode: String,
    val arrivalAirportCode: String,
    val departureTimeMillis: Long?,
    val arrivalTimeMillis: Long?,
    val flightNumber: String?,
    val airline: String?,
    val bookingReference: String?,
    val passengerName: String?,
    val seatNumber: String?,
    val aircraftType: String?,
    val departureGate: String? = null,
    val departureTerminal: String? = null
)

data class ParsedItinerary(
    val flights: List<ParsedFlight>,
    val bookingReference: String?,
    val airline: String?,
    val rawText: String
)

class BookingEmailParser {
    
    companion object {
        // Airport code pattern (3 letter IATA codes)
        private val AIRPORT_CODE_PATTERN = Pattern.compile("\\b([A-Z]{3})\\b")
        
        // Flight number patterns
        private val FLIGHT_NUMBER_PATTERNS = listOf(
            Pattern.compile("\\b(SQ|GA|QF|EK|BA|AA|UA|DL|SIA|QR|CX|JL|NH|KE|TG|MH|AK|TR|3K|ID|JT|QG)\\s*(\\d{1,4})\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Flight\\s*(?:Number|No\\.?|#)?\\s*:?\\s*([A-Z]{2,3})\\s*(\\d{1,4})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([A-Z]{2})\\s*(\\d{3,4})\\s+(?:from|departing)", Pattern.CASE_INSENSITIVE)
        )
        
        // Booking reference patterns
        private val BOOKING_REF_PATTERNS = listOf(
            Pattern.compile("(?:Booking|Confirmation|Reference|PNR|Record Locator)\\s*(?:Number|No\\.?|#|Code)?\\s*:?\\s*([A-Z0-9]{5,8})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b([A-Z]{6})\\b"), // Standard 6-letter PNR
            Pattern.compile("(?:Your|Trip)\\s+(?:booking|confirmation)\\s+(?:code|number|reference)\\s*:?\\s*([A-Z0-9]{5,8})", Pattern.CASE_INSENSITIVE)
        )
        
        // Date patterns
        private val DATE_FORMATS = listOf(
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH),
            SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),
            SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
            SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH)
        )
        
        // Time patterns
        private val TIME_PATTERN = Pattern.compile("\\b(\\d{1,2})[:.](\\d{2})\\s*(AM|PM|am|pm)?\\b")
        private val GATE_PATTERN = Pattern.compile(
            "\\b(?:Departure\\s+)?Gate\\s*[:#-]?\\s*([A-Z]?\\d{1,3}[A-Z]?)\\b",
            Pattern.CASE_INSENSITIVE
        )
        private val TERMINAL_PATTERN = Pattern.compile(
            "\\b(?:Departure\\s+)?Terminal\\s*[:#-]?\\s*([A-Z0-9]{1,4})\\b",
            Pattern.CASE_INSENSITIVE
        )
        private val DATE_TEXT_PATTERNS = listOf(
            Pattern.compile("\\b\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4}\\b"),
            Pattern.compile("\\b[A-Za-z]{3,9}\\s+\\d{1,2},\\s*\\d{4}\\b"),
            Pattern.compile("\\b\\d{1,2}/\\d{1,2}/\\d{4}\\b"),
            Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b")
        )
        
        // Route patterns (SIN → CGK, SIN - CGK, SIN to CGK)
        private val ROUTE_PATTERNS = listOf(
            Pattern.compile("\\b([A-Z]{3})\\s*[→➔>\\-–—]\\s*([A-Z]{3})\\b"),
            Pattern.compile("\\b([A-Z]{3})\\s+to\\s+([A-Z]{3})\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("from\\s+([A-Z]{3})\\s+to\\s+([A-Z]{3})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:Depart|Departing|From)\\s*:?\\s*([A-Z]{3}).*?(?:Arrive|Arriving|To)\\s*:?\\s*([A-Z]{3})", Pattern.CASE_INSENSITIVE)
        )
        
        // Known airlines for detection
        private val AIRLINE_NAMES = mapOf(
            "SQ" to "Singapore Airlines",
            "SIA" to "Singapore Airlines",
            "GA" to "Garuda Indonesia",
            "QF" to "Qantas",
            "EK" to "Emirates",
            "BA" to "British Airways",
            "AA" to "American Airlines",
            "UA" to "United Airlines",
            "DL" to "Delta Air Lines",
            "QR" to "Qatar Airways",
            "CX" to "Cathay Pacific",
            "JL" to "Japan Airlines",
            "NH" to "ANA",
            "KE" to "Korean Air",
            "TG" to "Thai Airways",
            "MH" to "Malaysia Airlines",
            "AK" to "AirAsia",
            "TR" to "Scoot",
            "3K" to "Jetstar Asia",
            "ID" to "Batik Air",
            "JT" to "Lion Air",
            "QG" to "Citilink",
            "LH" to "Lufthansa",
            "AF" to "Air France",
            "KL" to "KLM",
            "LX" to "Swiss",
            "OS" to "Austrian",
            "TK" to "Turkish Airlines",
            "EY" to "Etihad Airways"
        )
    }
    
    fun parseEmail(emailContent: String): ParsedItinerary {
        val text = emailContent.replace("\\s+".toRegex(), " ").trim()
        
        val bookingRef = extractBookingReference(text)
        val flights = extractFlights(text)
        val detectedAirline = detectAirline(text)
        
        return ParsedItinerary(
            flights = flights,
            bookingReference = bookingRef,
            airline = detectedAirline,
            rawText = emailContent
        )
    }
    
    private fun extractBookingReference(text: String): String? {
        for (pattern in BOOKING_REF_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val ref = matcher.group(1)
                // Validate it looks like a booking reference
                if (ref != null && ref.length in 5..8 && ref.matches(Regex("[A-Z0-9]+"))) {
                    // Exclude common words that might match
                    if (ref !in listOf("FLIGHT", "BOARD", "GATES", "CLASS", "SEATS")) {
                        return ref
                    }
                }
            }
        }
        return null
    }
    
    private fun extractFlights(text: String): List<ParsedFlight> {
        val flights = mutableListOf<ParsedFlight>()
        val dates = extractDates(text)
        val times = extractTimes(text)
        val gates = extractValues(text, GATE_PATTERN)
        val terminals = extractValues(text, TERMINAL_PATTERN)
        
        // Try to extract routes first
        val routes = mutableListOf<Pair<String, String>>()
        for (pattern in ROUTE_PATTERNS) {
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                val dep = matcher.group(1)?.uppercase()
                val arr = matcher.group(2)?.uppercase()
                if (dep != null && arr != null && isValidAirportCode(dep) && isValidAirportCode(arr)) {
                    routes.add(Pair(dep, arr))
                }
            }
        }
        
        // Extract flight numbers
        val flightNumbers = mutableListOf<Pair<String, String>>()
        for (pattern in FLIGHT_NUMBER_PATTERNS) {
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                val airline = matcher.group(1)?.uppercase()
                val number = matcher.group(2)
                if (airline != null && number != null) {
                    flightNumbers.add(Pair(airline, number))
                }
            }
        }
        
        // Try to match routes with flight numbers
        if (routes.isNotEmpty()) {
            for ((index, route) in routes.withIndex()) {
                val flightNum = flightNumbers.getOrNull(index)
                val airline = flightNum?.first?.let { AIRLINE_NAMES[it] ?: it }
                val fullFlightNumber = flightNum?.let { "${it.first}${it.second}" }
                val departureTime = combineDateTime(
                    dates.getOrNull(index) ?: dates.firstOrNull(),
                    times.getOrNull(index * 2)
                )
                var arrivalTime = combineDateTime(
                    dates.getOrNull(index) ?: dates.firstOrNull(),
                    times.getOrNull(index * 2 + 1)
                )
                if (arrivalTime != null && departureTime != null && arrivalTime < departureTime) {
                    arrivalTime += 24 * 60 * 60 * 1000L
                }
                
                flights.add(
                    ParsedFlight(
                        departureAirportCode = route.first,
                        arrivalAirportCode = route.second,
                        departureTimeMillis = departureTime,
                        arrivalTimeMillis = arrivalTime,
                        flightNumber = fullFlightNumber,
                        airline = airline,
                        bookingReference = null,
                        passengerName = null,
                        seatNumber = null,
                        aircraftType = null,
                        departureGate = gates.getOrNull(index),
                        departureTerminal = terminals.getOrNull(index)
                    )
                )
            }
        }
        
        // If no routes found, try to find consecutive airport codes
        if (flights.isEmpty()) {
            val airportCodes = mutableListOf<String>()
            val matcher = AIRPORT_CODE_PATTERN.matcher(text)
            while (matcher.find()) {
                val code = matcher.group(1)
                if (code != null && isValidAirportCode(code)) {
                    airportCodes.add(code)
                }
            }
            
            // Pair consecutive codes as routes
            for (i in 0 until airportCodes.size - 1 step 2) {
                if (airportCodes[i] != airportCodes[i + 1]) {
                    val flightIndex = i / 2
                    val departureTime = combineDateTime(
                        dates.getOrNull(flightIndex) ?: dates.firstOrNull(),
                        times.getOrNull(flightIndex * 2)
                    )
                    var arrivalTime = combineDateTime(
                        dates.getOrNull(flightIndex) ?: dates.firstOrNull(),
                        times.getOrNull(flightIndex * 2 + 1)
                    )
                    if (arrivalTime != null && departureTime != null && arrivalTime < departureTime) {
                        arrivalTime += 24 * 60 * 60 * 1000L
                    }
                    flights.add(
                        ParsedFlight(
                            departureAirportCode = airportCodes[i],
                            arrivalAirportCode = airportCodes[i + 1],
                            departureTimeMillis = departureTime,
                            arrivalTimeMillis = arrivalTime,
                            flightNumber = flightNumbers.getOrNull(i / 2)?.let { "${it.first}${it.second}" },
                            airline = null,
                            bookingReference = null,
                            passengerName = null,
                            seatNumber = null,
                            aircraftType = null,
                            departureGate = gates.getOrNull(i / 2),
                            departureTerminal = terminals.getOrNull(i / 2)
                        )
                    )
                }
            }
        }
        
        return flights
    }

    private fun extractValues(text: String, pattern: Pattern): List<String> {
        val values = mutableListOf<Pair<Int, String>>()
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            matcher.group(1)?.let { values.add(matcher.start() to it.uppercase()) }
        }
        return values.sortedBy { it.first }.map { it.second }
    }

    private fun extractDates(text: String): List<Long> {
        val candidates = mutableListOf<Pair<Int, String>>()
        DATE_TEXT_PATTERNS.forEach { pattern ->
            val matcher = pattern.matcher(text)
            while (matcher.find()) candidates.add(matcher.start() to matcher.group())
        }
        return candidates.sortedBy { it.first }.mapNotNull { (_, raw) ->
            DATE_FORMATS.firstNotNullOfOrNull { format ->
                runCatching {
                    format.isLenient = false
                    format.parse(raw)?.time
                }.getOrNull()
            }
        }
    }

    private fun extractTimes(text: String): List<Pair<Int, Int>> {
        val values = mutableListOf<Pair<Int, Pair<Int, Int>>>()
        val matcher = TIME_PATTERN.matcher(text)
        while (matcher.find()) {
            parseTime(matcher.group())?.let { values.add(matcher.start() to it) }
        }
        return values.sortedBy { it.first }.map { it.second }
    }

    private fun combineDateTime(dateMillis: Long?, time: Pair<Int, Int>?): Long? {
        if (dateMillis == null) return null
        if (time == null) return dateMillis
        return Calendar.getInstance().apply {
            this.timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, time.first)
            set(Calendar.MINUTE, time.second)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    
    private fun detectAirline(text: String): String? {
        // Check for airline names in text
        for ((code, name) in AIRLINE_NAMES) {
            if (text.contains(name, ignoreCase = true)) {
                return name
            }
        }
        
        // Check for airline codes
        for ((code, name) in AIRLINE_NAMES) {
            val pattern = Pattern.compile("\\b$code\\d{1,4}\\b", Pattern.CASE_INSENSITIVE)
            if (pattern.matcher(text).find()) {
                return name
            }
        }
        
        return null
    }
    
    private fun isValidAirportCode(code: String): Boolean {
        // Common non-airport 3-letter codes to exclude
        val excluded = setOf(
            "THE", "AND", "FOR", "ARE", "BUT", "NOT", "YOU", "ALL", "CAN",
            "HER", "WAS", "ONE", "OUR", "OUT", "DAY", "GET", "HAS", "HIM",
            "HIS", "HOW", "ITS", "MAY", "NEW", "NOW", "OLD", "SEE", "WAY",
            "WHO", "BOY", "DID", "OWN", "SAY", "SHE", "TOO", "USE", "VIA",
            "FLY", "AIR", "GMT", "UTC", "PDF", "URL", "WWW", "COM", "ORG"
        )
        return code.length == 3 && code.all { it.isLetter() } && code !in excluded
    }
    
    // Parse a specific date string in context
    fun parseDateInContext(text: String, nearbyText: String): Long? {
        for (format in DATE_FORMATS) {
            try {
                val date = format.parse(nearbyText)
                if (date != null) {
                    return date.time
                }
            } catch (e: Exception) {
                // Try next format
            }
        }
        return null
    }
    
    // Parse time from text (returns hour and minute)
    fun parseTime(text: String): Pair<Int, Int>? {
        val matcher = TIME_PATTERN.matcher(text)
        if (matcher.find()) {
            var hour = matcher.group(1)?.toIntOrNull() ?: return null
            val minute = matcher.group(2)?.toIntOrNull() ?: return null
            val amPm = matcher.group(3)
            
            if (amPm != null) {
                if (amPm.uppercase() == "PM" && hour < 12) hour += 12
                if (amPm.uppercase() == "AM" && hour == 12) hour = 0
            }
            
            if (hour in 0..23 && minute in 0..59) {
                return Pair(hour, minute)
            }
        }
        return null
    }
}
