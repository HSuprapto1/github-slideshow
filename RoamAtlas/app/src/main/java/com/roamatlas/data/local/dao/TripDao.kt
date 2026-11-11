package com.roamatlas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.roamatlas.data.local.entity.TripEntity

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trips: List<TripEntity>)

    @Query("SELECT * FROM trips ORDER BY id ASC")
    suspend fun getAllSnapshot(): List<TripEntity>

    @Query("DELETE FROM trips")
    suspend fun deleteAll()
}
