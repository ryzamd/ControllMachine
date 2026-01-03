package com.ryzamd.shellycontroller.di

import com.ryzamd.shellycontroller.data.remote.MqttManager
import com.ryzamd.shellycontroller.repository.ShellyRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMqttManager(): MqttManager {
        return MqttManager()
    }

    @Provides
    @Singleton
    fun provideShellyRepository(
        mqttManager: MqttManager
    ): ShellyRepository {
        return ShellyRepository(mqttManager)
    }
}