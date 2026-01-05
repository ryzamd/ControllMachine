package com.ryzamd.shellycontroller.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val devices: List<ShellyDeviceUiState> = emptyList(),
    val isScanning: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val mqttManager: MqttManager,
    private val discoveryManager: DeviceDiscoveryManager,
    private val savedDeviceDao: SavedDeviceDao,
    private val shellyRepository: ShellyRepository
) : ViewModel() {

    private val savedDevicesFlow = savedDeviceDao.getAllDevices()
    private val discoveredDevicesFlow = discoveryManager.discoveredDevices
    private val _switchStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _connectingDevices = MutableStateFlow<Set<String>>(emptySet())
    private val connectionState = mqttManager.connectionState

    val uiState: StateFlow<DashboardUiState> = combine(
        connectionState,
        savedDevicesFlow,
        discoveredDevicesFlow,
        _switchStates,
        _connectingDevices
    ) { brokerConnected, savedList, discoveredMap, switchStates, connectingSet ->

        val mergedDevices = mergeDeviceLists(
            savedList = savedList,
            discoveredMap = discoveredMap,
            switchStates = switchStates,
            connectingSet = connectingSet
        )

        DashboardUiState(
            isBrokerConnected = brokerConnected == MqttConnectionState.CONNECTED,
            devices = mergedDevices,
            isScanning = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    init {
        observeDeviceStatusUpdates()
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
                val currentStates = _switchStates.value.toMutableMap()
                currentStates[update.deviceId] = update.isOn
                _switchStates.value = currentStates
            }
        }
    }

    fun toggleSwitch(device: ShellyDeviceUiState) {
        if (!device.isSaved || !device.isOnline) return

        viewModelScope.launch {
            try {
                shellyRepository.setSwitchState(
                    deviceId = device.deviceId,
                    switchId = 0,
                    on = !device.isSwitchOn
                )
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun connectDevice(deviceId: String) {
        viewModelScope.launch {
            _connectingDevices.value = _connectingDevices.value + deviceId

            try {
                val status = shellyRepository.getSwitchStatus(deviceId, 0)

                status.onSuccess { isOn ->
                    val displayName = extractDeviceModel(deviceId)
                    savedDeviceDao.insertDevice(
                        SavedDevice(
                            deviceId = deviceId,
                            displayName = displayName
                        )
                    )

                    val currentStates = _switchStates.value.toMutableMap()
                    currentStates[deviceId] = isOn
                    _switchStates.value = currentStates
                }
            } catch (e: Exception) {
                // Connection failed
            } finally {
                _connectingDevices.value = _connectingDevices.value - deviceId
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
        discoveryManager.clearDevices()
    }
}