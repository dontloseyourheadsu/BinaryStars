package com.tds.binarystars

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tds.binarystars.fragments.DevicesFragment
import com.tds.binarystars.fragments.FilesFragment
import com.tds.binarystars.fragments.MapFragment
import com.tds.binarystars.fragments.NotesFragment
import com.tds.binarystars.fragments.SettingsFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(DevicesFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_devices -> DevicesFragment()
                R.id.nav_files -> FilesFragment()
                R.id.nav_notes -> NotesFragment()
                R.id.nav_map -> MapFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> DevicesFragment()
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
