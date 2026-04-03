package com.example.locateme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/**
 * MainActivity responsible for fetching and displaying the user's location.
 * Uses a two-step approach: tries getLastLocation() first, and if null,
 * requests a fresh location using getCurrentLocation() with high accuracy.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    /**
     * Activity Result Launcher for requesting multiple permissions.
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            // Permission granted, proceed to fetch location
            startLocationFetchFlow()
        } else {
            showLoading(false)
            handlePermissionDenial()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupUI()
    }

    private fun setupUI() {
        binding.btnGetLocation.setOnClickListener {
            showLoading(true)
            checkAndRequestPermissions()
        }
    }

    private fun checkAndRequestPermissions() {
        when {
            hasLocationPermission() -> {
                startLocationFetchFlow()
            }
            shouldShowRationale() -> {
                showPermissionRationaleDialog()
            }
            else -> {
                launchPermissionRequest()
            }
        }
    }

    /**
     * Entry point for fetching location after permissions are confirmed.
     * Implements fallback logic: getLastLocation() -> if null -> getCurrentLocation().
     */
    private fun startLocationFetchFlow() {
        if (!hasLocationPermission()) {
            showLoading(false)
            return
        }

        try {
            // Step 1: Try to get the last known location (fastest)
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        showLoading(false)
                        displayLocation(location.latitude, location.longitude)
                    } else {
                        // Step 2: Fallback to getting current location if last known is null
                        fetchCurrentLocation()
                    }
                }
                .addOnFailureListener {
                    // Fallback on failure as well
                    fetchCurrentLocation()
                }
        } catch (e: SecurityException) {
            showLoading(false)
            Toast.makeText(this, "Security Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Requests a fresh location fix using getCurrentLocation().
     * This is useful when the cache (lastLocation) is empty.
     */
    private fun fetchCurrentLocation() {
        if (!hasLocationPermission()) {
            showLoading(false)
            return
        }

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        try {
            fusedLocationClient.getCurrentLocation(request, null)
                .addOnSuccessListener { location: Location? ->
                    showLoading(false)
                    if (location != null) {
                        displayLocation(location.latitude, location.longitude)
                    } else {
                        Toast.makeText(this, "Location not available. Turn on location.", Toast.LENGTH_LONG).show()
                        resetLocationDisplay()
                    }
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    Toast.makeText(this, "Error fetching location: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    resetLocationDisplay()
                }
        } catch (e: SecurityException) {
            showLoading(false)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun shouldShowRationale(): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) || ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    private fun launchPermissionRequest() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun handlePermissionDenial() {
        if (!shouldShowRationale()) {
            showSettingsGuidanceDialog()
        } else {
            Toast.makeText(this, "Location permission is required.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPermissionRationaleDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permission Required")
            .setMessage("Location access is needed to show your coordinates.")
            .setPositiveButton("Grant") { _, _ -> launchPermissionRequest() }
            .setNegativeButton("Cancel") { dialog, _ ->
                showLoading(false)
                dialog.dismiss()
            }
            .show()
    }

    private fun showSettingsGuidanceDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permission Permanently Denied")
            .setMessage("Please enable location permission in App Settings to use this feature.")
            .setPositiveButton("Settings") { _, _ -> openAppSettings() }
            .setNegativeButton("Cancel") { dialog, _ ->
                showLoading(false)
                dialog.dismiss()
            }
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
        showLoading(false)
    }

    private fun displayLocation(latitude: Double, longitude: Double) {
        binding.tvLatitude.text = String.format(Locale.US, "%.6f", latitude)
        binding.tvLongitude.text = String.format(Locale.US, "%.6f", longitude)
    }

    private fun resetLocationDisplay() {
        binding.tvLatitude.text = getString(R.string.default_location_value)
        binding.tvLongitude.text = getString(R.string.default_location_value)
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnGetLocation.isEnabled = !isLoading
    }
}
