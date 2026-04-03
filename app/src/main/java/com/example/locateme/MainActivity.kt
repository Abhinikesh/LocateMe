package com.example.locateme

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
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
 * Senior-level Implementation: Google Maps Location App.
 * Handles permissions, fallback location fetching (emulator-friendly),
 * reverse geocoding, and modern Material 3 UI.
 */
class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mMap: GoogleMap? = null

    // Register permission launcher using modern Activity Result API
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            setupLocationOnMap()
            fetchLocation()
        } else {
            setLoading(false)
            handlePermissionDenial()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        // Adjust layout for system window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize Map Fragment
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.btnRefresh.setOnClickListener {
            fetchLocationWithCheck()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap?.uiSettings?.isZoomControlsEnabled = true
        mMap?.uiSettings?.isMyLocationButtonEnabled = true
        
        setupLocationOnMap()
        fetchLocationWithCheck()
    }

    /**
     * Enables the Blue Dot (My Location) layer on the map if permission is granted.
     */
    @SuppressLint("MissingPermission")
    private fun setupLocationOnMap() {
        if (hasLocationPermission()) {
            mMap?.isMyLocationEnabled = true
        }
    }

    private fun fetchLocationWithCheck() {
        setLoading(true)
        when {
            hasLocationPermission() -> fetchLocation()
            shouldShowRationale() -> showRationaleDialog()
            else -> requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    /**
     * Logic to retrieve location:
     * 1. Try lastLocation (fastest, cached).
     * 2. If null (common in emulators), use getCurrentLocation (fresh fix).
     */
    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                onLocationSuccess(location)
            } else {
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
                    onLocationSuccess(location)
                } else {
                    setLoading(false)
                    binding.tvAddress.text = "Unable to find location. Is GPS on?"
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun onLocationSuccess(location: Location) {
        setLoading(false)
        val latLng = LatLng(location.latitude, location.longitude)

        // Update Map: Marker and Camera
        mMap?.apply {
            clear()
            addMarker(MarkerOptions().position(latLng).title("You are here"))
            animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        }

        // Update Timestamp
        val sdf = SimpleDateFormat("hh:mm:ss a, dd MMM yyyy", Locale.getDefault())
        binding.tvTimestamp.text = sdf.format(Date(location.time))

        // Get Address Asynchronously
        getAddressFromLocation(location)
    }

    /**
     * Uses Geocoder to find readable address from coordinates.
     * Wrapped in a thread as getFromLocation is a network/blocking call.
     */
    private fun getAddressFromLocation(location: Location) {
        val geocoder = Geocoder(this, Locale.getDefault())
        
        Thread {
            try {
                // Note: Using deprecated getFromLocation for broad compatibility. 
                // In a production app targeting API 33+, use the callback-based version.
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                
                runOnUiThread {
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val addressDetails = "${address.locality}, ${address.adminArea}, ${address.countryName}"
                        binding.tvAddress.text = addressDetails
                    } else {
                        binding.tvAddress.text = "Address not available"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.tvAddress.text = "Geocoder Service Unavailable"
                }
            }
        }.start()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnRefresh.isEnabled = !isLoading
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.INVISIBLE
    }

    // Permission Helpers
    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun shouldShowRationale() = ActivityCompat.shouldShowRequestPermissionRationale(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    )

    private fun showRationaleDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permission Needed")
            .setMessage("Location access is required to show your position on the map.")
            .setPositiveButton("Grant") { _, _ ->
                requestPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
            .setNegativeButton("Cancel") { _, _ -> setLoading(false) }
            .show()
    }

    private fun handlePermissionDenial() {
        if (!shouldShowRationale()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Permission Required")
                .setMessage("Permission was permanently denied. Enable it in settings to use the map.")
                .setPositiveButton("Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    })
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
