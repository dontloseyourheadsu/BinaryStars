package com.tds.binarystars.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.tds.binarystars.MainActivity
import org.maplibre.android.MapLibre
import com.tds.binarystars.R
import com.tds.binarystars.adapter.LocationHistoryAdapter
import com.tds.binarystars.adapter.MapDevicesAdapter
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.DeviceTypeDto
import com.tds.binarystars.model.LocationHistoryPoint
import com.tds.binarystars.model.MapDeviceItem
import com.tds.binarystars.location.LocationUpdateScheduler
import com.tds.binarystars.storage.SettingsStorage
import com.tds.binarystars.util.NetworkUtils
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import android.content.res.Configuration
import kotlinx.coroutines.launch

class MapFragment : Fragment() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var contentView: View
    private lateinit var noConnectionView: View
    private lateinit var deviceListView: View
    private lateinit var mapDetailView: View
    private lateinit var devicesRecycler: RecyclerView
    private lateinit var historyRecycler: RecyclerView
    private lateinit var deviceListEmpty: TextView
    private lateinit var historyEmpty: TextView
    private lateinit var selectedDeviceTitle: TextView
    private lateinit var liveButton: Button
    private lateinit var backButton: ImageView
    private lateinit var switchLocationUpdates: Switch
    private lateinit var intervalSpinner: Spinner
    private lateinit var mapView: MapView

    private var mapLibreMap: MapLibreMap? = null
    private var pendingLocation: Triple<Double, Double, String>? = null
    private var selectedDevice: MapDeviceItem? = null
    private var pendingLocationAction: (() -> Unit)? = null
    private var pendingBackgroundEnable = false

    private companion object {
        private const val LOCATION_SOURCE_ID = "location-source"
        private const val LOCATION_LAYER_ID = "location-layer"
    }

    private val devices = mutableListOf<MapDeviceItem>()
    private val history = mutableListOf<LocationHistoryPoint>()
    private lateinit var devicesAdapter: MapDevicesAdapter
    private lateinit var historyAdapter: LocationHistoryAdapter

    private val requestForegroundPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                pendingLocationAction?.invoke()
            } else {
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
            }
            pendingLocationAction = null
        }

    private val requestBackgroundPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (pendingBackgroundEnable) {
                if (granted) {
                    enableBackgroundUpdates(true)
                } else {
                    switchLocationUpdates.isChecked = false
                    Toast.makeText(requireContext(), "Background location denied", Toast.LENGTH_SHORT).show()
                }
                pendingBackgroundEnable = false
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
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        contentView = view.findViewById(R.id.viewContent)
        noConnectionView = view.findViewById(R.id.viewNoConnection)
        deviceListView = view.findViewById(R.id.viewDeviceList)
        mapDetailView = view.findViewById(R.id.viewMapDetail)
        devicesRecycler = view.findViewById(R.id.rvDeviceList)
        historyRecycler = view.findViewById(R.id.rvHistory)
        deviceListEmpty = view.findViewById(R.id.tvDeviceListEmpty)
        historyEmpty = view.findViewById(R.id.tvHistoryEmpty)
        selectedDeviceTitle = view.findViewById(R.id.tvSelectedDevice)
        liveButton = view.findViewById(R.id.btnLive)
        backButton = view.findViewById(R.id.ivBack)
        switchLocationUpdates = view.findViewById(R.id.switchLocationUpdates)
        intervalSpinner = view.findViewById(R.id.spinnerUpdateInterval)
        mapView = view.findViewById(R.id.mapView)

        view.findViewById<Button>(R.id.btnRetry).setOnClickListener {
            loadDevices()
        }

        MapLibre.getInstance(requireContext())
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.uiSettings.apply {
                isCompassEnabled = true
                isLogoEnabled = false
                isAttributionEnabled = true
            }
            map.setStyle(Style.Builder().fromUri(resolveStyleUrl())) { style ->
                ensureLocationLayer(style)
                pendingLocation?.let { (lat, lon, title) ->
                    updateLocationSource(lat, lon, title)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 15.5))
                }
            }
        }

        devicesAdapter = MapDevicesAdapter(devices) { item ->
            openDevice(item)
        }
        devicesRecycler.layoutManager = LinearLayoutManager(requireContext())
        devicesRecycler.adapter = devicesAdapter

        historyAdapter = LocationHistoryAdapter(history) { point ->
            showHistoryPoint(point)
        }
        historyRecycler.layoutManager = LinearLayoutManager(requireContext())
        historyRecycler.adapter = historyAdapter

        backButton.setOnClickListener {
            showDeviceList()
        }

        liveButton.setOnClickListener {
            showLiveLocation()
        }

        setupLocationSettings()
        loadDevices()
    }

    private fun loadDevices() {
        if (!NetworkUtils.isOnline(requireContext())) {
            val currentDevice = buildCurrentDeviceItem()
            devices.clear()
            devices.add(currentDevice)
            devicesAdapter.notifyDataSetChanged()
            updateDeviceEmptyState()
            setNoConnection(false)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getDevices()
                if (!response.isSuccessful) {
                    setNoConnection(true)
                    return@launch
                }
                val deviceItems = response.body().orEmpty().map { dto ->
                    MapDeviceItem(
                        id = dto.id,
                        name = dto.name,
                        type = dto.type,
                        isOnline = dto.isOnline,
                        isCurrent = isCurrentDevice(dto.id)
                    )
                }.toMutableList()

                if (deviceItems.none { it.isCurrent }) {
                    deviceItems.add(0, buildCurrentDeviceItem())
                }

                devices.clear()
                devices.addAll(deviceItems)
                devicesAdapter.notifyDataSetChanged()
                updateDeviceEmptyState()
                setNoConnection(false)
            } catch (_: Exception) {
                setNoConnection(true)
            }
        }
    }

    private fun updateDeviceEmptyState() {
        deviceListEmpty.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openDevice(item: MapDeviceItem) {
        if (!NetworkUtils.isOnline(requireContext()) && !item.isCurrent) {
            Toast.makeText(requireContext(), "Offline mode supports current device only", Toast.LENGTH_SHORT).show()
            return
        }

        selectedDevice = item
        selectedDeviceTitle.text = item.name
        showMapDetail()
        loadHistory(item.id)
        showLiveLocation()
    }

    private fun showDeviceList() {
        mapDetailView.visibility = View.GONE
        deviceListView.visibility = View.VISIBLE
    }

    private fun showMapDetail() {
        deviceListView.visibility = View.GONE
        mapDetailView.visibility = View.VISIBLE
    }

    private fun loadHistory(deviceId: String) {
        history.clear()
        historyAdapter.notifyDataSetChanged()

        if (NetworkUtils.isOnline(requireContext())) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val response = ApiClient.apiService.getLocationHistory(deviceId)
                    if (response.isSuccessful) {
                        response.body().orEmpty().forEach { dto ->
                            history.add(
                                LocationHistoryPoint(
                                    id = dto.id,
                                    title = dto.title,
                                    timestamp = dto.recordedAt,
                                    latitude = dto.latitude,
                                    longitude = dto.longitude
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Fall back to mock data below.
                }

                if (history.isEmpty()) {
                    history.addAll(mockHistory(deviceId))
                }

                historyAdapter.notifyDataSetChanged()
                updateHistoryEmptyState()
            }
        } else {
            history.addAll(mockHistory(deviceId))
            historyAdapter.notifyDataSetChanged()
            updateHistoryEmptyState()
        }
    }

    private fun updateHistoryEmptyState() {
        historyEmpty.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showHistoryPoint(point: LocationHistoryPoint) {
        showMapLocation(point.latitude, point.longitude, point.title)
    }

    private fun showLiveLocation() {
        val device = selectedDevice ?: return
        if (device.isCurrent) {
            ensureLocationPermission {
                fetchCurrentLocation()
            }
            return
        }

        val latest = history.firstOrNull()
        if (latest != null) {
            showMapLocation(latest.latitude, latest.longitude, latest.title)
        } else {
            Toast.makeText(requireContext(), "No location history available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureLocationPermission(onGranted: () -> Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            onGranted()
        } else {
            pendingLocationAction = onGranted
            requestForegroundPermissions.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation() {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    showMapLocation(location.latitude, location.longitude, "Live location")
                } else {
                    val fallback = history.firstOrNull()
                    if (fallback != null) {
                        showMapLocation(fallback.latitude, fallback.longitude, fallback.title)
                    } else {
                        Toast.makeText(requireContext(), "Location unavailable", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener {
                val fallback = history.firstOrNull()
                if (fallback != null) {
                    showMapLocation(fallback.latitude, fallback.longitude, fallback.title)
                } else {
                    Toast.makeText(requireContext(), "Location unavailable", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showMapLocation(latitude: Double, longitude: Double, title: String) {
        pendingLocation = Triple(latitude, longitude, title)
        val map = mapLibreMap ?: return
        updateLocationSource(latitude, longitude, title)
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 15.5))
    }

    private fun ensureLocationLayer(style: Style) {
        if (style.getSource(LOCATION_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(LOCATION_SOURCE_ID))
        }
        if (style.getLayer(LOCATION_LAYER_ID) == null) {
            val layer = CircleLayer(LOCATION_LAYER_ID, LOCATION_SOURCE_ID).withProperties(
                circleColor("#6D28D9"),
                circleRadius(12f),
                circleStrokeWidth(3f),
                circleStrokeColor("#FFFFFF")
            )
            style.addLayer(layer)
        }
    }

    private fun updateLocationSource(latitude: Double, longitude: Double, title: String) {
        val map = mapLibreMap ?: return
        val style = map.style ?: return
        ensureLocationLayer(style)
        val source = style.getSourceAs<GeoJsonSource>(LOCATION_SOURCE_ID) ?: return
        source.setGeoJson(Point.fromLngLat(longitude, latitude))
    }

    private fun setupLocationSettings() {
        val intervalOptions = listOf(15, 30, 60)
        val labels = intervalOptions.map { "$it minutes" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        intervalSpinner.adapter = adapter

        val currentInterval = SettingsStorage.getLocationUpdateMinutes(15)
        val intervalIndex = intervalOptions.indexOf(currentInterval).let { if (it == -1) 0 else it }
        intervalSpinner.setSelection(intervalIndex)

        switchLocationUpdates.isChecked = SettingsStorage.areLocationUpdatesEnabled(false)
        switchLocationUpdates.setOnCheckedChangeListener { _, isChecked ->
            SettingsStorage.setLocationUpdatesEnabled(isChecked)
            if (isChecked) {
                requestBackgroundLocationPermissionIfNeeded()
            } else {
                LocationUpdateScheduler.cancel(requireContext())
            }
        }

        intervalSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val minutes = intervalOptions[position]
                SettingsStorage.setLocationUpdateMinutes(minutes)
                if (switchLocationUpdates.isChecked) {
                    LocationUpdateScheduler.schedule(requireContext(), minutes)
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                // No-op
            }
        })
    }

    private fun requestBackgroundLocationPermissionIfNeeded() {
        ensureLocationPermission {
            val hasPermission = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                enableBackgroundUpdates(true)
                return@ensureLocationPermission
            }

            pendingBackgroundEnable = true
            requestBackgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun enableBackgroundUpdates(enabled: Boolean) {
        if (!enabled) {
            LocationUpdateScheduler.cancel(requireContext())
            return
        }

        val minutes = SettingsStorage.getLocationUpdateMinutes(15)
        LocationUpdateScheduler.schedule(requireContext(), minutes)
    }

    private fun buildCurrentDeviceItem(): MapDeviceItem {
        val deviceId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        val deviceName = android.os.Build.MODEL
        return MapDeviceItem(
            id = deviceId,
            name = deviceName,
            type = DeviceTypeDto.Android,
            isOnline = NetworkUtils.isOnline(requireContext()),
            isCurrent = true
        )
    }

    private fun isCurrentDevice(deviceId: String): Boolean {
        val currentId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        return currentId == deviceId
    }

    private fun setNoConnection(show: Boolean) {
        noConnectionView.visibility = if (show) View.VISIBLE else View.GONE
        contentView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun mockHistory(deviceId: String): List<LocationHistoryPoint> {
        val now = OffsetDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
        val seed = deviceId.hashCode().toLong()
        val baseLat = 30.2672 + ((seed % 10) * 0.01)
        val baseLon = -97.7431 + ((seed % 7) * 0.01)
        return listOf(
            LocationHistoryPoint(
                id = UUID.randomUUID().toString(),
                title = "Last known",
                timestamp = now.format(formatter),
                latitude = baseLat,
                longitude = baseLon
            ),
            LocationHistoryPoint(
                id = UUID.randomUUID().toString(),
                title = "Earlier point",
                timestamp = now.minusHours(2).format(formatter),
                latitude = baseLat + 0.005,
                longitude = baseLon - 0.004
            )
        )
    }

    private fun resolveStyleUrl(): String {
        return "asset://osm_raster_style.json"
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}