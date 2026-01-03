package com.ryzamd.shellycontroller.data.local

data class BrokerConfig(
    val host: String = "192.168.22.111",
    val port: Int = 1883,
    val username: String = "ryzamdapp2026",
    val password: String = "ryzamd2026",
    val deviceId: String = "shellyplus1-default",
    val clientId: String = "android_app"
)