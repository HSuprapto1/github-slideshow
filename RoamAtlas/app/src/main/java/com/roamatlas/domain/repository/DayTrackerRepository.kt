package com.roamatlas.domain.repository

import com.roamatlas.domain.model.DayTracker
import kotlinx.coroutines.flow.Flow

interface DayTrackerRepository {
    fun getAllTrackers(): Flow<List<DayTracker>>
    suspend fun getTrackerById(id: Long): DayTracker?
    suspend fun insertTracker(tracker: DayTracker): Long
    suspend fun updateTracker(tracker: DayTracker)
    suspend fun deleteTracker(tracker: DayTracker)
    suspend fun deleteTrackerById(id: Long)
    suspend fun updateDisplayOrder(id: Long, order: Int)
    suspend fun getNextDisplayOrder(): Int
}
