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

    @Query("""
        SELECT COUNT(*) FROM locations
        WHERE (:startMillis IS NULL OR timestampMillis >= :startMillis)
          AND (:endMillis IS NULL OR timestampMillis < :endMillis)
          AND (:countryCode IS NULL OR countryCode = :countryCode)
    """)
    fun getFilteredLocationCount(
        startMillis: Long?,
        endMillis: Long?,
        countryCode: String?
    ): Flow<Int>

    @Query("""
        SELECT * FROM locations
        WHERE (:startMillis IS NULL OR timestampMillis >= :startMillis)
          AND (:endMillis IS NULL OR timestampMillis < :endMillis)
          AND (:countryCode IS NULL OR countryCode = :countryCode)
          AND (id % :sampleEvery = 0)
        ORDER BY timestampMillis ASC
        LIMIT :limit
    """)
    fun getMapSample(
        startMillis: Long?,
        endMillis: Long?,
        countryCode: String?,
        sampleEvery: Int,
        limit: Int
    ): Flow<List<LocationEntity>>

    @Query("""
        SELECT * FROM locations
        WHERE DATE(
            (timestampMillis / 1000) + (CAST(ROUND(longitude / 15.0) AS INTEGER) * 3600),
            'unixepoch'
        ) = :localDate
        ORDER BY timestampMillis ASC
        LIMIT :limit
    """)
    fun getLocationsForLocalDate(
        localDate: String,
        limit: Int
    ): Flow<List<LocationEntity>>

    @Query("SELECT COUNT(DISTINCT city) FROM locations WHERE city IS NOT NULL AND city != ''")
    suspend fun countDistinctCities(): Int

    @Query("""
        SELECT COUNT(DISTINCT DATE(
            (timestampMillis / 1000) + (CAST(ROUND(longitude / 15.0) AS INTEGER) * 3600),
            'unixepoch'
        )) FROM locations
    """)
    suspend fun countTrackedDays(): Int

    @Query("""
        SELECT COUNT(DISTINCT DATE(
            (timestampMillis / 1000) + (CAST(ROUND(longitude / 15.0) AS INTEGER) * 3600),
            'unixepoch'
        ))
        FROM locations
        WHERE timestampMillis BETWEEN :startMillis AND :endMillis
    """)
    suspend fun countTrackedDaysBetween(startMillis: Long, endMillis: Long): Int

    @Query("""
        SELECT countryCode, countryName,
               COUNT(DISTINCT DATE(
                   (timestampMillis / 1000) + (CAST(ROUND(longitude / 15.0) AS INTEGER) * 3600),
                   'unixepoch'
               )) AS daysSpent,
               COUNT(DISTINCT DATE(
                   (timestampMillis / 1000) + (CAST(ROUND(longitude / 15.0) AS INTEGER) * 3600),
                   'unixepoch'
               )) AS visitCount,
               MIN(timestampMillis) AS firstTimestamp, MAX(timestampMillis) AS lastTimestamp
        FROM locations
        WHERE countryCode IS NOT NULL
        GROUP BY countryCode, countryName
        ORDER BY daysSpent DESC
    """)
    suspend fun getCountryVisitSummaries(): List<CountryVisitSummary>

    @Query("""
        SELECT strftime(
                   '%Y',
                   (timestampMillis / 1000) + (CAST(ROUND(longitude / 15.0) AS INTEGER) * 3600),
                   'unixepoch'
               ) AS year,
               COUNT(DISTINCT DATE(
                   (timestampMillis / 1000) + (CAST(ROUND(longitude / 15.0) AS INTEGER) * 3600),
                   'unixepoch'
               )) AS daysTracked,
               COUNT(DISTINCT countryCode) AS countriesVisited
        FROM locations
        WHERE countryCode IS NOT NULL
        GROUP BY year
        ORDER BY year DESC
    """)
    suspend fun getYearlyLocationSummaries(): List<YearlyLocationSummary>

    @Query("SELECT * FROM locations WHERE id > :afterId ORDER BY id ASC LIMIT :limit")
    suspend fun getPage(afterId: Long, limit: Int): List<LocationEntity>

    @Query("""
        SELECT countryCode FROM locations
        WHERE timestampMillis BETWEEN :startMillis AND :endMillis
          AND countryCode IS NOT NULL
        GROUP BY countryCode
        ORDER BY COUNT(*) DESC
        LIMIT 1
    """)
    suspend fun getDominantCountryBetween(startMillis: Long, endMillis: Long): String?
    
    @Query("SELECT MIN(timestampMillis) FROM locations")
    suspend fun getEarliestTimestamp(): Long?
    
    @Query("SELECT MAX(timestampMillis) FROM locations")
    suspend fun getLatestTimestamp(): Long?
    
    @Query("SELECT * FROM locations WHERE originalSourceId = :sourceId LIMIT 1")
    suspend fun getByOriginalSourceId(sourceId: String): LocationEntity?
    
    @Query("""
        SELECT DISTINCT DATE(
            (timestampMillis / 1000) + (CAST(ROUND(longitude / 15.0) AS INTEGER) * 3600),
            'unixepoch'
        ) as date, countryCode
        FROM locations 
        WHERE countryCode = :countryCode 
        AND timestampMillis BETWEEN :startMillis AND :endMillis
    """)
    suspend fun getDaysInCountry(countryCode: String, startMillis: Long, endMillis: Long): List<DayCountryResult>
    
    @Query("""
        SELECT COUNT(DISTINCT DATE(
            (timestampMillis / 1000) + (CAST(ROUND(longitude / 15.0) AS INTEGER) * 3600),
            'unixepoch'
        ))
        FROM locations
        WHERE countryCode = :countryCode
          AND timestampMillis BETWEEN :startMillis AND :endMillis
    """)
    suspend fun countDaysInCountry(countryCode: String, startMillis: Long, endMillis: Long): Int

    @Query("""
        SELECT COUNT(DISTINCT DATE(
            (timestampMillis / 1000) + (CAST(ROUND(longitude / 15.0) AS INTEGER) * 3600),
            'unixepoch'
        ))
        FROM locations
        WHERE countryCode IS NOT NULL
          AND countryCode != :countryCode
          AND timestampMillis BETWEEN :startMillis AND :endMillis
    """)
    suspend fun countDaysOutsideCountry(
        countryCode: String,
        startMillis: Long,
        endMillis: Long
    ): Int

    @Query("""
        SELECT DISTINCT DATE(
            (timestampMillis / 1000) + (CAST(ROUND(longitude / 15.0) AS INTEGER) * 3600),
            'unixepoch'
        )
        FROM locations
        WHERE countryCode IS NOT NULL
          AND countryCode != :countryCode
          AND timestampMillis BETWEEN :startMillis AND :endMillis
    """)
    suspend fun getDaysOutsideCountry(
        countryCode: String,
        startMillis: Long,
        endMillis: Long
    ): List<String>

    @Query("""
        SELECT DATE(
                   (timestampMillis / 1000) +
                   (CAST(ROUND(longitude / 15.0) AS INTEGER) * 3600),
                   'unixepoch'
               ) AS date,
               countryCode,
               MAX(countryName) AS countryName,
               COUNT(*) AS pointCount
        FROM locations
        WHERE timestampMillis BETWEEN :startMillis AND :endMillis
        GROUP BY date, countryCode
        ORDER BY date ASC, pointCount DESC
    """)
    suspend fun getDailyCountryEvidence(
        startMillis: Long,
        endMillis: Long
    ): List<DailyCountryEvidence>
}

data class DayCountryResult(
    val date: String,
    val countryCode: String
)

data class CountryVisitSummary(
    val countryCode: String,
    val countryName: String?,
    val daysSpent: Int,
    val visitCount: Int,
    val firstTimestamp: Long,
    val lastTimestamp: Long
)

data class YearlyLocationSummary(
    val year: String,
    val daysTracked: Int,
    val countriesVisited: Int
)

data class DailyCountryEvidence(
    val date: String,
    val countryCode: String?,
    val countryName: String?,
    val pointCount: Int
)
