package com.roamatlas.data.geo

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPlace(
    val countryCode: String?,
    val countryName: String?,
    val city: String?
)

@Singleton
class OfflineGeoResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private data class Point(val lon: Double, val lat: Double)
    private data class Bounds(
        val minLon: Double,
        val minLat: Double,
        val maxLon: Double,
        val maxLat: Double
    ) {
        fun contains(lon: Double, lat: Double) =
            lon in minLon..maxLon && lat in minLat..maxLat
    }

    private data class Polygon(
        val rings: List<List<Point>>,
        val bounds: Bounds
    )

    private data class Country(
        val code: String,
        val name: String,
        val polygons: List<Polygon>
    )

    private data class City(
        val name: String,
        val lat: Double,
        val lon: Double,
        val countryCode: String,
        val population: Long
    )

    private val cache = ConcurrentHashMap<String, GeoPlace>()
    private val countries by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { loadCountries() }
    private val countryGrid by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { buildCountryGrid() }
    private val cityGrid by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { loadCityGrid() }

    fun resolve(latitude: Double, longitude: Double): GeoPlace {
        val key = String.format(Locale.US, "%.2f,%.2f", latitude, longitude)
        return cache.getOrPut(key) {
            val country = findCountry(latitude, longitude)
            val city = findCity(latitude, longitude, country?.code)
            GeoPlace(country?.code, country?.name, city?.name)
        }
    }

    private fun findCountry(lat: Double, lon: Double): Country? {
        val candidates = countryGrid[gridKey(lat, lon, COUNTRY_GRID_DEGREES)].orEmpty()
        return candidates
            .asSequence()
            .map { countries[it] }
            .firstOrNull { country ->
                country.polygons.any { polygon ->
                    polygon.bounds.contains(lon, lat) && insidePolygon(lon, lat, polygon)
                }
            }
    }

    private fun findCity(lat: Double, lon: Double, countryCode: String?): City? {
        var best: City? = null
        var bestDistance = MAX_CITY_DISTANCE_KM
        val centerLat = floor(lat).toInt()
        val centerLon = floor(lon).toInt()
        for (latOffset in -1..1) {
            for (lonOffset in -1..1) {
                val cities = cityGrid["${centerLat + latOffset},${centerLon + lonOffset}"].orEmpty()
                for (city in cities) {
                    if (countryCode != null && city.countryCode != countryCode) continue
                    val distance = distanceKm(lat, lon, city.lat, city.lon)
                    if (distance < bestDistance ||
                        (distance == bestDistance && city.population > (best?.population ?: 0))
                    ) {
                        best = city
                        bestDistance = distance
                    }
                }
            }
        }
        return best
    }

    private fun loadCountries(): List<Country> {
        context.assets.open(COUNTRIES_ASSET).use { input ->
            val root = JsonParser.parseReader(InputStreamReader(input)).asJsonArray
            return root.mapNotNull { element ->
                val feature = element.asJsonObject
                val code = feature.string("code")?.takeIf { it.length == 2 } ?: return@mapNotNull null
                val name = feature.string("name") ?: code
                val geometry = feature.getAsJsonObject("geometry") ?: return@mapNotNull null
                val coordinates = geometry.getAsJsonArray("coordinates") ?: return@mapNotNull null
                val polygons = when (geometry.string("type")) {
                    "Polygon" -> listOfNotNull(parsePolygon(coordinates))
                    "MultiPolygon" -> coordinates.mapNotNull { parsePolygon(it.asJsonArray) }
                    else -> emptyList()
                }
                Country(code, name, polygons)
            }
        }
    }

    private fun parsePolygon(array: JsonArray): Polygon? {
        val rings = array.mapNotNull { ringElement ->
            val ring = ringElement.asJsonArray.mapNotNull { pointElement ->
                val pair = pointElement.asJsonArray
                if (pair.size() < 2) null else Point(pair[0].asDouble, pair[1].asDouble)
            }
            ring.takeIf { it.size >= 3 }
        }
        if (rings.isEmpty()) return null
        val outer = rings.first()
        return Polygon(
            rings = rings,
            bounds = Bounds(
                minLon = outer.minOf { it.lon },
                minLat = outer.minOf { it.lat },
                maxLon = outer.maxOf { it.lon },
                maxLat = outer.maxOf { it.lat }
            )
        )
    }

    private fun buildCountryGrid(): Map<String, List<Int>> {
        val grid = mutableMapOf<String, MutableSet<Int>>()
        countries.forEachIndexed { index, country ->
            country.polygons.forEach { polygon ->
                val minLat = floor(polygon.bounds.minLat / COUNTRY_GRID_DEGREES).toInt()
                val maxLat = floor(polygon.bounds.maxLat / COUNTRY_GRID_DEGREES).toInt()
                val minLon = floor(polygon.bounds.minLon / COUNTRY_GRID_DEGREES).toInt()
                val maxLon = floor(polygon.bounds.maxLon / COUNTRY_GRID_DEGREES).toInt()
                for (latCell in minLat..maxLat) {
                    for (lonCell in minLon..maxLon) {
                        grid.getOrPut("$latCell,$lonCell") { mutableSetOf() }.add(index)
                    }
                }
            }
        }
        return grid.mapValues { it.value.toList() }
    }

    private fun loadCityGrid(): Map<String, List<City>> {
        val grid = mutableMapOf<String, MutableList<City>>()
        context.assets.open(CITIES_ASSET).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val columns = line.split('\t')
                if (columns.size < 5) return@forEach
                val lat = columns[1].toDoubleOrNull() ?: return@forEach
                val lon = columns[2].toDoubleOrNull() ?: return@forEach
                val city = City(
                    name = columns[0],
                    lat = lat,
                    lon = lon,
                    countryCode = columns[3],
                    population = columns[4].toLongOrNull() ?: 0
                )
                grid.getOrPut("${floor(lat).toInt()},${floor(lon).toInt()}") {
                    mutableListOf()
                }.add(city)
            }
        }
        return grid
    }

    private fun insidePolygon(lon: Double, lat: Double, polygon: Polygon): Boolean {
        if (!insideRing(lon, lat, polygon.rings.first())) return false
        return polygon.rings.drop(1).none { insideRing(lon, lat, it) }
    }

    private fun insideRing(lon: Double, lat: Double, ring: List<Point>): Boolean {
        var inside = false
        var previous = ring.last()
        for (current in ring) {
            if ((current.lat > lat) != (previous.lat > lat)) {
                val intersection = (previous.lon - current.lon) *
                    (lat - current.lat) / (previous.lat - current.lat) + current.lon
                if (lon < intersection) inside = !inside
            }
            previous = current
        }
        return inside
    }

    private fun gridKey(lat: Double, lon: Double, degrees: Double) =
        "${floor(lat / degrees).toInt()},${floor(lon / degrees).toInt()}"

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val latDelta = Math.toRadians(lat2 - lat1)
        val lonDelta = Math.toRadians(lon2 - lon1)
        val a = sin(latDelta / 2) * sin(latDelta / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(lonDelta / 2) * sin(lonDelta / 2)
        return 6371.0 * 2 * asin(sqrt(a))
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString

    companion object {
        private const val COUNTRIES_ASSET = "geodata/countries.json"
        private const val CITIES_ASSET = "geodata/cities.tsv"
        private const val COUNTRY_GRID_DEGREES = 5.0
        private const val MAX_CITY_DISTANCE_KM = 75.0
    }
}
