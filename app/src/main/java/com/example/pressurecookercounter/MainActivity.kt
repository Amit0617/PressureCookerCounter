package com.example.pressurecookercounter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private var whistleCount = 0
    private lateinit var countTextView: TextView
    private lateinit var incrementButton: Button
    private lateinit var resetButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements by finding their IDs
        countTextView = findViewById(R.id.countTextView)
        incrementButton = findViewById(R.id.incrementButton)
        resetButton = findViewById(R.id.resetButton)

        // Set up the click listener for the increment button
        incrementButton.setOnClickListener {
            whistleCount++
            updateCountTextView()
        }

        // Set up the click listener for the reset button
        resetButton.setOnClickListener {
            whistleCount = 0
            updateCountTextView()
        }

        // Initial update of the TextView
        updateCountTextView()
    }

    private fun updateCountTextView() {
        countTextView.text = "Whistles: $whistleCount"
    }
}