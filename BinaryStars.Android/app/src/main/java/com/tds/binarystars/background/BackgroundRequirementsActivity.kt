package com.tds.binarystars.background

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.tds.binarystars.LoginActivity
import com.tds.binarystars.MainActivity
import com.tds.binarystars.R
import com.tds.binarystars.api.AuthTokenStore

class BackgroundRequirementsActivity : AppCompatActivity() {
    private lateinit var missingTextView: TextView

    private val permissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        maybeRequestBackgroundLocation()
        refreshState()
    }

    private val backgroundLocationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_background_requirements)

        missingTextView = findViewById(R.id.tv_missing_requirements)
        val grantPermissionsButton = findViewById<Button>(R.id.btn_grant_permissions)
        val batteryButton = findViewById<Button>(R.id.btn_disable_battery_optimization)
        val appSettingsButton = findViewById<Button>(R.id.btn_open_app_settings)
        val continueButton = findViewById<Button>(R.id.btn_continue_after_requirements)

        grantPermissionsButton.setOnClickListener {
            val permissions = BackgroundRequirementsManager.requiredRuntimePermissions(this)
            if (permissions.isNotEmpty()) {
                permissionsLauncher.launch(permissions)
            } else {
                maybeRequestBackgroundLocation()
            }
        }

        batteryButton.setOnClickListener {
            openBatteryOptimizationSettings()
        }

        appSettingsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        continueButton.setOnClickListener {
            if (BackgroundRequirementsManager.areMandatoryRequirementsMet(this)) {
                continueToApp()
            } else {
                Toast.makeText(this, "Complete all required background settings to continue", Toast.LENGTH_LONG).show()
                refreshState()
            }
        }

        refreshState()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun maybeRequestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && BackgroundRequirementsManager.requiresBackgroundLocationRequest(this)) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
            runCatching {
                startActivity(requestIntent)
            }.onFailure {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            return
        }

        Toast.makeText(this, "Battery optimization control is not required on this Android version", Toast.LENGTH_SHORT).show()
    }

    private fun refreshState() {
        val missing = BackgroundRequirementsManager.missingRequirements(this)
        if (missing.isEmpty()) {
            continueToApp()
            return
        }

        val details = missing.joinToString(separator = "\n") { "• $it" }
        missingTextView.text = "Required to keep BinaryStars running in background:\n$details"
    }

    private fun continueToApp() {
        val destination = if (AuthTokenStore.hasStoredSession()) {
            MainActivity::class.java
        } else {
            LoginActivity::class.java
        }

        startActivity(Intent(this, destination))
        finish()
    }
}
