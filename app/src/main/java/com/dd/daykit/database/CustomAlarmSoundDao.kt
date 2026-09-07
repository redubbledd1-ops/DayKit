package com.dd.daykit.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for custom alarm sounds
 */
@Dao
interface CustomAlarmSoundDao {
    
    @Query("SELECT * FROM custom_alarm_sounds ORDER BY addedTimestamp DESC")
    fun getAllSounds(): Flow<List<CustomAlarmSound>>
    
    @Query("SELECT * FROM custom_alarm_sounds ORDER BY addedTimestamp DESC")
    suspend fun getAllSoundsList(): List<CustomAlarmSound>
    
    @Query("SELECT * FROM custom_alarm_sounds WHERE id = :id")
    suspend fun getSoundById(id: Long): CustomAlarmSound?
    
    @Query("SELECT * FROM custom_alarm_sounds WHERE storedFilename = :filename")
    suspend fun getSoundByFilename(filename: String): CustomAlarmSound?

    @Query("UPDATE custom_alarm_sounds SET haSoundUrl = :url WHERE id = :id")
    suspend fun updateHaSoundUrl(id: Long, url: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sound: CustomAlarmSound): Long
    
    @Delete
    suspend fun delete(sound: CustomAlarmSound)
    
    @Query("DELETE FROM custom_alarm_sounds WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM custom_alarm_sounds")
    suspend fun deleteAll()
}
