package com.roamatlas.di

import android.content.Context
import androidx.room.Room
import com.roamatlas.data.local.RoamAtlasDatabase
import com.roamatlas.data.local.dao.DayTrackerDao
import com.roamatlas.data.local.dao.LocationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RoamAtlasDatabase {
        return Room.databaseBuilder(
            context,
            RoamAtlasDatabase::class.java,
            "roamatlas.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    fun provideLocationDao(database: RoamAtlasDatabase): LocationDao {
        return database.locationDao()
    }
    
    @Provides
    fun provideDayTrackerDao(database: RoamAtlasDatabase): DayTrackerDao {
        return database.dayTrackerDao()
    }
}
