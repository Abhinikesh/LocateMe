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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.locateme.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/**
 * MainActivity responsible for fetching and displaying the user's last known location.
 * Implements robust runtime permission handling and utilizes FusedLocationProviderClient.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // FusedLocationProviderClient for location retrieval
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    /**
     * Activity Result Launcher for requesting multiple permissions.
     * Handles the user's response to the permission dialog.
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            // Permission granted, proceed to fetch location
            getLastKnownLocation()
        } else {
            // Permission denied, handle UI feedback and check if permanently denied
            showLoading(false)
            handlePermissionDenial()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        // Handle window insets for edge-to-edge layout
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupUI()
    }

    /**
     * Sets up UI listeners and initial states.
     */
    private fun setupUI() {
        binding.btnGetLocation.setOnClickListener {
            showLoading(true)
            checkAndRequestPermissions()
        }
    }

    /**
     * Checks if location permissions are already granted.
     * Requests permissions or shows rationale if necessary.
     */
    private fun checkAndRequestPermissions() {
        when {
            hasLocationPermission() -> {
                // Permissions already granted
                getLastKnownLocation()
            }
            shouldShowRationale() -> {
                // User previously denied, show explanation before requesting again
                showPermissionRationaleDialog()
            }
            else -> {
                // Request permissions directly
                launchPermissionRequest()
            }
        }
    }

    /**
     * Checks if at least one location permission is granted.
     */
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Determines if we should show a rationale for requesting location permissions.
     */
    private fun shouldShowRationale(): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) || ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    /**
     * Launches the system permission request dialog.
     */
    private fun launchPermissionRequest() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    /**
     * Handles the UI logic when permission is denied.
     * Distinguishes between temporary denial and permanent denial (Don't ask again).
     */
    private fun handlePermissionDenial() {
        if (!shouldShowRationale()) {
            // User permanently denied or checked "Don't ask again"
            showSettingsGuidanceDialog()
        } else {
            // User just denied once
            Toast.makeText(this, "Location permission is required.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shows a dialog explaining why the permission is needed.
     */
    private fun showPermissionRationaleDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permission Required")
            .setMessage("This app requires location access to show your current coordinates on the screen.")
            .setPositiveButton("Grant") { _, _ ->
                launchPermissionRequest()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                showLoading(false)
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Shows a dialog guiding the user to the app settings if permission is permanently denied.
     */
    private fun showSettingsGuidanceDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permission Permanently Denied")
            .setMessage("Location permission is permanently denied. Please enable it in the app settings to use this feature.")
            .setPositiveButton("Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                showLoading(false)
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Opens the system settings page for this application.
     */
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
        showLoading(false)
    }

    /**
     * Fetches the last known location using FusedLocationProviderClient.
     */
    private fun getLastKnownLocation() {
        // Final safety check before calling the API
        if (!hasLocationPermission()) {
            showLoading(false)
            return
        }

        try {
            fusedLocationClient.lastLocation
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
            Toast.makeText(this, "Security Exception: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Updates UI with coordinates.
     */
    private fun displayLocation(latitude: Double, longitude: Double) {
        binding.tvLatitude.text = String.format(Locale.US, "%.6f", latitude)
        binding.tvLongitude.text = String.format(Locale.US, "%.6f", longitude)
    }

    /**
     * Resets UI to default values.
     */
    private fun resetLocationDisplay() {
        binding.tvLatitude.text = getString(R.string.default_location_value)
        binding.tvLongitude.text = getString(R.string.default_location_value)
    }

    /**
     * Controls ProgressBar and button state.
     */
    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnGetLocation.isEnabled = !isLoading
    }
}
