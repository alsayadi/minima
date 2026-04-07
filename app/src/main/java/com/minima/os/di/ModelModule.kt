package com.minima.os.di

import android.content.Context
import android.content.SharedPreferences
import com.minima.os.model.provider.CloudModelProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModelModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("minima_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideCloudModelProvider(prefs: SharedPreferences): CloudModelProvider {
        val provider = CloudModelProvider()
        val apiKey = prefs.getString("openai_api_key", null)
        if (apiKey != null) {
            provider.configure(apiKey)
        }
        return provider
    }
}
