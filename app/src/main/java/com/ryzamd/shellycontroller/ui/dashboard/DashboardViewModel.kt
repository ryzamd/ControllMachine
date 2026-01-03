package com.ryzamd.shellycontroller.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryzamd.shellycontroller.data.local.BrokerConfigRepository
import com.ryzamd.shellycontroller.data.remote.MqttConnectionState
import com.ryzamd.shellycontroller.data.remote.MqttManager
import com.ryzamd.shellycontroller.repository.ShellyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isConnected: Boolean = false,
    val isSwitchOn: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(private val mqttManager: MqttManager, private val shellyRepository: ShellyRepository, private val configRepo: BrokerConfigRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeConnectionState()
        observeDeviceNotifications()
        connectToBroker()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            mqttManager.connectionState.collect { state ->
                _uiState.update { it.copy(isConnected = state == MqttConnectionState.CONNECTED) }
            }
        }
    }

    private fun observeDeviceNotifications() {
        viewModelScope.launch {
            mqttManager.deviceNotifications.collect { notification ->
                // Parse notification to update switch state
                val isOn = notification.contains("\"output\":true")
                _uiState.update { it.copy(isSwitchOn = isOn) }
            }
        }
    }

    private fun connectToBroker() {
        viewModelScope.launch {
            configRepo.brokerConfig.collectLatest { config ->
                try {
                    mqttManager.connect(config)
                    // Get initial switch status
                    getSwitchStatus()
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = "Connection failed: ${e.message}") }
                }
            }
        }
    }

    fun toggleSwitch() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val newState = !_uiState.value.isSwitchOn

            shellyRepository.setSwitchState(0, newState)
                .onSuccess {
                    _uiState.update { it.copy(isSwitchOn = newState, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    fun getSwitchStatus() {
        viewModelScope.launch {
            shellyRepository.getSwitchStatus(0)
                .onSuccess { isOn ->
                    _uiState.update { it.copy(isSwitchOn = isOn) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}