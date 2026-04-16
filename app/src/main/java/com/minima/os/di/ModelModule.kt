package com.minima.os.di

import android.content.Context
import android.content.SharedPreferences
import com.minima.os.data.security.SecurePrefs
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
    fun provideCloudModelProvider(
        prefs: SharedPreferences,
        @ApplicationContext context: Context
    ): CloudModelProvider {
        val cp = CloudModelProvider()
        val secure = SecurePrefs.get(context)
        val userChoice = prefs.getString("llm_provider", Provider.OPENAI.name) ?: Provider.OPENAI.name

        // OODA override: if AUTO_SAFE applied a provider_default change AND the user has
        // a key stored for that provider, honor it. Otherwise stick with the user's pick.
        val oodaPrefs = context.getSharedPreferences("minima_ooda", Context.MODE_PRIVATE)
        val oodaProvider = oodaPrefs.getString("applied_provider_default", null)
        val providerName = if (oodaProvider != null &&
            secure.getString("api_key_$oodaProvider").isNullOrBlank().not()
        ) oodaProvider else userChoice

        val selected = try { Provider.valueOf(providerName) } catch (_: Exception) { Provider.OPENAI }
        // API keys live in the encrypted store. Fallback read from legacy plain prefs for
        // the very first run before the migration in MinimaApp.onCreate has completed on
        // some race — belt-and-suspenders.
        val key = secure.getString("api_key_${selected.name}")
            ?: secure.getString("openai_api_key")
            ?: prefs.getString("api_key_${selected.name}", null)
            ?: prefs.getString("openai_api_key", null)
        val model = prefs.getString("llm_model_${selected.name}", null)
        if (key != null) {
            cp.configureProvider(selected, key, model)
        }
        // OODA-tuned temperature
        oodaPrefs.getString("applied_temperature", null)?.toDoubleOrNull()?.let {
            cp.temperatureOverride = it.coerceIn(0.0, 2.0)
        }
        return cp
    }
}
