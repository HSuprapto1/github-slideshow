package com.roamatlas.domain.repository

import com.roamatlas.domain.model.LocationRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class DailyCountryRecord(
    val date: String,
    val countryCode: String?,
    val countryName: String?,
    val pointCount: Int
)

data class CountryDetail(
    val countryCode: String,
    val countryName: String,
    val pointCount: Int,
    val daysRecorded: Int,
    val cityCount: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?
)

data class CountryYearRecord(
    val year: Int,
    val pointCount: Int,
    val daysRecorded: Int,
    val cityCount: Int
)

data class CountryDayRecord(
    val date: LocalDate,
    val pointCount: Int,
    val cities: List<String>
)

interface LocationRepository {
    fun getAllLocations(): Flow<List<LocationRecord>>
    fun getLocationsBetween(startMillis: Long, endMillis: Long): Flow<List<LocationRecord>>
    fun getLocationsByCountry(countryCode: String): Flow<List<LocationRecord>>
    fun getDistinctCountryCodes(): Flow<List<String>>
    fun getLocationCount(): Flow<Int>
    fun getFilteredLocationCount(startMillis: Long?, endMillis: Long?, countryCode: String?): Flow<Int>
    fun getMapSample(
        startMillis: Long?,
        endMillis: Long?,
        countryCode: String?,
        sampleEvery: Int,
        limit: Int
    ): Flow<List<LocationRecord>>
    fun getLocationsForLocalDate(
        localDate: LocalDate,
        countryCode: String? = null,
        limit: Int = 5_000
    ): Flow<List<LocationRecord>>
    
    suspend fun getLocationById(id: Long): LocationRecord?
    suspend fun getEarliestTimestamp(): Long?
    suspend fun getLatestTimestamp(): Long?
    suspend fun insertLocation(location: LocationRecord): Long
    suspend fun insertLocations(locations: List<LocationRecord>): List<Long>
    suspend fun updateLocation(location: LocationRecord)
    suspend fun deleteLocation(location: LocationRecord)
    suspend fun deleteAllLocations()
    suspend fun countDaysInCountry(countryCode: String, startMillis: Long, endMillis: Long): Int
    suspend fun getDaysInCountry(
        countryCode: String,
        startMillis: Long,
        endMillis: Long
    ): Set<LocalDate>
    suspend fun countDaysOutsideCountry(countryCode: String, startMillis: Long, endMillis: Long): Int
    suspend fun getDaysOutsideCountry(
        countryCode: String,
        startMillis: Long,
        endMillis: Long
    ): Set<LocalDate>
    suspend fun countTrackedDays(startMillis: Long, endMillis: Long): Int
    suspend fun getDailyCountryEvidence(
        startMillis: Long,
        endMillis: Long
    ): List<DailyCountryRecord>
    suspend fun getCountryDetail(countryCode: String): CountryDetail
    suspend fun getCountryYearRecords(countryCode: String): List<CountryYearRecord>
    suspend fun getCountryDayRecords(countryCode: String): List<CountryDayRecord>
    suspend fun isDuplicate(originalSourceId: String): Boolean
}
