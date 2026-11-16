package com.roamatlas.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.roamatlas.data.local.RoamAtlasDatabase
import com.roamatlas.data.local.entity.DayTrackerEntity
import com.roamatlas.data.local.entity.FlightEntity
import com.roamatlas.data.local.entity.LocationEntity
import com.roamatlas.data.local.entity.TransportMode
import com.roamatlas.data.local.entity.TripEntity
import com.roamatlas.data.local.entity.UserSettingsEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

data class BackupResult(
    val locations: Int,
    val flights: Int,
    val trackers: Int,
    val trips: Int,
    val settings: Int
) {
    val summary: String
        get() = "$locations locations, $flights flights, $trackers trackers and $trips trips"
}

data class BackupProgress(
    val message: String,
    /** 0–100 when known; null means indeterminate. */
    val percent: Int? = null
)

@Singleton
class EncryptedBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: RoamAtlasDatabase
) {
    private val gson = Gson()

    suspend fun export(
        uri: Uri,
        password: CharArray,
        onProgress: (BackupProgress) -> Unit = {}
    ): BackupResult = withContext(Dispatchers.IO) {
        require(password.size >= MIN_PASSWORD_LENGTH) {
            "Use at least $MIN_PASSWORD_LENGTH characters"
        }
        onProgress(BackupProgress("Encrypting backup…", 2))
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))

        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("Could not create backup file")
        DataOutputStream(BufferedOutputStream(output)).use { raw ->
            raw.write(MAGIC)
            raw.write(salt)
            raw.write(iv)
            raw.flush()
            CipherOutputStream(raw, cipher).use { encrypted ->
                JsonWriter(OutputStreamWriter(encrypted, Charsets.UTF_8)).use { writer ->
                    writeBackup(writer, onProgress)
                }
            }
        }
    }

    suspend fun restore(
        uri: Uri,
        password: CharArray,
        onProgress: (BackupProgress) -> Unit = {}
    ): BackupResult = withContext(Dispatchers.IO) {
        require(password.isNotEmpty()) { "Enter the backup password" }
        onProgress(BackupProgress("Opening backup…", 1))
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Could not open backup file")
        restoreFromStream(input, password, onProgress)
    }

    suspend fun restore(
        file: File,
        password: CharArray,
        onProgress: (BackupProgress) -> Unit = {}
    ): BackupResult = withContext(Dispatchers.IO) {
        require(password.isNotEmpty()) { "Enter the backup password" }
        onProgress(BackupProgress("Opening backup…", 1))
        FileInputStream(file).use { input ->
            restoreFromStream(input, password, onProgress, encryptedSizeHint = file.length())
        }
    }

    private suspend fun restoreFromStream(
        input: java.io.InputStream,
        password: CharArray,
        onProgress: (BackupProgress) -> Unit,
        encryptedSizeHint: Long = -1L
    ): BackupResult {
        val decryptedTemp = File(context.cacheDir, "backup-plain-${System.currentTimeMillis()}.json")
        try {
            DataInputStream(BufferedInputStream(input)).use { raw ->
                val magic = ByteArray(MAGIC.size)
                raw.readFully(magic)
                require(magic.contentEquals(MAGIC)) { "This is not a RoamAtlas encrypted backup" }
                val salt = ByteArray(SALT_BYTES).also { raw.readFully(it) }
                val iv = ByteArray(IV_BYTES).also { raw.readFully(it) }
                onProgress(BackupProgress("Checking password…", 3))
                val key = deriveKey(password, salt)
                val cipher = Cipher.getInstance(CIPHER)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                try {
                    CipherInputStream(raw, cipher).use { decrypted ->
                        decryptedTemp.outputStream().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            var copied = 0L
                            val total = encryptedSizeHint.takeIf { it > 0 }?.coerceAtLeast(1L)
                            while (true) {
                                val read = decrypted.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                copied += read
                                if (total != null) {
                                    val pct = (5 + (copied * 15L / total)).toInt().coerceIn(5, 20)
                                    onProgress(
                                        BackupProgress(
                                            "Decrypting backup… ${formatCount(copied)} bytes",
                                            pct
                                        )
                                    )
                                } else if (copied % (512 * 1024) < buffer.size) {
                                    onProgress(
                                        BackupProgress(
                                            "Decrypting backup… ${formatCount(copied)} bytes",
                                            null
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    throw IllegalArgumentException(
                        "Could not unlock this backup. Check the password and file.",
                        e
                    )
                }
            }
            onProgress(BackupProgress("Password OK. Importing…", 22))
            FileInputStream(decryptedTemp).use { plain ->
                JsonReader(InputStreamReader(BufferedInputStream(plain), Charsets.UTF_8)).use { reader ->
                    return readBackup(reader, onProgress)
                }
            }
        } finally {
            decryptedTemp.delete()
        }
    }

    private suspend fun writeBackup(
        writer: JsonWriter,
        onProgress: (BackupProgress) -> Unit = {}
    ): BackupResult {
        var locations = 0
        var flights = 0
        val expectedLocations = runCatching {
            database.locationDao().getLocationCount().first()
        }.getOrDefault(0)
        val expectedFlights = runCatching {
            database.flightDao().getFlightCount().first()
        }.getOrDefault(0)

        writer.beginObject()
        writer.name("formatVersion").value(FORMAT_VERSION)
        writer.name("createdAtMillis").value(System.currentTimeMillis())
        writer.name("counts").beginObject()
        writer.name("locations").value(expectedLocations)
        writer.name("flights").value(expectedFlights)
        writer.endObject()

        onProgress(BackupProgress("Exporting locations… 0 / $expectedLocations", 5))
        writer.name("locations").beginArray()
        var lastLocationId = 0L
        while (true) {
            val page = database.locationDao().getPage(lastLocationId, PAGE_SIZE)
            if (page.isEmpty()) break
            page.forEach { gson.toJson(it, LocationEntity::class.java, writer) }
            locations += page.size
            lastLocationId = page.last().id
            val pct = if (expectedLocations > 0) {
                5 + (locations * 70 / expectedLocations).coerceAtMost(70)
            } else {
                40
            }
            onProgress(
                BackupProgress(
                    "Exporting locations… ${formatCount(locations)} / ${formatCount(expectedLocations)}",
                    pct
                )
            )
        }
        writer.endArray()

        onProgress(BackupProgress("Exporting flights…", 78))
        writer.name("flights").beginArray()
        var lastFlightId = 0L
        while (true) {
            val page = database.flightDao().getPage(lastFlightId, PAGE_SIZE)
            if (page.isEmpty()) break
            page.forEach { gson.toJson(it, FlightEntity::class.java, writer) }
            flights += page.size
            lastFlightId = page.last().id
            val pct = if (expectedFlights > 0) {
                78 + (flights * 12 / expectedFlights).coerceAtMost(12)
            } else {
                85
            }
            onProgress(
                BackupProgress(
                    "Exporting flights… ${formatCount(flights)} / ${formatCount(expectedFlights)}",
                    pct
                )
            )
        }
        writer.endArray()

        onProgress(BackupProgress("Exporting trackers, trips and settings…", 92))
        val trackers = database.dayTrackerDao().getAllSnapshot()
        writer.name("trackers")
        gson.toJson(trackers, List::class.java, writer)
        val trips = database.tripDao().getAllSnapshot()
        writer.name("trips")
        gson.toJson(trips, List::class.java, writer)
        val settings = database.userSettingsDao().getAllSettingsSnapshot()
        writer.name("settings")
        gson.toJson(settings, List::class.java, writer)
        writer.endObject()
        onProgress(BackupProgress("Backup file ready", 100))
        return BackupResult(locations, flights, trackers.size, trips.size, settings.size)
    }

    private suspend fun readBackup(
        reader: JsonReader,
        onProgress: (BackupProgress) -> Unit = {}
    ): BackupResult {
        var formatVersion = 0
        var locations = 0
        var flights = 0
        var trackers = 0
        var trips = 0
        var settings = 0
        var expectedLocations = 0
        var expectedFlights = 0
        database.withTransaction {
            onProgress(BackupProgress("Clearing current data…", 24))
            database.locationDao().deleteAll()
            database.flightDao().deleteAll()
            database.dayTrackerDao().deleteAll()
            database.tripDao().deleteAll()
            database.userSettingsDao().deleteAll()

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "formatVersion" -> formatVersion = reader.nextInt()
                    "createdAtMillis" -> reader.nextLong()
                    "counts" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "locations" -> expectedLocations = reader.nextInt()
                                "flights" -> expectedFlights = reader.nextInt()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                    "locations" -> {
                        onProgress(BackupProgress("Restoring locations…", 25))
                        reader.beginArray()
                        val batch = ArrayList<LocationEntity>(PAGE_SIZE)
                        while (reader.hasNext()) {
                            batch.add(sanitizeLocation(gson.fromJson(reader, LocationEntity::class.java)))
                            if (batch.size >= PAGE_SIZE) {
                                locations += database.locationDao().insertAll(batch).count { it != -1L }
                                batch.clear()
                                onProgress(locationProgress(locations, expectedLocations))
                            }
                        }
                        if (batch.isNotEmpty()) {
                            locations += database.locationDao().insertAll(batch).count { it != -1L }
                            onProgress(locationProgress(locations, expectedLocations))
                        }
                        reader.endArray()
                    }
                    "flights" -> {
                        onProgress(BackupProgress("Restoring flights and journeys…", 88))
                        reader.beginArray()
                        val batch = ArrayList<FlightEntity>(PAGE_SIZE)
                        while (reader.hasNext()) {
                            batch.add(sanitizeFlight(gson.fromJson(reader, FlightEntity::class.java)))
                            if (batch.size >= PAGE_SIZE) {
                                flights += database.flightDao().insertAll(batch).size
                                batch.clear()
                                onProgress(flightProgress(flights, expectedFlights))
                            }
                        }
                        if (batch.isNotEmpty()) {
                            flights += database.flightDao().insertAll(batch).size
                            onProgress(flightProgress(flights, expectedFlights))
                        }
                        reader.endArray()
                    }
                    "trackers" -> {
                        onProgress(BackupProgress("Restoring day trackers…", 95))
                        reader.beginArray()
                        val values = mutableListOf<DayTrackerEntity>()
                        while (reader.hasNext()) {
                            values.add(sanitizeTracker(gson.fromJson(reader, DayTrackerEntity::class.java)))
                        }
                        reader.endArray()
                        database.dayTrackerDao().insertAll(values)
                        trackers = values.size
                    }
                    "trips" -> {
                        onProgress(BackupProgress("Restoring journeys…", 97))
                        reader.beginArray()
                        val values = mutableListOf<TripEntity>()
                        while (reader.hasNext()) {
                            values.add(sanitizeTrip(gson.fromJson(reader, TripEntity::class.java)))
                        }
                        reader.endArray()
                        database.tripDao().insertAll(values)
                        trips = values.size
                    }
                    "settings" -> {
                        onProgress(BackupProgress("Restoring settings…", 99))
                        reader.beginArray()
                        val values = mutableListOf<UserSettingsEntity>()
                        while (reader.hasNext()) {
                            values.add(sanitizeSetting(gson.fromJson(reader, UserSettingsEntity::class.java)))
                        }
                        reader.endArray()
                        database.userSettingsDao().setSettings(values)
                        settings = values.size
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            require(formatVersion in 1..FORMAT_VERSION) {
                "Unsupported RoamAtlas backup version $formatVersion"
            }
        }
        onProgress(BackupProgress("Restore finished", 100))
        return BackupResult(locations, flights, trackers, trips, settings)
    }

    private fun locationProgress(done: Int, expected: Int): BackupProgress {
        val pct = if (expected > 0) {
            25 + (done * 60 / expected).coerceAtMost(60)
        } else {
            // Soft climb when older backups have no counts.
            25 + min(60, (kotlin.math.ln((done + 1).toDouble()) * 8).toInt())
        }
        val totalLabel = if (expected > 0) formatCount(expected) else "?"
        return BackupProgress(
            "Restoring locations… ${formatCount(done)} / $totalLabel",
            pct
        )
    }

    private fun flightProgress(done: Int, expected: Int): BackupProgress {
        val pct = if (expected > 0) {
            88 + (done * 6 / expected).coerceAtMost(6)
        } else {
            90
        }
        val totalLabel = if (expected > 0) formatCount(expected) else "?"
        return BackupProgress(
            "Restoring flights… ${formatCount(done)} / $totalLabel",
            pct
        )
    }

    /** Gson can leave Kotlin non-null fields as null — sanitize before Room insert. */
    private fun sanitizeLocation(entity: LocationEntity): LocationEntity = entity.copy(
        source = nonNullString(entity.source, "BACKUP"),
        confidence = nonNullString(entity.confidence, "UNKNOWN")
    )

    private fun sanitizeFlight(entity: FlightEntity): FlightEntity = entity.copy(
        departureAirportCode = nonNullString(entity.departureAirportCode, "UNK"),
        arrivalAirportCode = nonNullString(entity.arrivalAirportCode, "UNK"),
        source = nonNullString(entity.source, "BACKUP"),
        transportMode = nonNullString(entity.transportMode, TransportMode.FLIGHT)
    )

    private fun sanitizeTracker(entity: DayTrackerEntity): DayTrackerEntity = entity.copy(
        name = nonNullString(entity.name, "Tracker"),
        countryCode = nonNullString(entity.countryCode, "XX"),
        countryName = nonNullString(entity.countryName, "Unknown"),
        countingMode = nonNullString(entity.countingMode, "CALENDAR"),
        countingRule = nonNullString(entity.countingRule, "ANY_POINT")
    )

    private fun sanitizeTrip(entity: TripEntity): TripEntity = entity.copy(
        name = nonNullString(entity.name, "Journey")
    )

    private fun sanitizeSetting(entity: UserSettingsEntity): UserSettingsEntity = entity.copy(
        key = nonNullString(entity.key, "unknown"),
        value = nonNullString(entity.value, "")
    )

    private fun nonNullString(value: String?, fallback: String): String =
        (value as String?)?.takeIf { it.isNotBlank() } ?: fallback

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun formatCount(value: Long): String =
        "%,d".format(value)

    private fun formatCount(value: Int): String = formatCount(value.toLong())

    companion object {
        private val MAGIC = byteArrayOf(0x52, 0x41, 0x42, 0x31) // RAB1
        private const val FORMAT_VERSION = 2
        private const val CIPHER = "AES/GCM/NoPadding"
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val KEY_BITS = 256
        private const val PBKDF2_ITERATIONS = 210_000
        private const val MIN_PASSWORD_LENGTH = 12
        private const val PAGE_SIZE = 2_000
    }
}
