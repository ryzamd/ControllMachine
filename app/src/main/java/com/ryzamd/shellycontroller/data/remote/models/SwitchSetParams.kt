package com.ryzamd.shellycontroller.data.remote.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SwitchSetParams(
    val id: Int,
    val on: Boolean,
    @SerialName("toggle_after")
    val toggleAfter: Int? = null
)