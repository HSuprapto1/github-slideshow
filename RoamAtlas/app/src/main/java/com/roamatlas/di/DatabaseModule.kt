package com.roamatlas.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.roamatlas.data.email.BookingEmailParser
import com.roamatlas.data.local.RoamAtlasDatabase
import com.roamatlas.data.local.dao.DayTrackerDao
import com.roamatlas.data.local.dao.FlightDao
import com.roamatlas.data.local.dao.LocationDao
import com.roamatlas.data.local.dao.UserSettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `flights` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `departureAirportCode` TEXT NOT NULL,
                    `departureAirportName` TEXT,
                    `departureCity` TEXT,
                    `departureCountryCode` TEXT,
                    `arrivalAirportCode` TEXT NOT NULL,
                    `arrivalAirportName` TEXT,
                    `arrivalCity` TEXT,
                    `arrivalCountryCode` TEXT,
                    `departureTimeMillis` INTEGER NOT NULL,
                    `arrivalTimeMillis` INTEGER NOT NULL,
                    `flightNumber` TEXT,
                    `airline` TEXT,
                    `aircraftType` TEXT,
                    `distanceKm` REAL,
                    `durationMinutes` INTEGER,
                    `seatNumber` TEXT,
                    `bookingReference` TEXT,
                    `notes` TEXT,
                    `photoUri` TEXT,
                    `source` TEXT NOT NULL,
                    `createdAtMillis` INTEGER NOT NULL,
                    `updatedAtMillis` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_flights_departureTimeMillis` ON `flights` (`departureTimeMillis`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_flights_departureAirportCode` ON `flights` (`departureAirportCode`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_flights_arrivalAirportCode` ON `flights` (`arrivalAirportCode`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `trips` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `startDateEpochDay` INTEGER NOT NULL,
                    `endDateEpochDay` INTEGER,
                    `destinationCountryCode` TEXT,
                    `destinationCity` TEXT,
                    `notes` TEXT,
                    `coverPhotoUri` TEXT,
                    `photoUrisJson` TEXT,
                    `createdAtMillis` INTEGER NOT NULL,
                    `updatedAtMillis` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `user_settings` (
                    `key` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    PRIMARY KEY(`key`)
                )
                """.trimIndent()
            )
        }
    }
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RoamAtlasDatabase {
        return Room.databaseBuilder(
            context,
            RoamAtlasDatabase::class.java,
            "roamatlas.db"
        )
            .addMigrations(MIGRATION_1_2)
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
    
    @Provides
    fun provideFlightDao(database: RoamAtlasDatabase): FlightDao {
        return database.flightDao()
    }
    
    @Provides
    fun provideUserSettingsDao(database: RoamAtlasDatabase): UserSettingsDao {
        return database.userSettingsDao()
    }
    
    @Provides
    @Singleton
    fun provideBookingEmailParser(): BookingEmailParser {
        return BookingEmailParser()
    }
}
