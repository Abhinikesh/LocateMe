package com.example.locateme

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.locateme.databinding.ActivityMainBinding
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Professional Location App with Google Maps and Reverse Geocoding.
 */
class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mMap: GoogleMap? = null

    // Permission Launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            enableMyLocationLayer()
            fetchLocationFlow()
        } else {
            setLoadingState(false)
            handlePermanentDenial()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        // Adjust for system bars
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize Map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.btnRefresh.setOnClickListener {
            checkPermissionsAndFetch()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap?.uiSettings?.isZoomControlsEnabled = true
        
        // Try to enable the blue dot layer if permission exists
        enableMyLocationLayer()
        
        // Initial fetch
        checkPermissionsAndFetch()
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocationLayer() {
        if (isPermissionGranted()) {
            mMap?.isMyLocationEnabled = true
        }
    }

    private fun checkPermissionsAndFetch() {
        setLoadingState(true)
        when {
            isPermissionGranted() -> fetchLocationFlow()
            shouldShowRationale() -> showRationaleDialog()
            else -> launchPermissionRequest()
        }
    }

    /**
     * Modern Location Fetch Flow:
     * 1. Try last known location (fastest)
     * 2. If null (common in emulators), request a fresh fix using getCurrentLocation
     */
    @SuppressLint("MissingPermission")
    private fun fetchLocationFlow() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                processNewLocation(location)
            } else {
                // Fallback for emulator or fresh device
                requestFreshLocation()
            }
        }.addOnFailureListener {
            requestFreshLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation() {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        fusedLocationClient.getCurrentLocation(request, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    processNewLocation(location)
                } else {
                    setLoadingState(false)
                    binding.tvAddress.text = "Location not found. Enable GPS."
                }
            }
            .addOnFailureListener {
                setLoadingState(false)
                Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Updates Map, Address, and Timestamp
     */
    private fun processNewLocation(location: Location) {
        setLoadingState(false)
        val latLng = LatLng(location.latitude, location.longitude)

        // 1. Update Map
        mMap?.apply {
            clear()
            addMarker(MarkerOptions().position(latLng).title("You are here"))
            animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        }

        // 2. Update Timestamp
        val time = SimpleDateFormat("hh:mm:ss a, dd MMM", Locale.getDefault()).format(Date(location.time))
        binding.tvTimestamp.text = time

        // 3. Get Address (Reverse Geocoding)
        fetchAddress(location)
    }

    /**
     * Reverse Geocoding using Geocoder.
     * Note: getFromLocation is a blocking call, so we wrap it to avoid UI stutter.
     */
    private fun fetchAddress(location: Location) {
        val geocoder = Geocoder(this, Locale.getDefault())
        
        // Using a thread to keep the UI responsive during Geocoding
        Thread {
            try {
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                runOnUiThread {
                    if (!addresses.isNullOrEmpty()) {
                        val address: Address = addresses[0]
                        val addressText = StringBuilder()
                        
                        // Extract City, State, Country
                        addressText.append(address.locality ?: "Unknown City").append(", ")
                        addressText.append(address.adminArea ?: "Unknown State").append(", ")
                        addressText.append(address.countryName ?: "Unknown Country")
                        
                        binding.tvAddress.text = addressText.toString()
                    } else {
                        binding.tvAddress.text = "Address not found"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.tvAddress.text = "Service unavailable"
                }
            }
        }.start()
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnRefresh.isEnabled = !isLoading
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.INVISIBLE
    }

    // --- Permission Management ---

    private fun isPermissionGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun shouldShowRationale() = ActivityCompat.shouldShowRequestPermissionRationale(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    )

    private fun launchPermissionRequest() {
        requestPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun showRationaleDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Location Permission")
            .setMessage("We need location access to show where you are on the map.")
            .setPositiveButton("Allow") { _, _ -> launchPermissionRequest() }
            .setNegativeButton("Cancel") { _, _ -> setLoadingState(false) }
            .show()
    }

    private fun handlePermanentDenial() {
        if (!shouldShowRationale()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Settings Required")
                .setMessage("Permission is permanently denied. Please enable it in Settings.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    })
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
