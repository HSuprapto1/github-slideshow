package com.roamatlas.data.repository

import com.roamatlas.data.local.dao.LocationDao
import com.roamatlas.data.local.toDomain
import com.roamatlas.data.local.toEntity
import com.roamatlas.domain.model.LocationRecord
import com.roamatlas.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    
    override suspend fun isDuplicate(originalSourceId: String): Boolean {
        return locationDao.getByOriginalSourceId(originalSourceId) != null
    }
}
