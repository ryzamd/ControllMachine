package com.ryzamd.shellycontroller.data.remote

import android.util.Log
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredDevice(
    val deviceId: String,
    val isOnline: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis()
)

data class DeviceStatusUpdate(
    val deviceId: String,
    val isOn: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class DeviceDiscoveryManager @Inject constructor() {

    private val _discoveredDevices = MutableStateFlow<Map<String, DiscoveredDevice>>(emptyMap())
    val discoveredDevices: StateFlow<Map<String, DiscoveredDevice>> = _discoveredDevices.asStateFlow()
    
    private val _deviceStatusUpdates = MutableSharedFlow<DeviceStatusUpdate>(replay = 0)
    val deviceStatusUpdates: SharedFlow<DeviceStatusUpdate> = _deviceStatusUpdates.asSharedFlow()
    
    companion object {
        private const val TAG = "DeviceDiscovery"
    }
    
    private val discoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun startDiscovery(client: Mqtt5AsyncClient) {
        Log.d(TAG, "Starting device discovery...")
        
        client.subscribeWith()
            .topicFilter("+/online")
            .callback { publish -> handleOnlineMessage(publish) }
            .send()
            .whenComplete { _, error ->
                if (error != null) {
                    Log.e(TAG, "Failed to subscribe +/online", error)
                } else {
                    Log.d(TAG, "Subscribed to +/online")
                }
            }
        
        client.subscribeWith()
            .topicFilter("+/status/switch:0")
            .callback { publish -> handleStatusMessage(publish) }
            .send()
            .whenComplete { _, error ->
                if (error != null) {
                    Log.e(TAG, "Failed to subscribe +/status/switch:0", error)
                } else {
                    Log.d(TAG, "Subscribed to +/status/switch:0")
                }
            }
        
        client.subscribeWith()
            .topicFilter("+/events/rpc")
            .callback { publish -> handleEventMessage(publish) }
            .send()
            .whenComplete { _, error ->
                if (error != null) {
                    Log.e(TAG, "Failed to subscribe +/events/rpc", error)
                } else {
                    Log.d(TAG, "Subscribed to +/events/rpc")
                }
            }
    }

    private fun handleOnlineMessage(publish: Mqtt5Publish) {
        // Move processing off main thread to avoid skipped frames
        discoveryScope.launch {
            val topic = publish.topic.toString()
            val payload = String(publish.payloadAsBytes)
            
            val deviceId = topic.substringBefore("/online")
            
            if (deviceId.isNotEmpty() && isValidShellyDevice(deviceId)) {
                val isOnline = payload.trim().equals("true", ignoreCase = true)
                updateDevice(deviceId, isOnline)
                Log.d(TAG, "Device ${if (isOnline) "ONLINE" else "OFFLINE"}: $deviceId")
            }
        }
    }

    private fun handleStatusMessage(publish: Mqtt5Publish) {
        // Move processing off main thread to avoid skipped frames
        discoveryScope.launch {
            val topic = publish.topic.toString()
            val payload = String(publish.payloadAsBytes)
            
            val deviceId = topic.substringBefore("/status")
            
            if (deviceId.isNotEmpty() && isValidShellyDevice(deviceId)) {
                updateDevice(deviceId, true)
                
                val isOn = payload.contains("\"output\":true", ignoreCase = true)
                _deviceStatusUpdates.emit(value = DeviceStatusUpdate(deviceId, isOn))
                
                Log.d(TAG, "Status update: $deviceId -> ${if (isOn) "ON" else "OFF"}")
            }
        }
    }

    private fun handleEventMessage(publish: Mqtt5Publish) {
        // Move processing off main thread to avoid skipped frames
        discoveryScope.launch {
            val topic = publish.topic.toString()
            val deviceId = topic.substringBefore("/events")
            
            if (deviceId.isNotEmpty() && isValidShellyDevice(deviceId)) {
                updateDevice(deviceId, true)
                Log.d(TAG, "Event received from: $deviceId")
            }
        }
    }

    private fun updateDevice(deviceId: String, isOnline: Boolean) {
        val currentMap = _discoveredDevices.value.toMutableMap()
        currentMap[deviceId] = DiscoveredDevice(
            deviceId = deviceId,
            isOnline = isOnline,
            lastSeen = System.currentTimeMillis()
        )
        _discoveredDevices.value = currentMap
    }

    private fun isValidShellyDevice(deviceId: String): Boolean {
        return deviceId.startsWith("shelly", ignoreCase = true) && 
               deviceId.contains("-") &&
               deviceId.length > 10
    }

    fun clearDevices() {
        _discoveredDevices.value = emptyMap()
        Log.d(TAG, "Cleared all discovered devices")
    }
}
