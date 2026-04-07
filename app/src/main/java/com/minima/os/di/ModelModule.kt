package com.minima.os.di

import android.content.Context
import android.content.SharedPreferences
import com.minima.os.model.provider.CloudModelProvider
import com.minima.os.model.provider.Provider
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
        val cp = CloudModelProvider()
        val providerName = prefs.getString("llm_provider", Provider.OPENAI.name) ?: Provider.OPENAI.name
        val selected = try { Provider.valueOf(providerName) } catch (_: Exception) { Provider.OPENAI }
        // Per-provider key: api_key_OPENAI, api_key_GROQ, etc. Fallback to legacy openai_api_key.
        val key = prefs.getString("api_key_${selected.name}", null)
            ?: prefs.getString("openai_api_key", null)
        val model = prefs.getString("llm_model_${selected.name}", null)
        if (key != null) {
            cp.configureProvider(selected, key, model)
        }
        return cp
    }
}
