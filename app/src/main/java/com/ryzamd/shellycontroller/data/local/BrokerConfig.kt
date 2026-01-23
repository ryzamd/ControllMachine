package com.ryzamd.shellycontroller.data.local

data class BrokerConfig(
    val host: String = "",
    val port: Int = 1883,
    val username: String = "",
    val password: String = "",
    val deviceId: String = "",
    val clientId: String = "android_app"
)