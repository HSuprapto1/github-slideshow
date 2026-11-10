package com.roamatlas.data.flight

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.roamatlas.R
import com.roamatlas.data.local.dao.FlightDao
import com.roamatlas.data.local.entity.FlightEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Background service that monitors upcoming flights and user location.
 * 
 * Features:
 * - Detects if user is not near departure airport before flight
 * - Sends reminders based on traffic and travel time
 * - Warns about potentially cancelled/missed flights
 * - Recommends departure time (2 hours before flight + travel time)
 */
@AndroidEntryPoint
class FlightReminderService : Service() {
    
    @Inject
    lateinit var flightDao: FlightDao
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var notificationManager: NotificationManager
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var isRunning = false
    
    companion object {
        const val CHANNEL_ID = "flight_reminders"
        const val CHANNEL_NAME = "Flight Reminders"
        const val NOTIFICATION_ID_SERVICE = 2001
        const val NOTIFICATION_ID_REMINDER = 2002
        const val NOTIFICATION_ID_WARNING = 2003
        
        // Check interval: every 15 minutes
        const val CHECK_INTERVAL_MS = 15 * 60 * 1000L
        
        // Airport proximity threshold: 50km (considered "at airport area")
        const val AIRPORT_PROXIMITY_KM = 50.0
        
        // Recommended hours before departure to be at airport
        const val HOURS_BEFORE_DEPARTURE = 2
        
        // Alert thresholds
        const val HOURS_ADVANCE_WARNING = 6  // Start warning 6 hours before
        const val HOURS_URGENT_WARNING = 3   // Urgent warning 3 hours before
        
        fun start(context: Context) {
            val intent = Intent(context, FlightReminderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            context.stopService(Intent(context, FlightReminderService::class.java))
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID_SERVICE, createServiceNotification())
        
        if (!isRunning) {
            isRunning = true
            startFlightMonitoring()
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for upcoming flight reminders"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createServiceNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Flight Monitor Active")
        .setContentText("Monitoring your upcoming flights")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()
    
    private fun startFlightMonitoring() {
        serviceScope.launch {
            while (isRunning) {
                checkUpcomingFlights()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }
    
    private suspend fun checkUpcomingFlights() {
        try {
            val now = System.currentTimeMillis()
            val next24Hours = now + TimeUnit.HOURS.toMillis(24)
            
            // Get flights in the next 24 hours
            val upcomingFlights = flightDao.getFlightsBetween(now, next24Hours).first()
            
            if (upcomingFlights.isEmpty()) return
            
            // Get current location
            val currentLocation = getCurrentLocation() ?: return
            
            for (flight in upcomingFlights) {
                analyzeFlightStatus(flight, currentLocation, now)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private suspend fun getCurrentLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        
        return try {
            val cancellationToken = CancellationTokenSource()
            val locationTask = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationToken.token
            )
            
            // Wait for location with timeout
            var result: Location? = null
            var waited = 0
            while (!locationTask.isComplete && waited < 10000) {
                delay(100)
                waited += 100
            }
            
            if (locationTask.isSuccessful) {
                result = locationTask.result
            }
            result
        } catch (e: Exception) {
            null
        }
    }
    
    private fun analyzeFlightStatus(flight: FlightEntity, currentLocation: Location, now: Long) {
        val departureTime = flight.departureTimeMillis
        val hoursUntilFlight = TimeUnit.MILLISECONDS.toHours(departureTime - now)
        val minutesUntilFlight = TimeUnit.MILLISECONDS.toMinutes(departureTime - now)
        
        // Get departure airport coordinates
        val airportCoords = getAirportCoordinates(flight.departureAirportCode)
        if (airportCoords == null) {
            // Unknown airport, can't calculate distance
            return
        }
        
        // Calculate distance to airport
        val distanceToAirport = calculateDistance(
            currentLocation.latitude, currentLocation.longitude,
            airportCoords.first, airportCoords.second
        )
        
        // Estimate travel time (rough estimate: 30km/h average with traffic)
        val estimatedTravelMinutes = (distanceToAirport / 30.0 * 60).toLong()
        
        // Calculate when user should leave (travel time + 2 hours buffer)
        val requiredDepartureMinutes = estimatedTravelMinutes + (HOURS_BEFORE_DEPARTURE * 60)
        val shouldLeaveBy = departureTime - TimeUnit.MINUTES.toMillis(requiredDepartureMinutes)
        val minutesUntilShouldLeave = TimeUnit.MILLISECONDS.toMinutes(shouldLeaveBy - now)
        
        val isNearAirport = distanceToAirport <= AIRPORT_PROXIMITY_KM
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val flightTime = timeFormatter.format(Date(departureTime))
        val route = "${flight.departureAirportCode} → ${flight.arrivalAirportCode}"
        
        when {
            // User is already near airport - all good
            isNearAirport && hoursUntilFlight <= HOURS_ADVANCE_WARNING -> {
                sendNotification(
                    id = NOTIFICATION_ID_REMINDER,
                    title = "✈️ You're at the airport!",
                    message = "Flight $route departs at $flightTime. Have a safe journey!",
                    priority = NotificationCompat.PRIORITY_DEFAULT
                )
            }
            
            // URGENT: Less than 3 hours, not near airport, might miss flight
            hoursUntilFlight <= HOURS_URGENT_WARNING && !isNearAirport -> {
                val distanceText = String.format("%.1f km", distanceToAirport)
                sendNotification(
                    id = NOTIFICATION_ID_WARNING,
                    title = "⚠️ URGENT: Flight in ${hoursUntilFlight}h!",
                    message = "You're $distanceText from ${flight.departureAirportCode}! " +
                            "Flight $route at $flightTime. You may miss your flight!",
                    priority = NotificationCompat.PRIORITY_HIGH
                )
            }
            
            // WARNING: 3-6 hours before, should leave soon
            hoursUntilFlight in (HOURS_URGENT_WARNING + 1)..HOURS_ADVANCE_WARNING && !isNearAirport -> {
                if (minutesUntilShouldLeave <= 60) {
                    // Should leave within the hour
                    val leaveTime = timeFormatter.format(Date(shouldLeaveBy))
                    sendNotification(
                        id = NOTIFICATION_ID_REMINDER,
                        title = "🚗 Time to head to airport!",
                        message = "Leave by $leaveTime to catch flight $route at $flightTime. " +
                                "Estimated travel: ${estimatedTravelMinutes}min + 2h airport time.",
                        priority = NotificationCompat.PRIORITY_HIGH
                    )
                } else if (minutesUntilShouldLeave <= 120) {
                    // Should leave within 2 hours
                    val leaveTime = timeFormatter.format(Date(shouldLeaveBy))
                    sendNotification(
                        id = NOTIFICATION_ID_REMINDER,
                        title = "📍 Upcoming flight reminder",
                        message = "Flight $route at $flightTime. " +
                                "Suggested departure: $leaveTime (${estimatedTravelMinutes}min travel + 2h buffer).",
                        priority = NotificationCompat.PRIORITY_DEFAULT
                    )
                }
            }
            
            // Flight time passed but user wasn't at airport - likely cancelled/missed
            now > departureTime && !isNearAirport -> {
                sendNotification(
                    id = NOTIFICATION_ID_WARNING,
                    title = "❓ Flight status check",
                    message = "Flight $route was scheduled for $flightTime. " +
                            "You weren't detected near ${flight.departureAirportCode}. " +
                            "Was this flight cancelled or rescheduled?",
                    priority = NotificationCompat.PRIORITY_DEFAULT
                )
            }
        }
    }
    
    private fun sendNotification(id: Int, title: String, message: String, priority: Int) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(priority)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(id, notification)
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * acos(kotlin.math.sqrt(1 - a).coerceIn(0.0, 1.0))
        return earthRadiusKm * c
    }
    
    // Airport coordinates database
    private fun getAirportCoordinates(code: String): Pair<Double, Double>? {
        return AIRPORT_COORDINATES[code.uppercase()]
    }
    
    private val AIRPORT_COORDINATES = mapOf(
        // Southeast Asia
        "SIN" to Pair(1.3644, 103.9915),    // Singapore Changi
        "CGK" to Pair(-6.1256, 106.6558),   // Jakarta Soekarno-Hatta
        "DPS" to Pair(-8.7467, 115.1672),   // Bali Ngurah Rai
        "SUB" to Pair(-7.3798, 112.7868),   // Surabaya Juanda
        "JOG" to Pair(-7.7882, 110.4317),   // Yogyakarta Adisucipto
        "UPG" to Pair(-5.0617, 119.5540),   // Makassar Sultan Hasanuddin
        "KNO" to Pair(3.6422, 98.8851),     // Medan Kualanamu
        "BPN" to Pair(-1.2683, 116.8944),   // Balikpapan
        "PLM" to Pair(-2.8983, 104.6997),   // Palembang
        "KUL" to Pair(2.7456, 101.7072),    // Kuala Lumpur KLIA
        "PEN" to Pair(5.2972, 100.2769),    // Penang
        "BKK" to Pair(13.6900, 100.7501),   // Bangkok Suvarnabhumi
        "DMK" to Pair(13.9126, 100.6068),   // Bangkok Don Mueang
        "HKT" to Pair(8.1132, 98.3169),     // Phuket
        "CNX" to Pair(18.7669, 98.9628),    // Chiang Mai
        "SGN" to Pair(10.8188, 106.6520),   // Ho Chi Minh City
        "HAN" to Pair(21.2212, 105.8070),   // Hanoi
        "MNL" to Pair(14.5086, 121.0197),   // Manila NAIA
        "CEB" to Pair(10.3075, 123.9794),   // Cebu
        
        // East Asia
        "HKG" to Pair(22.3080, 113.9185),   // Hong Kong
        "TPE" to Pair(25.0797, 121.2342),   // Taipei Taoyuan
        "NRT" to Pair(35.7720, 140.3929),   // Tokyo Narita
        "HND" to Pair(35.5494, 139.7798),   // Tokyo Haneda
        "KIX" to Pair(34.4347, 135.2441),   // Osaka Kansai
        "ICN" to Pair(37.4602, 126.4407),   // Seoul Incheon
        "GMP" to Pair(37.5583, 126.7906),   // Seoul Gimpo
        "PVG" to Pair(31.1443, 121.8083),   // Shanghai Pudong
        "PEK" to Pair(40.0799, 116.6031),   // Beijing Capital
        "PKX" to Pair(39.5098, 116.4105),   // Beijing Daxing
        "CAN" to Pair(23.3924, 113.2988),   // Guangzhou
        "SZX" to Pair(22.6393, 113.8106),   // Shenzhen
        
        // South Asia
        "DEL" to Pair(28.5562, 77.1000),    // Delhi
        "BOM" to Pair(19.0896, 72.8656),    // Mumbai
        "BLR" to Pair(13.1986, 77.7066),    // Bangalore
        "MAA" to Pair(12.9941, 80.1709),    // Chennai
        "CCU" to Pair(22.6547, 88.4467),    // Kolkata
        "CMB" to Pair(7.1808, 79.8841),     // Colombo
        
        // Middle East
        "DXB" to Pair(25.2532, 55.3657),    // Dubai
        "AUH" to Pair(24.4330, 54.6511),    // Abu Dhabi
        "DOH" to Pair(25.2731, 51.6081),    // Doha
        "JED" to Pair(21.6796, 39.1565),    // Jeddah
        "RUH" to Pair(24.9578, 46.6989),    // Riyadh
        "IST" to Pair(41.2753, 28.7519),    // Istanbul
        "TLV" to Pair(32.0055, 34.8854),    // Tel Aviv
        
        // Europe
        "LHR" to Pair(51.4700, -0.4543),    // London Heathrow
        "LGW" to Pair(51.1537, -0.1821),    // London Gatwick
        "STN" to Pair(51.8860, 0.2389),     // London Stansted
        "CDG" to Pair(49.0097, 2.5479),     // Paris CDG
        "ORY" to Pair(48.7262, 2.3652),     // Paris Orly
        "FRA" to Pair(50.0379, 8.5622),     // Frankfurt
        "MUC" to Pair(48.3537, 11.7750),    // Munich
        "AMS" to Pair(52.3105, 4.7683),     // Amsterdam
        "MAD" to Pair(40.4983, -3.5676),    // Madrid
        "BCN" to Pair(41.2974, 2.0833),     // Barcelona
        "FCO" to Pair(41.8003, 12.2389),    // Rome Fiumicino
        "MXP" to Pair(45.6306, 8.7281),     // Milan Malpensa
        "ZRH" to Pair(47.4647, 8.5492),     // Zurich
        "VIE" to Pair(48.1103, 16.5697),    // Vienna
        "CPH" to Pair(55.6180, 12.6508),    // Copenhagen
        "ARN" to Pair(59.6519, 17.9186),    // Stockholm
        "OSL" to Pair(60.1939, 11.1004),    // Oslo
        "HEL" to Pair(60.3172, 24.9633),    // Helsinki
        
        // Oceania
        "SYD" to Pair(-33.9399, 151.1753),  // Sydney
        "MEL" to Pair(-37.6690, 144.8410),  // Melbourne
        "BNE" to Pair(-27.3842, 153.1175),  // Brisbane
        "PER" to Pair(-31.9403, 115.9670),  // Perth
        "AKL" to Pair(-37.0082, 174.7850),  // Auckland
        "WLG" to Pair(-41.3272, 174.8050),  // Wellington
        
        // Americas
        "JFK" to Pair(40.6413, -73.7781),   // New York JFK
        "EWR" to Pair(40.6895, -74.1745),   // Newark
        "LGA" to Pair(40.7769, -73.8740),   // New York LaGuardia
        "LAX" to Pair(33.9416, -118.4085),  // Los Angeles
        "SFO" to Pair(37.6213, -122.3790),  // San Francisco
        "ORD" to Pair(41.9742, -87.9073),   // Chicago O'Hare
        "DFW" to Pair(32.8998, -97.0403),   // Dallas/Fort Worth
        "MIA" to Pair(25.7959, -80.2870),   // Miami
        "ATL" to Pair(33.6407, -84.4277),   // Atlanta
        "SEA" to Pair(47.4502, -122.3088),  // Seattle
        "BOS" to Pair(42.3656, -71.0096),   // Boston
        "IAD" to Pair(38.9531, -77.4565),   // Washington Dulles
        "YYZ" to Pair(43.6777, -79.6248),   // Toronto
        "YVR" to Pair(49.1967, -123.1815),  // Vancouver
        "MEX" to Pair(19.4363, -99.0721),   // Mexico City
        "GRU" to Pair(-23.4356, -46.4731),  // Sao Paulo
        "EZE" to Pair(-34.8222, -58.5358),  // Buenos Aires
        "SCL" to Pair(-33.3930, -70.7858),  // Santiago
        "LIM" to Pair(-12.0219, -77.1143),  // Lima
        "BOG" to Pair(4.7016, -74.1469),    // Bogota
        
        // Africa
        "JNB" to Pair(-26.1392, 28.2460),   // Johannesburg
        "CPT" to Pair(-33.9715, 18.6021),   // Cape Town
        "CAI" to Pair(30.1219, 31.4056),    // Cairo
        "NBO" to Pair(-1.3192, 36.9278),    // Nairobi
        "ADD" to Pair(8.9779, 38.7993),     // Addis Ababa
        "CMN" to Pair(33.3675, -7.5900),    // Casablanca
        "LOS" to Pair(6.5774, 3.3212),      // Lagos
    )
}
