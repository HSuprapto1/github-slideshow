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
import com.roamatlas.data.local.entity.TripEntity
import com.roamatlas.data.local.entity.UserSettingsEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
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

@Singleton
class EncryptedBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: RoamAtlasDatabase
) {
    private val gson = Gson()

    suspend fun export(
        uri: Uri,
        password: CharArray,
        onProgress: (String) -> Unit = {}
    ): BackupResult = withContext(Dispatchers.IO) {
        require(password.size >= MIN_PASSWORD_LENGTH) {
            "Use at least $MIN_PASSWORD_LENGTH characters"
        }
        onProgress("Encrypting backup…")
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
        onProgress: (String) -> Unit = {}
    ): BackupResult = withContext(Dispatchers.IO) {
        require(password.isNotEmpty()) { "Enter the backup password" }
        onProgress("Opening backup…")
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Could not open backup file")
        restoreFromStream(input, password, onProgress)
    }

    suspend fun restore(
        file: File,
        password: CharArray,
        onProgress: (String) -> Unit = {}
    ): BackupResult = withContext(Dispatchers.IO) {
        require(password.isNotEmpty()) { "Enter the backup password" }
        onProgress("Opening backup…")
        FileInputStream(file).use { input ->
            restoreFromStream(input, password, onProgress)
        }
    }

    private suspend fun restoreFromStream(
        input: java.io.InputStream,
        password: CharArray,
        onProgress: (String) -> Unit
    ): BackupResult {
        // Decrypt fully first so a wrong password fails before any data is wiped.
        val decryptedTemp = File(context.cacheDir, "backup-plain-${System.currentTimeMillis()}.json")
        try {
            DataInputStream(BufferedInputStream(input)).use { raw ->
                val magic = ByteArray(MAGIC.size)
                raw.readFully(magic)
                require(magic.contentEquals(MAGIC)) { "This is not a RoamAtlas encrypted backup" }
                val salt = ByteArray(SALT_BYTES).also { raw.readFully(it) }
                val iv = ByteArray(IV_BYTES).also { raw.readFully(it) }
                onProgress("Checking password…")
                val key = deriveKey(password, salt)
                val cipher = Cipher.getInstance(CIPHER)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                try {
                    CipherInputStream(raw, cipher).use { decrypted ->
                        decryptedTemp.outputStream().use { out -> decrypted.copyTo(out) }
                    }
                } catch (e: Exception) {
                    throw IllegalArgumentException(
                        "Could not unlock this backup. Check the password and file.",
                        e
                    )
                }
            }
            onProgress("Password OK. Importing…")
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
        onProgress: (String) -> Unit = {}
    ): BackupResult {
        var locations = 0
        var flights = 0
        writer.beginObject()
        writer.name("formatVersion").value(FORMAT_VERSION)
        writer.name("createdAtMillis").value(System.currentTimeMillis())

        onProgress("Exporting locations…")
        writer.name("locations").beginArray()
        var lastLocationId = 0L
        while (true) {
            val page = database.locationDao().getPage(lastLocationId, PAGE_SIZE)
            if (page.isEmpty()) break
            page.forEach { gson.toJson(it, LocationEntity::class.java, writer) }
            locations += page.size
            lastLocationId = page.last().id
        }
        writer.endArray()

        onProgress("Exporting flights…")
        writer.name("flights").beginArray()
        var lastFlightId = 0L
        while (true) {
            val page = database.flightDao().getPage(lastFlightId, PAGE_SIZE)
            if (page.isEmpty()) break
            page.forEach { gson.toJson(it, FlightEntity::class.java, writer) }
            flights += page.size
            lastFlightId = page.last().id
        }
        writer.endArray()

        onProgress("Exporting trackers, trips and settings…")
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
        return BackupResult(locations, flights, trackers.size, trips.size, settings.size)
    }

    private suspend fun readBackup(
        reader: JsonReader,
        onProgress: (String) -> Unit = {}
    ): BackupResult {
        var formatVersion = 0
        var locations = 0
        var flights = 0
        var trackers = 0
        var trips = 0
        var settings = 0
        database.withTransaction {
            onProgress("Clearing current data…")
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
                    "locations" -> {
                        onProgress("Restoring locations…")
                        reader.beginArray()
                        val batch = ArrayList<LocationEntity>(PAGE_SIZE)
                        while (reader.hasNext()) {
                            batch.add(gson.fromJson(reader, LocationEntity::class.java))
                            if (batch.size >= PAGE_SIZE) {
                                locations += database.locationDao().insertAll(batch).count { it != -1L }
                                batch.clear()
                                onProgress("Restoring locations… $locations")
                            }
                        }
                        if (batch.isNotEmpty()) {
                            locations += database.locationDao().insertAll(batch).count { it != -1L }
                        }
                        reader.endArray()
                    }
                    "flights" -> {
                        onProgress("Restoring flights and journeys…")
                        reader.beginArray()
                        val batch = ArrayList<FlightEntity>(PAGE_SIZE)
                        while (reader.hasNext()) {
                            batch.add(gson.fromJson(reader, FlightEntity::class.java))
                            if (batch.size >= PAGE_SIZE) {
                                flights += database.flightDao().insertAll(batch).size
                                batch.clear()
                            }
                        }
                        if (batch.isNotEmpty()) {
                            flights += database.flightDao().insertAll(batch).size
                        }
                        reader.endArray()
                    }
                    "trackers" -> {
                        reader.beginArray()
                        val values = mutableListOf<DayTrackerEntity>()
                        while (reader.hasNext()) {
                            values.add(gson.fromJson(reader, DayTrackerEntity::class.java))
                        }
                        reader.endArray()
                        database.dayTrackerDao().insertAll(values)
                        trackers = values.size
                    }
                    "trips" -> {
                        reader.beginArray()
                        val values = mutableListOf<TripEntity>()
                        while (reader.hasNext()) values.add(gson.fromJson(reader, TripEntity::class.java))
                        reader.endArray()
                        database.tripDao().insertAll(values)
                        trips = values.size
                    }
                    "settings" -> {
                        reader.beginArray()
                        val values = mutableListOf<UserSettingsEntity>()
                        while (reader.hasNext()) {
                            values.add(gson.fromJson(reader, UserSettingsEntity::class.java))
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
        return BackupResult(locations, flights, trackers, trips, settings)
    }

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

    companion object {
        private val MAGIC = byteArrayOf(0x52, 0x41, 0x42, 0x31) // RAB1
        private const val FORMAT_VERSION = 1
        private const val CIPHER = "AES/GCM/NoPadding"
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val KEY_BITS = 256
        private const val PBKDF2_ITERATIONS = 210_000
        private const val MIN_PASSWORD_LENGTH = 12
        private const val PAGE_SIZE = 500
    }
}
