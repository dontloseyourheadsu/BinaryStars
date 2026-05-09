package com.tds.binarystars.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tds.binarystars.api.AuthTokenStore
import com.tds.binarystars.location.LiveLocationService
import com.tds.binarystars.location.LocationUpdateScheduler
import com.tds.binarystars.storage.SettingsStorage

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        AuthTokenStore.init(context)
        SettingsStorage.init(context)

        if (!AuthTokenStore.hasStoredSession()) {
            return
        }

        DeviceSyncForegroundService.start(context)
        LocationUpdateScheduler.schedule(context, SettingsStorage.getLocationUpdateMinutes(15))
        LiveLocationService.start(context)
    }
}
