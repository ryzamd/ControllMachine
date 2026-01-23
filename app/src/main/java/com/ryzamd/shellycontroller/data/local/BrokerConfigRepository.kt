package com.ryzamd.shellycontroller.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mqtt_broker_settings")

@Singleton
class BrokerConfigRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val HOST = stringPreferencesKey("broker_host")
        val PORT = intPreferencesKey("broker_port")
        val USER = stringPreferencesKey("username")
        val PASS = stringPreferencesKey("password")
        val CLIENT_ID = stringPreferencesKey("client_id")
    }

    val brokerConfig: Flow<BrokerConfig> = context.dataStore.data.map { prefs ->
        BrokerConfig(
            host = prefs[Keys.HOST] ?: "",
            port = prefs[Keys.PORT] ?: 1883,
            username = prefs[Keys.USER] ?: "",
            password = prefs[Keys.PASS] ?: "",
            clientId = prefs[Keys.CLIENT_ID] ?: "android_app"
        )
    }

    suspend fun updateConfig(newConfig: BrokerConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOST] = newConfig.host
            prefs[Keys.PORT] = newConfig.port
            prefs[Keys.USER] = newConfig.username
            prefs[Keys.PASS] = newConfig.password
            prefs[Keys.CLIENT_ID] = newConfig.clientId
        }
    }
}