package com.tds.binarystars.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.tds.binarystars.R
import com.tds.binarystars.MainActivity
import com.tds.binarystars.util.NetworkUtils

class MapFragment : Fragment() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationDebugText: TextView
    private lateinit var contentView: View
    private lateinit var noConnectionView: View

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                getLocation()
            } else {
                locationDebugText.text = "Location permission denied"
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ImageView>(R.id.ivMenu)?.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }
        locationDebugText = view.findViewById(R.id.textLocationDebug)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        contentView = view.findViewById(R.id.viewContent)
        noConnectionView = view.findViewById(R.id.viewNoConnection)
        view.findViewById<android.widget.Button>(R.id.btnRetry).setOnClickListener {
            refreshLocation()
        }

        refreshLocation()
    }

    private fun refreshLocation() {
        if (!NetworkUtils.isOnline(requireContext())) {
            contentView.visibility = View.GONE
            noConnectionView.visibility = View.VISIBLE
            return
        }

        contentView.visibility = View.VISIBLE
        noConnectionView.visibility = View.GONE
        checkLocationPermissions()
    }

    private fun checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getLocation()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission") // Checked in checkLocationPermissions
    private fun getLocation() {
        locationDebugText.text = "Getting location..."
        
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    locationDebugText.text = "Location: Lat ${location.latitude}, Lon ${location.longitude}"
                } else {
                    locationDebugText.text = "Location is null"
                }
            }
            .addOnFailureListener { e ->
                locationDebugText.text = "Error getting location: ${e.message}"
            }
    }
}