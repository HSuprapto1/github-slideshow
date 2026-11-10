package com.roamatlas.data.repository

import com.roamatlas.data.local.dao.DayTrackerDao
import com.roamatlas.data.local.toDomain
import com.roamatlas.data.local.toEntity
import com.roamatlas.domain.model.DayTracker
import com.roamatlas.domain.repository.DayTrackerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DayTrackerRepositoryImpl @Inject constructor(
    private val dayTrackerDao: DayTrackerDao
) : DayTrackerRepository {
    
    override fun getAllTrackers(): Flow<List<DayTracker>> {
        return dayTrackerDao.getAllTrackers().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getTrackerById(id: Long): DayTracker? {
        return dayTrackerDao.getTrackerById(id)?.toDomain()
    }
    
    override suspend fun insertTracker(tracker: DayTracker): Long {
        return dayTrackerDao.insert(tracker.toEntity())
    }
    
    override suspend fun updateTracker(tracker: DayTracker) {
        dayTrackerDao.update(tracker.toEntity())
    }
    
    override suspend fun deleteTracker(tracker: DayTracker) {
        dayTrackerDao.delete(tracker.toEntity())
    }
    
    override suspend fun deleteTrackerById(id: Long) {
        dayTrackerDao.deleteById(id)
    }
    
    override suspend fun updateDisplayOrder(id: Long, order: Int) {
        dayTrackerDao.updateDisplayOrder(id, order)
    }
    
    override suspend fun getNextDisplayOrder(): Int {
        return (dayTrackerDao.getMaxDisplayOrder() ?: -1) + 1
    }
}
