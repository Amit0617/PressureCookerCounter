package com.example.pressurecookercounter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*

class CalibrationActivity : AppCompatActivity() {
    private lateinit var statusTextView: TextView
    private lateinit var instructionTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var startButton: Button
    private lateinit var doneButton: Button

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    private var maxAmplitude = 0.0
    private var noiseFloor = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)

        statusTextView = findViewById(R.id.calibrationStatusTextView)
        instructionTextView = findViewById(R.id.instructionsTextView)
        progressBar = findViewById(R.id.calibrationProgressBar)
        startButton = findViewById(R.id.startCalibrationButton)
        doneButton = findViewById(R.id.doneButton)

        startButton.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
                startCalibration()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    100
                )
            }
        }

        doneButton.setOnClickListener {
            finish()
        }
    }

    private fun startCalibration() {
        startButton.isEnabled = false
        progressBar.progress = 0

        // First, calibrate the ambient noise
        instructionTextView.text = "Keep quiet for background noise calibration..."
        statusTextView.text = "Calibrating noise floor..."

        calibrateNoiseFloor()
    }

    private fun calibrateNoiseFloor() {
        startRecording()

        // 5 second timer for noise floor calibration
        object : CountDownTimer(5000, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val progress = ((5000 - millisUntilFinished) / 5000.0 * 50).toInt()
                progressBar.progress = progress
            }

            override fun onFinish() {
                stopRecording()
                progressBar.progress = 50

                // Now proceed to whistle calibration
                instructionTextView.text = "Now blow your pressure cooker whistle (or simulate it)!"
                statusTextView.text = "Waiting for whistle sound..."

                // Short delay before starting whistle detection
                CoroutineScope(Dispatchers.Main).launch {
                    delay(1000)
                    calibrateWhistle()
                }
            }
        }.start()
    }

    private fun calibrateWhistle() {
        startRecording()

        // 10 second timer for whistle detection
        object : CountDownTimer(10000, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val progress = 50 + ((10000 - millisUntilFinished) / 10000.0 * 50).toInt()
                progressBar.progress = progress
            }

            override fun onFinish() {
                stopRecording()
                progressBar.progress = 100

                // Calculate and save the calibration values
                calculateAndSaveCalibration()

                instructionTextView.text = "Calibration complete!"
                statusTextView.text = "Ready to detect whistles"
                startButton.isEnabled = true
                doneButton.isEnabled = true
            }
        }.start()
    }

    private fun startRecording() {
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        isRecording = true

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            audioRecord?.startRecording()

            val buffer = ShortArray(bufferSize)
            while (isRecording) {
                val readSize = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (readSize > 0) {
                    processAudio(buffer, readSize)
                }
            }
        }
    }

    private fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun processAudio(buffer: ShortArray, readSize: Int) {
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += Math.abs(buffer[i].toDouble())
        }

        val average = sum / readSize

        // Update max amplitude if needed
        if (average > maxAmplitude) {
            maxAmplitude = average

            // Update UI on main thread
            CoroutineScope(Dispatchers.Main).launch {
                statusTextView.text = "Detected sound level: ${average.toInt()}"
            }
        }

        // For noise floor, we set it to a running average during the first phase
        if (noiseFloor == 0.0) {
            noiseFloor = average
        } else {
            noiseFloor = (noiseFloor * 0.9) + (average * 0.1) // Weighted average
        }
    }

    private fun calculateAndSaveCalibration() {
        // Calculate a good threshold between noise floor and max amplitude
        // We add some margin to avoid false positives
        val threshold = noiseFloor + (maxAmplitude - noiseFloor) * 0.7

        // Save the calibration data
        val sharedPrefs = getSharedPreferences("WhistleCounterPrefs", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        editor.putFloat("threshold", threshold.toFloat())
        editor.putFloat("noiseFloor", noiseFloor.toFloat())
        editor.putFloat("maxAmplitude", maxAmplitude.toFloat())
        editor.apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            stopRecording()
        }
    }
}