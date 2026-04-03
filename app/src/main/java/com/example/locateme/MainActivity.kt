package com.example.locateme

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Professional-grade Location Finder Activity.
 * Features: High-accuracy location fetching, UI state management, 
 * data formatting, and sharing/copying functionality.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Store the last fetched location for copy/share functionality
    private var lastFetchedLocation: Location? = null

    // Activity Result Launcher for Location Permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            startLocationFetch()
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

        // Handle System UI Overlays (Edge-to-Edge)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        initClickListeners()
    }

    private fun initClickListeners() {
        binding.btnGetLocation.setOnClickListener {
            checkPermissionsAndFetch()
        }

        binding.btnCopy.setOnClickListener {
            copyToClipboard()
        }

        binding.btnShare.setOnClickListener {
            shareLocation()
        }
    }

    private fun checkPermissionsAndFetch() {
        setLoadingState(true)
        when {
            isPermissionGranted() -> {
                startLocationFetch()
            }
            shouldShowRationale() -> {
                showRationaleDialog()
            }
            else -> {
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
     * Step 1: Try getLastLocation (Cache)
     * Step 2: Fallback to getCurrentLocation (Fresh Fix)
     */
    private fun startLocationFetch() {
        if (!isPermissionGranted()) return

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null && isRecent(location)) {
                    updateUI(location)
                } else {
                    requestFreshLocation()
                }
            }.addOnFailureListener {
                requestFreshLocation()
            }
        } catch (e: SecurityException) {
            setLoadingState(false)
            showToast("Security Exception: ${e.localizedMessage}")
        }
    }

    private fun requestFreshLocation() {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        try {
            fusedLocationClient.getCurrentLocation(request, null)
                .addOnSuccessListener { location: Location? ->
                    updateUI(location)
                }
                .addOnFailureListener { e ->
                    setLoadingState(false)
                    showToast("Failed to fetch location: ${e.localizedMessage}")
                }
        } catch (e: SecurityException) {
            setLoadingState(false)
        }
    }

    private fun updateUI(location: Location?) {
        setLoadingState(false)
        if (location == null) {
            showToast("Unable to fetch location. Please ensure GPS is ON.")
            return
        }

        lastFetchedLocation = location

        // Format and display coordinates (6 decimal places)
        binding.tvLatitude.text = String.format(Locale.US, "%.6f", location.latitude)
        binding.tvLongitude.text = String.format(Locale.US, "%.6f", location.longitude)

        // Display Accuracy
        binding.tvAccuracy.text = String.format(Locale.US, "± %.1f m", location.accuracy)

        // Display Readable Timestamp
        val sdf = SimpleDateFormat("HH:mm:ss, dd MMM", Locale.getDefault())
        binding.tvTimestamp.text = sdf.format(Date(location.time))
    }

    private fun copyToClipboard() {
        val loc = lastFetchedLocation
        if (loc == null) {
            showToast("No location data to copy")
            return
        }

        val textToCopy = "Lat: ${loc.latitude}, Lon: ${loc.longitude}"
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Location", textToCopy)
        clipboard.setPrimaryClip(clip)
        
        showToast("Location copied to clipboard!")
    }

    private fun shareLocation() {
        val loc = lastFetchedLocation
        if (loc == null) {
            showToast("No location data to share")
            return
        }

        val shareBody = "My Location:\nLatitude: ${loc.latitude}\nLongitude: ${loc.longitude}\n" +
                "Maps: https://www.google.com/maps/search/?api=1&query=${loc.latitude},${loc.longitude}"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Current Location")
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnGetLocation.isEnabled = !isLoading
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.INVISIBLE
    }

    // Permission Helpers
    private fun isPermissionGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun shouldShowRationale() = ActivityCompat.shouldShowRequestPermissionRationale(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    )

    private fun showRationaleDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permission Required")
            .setMessage("Location access is required to show your coordinates and accuracy.")
            .setPositiveButton("Grant") { _, _ ->
                requestPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
            .setNegativeButton("Cancel") { _, _ -> setLoadingState(false) }
            .show()
    }

    private fun handlePermanentDenial() {
        if (!shouldShowRationale()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Permission Denied")
                .setMessage("Please enable location permissions in App Settings to use this feature.")
                .setPositiveButton("Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun isRecent(location: Location): Boolean {
        val age = System.currentTimeMillis() - location.time
        return age < 60000 // Less than 1 minute old
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
