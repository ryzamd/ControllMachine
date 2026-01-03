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

    suspend fun setSwitchState(switchId: Int, on: Boolean): Result<Boolean> {
        return try {
            val params = SwitchSetParams(id = switchId, on = on)
            val response = mqttManager.sendRpcCommand("Switch.Set", params)

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

    suspend fun getSwitchStatus(switchId: Int): Result<Boolean> {
        return try {
            val params = mapOf("id" to switchId)
            val response = mqttManager.sendRpcCommand("Switch.GetStatus", params)

            if (response.error != null) {
                Result.failure(Exception(response.error.message))
            } else {
                // Parse output from result
                val output = response.result?.toString()?.contains("\"output\":true") ?: false
                Result.success(output)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}