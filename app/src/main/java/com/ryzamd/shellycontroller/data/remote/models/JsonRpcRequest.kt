package com.ryzamd.shellycontroller.data.remote.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class JsonRpcRequest(
    val id: Int,
    val src: String,
    val method: String,
    val params: JsonElement? = null,
    val jsonrpc: String = "2.0"
)