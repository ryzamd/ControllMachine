package com.ryzamd.shellycontroller.di

import android.content.Context
import com.ryzamd.shellycontroller.data.local.BrokerConfigRepository
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
    fun provideBrokerConfigRepository(
        @ApplicationContext context: Context
    ): BrokerConfigRepository {
        return BrokerConfigRepository(context)
    }
}