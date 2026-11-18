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
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.roamatlas.R
import com.roamatlas.data.airport.Airport
import com.roamatlas.data.airport.AirportRepository
import com.roamatlas.data.local.dao.FlightDao
import com.roamatlas.data.local.entity.FlightEntity
import com.roamatlas.data.local.entity.TransportMode
import com.roamatlas.data.local.entity.TravelLegStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Background service that monitors upcoming flights and user location.
 *
 * Waiting uses **departure airport timezone** (via [FlightScheduleTimes]), not
 * the phone zone. GPS confirms whether you are at that airport (SIN now, CDG later).
 */
@AndroidEntryPoint
class FlightReminderService : Service() {

    @Inject
    lateinit var flightDao: FlightDao

    @Inject
    lateinit var reminderManager: FlightReminderManager

    @Inject
    lateinit var airportRepository: AirportRepository

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

        const val CHECK_INTERVAL_MS = 15 * 60 * 1000L
        const val AIRPORT_PROXIMITY_KM = 50.0

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
            val settings = reminderManager.settings.first()
            if (!settings.isEnabled) {
                stopSelf()
                return
            }

            val now = System.currentTimeMillis()
            // Wide window in raw millis; true airport-TZ filter happens below.
            val windowStart = now - TimeUnit.HOURS.toMillis(36)
            val windowEnd = now + TimeUnit.HOURS.toMillis(36)
            val nearbyFlights = flightDao.getFlightsBetween(windowStart, windowEnd).first()
                .filterNot { TransportMode.isHotel(it.transportMode) }
                .filterNot {
                    val status = TravelLegStatus.normalize(it.travelLegStatus)
                    status == TravelLegStatus.ARRIVED ||
                        status == TravelLegStatus.SKIPPED ||
                        status == TravelLegStatus.MISSED
                }

            val codes = nearbyFlights.flatMap {
                listOf(it.departureAirportCode, it.arrivalAirportCode)
            }
            val airports = airportRepository.getByIataCodes(codes)

            val withDeparture = nearbyFlights.map { flight ->
                val depAirport = airports[flight.departureAirportCode.uppercase(Locale.US)]
                val arrAirport = airports[flight.arrivalAirportCode.uppercase(Locale.US)]
                val depInstant = FlightScheduleTimes.departureInstantMillis(
                    flight,
                    depAirport,
                    arrAirport
                )
                Triple(flight, depInstant, depAirport)
            }

            val upcoming = withDeparture
                .filter { (_, depInstant, _) -> depInstant >= now }
                .minByOrNull { it.second }
                ?: return

            val previous = withDeparture
                .filter { (flight, depInstant, _) ->
                    val arrAirport = airports[flight.arrivalAirportCode.uppercase(Locale.US)]
                    val arrInstant = FlightScheduleTimes.arrivalInstantMillis(
                        flight,
                        airports[flight.departureAirportCode.uppercase(Locale.US)],
                        arrAirport
                    )
                    arrInstant <= now && depInstant < upcoming.second
                }
                .maxByOrNull { (flight, _, _) ->
                    FlightScheduleTimes.arrivalInstantMillis(
                        flight,
                        airports[flight.departureAirportCode.uppercase(Locale.US)],
                        airports[flight.arrivalAirportCode.uppercase(Locale.US)]
                    )
                }?.first

