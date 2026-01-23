package com.ryzamd.shellycontroller.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryzamd.shellycontroller.data.local.BrokerConfigRepository
import com.ryzamd.shellycontroller.data.local.SavedDevice
import com.ryzamd.shellycontroller.data.local.SavedDeviceDao
import com.ryzamd.shellycontroller.data.remote.DeviceDiscoveryManager
import com.ryzamd.shellycontroller.data.remote.MqttConnectionState
import com.ryzamd.shellycontroller.data.remote.MqttManager
import com.ryzamd.shellycontroller.repository.ShellyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShellyDeviceUiState(
    val deviceId: String,
    val displayName: String,
    val isSaved: Boolean,
    val isOnline: Boolean,
    val isSwitchOn: Boolean = false,
    val isConnecting: Boolean = false
)

data class DashboardUiState(
    val isBrokerConnected: Boolean = false,
    val isConnecting: Boolean = true,
    val devices: List<ShellyDeviceUiState> = emptyList(),
    val isScanning: Boolean = false,
    val error: String? = null,
    val connectionError: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val mqttManager: MqttManager,
    private val discoveryManager: DeviceDiscoveryManager,
    private val savedDeviceDao: SavedDeviceDao,
    private val shellyRepository: ShellyRepository,
    private val configRepo: BrokerConfigRepository
) : ViewModel() {

    private val savedDevicesFlow = savedDeviceDao.getAllDevices()
    private val discoveredDevicesFlow = discoveryManager.discoveredDevices
    private val _switchStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _connectingDevices = MutableStateFlow<Set<String>>(emptySet())
    private val _connectionError = MutableStateFlow<String?>(null)
    private val connectionState = mqttManager.connectionState

    val uiState: StateFlow<DashboardUiState> = combine(
        connectionState,
        savedDevicesFlow,
        discoveredDevicesFlow,
        _switchStates,
        _connectingDevices,
        _connectionError
    ) { values ->
        val brokerConnected = values[0] as MqttConnectionState
        @Suppress("UNCHECKED_CAST")
        val savedList = values[1] as List<SavedDevice>
        @Suppress("UNCHECKED_CAST")
        val discoveredMap = values[2] as Map<String, com.ryzamd.shellycontroller.data.remote.DiscoveredDevice>
        @Suppress("UNCHECKED_CAST")
        val switchStates = values[3] as Map<String, Boolean>
        @Suppress("UNCHECKED_CAST")
        val connectingSet = values[4] as Set<String>
        val connectionError = values[5] as String?

        val mergedDevices = mergeDeviceLists(
            savedList = savedList,
            discoveredMap = discoveredMap,
            switchStates = switchStates,
            connectingSet = connectingSet
        )

        DashboardUiState(
            isBrokerConnected = brokerConnected == MqttConnectionState.CONNECTED,
            isConnecting = brokerConnected == MqttConnectionState.CONNECTING,
            devices = mergedDevices,
            isScanning = false,
            connectionError = connectionError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    init {
        observeDeviceStatusUpdates()
        connectToBroker()
    }

    private fun mergeDeviceLists(
        savedList: List<SavedDevice>,
        discoveredMap: Map<String, com.ryzamd.shellycontroller.data.remote.DiscoveredDevice>,
        switchStates: Map<String, Boolean>,
        connectingSet: Set<String>
    ): List<ShellyDeviceUiState> {

        val result = mutableListOf<ShellyDeviceUiState>()
        val savedIds = savedList.map { it.deviceId }.toSet()

        savedList.forEach { saved ->
            val discovered = discoveredMap[saved.deviceId]
            result.add(
                ShellyDeviceUiState(
                    deviceId = saved.deviceId,
                    displayName = saved.displayName,
                    isSaved = true,
                    isOnline = discovered?.isOnline ?: false,
                    isSwitchOn = switchStates[saved.deviceId] ?: false,
                    isConnecting = false
                )
            )
        }

        discoveredMap.values.forEach { discovered ->
            if (!savedIds.contains(discovered.deviceId)) {
                result.add(
                    ShellyDeviceUiState(
                        deviceId = discovered.deviceId,
                        displayName = extractDeviceModel(discovered.deviceId),
                        isSaved = false,
                        isOnline = discovered.isOnline,
                        isSwitchOn = switchStates[discovered.deviceId] ?: false,
                        isConnecting = connectingSet.contains(discovered.deviceId)
                    )
                )
            }
        }

        return result.sortedWith(
            compareByDescending<ShellyDeviceUiState> { it.isSaved }
                .thenByDescending { it.isOnline }
                .thenBy { it.deviceId }
        )
    }

    private fun extractDeviceModel(deviceId: String): String {
        return when {
            deviceId.contains("plus1pm", ignoreCase = true) -> "Shelly Plus 1PM"
            deviceId.contains("plus1", ignoreCase = true) -> "Shelly Plus 1"
            deviceId.contains("plus2pm", ignoreCase = true) -> "Shelly Plus 2PM"
            deviceId.contains("plugs", ignoreCase = true) -> "Shelly Plug S"
            deviceId.contains("pro4pm", ignoreCase = true) -> "Shelly Pro 4PM"
            else -> deviceId
        }
    }

    private fun observeDeviceStatusUpdates() {
        viewModelScope.launch {
            discoveryManager.deviceStatusUpdates.collect { update ->
                _switchStates.update { currentStates ->
                    currentStates + (update.deviceId to update.isOn)
                }
            }
        }
    }

    fun toggleSwitch(device: ShellyDeviceUiState) {
        Log.d("DashboardViewModel", "Toggle: deviceId=${device.deviceId}, current=${device.isSwitchOn}, sending=${!device.isSwitchOn}")

        if (!device.isSaved || !device.isOnline) return

        viewModelScope.launch {
            try {
                val newState = !device.isSwitchOn
                Log.d("DashboardViewModel", "Calling setSwitchState with on=$newState")

                val result = shellyRepository.setSwitchState(
                    deviceId = device.deviceId,
                    switchId = 0,
                    on = newState
                )

                result.onSuccess {
                    _switchStates.update { currentStates ->
                        currentStates + (device.deviceId to newState)
                    }
                    Log.d("DashboardViewModel", "Updated state to $newState")
                }
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Toggle failed", e)
            }
        }
    }

    fun connectDevice(deviceId: String) {
        Log.d("DashboardViewModel", "connectDevice called for: $deviceId")
        viewModelScope.launch {
            _connectingDevices.update { it + deviceId }

            try {
                Log.d("DashboardViewModel", "📡 Calling getSwitchStatus...")
                val status = shellyRepository.getSwitchStatus(deviceId, 0)

                status.onSuccess { isOn ->
                    Log.d("DashboardViewModel", "Got status: $isOn")
                    val displayName = extractDeviceModel(deviceId)
                    savedDeviceDao.insertDevice(
                        SavedDevice(
                            deviceId = deviceId,
                            displayName = displayName
                        )
                    )

                    _switchStates.update { currentStates ->
                        currentStates + (deviceId to isOn)
                    }
                }.onFailure { e ->
                    Log.e("DashboardViewModel", "Failed: ${e.message}", e)
                }
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Exception: ${e.message}", e)
            } finally {
                _connectingDevices.update { it - deviceId }
            }
        }
    }

    fun deleteDevice(deviceId: String) {
        viewModelScope.launch {
            savedDeviceDao.deleteDeviceById(deviceId)
        }
    }

    fun renameDevice(deviceId: String, newName: String) {
        viewModelScope.launch {
            savedDeviceDao.updateDeviceName(deviceId, newName)
        }
    }

    fun refreshDiscovery() {
        viewModelScope.launch {
            try {
                mqttManager.reconnect()
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Reconnect failed", e)
            }
        }
    }

    private fun connectToBroker() {
        viewModelScope.launch {
            try {
                val config = configRepo.brokerConfig.first()
                if (mqttManager.connectionState.value != MqttConnectionState.CONNECTED) {
                    mqttManager.connect(config)
                    _connectionError.value = null
                }
            } catch (e: Exception) {
                _connectionError.value = e.message ?: "Connection failed"
                Log.e("DashboardViewModel", "Connection failed", e)
            }
        }
    }
}