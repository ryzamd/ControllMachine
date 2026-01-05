package com.ryzamd.shellycontroller.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedDeviceDao {
    
    @Query("SELECT * FROM saved_devices ORDER BY addedAt DESC")
    fun getAllDevices(): Flow<List<SavedDevice>>
    
    @Query("SELECT * FROM saved_devices WHERE deviceId = :deviceId")
    suspend fun getDevice(deviceId: String): SavedDevice?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: SavedDevice)
    
    @Delete
    suspend fun deleteDevice(device: SavedDevice)
    
    @Query("DELETE FROM saved_devices WHERE deviceId = :deviceId")
    suspend fun deleteDeviceById(deviceId: String)
    
    @Query("SELECT EXISTS(SELECT 1 FROM saved_devices WHERE deviceId = :deviceId)")
    suspend fun isDeviceSaved(deviceId: String): Boolean
    
    @Query("UPDATE saved_devices SET displayName = :newName WHERE deviceId = :deviceId")
    suspend fun updateDeviceName(deviceId: String, newName: String)
}
