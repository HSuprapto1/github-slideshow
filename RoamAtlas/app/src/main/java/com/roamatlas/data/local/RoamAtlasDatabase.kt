package com.roamatlas.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.roamatlas.data.local.dao.DayTrackerDao
import com.roamatlas.data.local.dao.LocationDao
import com.roamatlas.data.local.entity.DayTrackerEntity
import com.roamatlas.data.local.entity.LocationEntity

@Database(
    entities = [
        LocationEntity::class,
        DayTrackerEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class RoamAtlasDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun dayTrackerDao(): DayTrackerDao
}
