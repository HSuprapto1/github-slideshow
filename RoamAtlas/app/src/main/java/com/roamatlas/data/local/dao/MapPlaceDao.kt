package com.roamatlas.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.roamatlas.data.local.entity.MapPlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MapPlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(place: MapPlaceEntity): Long

    @Update
    suspend fun update(place: MapPlaceEntity)

    @Delete
    suspend fun delete(place: MapPlaceEntity)

    @Query("DELETE FROM map_places WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM map_places ORDER BY name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<MapPlaceEntity>>

    @Query("SELECT * FROM map_places ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllSnapshot(): List<MapPlaceEntity>

    @Query("SELECT COUNT(*) FROM map_places")
    fun getCount(): Flow<Int>

    @Query("DELETE FROM map_places")
    suspend fun deleteAll()
}
