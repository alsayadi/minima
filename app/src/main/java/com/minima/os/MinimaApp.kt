package com.minima.os

import android.app.Application
import android.util.Log
import com.minima.os.data.security.SecurePrefs
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MinimaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // One-shot migration: move API keys from plain minima_prefs into the
        // encrypted store. Idempotent — no-op once keys already live in SecurePrefs.
        runCatching {
            val n = SecurePrefs.migrateFromPlainPrefs(this)
            if (n > 0) Log.i("MinimaApp", "Migrated $n secret(s) from plain prefs → SecurePrefs")
        }
    }
}

