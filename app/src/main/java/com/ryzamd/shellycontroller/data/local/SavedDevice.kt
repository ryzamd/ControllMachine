package com.ryzamd.shellycontroller.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_devices")
data class SavedDevice(
    @PrimaryKey val deviceId: String,
    val displayName: String,
    val addedAt: Long = System.currentTimeMillis()
)
