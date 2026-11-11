package com.roamatlas.data.repository

import com.roamatlas.data.local.dao.LocationDao
import com.roamatlas.data.local.toDomain
import com.roamatlas.data.local.toEntity
import com.roamatlas.domain.model.LocationRecord
import com.roamatlas.domain.repository.DailyCountryRecord
import com.roamatlas.domain.repository.CountryDayRecord
import com.roamatlas.domain.repository.CountryDetail
import com.roamatlas.domain.repository.CountryYearRecord
import com.roamatlas.domain.repository.CityDayRecord
import com.roamatlas.domain.repository.CityRecord
import com.roamatlas.domain.repository.CityYearRecord
import com.roamatlas.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val locationDao: LocationDao
) : LocationRepository {
    
    override fun getAllLocations(): Flow<List<LocationRecord>> {
        return locationDao.getAllLocations().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getLocationsBetween(startMillis: Long, endMillis: Long): Flow<List<LocationRecord>> {
        return locationDao.getLocationsBetween(startMillis, endMillis).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getLocationsByCountry(countryCode: String): Flow<List<LocationRecord>> {
        return locationDao.getLocationsByCountry(countryCode).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getDistinctCountryCodes(): Flow<List<String>> {
        return locationDao.getDistinctCountryCodes()
    }
    
    override fun getLocationCount(): Flow<Int> {
        return locationDao.getLocationCount()
    }

    override fun getFilteredLocationCount(
        startMillis: Long?,
        endMillis: Long?,
        countryCode: String?
    ): Flow<Int> = locationDao.getFilteredLocationCount(startMillis, endMillis, countryCode)

    override fun getMapSample(
        startMillis: Long?,
        endMillis: Long?,
        countryCode: String?,
        sampleEvery: Int,
        limit: Int
    ): Flow<List<LocationRecord>> =
        locationDao.getMapSample(startMillis, endMillis, countryCode, sampleEvery, limit)
            .map { entities -> entities.map { it.toDomain() } }

    override fun getLocationsForLocalDate(
        localDate: LocalDate,
        countryCode: String?,
        city: String?,
        limit: Int
    ): Flow<List<LocationRecord>> =
        locationDao.getLocationsForLocalDate(localDate.toString(), countryCode, city, limit)
            .map { entities -> entities.map { it.toDomain() } }
    
    override suspend fun getLocationById(id: Long): LocationRecord? {
        return locationDao.getLocationById(id)?.toDomain()
    }
    
    override suspend fun getEarliestTimestamp(): Long? {
        return locationDao.getEarliestTimestamp()
    }
    
    override suspend fun getLatestTimestamp(): Long? {
        return locationDao.getLatestTimestamp()
    }
    
    override suspend fun insertLocation(location: LocationRecord): Long {
        return locationDao.insert(location.toEntity())
    }
    
    override suspend fun insertLocations(locations: List<LocationRecord>): List<Long> {
        return locationDao.insertAll(locations.map { it.toEntity() })
    }
    
    override suspend fun updateLocation(location: LocationRecord) {
        locationDao.update(location.toEntity())
    }
    
    override suspend fun deleteLocation(location: LocationRecord) {
        locationDao.delete(location.toEntity())
    }
    
    override suspend fun deleteAllLocations() {
        locationDao.deleteAll()
    }
    
    override suspend fun countDaysInCountry(countryCode: String, startMillis: Long, endMillis: Long): Int {
        return locationDao.countDaysInCountry(countryCode, startMillis, endMillis)
    }

    override suspend fun getDaysInCountry(
        countryCode: String,
        startMillis: Long,
        endMillis: Long
    ): Set<LocalDate> = locationDao.getDaysInCountry(countryCode, startMillis, endMillis)
        .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        .toSet()

    override suspend fun countDaysOutsideCountry(
        countryCode: String,
        startMillis: Long,
        endMillis: Long
    ): Int = locationDao.countDaysOutsideCountry(countryCode, startMillis, endMillis)

    override suspend fun getDaysOutsideCountry(
        countryCode: String,
        startMillis: Long,
        endMillis: Long
    ): Set<LocalDate> = locationDao.getDaysOutsideCountry(countryCode, startMillis, endMillis)
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .toSet()

    override suspend fun countTrackedDays(startMillis: Long, endMillis: Long): Int {
        return locationDao.countTrackedDaysBetween(startMillis, endMillis)
    }

    override suspend fun getDailyCountryEvidence(
        startMillis: Long,
        endMillis: Long
    ): List<DailyCountryRecord> =
        locationDao.getDailyCountryEvidence(startMillis, endMillis).map {
            DailyCountryRecord(
                date = it.date,
                countryCode = it.countryCode,
                countryName = it.countryName,
                pointCount = it.pointCount
            )
        }

    override suspend fun getCountryDetail(countryCode: String): CountryDetail {
        val summary = locationDao.getCountryDetailsSummary(countryCode)
        return CountryDetail(
            countryCode = countryCode,
            countryName = summary.countryName ?: countryCode,
            pointCount = summary.pointCount,
            daysRecorded = summary.daysRecorded,
            cityCount = summary.cityCount,
            firstDate = summary.firstDate.toLocalDateOrNull(),
            lastDate = summary.lastDate.toLocalDateOrNull()
        )
    }

    override suspend fun getCountryYearRecords(
        countryCode: String
    ): List<CountryYearRecord> = locationDao.getCountryYearSummaries(countryCode).mapNotNull {
        val year = it.year.toIntOrNull() ?: return@mapNotNull null
        CountryYearRecord(year, it.pointCount, it.daysRecorded, it.cityCount)
    }

    override suspend fun getCountryDayRecords(
        countryCode: String
    ): List<CountryDayRecord> = locationDao.getCountryDailySummaries(countryCode).mapNotNull {
        val date = it.date.toLocalDateOrNull() ?: return@mapNotNull null
        CountryDayRecord(
            date = date,
            pointCount = it.pointCount,
            cities = it.cities?.split(',')?.filter(String::isNotBlank).orEmpty()
        )
    }

    override suspend fun getCities(): List<CityRecord> =
        locationDao.getCitySummaries().map {
            CityRecord(
                city = it.city,
                countryCode = it.countryCode,
                countryName = it.countryName ?: it.countryCode,
                pointCount = it.pointCount,
                daysRecorded = it.daysRecorded,
                firstDate = it.firstDate.toLocalDateOrNull(),
                lastDate = it.lastDate.toLocalDateOrNull()
            )
        }

    override suspend fun getCityDetail(city: String, countryCode: String): CityRecord {
        val detail = locationDao.getCityDetails(city, countryCode)
        return CityRecord(
            city = city,
            countryCode = countryCode,
            countryName = detail.countryName ?: countryCode,
            pointCount = detail.pointCount,
            daysRecorded = detail.daysRecorded,
            firstDate = detail.firstDate.toLocalDateOrNull(),
            lastDate = detail.lastDate.toLocalDateOrNull()
        )
    }

    override suspend fun getCityYearRecords(
        city: String,
        countryCode: String
    ): List<CityYearRecord> =
        locationDao.getCityYearSummaries(city, countryCode).mapNotNull {
            CityYearRecord(
                year = it.year.toIntOrNull() ?: return@mapNotNull null,
                pointCount = it.pointCount,
                daysRecorded = it.daysRecorded
            )
        }

    override suspend fun getCityDayRecords(
        city: String,
        countryCode: String
    ): List<CityDayRecord> =
        locationDao.getCityDailySummaries(city, countryCode).mapNotNull {
            CityDayRecord(
                date = it.date.toLocalDateOrNull() ?: return@mapNotNull null,
                pointCount = it.pointCount
            )
        }
    
    override suspend fun isDuplicate(originalSourceId: String): Boolean {
        return locationDao.getByOriginalSourceId(originalSourceId) != null
    }

    private fun String?.toLocalDateOrNull(): LocalDate? = try {
        this?.let(LocalDate::parse)
    } catch (_: Exception) {
        null
    }
}
