package com.example.locateme

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.locateme.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.Locale

/**
 * MainActivity responsible for fetching and displaying the user's last known location.
 * Implements ViewBinding and utilizes FusedLocationProviderClient for location services.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // FusedLocationProviderClient for location retrieval
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Runtime permission launcher to handle location permission requests
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            // Permission granted, attempt to fetch location
            getLastKnownLocation()
        } else {
            // Permission denied, inform the user and stop loading
            showLoading(false)
            Toast.makeText(this, "Permission denied. Cannot fetch location.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        // Handle edge-to-edge window insets
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
     * Sets up UI component listeners.
     */
    private fun setupUI() {
        binding.btnGetLocation.setOnClickListener {
            showLoading(true)
            checkAndRequestPermissions()
        }
    }

    /**
     * Checks if location permissions are already granted.
     * If yes, fetches location. If no, requests them.
     */
    private fun checkAndRequestPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permissions already granted
                getLastKnownLocation()
            }
            else -> {
                // Request permissions
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    /**
     * Fetches the last known location using FusedLocationProviderClient.
     * Handles success, null results, and failure cases.
     */
    private fun getLastKnownLocation() {
        // Double check permissions to satisfy the compiler and ensure safety
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showLoading(false)
            Toast.makeText(this, "Location permission is required.", Toast.LENGTH_SHORT).show()
            return
        }

        // Using getLastLocation() as requested
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                showLoading(false)
                if (location != null) {
                    // Successfully retrieved location, update UI
                    displayLocation(location.latitude, location.longitude)
                } else {
                    // Location is null (e.g., location settings are off or no recent cache)
                    Toast.makeText(this, "Location not available. Turn on location.", Toast.LENGTH_LONG).show()
                    resetLocationDisplay()
                }
            }
            .addOnFailureListener { e ->
                // Handle any errors that occurred during location retrieval
                showLoading(false)
                Toast.makeText(this, "Error fetching location: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                resetLocationDisplay()
            }
    }

    /**
     * Updates the UI with the fetched latitude and longitude.
     */
    private fun displayLocation(latitude: Double, longitude: Double) {
        // Use Locale.US to ensure consistent decimal separator formatting
        binding.tvLatitude.text = String.format(Locale.US, "%.6f", latitude)
        binding.tvLongitude.text = String.format(Locale.US, "%.6f", longitude)
    }

    /**
     * Resets the location display to default values.
     */
    private fun resetLocationDisplay() {
        binding.tvLatitude.text = getString(R.string.default_location_value)
        binding.tvLongitude.text = getString(R.string.default_location_value)
    }

    /**
     * Toggles the visibility of the ProgressBar and the state of the button.
     */
    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnGetLocation.isEnabled = !isLoading
    }
}
