package com.roamatlas.domain.repository

import com.roamatlas.domain.model.LocationRecord
import kotlinx.coroutines.flow.Flow

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
    
    suspend fun getLocationById(id: Long): LocationRecord?
    suspend fun getEarliestTimestamp(): Long?
    suspend fun getLatestTimestamp(): Long?
    suspend fun insertLocation(location: LocationRecord): Long
    suspend fun insertLocations(locations: List<LocationRecord>): List<Long>
    suspend fun updateLocation(location: LocationRecord)
    suspend fun deleteLocation(location: LocationRecord)
    suspend fun deleteAllLocations()
    suspend fun countDaysInCountry(countryCode: String, startMillis: Long, endMillis: Long): Int
    suspend fun isDuplicate(originalSourceId: String): Boolean
}
