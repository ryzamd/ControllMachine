package com.ryzamd.shellycontroller.data.remote.models

import kotlinx.serialization.Serializable

@Serializable
data class SwitchStatus(
    val id: Int,
    val output: Boolean,
    val source: String? = null,
    val temperature: Temperature? = null,
    val apower: Double? = null,
    val voltage: Double? = null
)

@Serializable
data class Temperature(
    val tC: Double?,
    val tF: Double?
)

@Serializable
data class SwitchSetResult(
    val was_on: Boolean
)