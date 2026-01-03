package com.ryzamd.shellycontroller.data.remote.models

import kotlinx.serialization.Serializable

@Serializable
data class SwitchSetParams(
    val id: Int,
    val on: Boolean,
    val toggle_after: Int? = null
)