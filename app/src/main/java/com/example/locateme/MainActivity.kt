package com.example.locateme

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
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
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mMap: GoogleMap? = null
    private var currentMarker: Marker? = null
    private var pulseAnimator: ObjectAnimator? = null
    private var locationCallback: LocationCallback? = null
    private var lastLocation: Location? = null
    private val handler = Handler(Looper.getMainLooper())
    private var locationReceived = false
    private var isCameraMovedOnce = false
    private val cancellationTokenSource = CancellationTokenSource()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            startLocationProcess()
        } else {
            showPermissionSnackBar()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        binding.btnRefresh.setOnClickListener {
            fetchLocation()
        }

        binding.btnShare.setOnClickListener {
            shareLocation()
        }

        setupLongClickToCopy()
        startPulseAnimation()
        
        // Hide location card initially for fade-in effect
        binding.locationCard.visibility = View.INVISIBLE
    }

    private fun setupLongClickToCopy() {
        val longClickListener = View.OnLongClickListener { view ->
            val textToCopy = when (view.id) {
                R.id.tvLatChip -> binding.tvLatChip.text.toString()
                R.id.tvLngChip -> binding.tvLngChip.text.toString()
                else -> ""
            }
            if (textToCopy.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Location Coordinate", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
            true
        }
        binding.tvLatChip.setOnLongClickListener(longClickListener)
        binding.tvLngChip.setOnLongClickListener(longClickListener)
    }

    private fun shareLocation() {
        lastLocation?.let { location ->
            val placeName = binding.tvPlaceName.text.toString()
            val shareText = getString(
                R.string.share_location_text,
                placeName,
                location.latitude,
                location.longitude
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
        } ?: run {
            Toast.makeText(this, "Location not available to share", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap?.uiSettings?.isZoomControlsEnabled = false
        mMap?.uiSettings?.isMyLocationButtonEnabled = true
        startLocationProcess()
    }

    private fun startLocationProcess() {
        if (!isGpsEnabled()) {
            showGpsDisabledDialog()
            return
        }

        if (hasLocationPermission()) {
            setupLocationUpdates()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationUpdates() {
        try {
            locationReceived = false
            setLoadingState(true)
            
            // Remove previous callbacks to avoid multiple fallbacks
            handler.removeCallbacksAndMessages(null)

            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateDistanceMeters(0f)
                .setGranularity(Granularity.GRANULARITY_FINE)
                .setWaitForAccurateLocation(true)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationReceived = true
                    handler.removeCallbacksAndMessages(null)
                    for (location in locationResult.locations) {
                        if (location.accuracy < 50f) {
                            onLocationReceived(location)
                        }
                    }
                    setLoadingState(false)
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )

            // Fallback after 5 seconds if no update received
            handler.postDelayed({
                if (!locationReceived) {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationTokenSource.token
                    ).addOnSuccessListener { location ->
                        if (location != null && location.accuracy < 50f) {
                            onLocationReceived(location)
                            setLoadingState(false)
                        }
                    }
                }
            }, 5000L)
        } catch (e: Exception) {
            setLoadingState(false)
            e.printStackTrace()
        }
    }

    private fun onLocationReceived(location: Location) {
        try {
            lastLocation = location
            updateGpsStatus(true)
            
            if (binding.locationCard.visibility != View.VISIBLE) {
                binding.locationCard.visibility = View.VISIBLE
                val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 500 }
                binding.locationCard.startAnimation(fadeIn)
            }

            val latLng = LatLng(location.latitude, location.longitude)

            binding.tvLatChip.text = "Lat: %.6f".format(location.latitude)
            binding.tvLngChip.text = "Lng: %.6f".format(location.longitude)

            val sdf = SimpleDateFormat("hh:mm:ss a, dd MMM yyyy", Locale.getDefault())
            binding.tvTimestamp.text = "Last updated: ${sdf.format(Date(location.time))}"

            mMap?.apply {
                currentMarker?.remove()
                currentMarker = addMarker(MarkerOptions().position(latLng).title("You are here"))
                if (!isCameraMovedOnce) {
                    animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    isCameraMovedOnce = true
                }
            }

            reverseGeocode(location)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun reverseGeocode(location: Location) {
        val geocoder = Geocoder(this, Locale.getDefault())
        binding.tvPlaceName.text = getString(R.string.fetching_place_name)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<android.location.Address>) {
                        runOnUiThread {
                            if (!addresses.isNullOrEmpty()) {
                                binding.tvPlaceName.text = addresses[0].getAddressLine(0)
                            } else {
                                binding.tvPlaceName.text = "Unable to fetch address"
                            }
                        }
                    }

                    override fun onError(errorMessage: String?) {
                        runOnUiThread {
                            binding.tvPlaceName.text = "Unable to fetch address"
                        }
                    }
                })
            } catch (e: Exception) {
                binding.tvPlaceName.text = "Unable to fetch address"
            }
        } else {
            Thread {
                try {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    runOnUiThread {
                        if (!addresses.isNullOrEmpty()) {
                            binding.tvPlaceName.text = addresses[0].getAddressLine(0)
                        } else {
                            binding.tvPlaceName.text = "Unable to fetch address"
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        binding.tvPlaceName.text = "Unable to fetch address"
                    }
                }
            }.start()
        }
    }

    private fun fetchLocation() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsOn = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkOn = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!isGpsOn && !isNetworkOn) {
            showGpsDisabledDialog()
            return
        }

        isCameraMovedOnce = false
        if (hasLocationPermission()) {
            setupLocationUpdates()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        if (isLoading) {
            binding.btnRefresh.text = ""
            binding.btnRefresh.icon = null
            binding.progressBar.visibility = View.VISIBLE
        } else {
            binding.btnRefresh.text = getString(R.string.btn_refresh)
            binding.btnRefresh.setIconResource(android.R.drawable.ic_menu_rotate)
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun isGpsEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun showGpsDisabledDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.enable_gps_title)
            .setMessage(R.string.enable_gps_message)
            .setPositiveButton(R.string.settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showPermissionSnackBar() {
        Snackbar.make(binding.root, R.string.location_permission_required, Snackbar.LENGTH_LONG)
            .setAction(R.string.settings) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }.show()
    }

    private fun updateGpsStatus(isActive: Boolean) {
        binding.gpsStatusDot.setBackgroundResource(
            if (isActive) R.drawable.dot_indicator_green else R.drawable.dot_indicator_red
        )
    }

    private fun startPulseAnimation() {
        pulseAnimator = ObjectAnimator.ofFloat(binding.gpsStatusDot, View.ALPHA, 1f, 0.3f).apply {
            duration = 800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (hasLocationPermission() && isGpsEnabled()) {
            setupLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.onDestroy()
        pulseAnimator?.cancel()
        locationCallback = null
        cancellationTokenSource.cancel()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }
}
