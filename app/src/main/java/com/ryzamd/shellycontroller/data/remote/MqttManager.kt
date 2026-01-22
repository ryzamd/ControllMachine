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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class MqttConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

@Singleton
class MqttManager @Inject constructor(private val discoveryManager: DeviceDiscoveryManager) {

    var client: Mqtt5AsyncClient? = null
    public val json = Json { ignoreUnknownKeys = true }

    val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JsonRpcResponse<JsonElement>>>()

    private val _connectionState = MutableStateFlow(MqttConnectionState.DISCONNECTED)
    val connectionState: StateFlow<MqttConnectionState> = _connectionState.asStateFlow()

    private val _deviceNotifications = MutableSharedFlow<String>()
    val deviceNotifications: SharedFlow<String> = _deviceNotifications.asSharedFlow()

    private var currentConfig: BrokerConfig? = null
    public val clientId = "android_app_`${UUID.randomUUID()}"

    val discoveredDevices = discoveryManager.discoveredDevices

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

    fun subscribeToDevice(deviceId: String) {
        client?.subscribeWith()
            ?.topicFilter("`$deviceId/events/rpc")
            ?.callback { publish -> handleNotification(publish) }
            ?.send()

        client?.subscribeWith()
            ?.topicFilter("`$deviceId/status/switch:0")
            ?.callback { publish -> handleNotification(publish) }
            ?.send()

        client?.subscribeWith()
            ?.topicFilter("$deviceId/online")
            ?.callback { publish ->
                val online = String(publish.payloadAsBytes)
                Log.d("MqttManager", "Device `$deviceId online: `$online")
            }
            ?.send()

        Log.d("MqttManager", "Subscribed to device: `$deviceId")
    }

    private fun handleRpcResponse(publish: Mqtt5Publish) {
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

    private fun handleNotification(publish: Mqtt5Publish) {
        try {
            val payload = String(publish.payloadAsBytes)
            Log.d("MqttManager", "Notification: `$payload")

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                _deviceNotifications.emit(value = payload)
            }
        } catch (e: Exception) {
            Log.e("MqttManager", "Error handling notification", e)
        }
    }

    suspend inline fun <reified T> sendRpcCommand(
        deviceId: String,
        method: String,
        params: T?,
        timeout: Long = 5000
    ): JsonRpcResponse<JsonElement> {
        val requestId = (1..1000000).random()
        val deferred = CompletableDeferred<JsonRpcResponse<JsonElement>>()

        pendingRequests[requestId] = deferred

        val request = JsonRpcRequest(
            id = requestId,
            src = clientId,
            method = method,
            params = params?.let { json.encodeToJsonElement(it) }
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
        client?.disconnect()
        _connectionState.value = MqttConnectionState.DISCONNECTED
        pendingRequests.clear()
    }
}