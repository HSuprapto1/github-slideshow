package com.roamatlas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.roamatlas.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: UserSettingsEntity)
    
    @Query("SELECT value FROM user_settings WHERE `key` = :key")
    suspend fun getSetting(key: String): String?
    
    @Query("SELECT value FROM user_settings WHERE `key` = :key")
    fun getSettingFlow(key: String): Flow<String?>
    
    @Query("SELECT * FROM user_settings")
    fun getAllSettings(): Flow<List<UserSettingsEntity>>
    
    @Query("DELETE FROM user_settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)
}
