package com.roamatlas.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.roamatlas.data.local.dao.DayTrackerDao
import com.roamatlas.data.local.dao.FlightDao
import com.roamatlas.data.local.dao.LocationDao
import com.roamatlas.data.local.dao.TripDao
import com.roamatlas.data.local.dao.UserSettingsDao
import com.roamatlas.data.local.entity.DayTrackerEntity
import com.roamatlas.data.local.entity.FlightEntity
import com.roamatlas.data.local.entity.LocationEntity
import com.roamatlas.data.local.entity.TripEntity
import com.roamatlas.data.local.entity.UserSettingsEntity

@Database(
    entities = [
        LocationEntity::class,
        DayTrackerEntity::class,
        FlightEntity::class,
        TripEntity::class,
        UserSettingsEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class RoamAtlasDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun dayTrackerDao(): DayTrackerDao
    abstract fun flightDao(): FlightDao
    abstract fun tripDao(): TripDao
    abstract fun userSettingsDao(): UserSettingsDao
}
