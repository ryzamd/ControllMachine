package com.ryzamd.shellycontroller.repository

import com.ryzamd.shellycontroller.data.remote.MqttManager
import com.ryzamd.shellycontroller.data.remote.models.SwitchSetParams
import com.ryzamd.shellycontroller.data.remote.models.SwitchSetResult
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShellyRepository @Inject constructor(private val mqttManager: MqttManager) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun setSwitchState(
        deviceId: String,
        switchId: Int,
        on: Boolean
    ): Result<Boolean> {
        return try {
            val params = SwitchSetParams(id = switchId, on = on)
            val response = mqttManager.sendRpcCommand(
                deviceId = deviceId,
                method = "Switch.Set",
                params = params
            )

            if (response.error != null) {
                Result.failure(Exception(response.error.message))
            } else {
                val result = response.result?.let {
                    json.decodeFromJsonElement(SwitchSetResult.serializer(), it)
                }
                Result.success(on)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSwitchStatus(
        deviceId: String,
        switchId: Int
    ): Result<Boolean> {
        return try {
            val params = mapOf("id" to switchId)
            val response = mqttManager.sendRpcCommand(
                deviceId = deviceId,
                method = "Switch.GetStatus",
                params = params
            )

            if (response.error != null) {
                Result.failure(Exception(response.error.message))
            } else {
                val output = response.result?.toString()?.contains("\"output\":true") ?: false
                Result.success(output)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDeviceInfo(deviceId: String): Result<String> {
        return try {
            val response = mqttManager.sendRpcCommand(
                deviceId = deviceId,
                method = "Shelly.GetDeviceInfo",
                params = null
            )

            if (response.error != null) {
                Result.failure(Exception(response.error.message))
            } else {
                Result.success(response.result?.toString() ?: "")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}