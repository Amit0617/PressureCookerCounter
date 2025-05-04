package com.example.pressurecookercounter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.math.abs

class WhistleDetectionService : Service() {

    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "whistle_detection_channel"

    // Audio recording parameters
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var counter = 0
    private var whistleDetector: WhistleDetector? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): WhistleDetectionService = this@WhistleDetectionService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_DETECTION" -> {
                counter = intent.getIntExtra("CURRENT_COUNT", 0)
                val threshold = intent.getIntExtra("THRESHOLD", 2000)
                val sensitivity = intent.getIntExtra("SENSITIVITY", 50)

                // Initialize the whistle detector
                whistleDetector = WhistleDetector(sampleRate, bufferSize).apply {
                    setSensitivity(sensitivity)
                    setListener(object : WhistleDetector.WhistleDetectionListener {
                        override fun onWhistleDetected() {
                            counter++
                            // Broadcast the updated count to MainActivity
                            val updateIntent = Intent("WHISTLE_COUNT_UPDATED").apply {
                                putExtra("COUNT", counter)
                            }
                            sendBroadcast(updateIntent)
                        }
                    })
                }

                startForeground()
                startRecording()
            }
            "STOP_DETECTION" -> {
                stopRecording()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForeground() {
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Whistle Counter Active")
            .setContentText("Listening for whistles")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Whistle Detection Service"
            val descriptionText = "Detects whistle sounds from pressure cooker"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startRecording() {
        if (isRecording) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.startRecording()
                isRecording = true

                recordingJob = serviceScope.launch {
                    val buffer = ShortArray(bufferSize)
                    while (isRecording) {
                        val readSize = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                        if (readSize > 0) {
                            whistleDetector?.detectWhistle(buffer, readSize)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    override fun onDestroy() {
        stopRecording()
        serviceScope.cancel()
        super.onDestroy()
    }
}