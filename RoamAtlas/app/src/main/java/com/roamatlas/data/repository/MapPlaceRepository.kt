package com.roamatlas.data.repository

import com.roamatlas.data.local.dao.MapPlaceDao
import com.roamatlas.data.local.entity.MapPlaceEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapPlaceRepository @Inject constructor(
    private val mapPlaceDao: MapPlaceDao
) {
    fun getAll(): Flow<List<MapPlaceEntity>> = mapPlaceDao.getAll()

    fun getCount(): Flow<Int> = mapPlaceDao.getCount()

    suspend fun getAllSnapshot(): List<MapPlaceEntity> = mapPlaceDao.getAllSnapshot()

    suspend fun savePlace(
        name: String,
        latitude: Double,
        longitude: Double,
        notes: String? = null,
        radiusMeters: Float? = 100f
    ): Long {
        val now = System.currentTimeMillis()
        return mapPlaceDao.insert(
            MapPlaceEntity(
                name = name.trim().ifBlank { "Saved place" },
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters,
                notes = notes?.takeIf { it.isNotBlank() },
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
    }

    suspend fun deletePlace(id: Long) {
        mapPlaceDao.deleteById(id)
    }
}
