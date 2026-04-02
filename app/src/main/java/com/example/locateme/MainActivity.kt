package com.example.locateme

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.locateme.databinding.ActivityMainBinding

/**
 * MainActivity responsible for displaying the user's current location.
 * Implements ViewBinding for efficient UI component access.
 */
class MainActivity : AppCompatActivity() {

    // ViewBinding instance to access UI elements
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        
        // Enable Edge-to-Edge display
        enableEdgeToEdge()
        
        setContentView(binding.root)

        // Handle Window Insets for edge-to-edge support
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
    }

    private fun setupUI() {
        // Set click listener for the "Get Location" button
        binding.btnGetLocation.setOnClickListener {
            showLoading(true)
            
            // TODO: Implement location retrieval logic here
            // For now, we just simulate a delay or state change
        }
    }

    /**
     * Toggles the loading indicator and button state.
     */
    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnGetLocation.isEnabled = !isLoading
    }
}