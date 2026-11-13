package com.roamatlas.data.airport

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class Airport(
    val iataCode: String,
    val icaoCode: String,
    val name: String,
    val city: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String?,
    val type: String
) {
    val countryName: String
        get() = Locale("", countryCode).displayCountry.ifBlank { countryCode }

    val displayName: String
        get() = "$iataCode · $name${city.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}"
}

@Singleton
class AirportRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var cachedAirports: List<Airport>? = null

    suspend fun search(query: String, limit: Int = 30): List<Airport> =
        withContext(Dispatchers.IO) {
            val normalized = query.trim().lowercase()
            if (normalized.isBlank()) return@withContext airports()
                .sortedBy { typeRank(it.type) }
                .take(limit)
            airports().asSequence()
                .mapNotNull { airport ->
                    val rank = when {
                        airport.iataCode.equals(normalized, true) -> 0
                        airport.icaoCode.equals(normalized, true) -> 1
                        airport.iataCode.lowercase().startsWith(normalized) -> 2
                        airport.city.lowercase().startsWith(normalized) -> 3
                        airport.name.lowercase().startsWith(normalized) -> 4
                        airport.city.contains(normalized, true) -> 5
                        airport.name.contains(normalized, true) -> 6
                        airport.countryName.contains(normalized, true) -> 7
                        else -> return@mapNotNull null
                    }
                    airport to rank
                }
                .sortedWith(
                    compareBy<Pair<Airport, Int>> { it.second }
                        .thenBy { typeRank(it.first.type) }
                        .thenBy { it.first.name }
                )
                .take(limit)
                .map { it.first }
                .toList()
        }

    suspend fun findByCode(code: String): Airport? = withContext(Dispatchers.IO) {
        val normalized = code.trim().uppercase()
        airports().firstOrNull {
            it.iataCode == normalized || it.icaoCode == normalized
        }
    }

    suspend fun getByIataCodes(codes: Collection<String>): Map<String, Airport> =
        withContext(Dispatchers.IO) {
            val wanted = codes.map { it.uppercase() }.toSet()
            airports().filter { it.iataCode in wanted }.associateBy { it.iataCode }
        }

    private fun airports(): List<Airport> {
        cachedAirports?.let { return it }
        return synchronized(this) {
            cachedAirports ?: loadAirports().also { cachedAirports = it }
        }
    }

    private fun loadAirports(): List<Airport> =
        context.assets.open("airports.tsv").bufferedReader().useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val fields = line.split('\t')
                if (fields.size < 9) return@mapNotNull null
                Airport(
                    iataCode = fields[0],
                    icaoCode = fields[1],
                    name = fields[2],
                    city = fields[3],
                    countryCode = fields[4],
                    latitude = fields[5].toDoubleOrNull() ?: return@mapNotNull null,
                    longitude = fields[6].toDoubleOrNull() ?: return@mapNotNull null,
                    timeZoneId = fields[7].takeIf { it.isNotBlank() },
                    type = fields[8]
                )
            }.toList()
        }

    private fun typeRank(type: String): Int = when (type) {
        "large_airport" -> 0
        "medium_airport" -> 1
        "small_airport" -> 2
        else -> 3
    }
}
