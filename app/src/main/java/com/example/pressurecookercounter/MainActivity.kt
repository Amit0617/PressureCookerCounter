package com.example.pressurecookercounter

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private var whistleCount = 0
    private lateinit var countTextView: TextView
    private lateinit var toggleMonitorButton: Button
    private lateinit var resetButton: Button
    private var audioRecord: AudioRecord? = null
    private var isMonitoring = false
    private val sampleRate = 44100 // Standard sample rate
    private val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
    private var bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private val handler = Handler(Looper.getMainLooper())
    private val audioMonitoringInterval = 100L // Check audio every 100 milliseconds
    private val amplitudeThreshold = 32000 // Adjust this based on your environment and cooker
    private var lastWhistleDetectedTime: Long = 0
    private val whistleDebounceTime = 1000L // Minimum time between whistle detections (1 second)

    private val RECORD_AUDIO_PERMISSION_CODE = 2 // Using a different code for clarity

    override fun onCreate(savedInstanceState: Bundle?) {
        val handler = Handler(Looper.getMainLooper())
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        countTextView = findViewById(R.id.countTextView)
        toggleMonitorButton = findViewById(R.id.toggleMonitorButton)
        resetButton = findViewById(R.id.resetButton)

        // Check and request microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION_CODE)
        } else {
            initializeAudioRecorder()
        }

        toggleMonitorButton.setOnClickListener {
            if (isMonitoring) {
                stopMonitoring()
            } else {
                startMonitoring()
            }
        }

        resetButton.setOnClickListener {
            whistleCount = 0
            updateCountTextView()
        }

        updateCountTextView()
    }

    private fun initializeAudioRecorder() {
        try {
            bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (bufferSize <= 0) {
                Log.e("Audio", "Error: Could not get minimum buffer size.")
                Toast.makeText(this, "Audio initialization failed", Toast.LENGTH_SHORT).show()
                return
            }
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2 // Double the buffer size for safety
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("Audio", "Error: AudioRecord initialization failed.")
                Toast.makeText(this, "Audio initialization failed", Toast.LENGTH_SHORT).show()
                audioRecord = null
            }
        } catch (e: Exception) {
            Log.e("Audio", "Error initializing AudioRecord: ${e.message}")
            Toast.makeText(this, "Audio initialization failed", Toast.LENGTH_SHORT).show()
            audioRecord = null
        }
    }

    private fun startMonitoring() {
        if (audioRecord != null && audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
            try {
                audioRecord?.startRecording()
                isMonitoring = true
                toggleMonitorButton.text = "Stop Monitoring"
                startAudioMonitoringLoop()
            } catch (e: IllegalStateException) {
                Log.e("Audio", "Error starting audio recording: ${e.message}")
                Toast.makeText(this, "Error starting monitoring", Toast.LENGTH_SHORT).show()
                stopMonitoring()
            }
        } else {
            Toast.makeText(this, "Audio recorder not initialized", Toast.LENGTH_SHORT).show()
            initializeAudioRecorder()
        }
    }

    private fun stopMonitoring() {
        isMonitoring = false
        toggleMonitorButton.text = "Start Monitoring"
        handler.removeCallbacks(audioMonitoringRunnable)
        try {
            audioRecord?.stop()
        } catch (e: IllegalStateException) {
            Log.e("Audio", "Error stopping audio recording: ${e.message}")
        }
    }

    private val audioMonitoringRunnable : Runnable = Runnable {
        if (isMonitoring && audioRecord != null) {
            val buffer = ShortArray(bufferSize)
            val readSize = audioRecord?.read(buffer, 0, bufferSize) ?: 0

            if (readSize > 0) {
                var maxAmplitude = 0
                for (i in 0 until readSize) {
                    maxAmplitude = maxOf(maxAmplitude, abs(buffer[i].toInt()))
                }

                if (maxAmplitude > amplitudeThreshold && (System.currentTimeMillis() - lastWhistleDetectedTime > whistleDebounceTime)) {
                    whistleCount++
                    updateCountTextView()
                    lastWhistleDetectedTime = System.currentTimeMillis()
                    Log.d("Audio", "Whistle detected! Amplitude: $maxAmplitude")
                }
            }
            handler.postDelayed(audioMonitoringRunnable, audioMonitoringInterval)
        }
    }

    private fun startAudioMonitoringLoop() {
        handler.postDelayed(audioMonitoringRunnable, audioMonitoringInterval)
    }

    private fun updateCountTextView() {
        runOnUiThread {
            countTextView.text = "Whistles: $whistleCount"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeAudioRecorder()
            } else {
                Toast.makeText(this, "Audio recording permission denied", Toast.LENGTH_LONG).show()
                // Optionally, disable the audio monitoring button or show a message
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        audioRecord?.release()
        audioRecord = null
    }
}