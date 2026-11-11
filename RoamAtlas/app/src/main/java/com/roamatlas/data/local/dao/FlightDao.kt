package com.roamatlas.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.roamatlas.data.local.entity.FlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FlightDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(flight: FlightEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(flights: List<FlightEntity>): List<Long>
    
    @Update
    suspend fun update(flight: FlightEntity)
    
    @Delete
    suspend fun delete(flight: FlightEntity)
    
    @Query("DELETE FROM flights WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("SELECT * FROM flights ORDER BY departureTimeMillis DESC")
    fun getAllFlights(): Flow<List<FlightEntity>>
    
    @Query("SELECT * FROM flights WHERE id = :id")
    suspend fun getFlightById(id: Long): FlightEntity?
    
    @Query("SELECT * FROM flights WHERE departureTimeMillis BETWEEN :startMillis AND :endMillis ORDER BY departureTimeMillis ASC")
    fun getFlightsBetween(startMillis: Long, endMillis: Long): Flow<List<FlightEntity>>
    
    @Query("SELECT COUNT(*) FROM flights")
    fun getFlightCount(): Flow<Int>

    @Query("SELECT * FROM flights WHERE id > :afterId ORDER BY id ASC LIMIT :limit")
    suspend fun getPage(afterId: Long, limit: Int): List<FlightEntity>

    @Query("DELETE FROM flights")
    suspend fun deleteAll()
    
    @Query("SELECT SUM(distanceKm) FROM flights WHERE distanceKm IS NOT NULL")
    suspend fun getTotalFlightDistance(): Double?
    
    @Query("SELECT SUM(durationMinutes) FROM flights WHERE durationMinutes IS NOT NULL")
    suspend fun getTotalFlightDuration(): Int?
    
    @Query("SELECT * FROM flights WHERE distanceKm IS NOT NULL ORDER BY distanceKm DESC LIMIT 1")
    suspend fun getLongestFlight(): FlightEntity?
    
    @Query("""
        SELECT strftime('%Y', departureTimeMillis / 1000, 'unixepoch') as year,
               COUNT(*) as flightCount,
               SUM(distanceKm) as totalDistance
        FROM flights
        GROUP BY year
        ORDER BY year DESC
    """)
    suspend fun getYearlyFlightStats(): List<YearlyFlightStatsResult>
}

data class YearlyFlightStatsResult(
    val year: String,
    val flightCount: Int,
    val totalDistance: Double?
)
