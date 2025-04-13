package com.example.pressurecookercounter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    private lateinit var countTextView: TextView
    private lateinit var startButton: Button
    private var whistleCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        countTextView = findViewById(R.id.countTextView)
        startButton = findViewById(R.id.startButton)

        // Set up click listener
        startButton.setOnClickListener {
            // Toggle monitoring
            if ((it as Button).text == "Start Monitoring") {
                startMonitoring()
                it.text = "Stop Monitoring"
            } else {
                stopMonitoring()
                it.text = "Start Monitoring"
            }
        }
    }

    private fun startMonitoring() {
        // Initialize audio processing
        WhistleDetector.getInstance().startListening(object : OnWhistleDetectedListener {
            override fun onWhistleDetected() {
                runOnUiThread {
                    whistleCount++
                    updateCountDisplay()
                }
            }
        })
    }

    private fun stopMonitoring() {
        WhistleDetector.getInstance().stopListening()
    }

    private fun updateCountDisplay() {
        countTextView.text = "Whistles: $whistleCount"
    }
}