package com.ryzamd.shellycontroller.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryzamd.shellycontroller.data.local.BrokerConfig
import com.ryzamd.shellycontroller.data.local.BrokerConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val config: BrokerConfig = BrokerConfig(),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val configRepo: BrokerConfigRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadCurrentConfig()
    }

    private fun loadCurrentConfig() {
        viewModelScope.launch {
            val config = configRepo.brokerConfig.first()
            _uiState.value = _uiState.value.copy(config = config)
        }
    }

    fun updateHost(host: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(host = host)
        )
    }

    fun updatePort(port: String) {
        val portInt = port.toIntOrNull() ?: 1883
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(port = portInt)
        )
    }

    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(username = username)
        )
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(password = password)
        )
    }

    fun updateClientId(clientId: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(clientId = clientId)
        )
    }

    fun saveConfig() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            try {
                configRepo.updateConfig(_uiState.value.config)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveSuccess = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message
                )
            }
        }
    }

    fun resetSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }
}