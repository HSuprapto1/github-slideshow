package com.roamatlas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.roamatlas.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: TripEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trips: List<TripEntity>)

    @Update
    suspend fun update(trip: TripEntity)

    @Delete
    suspend fun delete(trip: TripEntity)

    @Query("SELECT * FROM trips ORDER BY startDateEpochDay DESC")
    fun getAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: Long): TripEntity?

    @Query("SELECT * FROM trips ORDER BY id ASC")
    suspend fun getAllSnapshot(): List<TripEntity>

    @Query("DELETE FROM trips")
    suspend fun deleteAll()
}
