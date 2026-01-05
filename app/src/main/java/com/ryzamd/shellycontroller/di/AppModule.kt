package com.ryzamd.shellycontroller.di

import android.content.Context
import com.ryzamd.shellycontroller.data.local.AppDatabase
import com.ryzamd.shellycontroller.data.local.BrokerConfigRepository
import com.ryzamd.shellycontroller.data.local.SavedDeviceDao
import com.ryzamd.shellycontroller.data.remote.DeviceDiscoveryManager
import com.ryzamd.shellycontroller.data.remote.MqttManager
import com.ryzamd.shellycontroller.repository.ShellyRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideSavedDeviceDao(database: AppDatabase): SavedDeviceDao {
        return database.savedDeviceDao()
    }

    @Provides
    @Singleton
    fun provideBrokerConfigRepository(@ApplicationContext context: Context): BrokerConfigRepository {
        return BrokerConfigRepository(context)
    }

    @Provides
    @Singleton
    fun provideDeviceDiscoveryManager(): DeviceDiscoveryManager {
        return DeviceDiscoveryManager()
    }

    @Provides
    @Singleton
    fun provideMqttManager(discoveryManager: DeviceDiscoveryManager): MqttManager {
        return MqttManager(discoveryManager)
    }

    @Provides
    @Singleton
    fun provideShellyRepository(mqttManager: MqttManager): ShellyRepository {
        return ShellyRepository(mqttManager)
    }
}
