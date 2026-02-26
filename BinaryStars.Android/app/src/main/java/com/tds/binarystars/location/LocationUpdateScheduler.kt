package com.tds.binarystars.location

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules periodic background location uploads with WorkManager.
 */
object LocationUpdateScheduler {
    private const val WORK_NAME = "location_updates"
    private const val INITIAL_WORK_NAME = "location_updates_initial"

    /** Enqueues periodic location updates. */
    fun schedule(context: Context, intervalMinutes: Int) {
        val repeatMinutes = intervalMinutes.coerceAtLeast(15)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        val request = PeriodicWorkRequestBuilder<LocationUpdateWorker>(repeatMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)

        val initialRequest = OneTimeWorkRequestBuilder<LocationUpdateWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(INITIAL_WORK_NAME, ExistingWorkPolicy.REPLACE, initialRequest)

        LiveLocationService.start(context)
    }

    /** Cancels scheduled location updates. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(INITIAL_WORK_NAME)
        LiveLocationService.stop(context)
    }
}
