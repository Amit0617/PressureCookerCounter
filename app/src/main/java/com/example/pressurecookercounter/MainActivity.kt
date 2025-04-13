package com.example.pressurecookercounter

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.log10
import android.content.Intent
import android.content.ServiceConnection
import android.view.Menu
import android.view.MenuItem


class MainActivity : AppCompatActivity() {
    private var counter = 0
    private lateinit var counterTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var toggleButton: Button

    private val PREFS_NAME = "WhistleCounterPrefs"
    private val COUNTER_KEY = "counter"
    private val PERMISSION_REQUEST_CODE = 200

    // Audio recording parameters
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    // Whistle detection parameters
    private var threshold = 2000 // Default value, will be adjusted by sensitivity slider
    private var detectionCooldown = 2000L // 2 seconds between whistles
    private var lastDetectionTime = 0L
    private var sensitivity = 50 // Default sensitivity (0-100)

    private var whistleDetectionServiceBound = false
    private var whistleDetectionService: WhistleDetectionService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WhistleDetectionService.LocalBinder
            whistleDetectionService = binder.getService()
            whistleDetectionServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            whistleDetectionServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Load saved counter value
        val sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        counter = sharedPrefs.getInt(COUNTER_KEY, 0)

        // Initialize UI elements
        counterTextView = findViewById(R.id.counterTextView)
        statusTextView = findViewById(R.id.statusTextView)
        toggleButton = findViewById(R.id.toggleButton)
        val resetButton = findViewById<Button>(R.id.resetButton)
        val sensitivitySeekBar = findViewById<SeekBar>(R.id.sensitivitySeekBar)

        updateCounterDisplay()

        val whistleCountReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "WHISTLE_COUNT_UPDATED") {
                    counter = intent.getIntExtra("COUNT", counter)
                    updateCounterDisplay()
                }
            }
        }
        registerReceiver(whistleCountReceiver, IntentFilter("WHISTLE_COUNT_UPDATED"))

        // Set up button listeners
        toggleButton.setOnClickListener {
            if (isRecording) {
                stopDetection()
            } else {
                if (checkPermission()) {
                    startDetection()
                } else {
                    requestPermission()
                }
            }
        }

        resetButton.setOnClickListener {
            counter = 0
            updateCounterDisplay()
            saveCounter()
        }

        // Set up sensitivity adjustment
        sensitivitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sensitivity = progress
                // Adjust threshold based on sensitivity (inverse relationship)
                // Lower threshold means more sensitive
                threshold = 5000 - (sensitivity * 30)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                Toast.makeText(
                    this,
                    "Microphone permission is required for whistle detection",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startDetection() {
        // Background service approach
        val serviceIntent = Intent(this, WhistleDetectionService::class.java).apply {
            action = "START_DETECTION"
            putExtra("CURRENT_COUNT", counter)
            putExtra("THRESHOLD", threshold)
        }

        ContextCompat.startForegroundService(this, serviceIntent)
        bindService(Intent(this, WhistleDetectionService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE)

        isRecording = true
        toggleButton.text = "Stop Listening"
        statusTextView.text = "Listening for whistles in background..."
    }

    private fun stopDetection() {
        val serviceIntent = Intent(this, WhistleDetectionService::class.java).apply {
            action = "STOP_DETECTION"
        }
        startService(serviceIntent)

        if (whistleDetectionServiceBound) {
            unbindService(serviceConnection)
            whistleDetectionServiceBound = false
        }

        isRecording = false
        toggleButton.text = "Start Listening"
        statusTextView.text = "Waiting for whistles..."
    }

    private suspend fun detectWhistle(buffer: ShortArray, readSize: Int) {
        // Calculate audio energy
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += abs(buffer[i].toDouble())
        }

        val average = sum / readSize

        // Check for sudden loud sounds that could be whistles
        if (average > threshold) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastDetectionTime > detectionCooldown) {
                lastDetectionTime = currentTime

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    counter++
                    updateCounterDisplay()
                    saveCounter()
                    statusTextView.text = "Whistle detected!"
                    vibrate()

                    // Reset status text after a delay
                    delay(1000)
                    if (isRecording) {
                        statusTextView.text = "Listening for whistles..."
                    }
                }
            }
        }
    }

    private fun updateCounterDisplay() {
        counterTextView.text = counter.toString()
    }

    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun saveCounter() {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        editor.putInt(COUNTER_KEY, counter)
        editor.apply()
    }

    override fun onPause() {
        super.onPause()
        saveCounter()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (whistleDetectionServiceBound) {
            unbindService(serviceConnection)
            whistleDetectionServiceBound = false
        }

        try {
            unregisterReceiver(whistleCountReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_calibrate -> {
                // Launch calibration activity
                val intent = Intent(this, CalibrationActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}