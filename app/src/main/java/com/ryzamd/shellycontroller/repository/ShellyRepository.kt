package com.ryzamd.shellycontroller.repository

import com.ryzamd.shellycontroller.data.remote.MqttManager
import com.ryzamd.shellycontroller.data.remote.models.SwitchSetParams
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShellyRepository @Inject constructor(private val mqttManager: MqttManager) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun setSwitchState(deviceId: String, switchId: Int, on: Boolean): Result<Boolean> {
        return try {
            val params = SwitchSetParams(id = switchId, on = on)
            val response = mqttManager.sendRpcCommand(deviceId, "Switch.Set", json.encodeToJsonElement(params))

            if (response.error != null) {
                Result.failure(Exception(response.error.message))
            } else {
                Result.success(on)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSwitchStatus(deviceId: String, switchId: Int): Result<Boolean> {
        return try {
            val params = mapOf("id" to switchId)
            val response = mqttManager.sendRpcCommand(deviceId, "Switch.GetStatus", json.encodeToJsonElement(params))

            if (response.error != null) {
                Result.failure(Exception(response.error.message))
            } else {
                val isOn = response.result?.toString()?.contains("\"output\":true", ignoreCase = true) ?: false
                Result.success(isOn)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}