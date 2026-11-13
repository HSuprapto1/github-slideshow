package com.roamatlas.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.roamatlas.data.local.entity.DayTrackerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DayTrackerDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tracker: DayTrackerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trackers: List<DayTrackerEntity>)
    
    @Update
    suspend fun update(tracker: DayTrackerEntity)
    
    @Delete
    suspend fun delete(tracker: DayTrackerEntity)
    
    @Query("DELETE FROM day_trackers WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("SELECT * FROM day_trackers ORDER BY displayOrder ASC")
    fun getAllTrackers(): Flow<List<DayTrackerEntity>>
    
    @Query("SELECT * FROM day_trackers WHERE id = :id")
    suspend fun getTrackerById(id: Long): DayTrackerEntity?
    
    @Query("SELECT MAX(displayOrder) FROM day_trackers")
    suspend fun getMaxDisplayOrder(): Int?

    @Query("SELECT * FROM day_trackers ORDER BY id ASC")
    suspend fun getAllSnapshot(): List<DayTrackerEntity>

    @Query("DELETE FROM day_trackers")
    suspend fun deleteAll()
    
    @Query("UPDATE day_trackers SET displayOrder = :order WHERE id = :id")
    suspend fun updateDisplayOrder(id: Long, order: Int)
}
