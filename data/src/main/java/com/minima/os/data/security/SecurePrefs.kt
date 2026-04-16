package com.minima.os.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * AES-256-GCM encrypted SharedPreferences for sensitive values (API keys).
 *
 * Public API is plain String-based so consumers (UI, DI) don't need to depend
 * on androidx.security — they never see EncryptedSharedPreferences types.
 *
 * If the encrypted store fails to initialize on a given device (rare, but can
 * happen with corrupted keystore state after factory reset), falls back to a
 * regular SharedPreferences instance tagged `minima_secure_fallback` and logs
 * a warning. This keeps the app functional; users just don't get at-rest
 * encryption in that edge case.
 */
class SecurePrefs private constructor(private val impl: SharedPreferences) {

    fun getString(key: String, default: String? = null): String? =
        impl.getString(key, default)

    fun putString(key: String, value: String?) {
        impl.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    fun remove(key: String) {
        impl.edit().remove(key).apply()
    }

    fun contains(key: String): Boolean = impl.contains(key)

    companion object {
        private const val FILE_NAME = "minima_secure"
        private const val FALLBACK_FILE_NAME = "minima_secure_fallback"
        private const val TAG = "SecurePrefs"

        @Volatile private var instance: SecurePrefs? = null

        fun get(context: Context): SecurePrefs {
            return instance ?: synchronized(this) {
                instance ?: SecurePrefs(buildImpl(context.applicationContext)).also {
                    instance = it
                }
            }
        }

        private fun buildImpl(ctx: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    ctx,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.w(TAG, "EncryptedSharedPreferences unavailable; falling back to plain prefs: ${e.message}")
                ctx.getSharedPreferences(FALLBACK_FILE_NAME, Context.MODE_PRIVATE)
            }
        }

        /**
         * One-shot migration: moves any pre-existing API keys from the regular
         * `minima_prefs` store into the encrypted store, then wipes the plain
         * copies. Safe to call repeatedly — keys already in SecurePrefs are
         * left alone. Returns the number of keys migrated.
         */
        fun migrateFromPlainPrefs(ctx: Context, plainPrefsName: String = "minima_prefs"): Int {
            val plain = ctx.getSharedPreferences(plainPrefsName, Context.MODE_PRIVATE)
            val secure = get(ctx)
            var migrated = 0
            val toWipe = mutableListOf<String>()

            // Sweep every key that looks like an API key.
            for ((k, v) in plain.all) {
                val looksSecret = k.startsWith("api_key_") ||
                                  k == "openai_api_key" ||
                                  k.endsWith("_api_key") ||
                                  k.contains("token", ignoreCase = true)
                if (looksSecret && v is String && v.isNotBlank()) {
                    // Only copy if target slot is empty — never clobber a newer key.
                    if (secure.getString(k).isNullOrBlank()) {
                        secure.putString(k, v)
                        migrated++
                    }
                    toWipe += k
                }
            }
            if (toWipe.isNotEmpty()) {
                plain.edit().apply {
                    toWipe.forEach { remove(it) }
                }.apply()
            }
            return migrated
        }
    }
}
