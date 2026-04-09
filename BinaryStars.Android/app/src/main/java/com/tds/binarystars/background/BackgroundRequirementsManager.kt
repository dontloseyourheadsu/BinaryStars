package com.tds.binarystars.background

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

object BackgroundRequirementsManager {
    fun areMandatoryRequirementsMet(context: Context): Boolean = missingRequirements(context).isEmpty()

    fun missingRequirements(context: Context): List<String> {
        val missing = mutableListOf<String>()

        if (!hasForegroundLocationPermission(context)) {
            missing.add("Location permission")
        }

        if (!hasBackgroundLocationPermission(context)) {
            missing.add("Background location permission")
        }

        if (!hasNotificationPermission(context)) {
            missing.add("Notification permission")
        }

        if (!isIgnoringBatteryOptimizations(context)) {
            missing.add("Unrestricted battery mode")
        }

        return missing
    }

    fun requiredRuntimePermissions(context: Context): Array<String> {
        val permissions = mutableListOf<String>()

        if (!hasForegroundLocationPermission(context)) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context)) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions.distinct().toTypedArray()
    }

    fun requiresBackgroundLocationRequest(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        return !hasBackgroundLocationPermission(context)
    }

    private fun hasForegroundLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun hasBackgroundLocationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true
        }

        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
