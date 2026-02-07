package com.tds.binarystars.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import com.tds.binarystars.R
import com.tds.binarystars.LoginActivity
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.AuthTokenStore
import com.tds.binarystars.api.UserRoleDto
import com.tds.binarystars.storage.SettingsStorage
import com.tds.binarystars.MainActivity
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.ivMenu)?.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }
        
        val switchTheme = view.findViewById<Switch>(R.id.switchTheme)
        val tvUsername = view.findViewById<TextView>(R.id.tvUsername)
        val tvPlanStatus = view.findViewById<TextView>(R.id.tvPlanStatus)
        val tvEmail = view.findViewById<TextView>(R.id.tvEmail)
        val tvDevicesCount = view.findViewById<TextView>(R.id.tvDevicesCount)
        val devicesListContainer = view.findViewById<LinearLayout>(R.id.devicesListContainer)
        val btnUpgradePlan = view.findViewById<Button>(R.id.btnUpgradePlan)
        val btnSignOut = view.findViewById<Button>(R.id.btnSignOut)
        
        // Initialize state
        val isDarkModeEnabled = SettingsStorage.isDarkModeEnabled(false)
        switchTheme.isChecked = isDarkModeEnabled
        
        switchTheme.setOnCheckedChangeListener { _, isChecked ->
             if (isChecked) {
                 AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
             } else {
                 AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
             }
            SettingsStorage.setDarkModeEnabled(isChecked)
        }

        btnUpgradePlan.setOnClickListener {
            toast("Upgrade coming soon")
        }

        btnSignOut.setOnClickListener {
            AuthTokenStore.clear()
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        loadAccountProfile(tvUsername, tvEmail, tvPlanStatus, btnUpgradePlan)
        loadDevices(tvDevicesCount, devicesListContainer)
    }

    private fun loadAccountProfile(
        tvUsername: TextView,
        tvEmail: TextView,
        tvPlanStatus: TextView,
        btnUpgradePlan: Button
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getProfile()
                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!
                    tvUsername.text = profile.username
                    tvEmail.text = profile.email

                    when (profile.role) {
                        UserRoleDto.Premium -> {
                            tvPlanStatus.text = "Premium"
                            btnUpgradePlan.visibility = View.GONE
                        }
                        UserRoleDto.Free -> {
                            tvPlanStatus.text = "Free"
                            btnUpgradePlan.visibility = View.VISIBLE
                        }
                        UserRoleDto.Disabled -> {
                            tvPlanStatus.text = "Disabled"
                            btnUpgradePlan.visibility = View.GONE
                        }
                    }
                } else {
                    tvUsername.text = "Unknown user"
                }
            } catch (e: Exception) {
                tvUsername.text = "Unknown user"
            }
        }
    }

    private fun loadDevices(tvDevicesCount: TextView, devicesListContainer: LinearLayout) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getDevices()
                if (response.isSuccessful && response.body() != null) {
                    val devices = response.body()!!
                    tvDevicesCount.text = "Connected devices (${devices.size})"
                    devicesListContainer.removeAllViews()

                    if (devices.isEmpty()) {
                        addDeviceName(devicesListContainer, "No devices connected")
                    } else {
                        devices.forEach { device ->
                            addDeviceName(devicesListContainer, device.name)
                        }
                    }
                } else {
                    tvDevicesCount.text = "Connected devices (0)"
                }
            } catch (e: Exception) {
                tvDevicesCount.text = "Connected devices (0)"
            }
        }
    }

    private fun addDeviceName(container: LinearLayout, name: String) {
        val textView = TextView(requireContext())
        textView.text = name
        textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.carbon_text_muted))
        textView.textSize = 12f
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = (4 * resources.displayMetrics.density).toInt()
        textView.layoutParams = params
        container.addView(textView)
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }
}