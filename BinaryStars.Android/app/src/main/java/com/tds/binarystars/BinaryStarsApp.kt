package com.tds.binarystars

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.tds.binarystars.api.AuthTokenStore
import com.tds.binarystars.storage.ChatStorage
import com.tds.binarystars.storage.DeviceCacheStorage
import com.tds.binarystars.storage.FileTransferStorage
import com.tds.binarystars.storage.LocationCacheStorage
import com.tds.binarystars.storage.NotesStorage
import com.tds.binarystars.storage.SettingsStorage
import com.tds.binarystars.location.LocationUpdateScheduler
import com.tds.binarystars.location.LiveLocationService
import org.maplibre.android.MapLibre

class BinaryStarsApp : Application() {
    /**
     * Initializes app-wide singletons and schedules background work.
     */
    override fun onCreate() {
        super.onCreate()
        AuthTokenStore.init(this)
        SettingsStorage.init(this)
        ChatStorage.init(this)
        NotesStorage.init(this)
        FileTransferStorage.init(this)
        DeviceCacheStorage.init(this)
        LocationCacheStorage.init(this)
        MapLibre.getInstance(this)

        if (SettingsStorage.areLocationUpdatesEnabled(false)) {
            val minutes = SettingsStorage.getLocationUpdateMinutes(15)
            LocationUpdateScheduler.schedule(this, minutes)
            LiveLocationService.start(this)
        }

        AppCompatDelegate.setDefaultNightMode(
            SettingsStorage.getAppCompatNightMode(SettingsStorage.ThemeMode.SYSTEM)
        )
    }
}