            val currentLocation = getCurrentLocation() ?: return
            analyzeFlightStatus(
                flight = upcoming.first,
                departureInstant = upcoming.second,
                departureAirport = upcoming.third,
                previousFlight = previous,
                airports = airports,
                currentLocation = currentLocation,
                now = now,
                settings = settings
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun getCurrentLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return try {
            val cancellationToken = CancellationTokenSource()
            val locationTask = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationToken.token
            )

            var waited = 0
            while (!locationTask.isComplete && waited < 10000) {
                delay(100)
                waited += 100
            }

            if (locationTask.isSuccessful) locationTask.result else null
        } catch (_: Exception) {
            null
        }
    }

    private fun analyzeFlightStatus(
        flight: FlightEntity,
        departureInstant: Long,
        departureAirport: Airport?,
        previousFlight: FlightEntity?,
        airports: Map<String, Airport>,
        currentLocation: Location,
        now: Long,
        settings: FlightReminderSettings
    ) {
        val hoursUntilFlight = TimeUnit.MILLISECONDS.toHours(departureInstant - now)
        val minutesUntilFlight = TimeUnit.MILLISECONDS.toMinutes(departureInstant - now)

        val airportCoords = airportCoordinates(departureAirport, flight.departureAirportCode)
            ?: return

        val distanceToAirport = FlightScheduleTimes.haversineKm(
            currentLocation.latitude,
            currentLocation.longitude,
            airportCoords.first,
            airportCoords.second
        ) ?: return

        val estimatedTravelMinutes = maxOf(15L, (distanceToAirport / 40.0 * 60).toLong())
        val requiredDepartureMinutes =
            estimatedTravelMinutes + (settings.hoursBeforeDeparture * 60)
        val shouldLeaveBy =
            departureInstant - TimeUnit.MINUTES.toMillis(requiredDepartureMinutes)
        val minutesUntilShouldLeave = TimeUnit.MILLISECONDS.toMinutes(shouldLeaveBy - now)

        val isNearAirport = distanceToAirport <= AIRPORT_PROXIMITY_KM
        val flightTime = FlightScheduleTimes.formatAirportLocal(
            departureInstant,
            departureAirport?.timeZoneId,
            pattern = "HH:mm"
        )
        val route = "${flight.departureAirportCode} → ${flight.arrivalAirportCode}"
        val isTransit = previousFlight != null &&
            previousFlight.arrivalAirportCode.equals(flight.departureAirportCode, ignoreCase = true) &&
            FlightScheduleTimes.connectionMinutes(previousFlight, flight, airports) in 0..(24 * 60)
        val gate = noteValue(flight.notes, "Departure gate")
        val automaticGate = noteValue(flight.notes, "Automatic departure gate")
        val gateConflict = !gate.isNullOrBlank() &&
            !automaticGate.isNullOrBlank() &&
            !gate.equals(automaticGate, ignoreCase = true)
        val gateText = when {
            gateConflict ->
                " You entered Gate $gate, but automatic information says Gate $automaticGate. " +
                    "Check the nearest airport board again."
            gate != null -> " Gate $gate."
            automaticGate != null -> " Gate $automaticGate."
            else -> " Gate not available; check the airline or airport display."
        }
        val atAirportConfirm = if (isNearAirport) {
            " GPS confirms you are at ${flight.departureAirportCode}."
        } else {
            ""
        }

        when {
            isNearAirport && hoursUntilFlight <= settings.advanceWarningHours &&
                settings.notifyWhenAtAirport -> {
                sendNotification(
                    id = NOTIFICATION_ID_REMINDER,
                    title = if (isTransit) {
                        "✈️ Next flight${flight.flightNumber?.let { " $it" }.orEmpty()}"
                    } else {
                        "✈️ You're at the airport"
                    },
                    message = buildString {
                        append("Flight $route departs at $flightTime local.")
                        if (isTransit) {
                            append(" Transit time remaining: ${formatDuration(minutesUntilFlight)}.")
                        }
                        append(atAirportConfirm)
                        append(gateText)
                    },
                    priority = NotificationCompat.PRIORITY_DEFAULT,
                    airportCoords = airportCoords,
                    flight = flight
                )
            }

            hoursUntilFlight <= settings.urgentWarningHours && !isNearAirport &&
                settings.notifyPossibleMissed -> {
                val distanceText = String.format(Locale.getDefault(), "%.1f km", distanceToAirport)
                sendNotification(
                    id = NOTIFICATION_ID_WARNING,
                    title = "⚠️ URGENT: Flight in ${hoursUntilFlight}h!",
                    message = "You're $distanceText from ${flight.departureAirportCode}. " +
                        "Flight $route is scheduled at $flightTime local. " +
                        "Check live traffic and airline status.",
                    priority = NotificationCompat.PRIORITY_HIGH,
                    airportCoords = airportCoords,
                    flight = flight
                )
            }

            hoursUntilFlight in (settings.urgentWarningHours + 1)..settings.advanceWarningHours &&
                !isNearAirport -> {
                if (minutesUntilShouldLeave <= 60) {
                    val leaveTime = FlightScheduleTimes.formatAirportLocal(
                        shouldLeaveBy,
                        departureAirport?.timeZoneId,
                        pattern = "HH:mm"
                    )
                    sendNotification(
                        id = NOTIFICATION_ID_REMINDER,
                        title = "🚗 Time to head to airport!",
                        message = "Leave by $leaveTime to catch flight $route at $flightTime local. " +
                            "Distance estimate: ${estimatedTravelMinutes}min + " +
                            "${settings.hoursBeforeDeparture}h airport time. Check live traffic.",
                        priority = NotificationCompat.PRIORITY_HIGH,
                        airportCoords = airportCoords,
                        flight = flight
                    )
                } else if (minutesUntilShouldLeave <= 120) {
                    val leaveTime = FlightScheduleTimes.formatAirportLocal(
                        shouldLeaveBy,
                        departureAirport?.timeZoneId,
                        pattern = "HH:mm"
                    )
                    sendNotification(
                        id = NOTIFICATION_ID_REMINDER,
                        title = "📍 Upcoming flight reminder",
                        message = "Flight $route at $flightTime local. " +
                            "Suggested departure: $leaveTime " +
                            "(${estimatedTravelMinutes}min distance estimate + " +
                            "${settings.hoursBeforeDeparture}h buffer). Check live traffic.",
                        priority = NotificationCompat.PRIORITY_DEFAULT,
                        airportCoords = airportCoords,
                        flight = flight
                    )
                }
            }
        }
    }

    private fun airportCoordinates(
        airport: Airport?,
        code: String
    ): Pair<Double, Double>? {
        if (airport != null) return airport.latitude to airport.longitude
        return FALLBACK_AIRPORT_COORDINATES[code.uppercase(Locale.US)]
    }

    private fun noteValue(notes: String?, label: String): String? =
        notes?.lineSequence()
            ?.firstOrNull { it.startsWith("$label:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun formatDuration(totalMinutes: Long): String {
        val hours = totalMinutes.coerceAtLeast(0) / 60
        val minutes = totalMinutes.coerceAtLeast(0) % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun sendNotification(
        id: Int,
        title: String,
        message: String,
        priority: Int,
        airportCoords: Pair<Double, Double>? = null,
        flight: FlightEntity? = null
    ) {
        val notificationKey = "${flight?.id}:$id:$title"
        val sent = getSharedPreferences("flight_notification_history", MODE_PRIVATE)
        if (sent.getBoolean(notificationKey, false)) return

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(priority)
            .setAutoCancel(true)

        if (airportCoords != null) {
            val mapsIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("google.navigation:q=${airportCoords.first},${airportCoords.second}&mode=d")
            )
            val mapsPendingIntent = PendingIntent.getActivity(
                this,
                id + 100,
                mapsIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, "Check live traffic", mapsPendingIntent)
        }
        flight?.flightNumber?.takeIf { it.isNotBlank() }?.let { number ->
            val statusIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=${Uri.encode("$number flight status")}")
            )
            val statusPendingIntent = PendingIntent.getActivity(
                this,
                id + 200,
                statusIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, "Check airline status", statusPendingIntent)
        }

        notificationManager.notify(id, builder.build())
        sent.edit().putBoolean(notificationKey, true).apply()
    }

    private val FALLBACK_AIRPORT_COORDINATES = mapOf(
        "SIN" to Pair(1.3644, 103.9915),
        "CGK" to Pair(-6.1256, 106.6558),
        "CDG" to Pair(49.0097, 2.5479),
        "GIG" to Pair(-22.809999, -43.250557),
        "DPS" to Pair(-8.7467, 115.1672),
        "KUL" to Pair(2.7456, 101.7072),
        "BKK" to Pair(13.6900, 100.7501),
        "HKG" to Pair(22.3080, 113.9185),
        "NRT" to Pair(35.7720, 140.3929),
        "HND" to Pair(35.5494, 139.7798),
        "ICN" to Pair(37.4602, 126.4407),
        "LHR" to Pair(51.4700, -0.4543),
        "FRA" to Pair(50.0379, 8.5622),
        "AMS" to Pair(52.3105, 4.7683),
        "JFK" to Pair(40.6413, -73.7781),
        "LAX" to Pair(33.9416, -118.4085),
        "SYD" to Pair(-33.9399, 151.1753),
        "DXB" to Pair(25.2532, 55.3657)
    )
}
