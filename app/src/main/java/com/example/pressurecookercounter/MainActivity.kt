package com.example.pressurecookercounter

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.*
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var countText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private var whistleCount = 0
    private var isRecording = false
    private var audioThread: Thread? = null
    private var lastWhistleTime = 0L

    private val SAMPLE_RATE = 44100
    private val THRESHOLD = 15000 // Adjust after testing
    private val MIN_GAP_MS = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        countText = findViewById(R.id.whistleCountText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        startButton.setOnClickListener { startListening() }
        stopButton.setOnClickListener { stopListening() }

        requestPermissions()
    }

    private fun requestPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    private fun startListening() {
        if (isRecording) return
        isRecording = true
        whistleCount = 0
        updateUI()

        audioThread = Thread {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            val buffer = ShortArray(bufferSize)

            recorder.startRecording()

            while (isRecording) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val max = buffer.take(read).maxOf { abs(it.toInt()) }
                    checkWhistle(max)
                }
            }

            recorder.stop()
            recorder.release()
        }
        audioThread?.start()
    }

    private fun stopListening() {
        isRecording = false
        audioThread?.join()
    }

    private fun checkWhistle(amplitude: Int) {
        val now = System.currentTimeMillis()
        if (amplitude > THRESHOLD && now - lastWhistleTime > MIN_GAP_MS) {
            lastWhistleTime = now
            whistleCount++
            runOnUiThread { updateUI() }
        }
    }

    private fun updateUI() {
        countText.text = "Whistles: $whistleCount"
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
    }
}
