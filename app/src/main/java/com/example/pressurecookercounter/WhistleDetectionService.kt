package com.example.pressurecookercounter

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.math.abs

class WhistleDetectionService : Service() {
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "WhistleDetectionChannel"

    private var isRunning = false
    private var whistleCount = 0
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    // Audio recording parameters
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    // Whistle detection parameters
    private var threshold = 2000
    private var detectionCooldown = 2000L
    private var lastDetectionTime = 0L

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): WhistleDetectionService = this@WhistleDetectionService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_DETECTION" -> {
                if (!isRunning) {
                    whistleCount = intent.getIntExtra("CURRENT_COUNT", 0)
                    threshold = intent.getIntExtra("THRESHOLD", 2000)
                    startDetection()
                }
            }
            "STOP_DETECTION" -> {
                stopDetection()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startDetection() {
        isRunning = true

        // Create and show notification
        val notification = createNotification("Listening for whistles...", whistleCount)
        startForeground(NOTIFICATION_ID, notification)

        // Start audio recording and detection
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            audioRecord?.startRecording()

            val buffer = ShortArray(bufferSize)
            while (isRunning) {
                val readSize = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (readSize > 0) {
                    detectWhistle(buffer, readSize)
                }
            }
        }
    }

    private fun stopDetection() {
        isRunning = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        // Save the final count
        val sharedPrefs = getSharedPreferences("WhistleCounterPrefs", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        editor.putInt("counter", whistleCount)
        editor.apply()

        // Broadcast the final count to update UI if app is open
        val intent = Intent("WHISTLE_COUNT_UPDATED")
        intent.putExtra("COUNT", whistleCount)
        sendBroadcast(intent)
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
                whistleCount++

                // Update notification
                val notification = createNotification("Whistle detected!", whistleCount)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)

                // Broadcast the updated count
                val intent = Intent("WHISTLE_COUNT_UPDATED")
                intent.putExtra("COUNT", whistleCount)
                sendBroadcast(intent)

                // Vibrate to indicate detection
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))

                // Reset notification after a delay
                delay(1000)
                if (isRunning) {
                    val updatedNotification = createNotification("Listening for whistles...", whistleCount)
                    notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Whistle Detection"
            val descriptionText = "Detects pressure cooker whistles"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String, count: Int): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WhistleDetectionService::class.java).apply {
            action = "STOP_DETECTION"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Whistle Counter: $count")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        stopDetection()
        super.onDestroy()
    }
}