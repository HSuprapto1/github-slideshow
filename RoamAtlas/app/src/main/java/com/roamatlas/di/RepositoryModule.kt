package com.roamatlas.di

import com.roamatlas.data.repository.DayTrackerRepositoryImpl
import com.roamatlas.data.repository.LocationRepositoryImpl
import com.roamatlas.domain.repository.DayTrackerRepository
import com.roamatlas.domain.repository.LocationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        locationRepositoryImpl: LocationRepositoryImpl
    ): LocationRepository
    
    @Binds
    @Singleton
    abstract fun bindDayTrackerRepository(
        dayTrackerRepositoryImpl: DayTrackerRepositoryImpl
    ): DayTrackerRepository
}
