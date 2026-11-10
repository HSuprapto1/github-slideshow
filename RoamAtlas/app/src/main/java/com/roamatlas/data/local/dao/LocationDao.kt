package com.roamatlas.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.roamatlas.data.local.entity.LocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(location: LocationEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(locations: List<LocationEntity>): List<Long>
    
    @Update
    suspend fun update(location: LocationEntity)
    
    @Delete
    suspend fun delete(location: LocationEntity)
    
    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM locations")
    suspend fun deleteAll()
    
    @Query("SELECT * FROM locations ORDER BY timestampMillis DESC")
    fun getAllLocations(): Flow<List<LocationEntity>>
    
    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getLocationById(id: Long): LocationEntity?
    
    @Query("SELECT * FROM locations WHERE timestampMillis BETWEEN :startMillis AND :endMillis ORDER BY timestampMillis ASC")
    fun getLocationsBetween(startMillis: Long, endMillis: Long): Flow<List<LocationEntity>>
    
    @Query("SELECT * FROM locations WHERE countryCode = :countryCode ORDER BY timestampMillis ASC")
    fun getLocationsByCountry(countryCode: String): Flow<List<LocationEntity>>
    
    @Query("SELECT DISTINCT countryCode FROM locations WHERE countryCode IS NOT NULL")
    fun getDistinctCountryCodes(): Flow<List<String>>
    
    @Query("SELECT COUNT(*) FROM locations")
    fun getLocationCount(): Flow<Int>
    
    @Query("SELECT MIN(timestampMillis) FROM locations")
    suspend fun getEarliestTimestamp(): Long?
    
    @Query("SELECT MAX(timestampMillis) FROM locations")
    suspend fun getLatestTimestamp(): Long?
    
    @Query("SELECT * FROM locations WHERE originalSourceId = :sourceId LIMIT 1")
    suspend fun getByOriginalSourceId(sourceId: String): LocationEntity?
    
    @Query("""
        SELECT DISTINCT DATE(timestampMillis / 1000, 'unixepoch') as date, countryCode 
        FROM locations 
        WHERE countryCode = :countryCode 
        AND timestampMillis BETWEEN :startMillis AND :endMillis
    """)
    suspend fun getDaysInCountry(countryCode: String, startMillis: Long, endMillis: Long): List<DayCountryResult>
    
    @Query("SELECT COUNT(DISTINCT DATE(timestampMillis / 1000, 'unixepoch')) FROM locations WHERE countryCode = :countryCode AND timestampMillis BETWEEN :startMillis AND :endMillis")
    suspend fun countDaysInCountry(countryCode: String, startMillis: Long, endMillis: Long): Int
}

data class DayCountryResult(
    val date: String,
    val countryCode: String
)
