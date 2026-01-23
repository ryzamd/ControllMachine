package com.ryzamd.shellycontroller.data.remote

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.ryzamd.shellycontroller.data.local.BrokerConfig
import com.ryzamd.shellycontroller.data.remote.models.JsonRpcRequest
import com.ryzamd.shellycontroller.data.remote.models.JsonRpcResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.cancelChildren
import java.util.concurrent.atomic.AtomicInteger

enum class MqttConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

@Singleton
class MqttManager @Inject constructor(private val discoveryManager: DeviceDiscoveryManager) {

    private var client: Mqtt5AsyncClient? = null
    private val json = Json { ignoreUnknownKeys = true }

    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JsonRpcResponse<JsonElement>>>()

    private val _connectionState = MutableStateFlow(MqttConnectionState.DISCONNECTED)
    val connectionState: StateFlow<MqttConnectionState> = _connectionState.asStateFlow()

    private var currentConfig: BrokerConfig? = null
    private val clientId = "android_app_`${UUID.randomUUID()}"
    
    private val managerScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
    )
    private val requestIdCounter = AtomicInteger(0)

    suspend fun connect(config: BrokerConfig) = suspendCancellableCoroutine { continuation ->
        try {
            _connectionState.value = MqttConnectionState.CONNECTING
            currentConfig = config

            client?.disconnect()

            client = MqttClient.builder()
                .useMqttVersion5()
                .identifier(clientId)
                .serverHost(config.host)
                .serverPort(config.port)
                .simpleAuth()
                .username(config.username)
                .password(config.password.toByteArray())
                .applySimpleAuth()
                .automaticReconnectWithDefaultConfig()
                .buildAsync()

            client?.connect()?.whenComplete { _, throwable ->
                if (throwable == null) {
                    _connectionState.value = MqttConnectionState.CONNECTED
                    subscribeToTopics()

                    client?.let { discoveryManager.startDiscovery(it) }

                    Log.d("MqttManager", "Connected successfully")
                    continuation.resume(Unit)
                } else {
                    _connectionState.value = MqttConnectionState.ERROR
                    Log.e("MqttManager", "Connection failed", throwable)
                    continuation.resumeWithException(throwable)
                }
            }
        } catch (e: Exception) {
            _connectionState.value = MqttConnectionState.ERROR
            continuation.resumeWithException(e)
        }
    }

    private fun subscribeToTopics() {
        client?.subscribeWith()
            ?.topicFilter("$clientId/rpc")
            ?.callback { publish -> handleRpcResponse(publish) }
            ?.send()

        Log.d("MqttManager", "Subscribed to `$clientId/rpc")
    }

    private fun handleRpcResponse(publish: Mqtt5Publish) {
        // Move JSON parsing off callback thread to avoid main thread blocking
        managerScope.launch {
            try {
                val payload = String(publish.payloadAsBytes)
                Log.d("MqttManager", "RPC Response: `$payload")

                val response = json.decodeFromString<JsonRpcResponse<JsonElement>>(payload)

                pendingRequests[response.id]?.complete(response)
                pendingRequests.remove(response.id)
            } catch (e: Exception) {
                Log.e("MqttManager", "Error handling RPC response", e)
            }
        }
    }

    suspend fun sendRpcCommand(deviceId: String, method: String, params: JsonElement?, timeout: Long = 5000): JsonRpcResponse<JsonElement> {
        val requestId = requestIdCounter.incrementAndGet()
        val deferred = CompletableDeferred<JsonRpcResponse<JsonElement>>()

        pendingRequests[requestId] = deferred

        val request = JsonRpcRequest(
            id = requestId,
            src = clientId,
            method = method,
            params = params
        )

        val topic = "$deviceId/rpc"
        val payload = json.encodeToString(JsonRpcRequest.serializer(), request).toByteArray()

        Log.d("MqttManager", "Sending RPC: $method to $deviceId")
        Log.d("MqttManager", "Payload: ${String(payload)}")

        client?.publishWith()
            ?.topic(topic)
            ?.payload(payload)
            ?.send()

        return try {
            withTimeout(timeout) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            pendingRequests.remove(requestId)
            throw Exception("Request timeout for device $deviceId")
        }
    }

    fun disconnect() {
        discoveryManager.clearDevices()
        managerScope.coroutineContext.cancelChildren()
        client?.disconnect()
        _connectionState.value = MqttConnectionState.DISCONNECTED
        pendingRequests.clear()
    }

    suspend fun reconnect() {
        currentConfig?.let { config ->
            disconnect()
            connect(config)
        }
    }
}