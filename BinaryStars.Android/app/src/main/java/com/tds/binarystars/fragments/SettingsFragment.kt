package com.tds.binarystars.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.tds.binarystars.R

class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val switchTheme = view.findViewById<Switch>(R.id.switchTheme)
        
        // Initialize state
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        switchTheme.isChecked = currentMode == AppCompatDelegate.MODE_NIGHT_YES
        
        switchTheme.setOnCheckedChangeListener { _, isChecked ->
             if (isChecked) {
                 AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
             } else {
                 AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
             }
        }
    }
}