package com.ryzamd.shellycontroller.data.local

data class BrokerConfig(
    val host: String = "RCCServer.local",
    val port: Int = 1883,
    val username: String = "MobileRCC",
    val password: String = "MobileRCC@#!",
    val clientId: String = "android_app"
)