package com.ryzamd.shellycontroller.data.remote.models

import kotlinx.serialization.Serializable

@Serializable
data class JsonRpcResponse<T>(
    val id: Int,
    val src: String,
    val dst: String? = null,
    val result: T? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String
)