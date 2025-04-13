package com.example.pressurecookercounter

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.abs

object WhistleDetector {
    private const val SAMPLE_RATE = 44100
    private const val BUFFER_SIZE = 1024
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private var listener: OnWhistleDetectedListener? = null

    fun startListening(listener: OnWhistleDetectedListener) {
        this.listener = listener

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            BUFFER_SIZE
        ).apply {
            startRecording()
        }

        isListening = true
        startProcessingThread()
    }

    fun stopListening() {
        isListening = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun startProcessingThread() {
        Thread {
            val buffer = ShortArray(BUFFER_SIZE)

            while (isListening) {
                audioRecord?.read(buffer, 0, BUFFER_SIZE)

                // Analyze audio signal
                val amplitude = calculateAmplitude(buffer)
                if (amplitude > THRESHOLD && isWhistleFrequency(buffer)) {
                    listener?.onWhistleDetected()
                }
            }
        }.start()
    }

    private fun calculateAmplitude(buffer: ShortArray): Float {
        return buffer.map { abs(it.toFloat()) }.average()
    }

    private fun isWhistleFrequency(buffer: ShortArray): Boolean {
        // Implement frequency analysis here
        // For simplicity, this checks if the signal matches typical whistle frequencies
        return true // Replace with actual frequency analysis
    }

    interface OnWhistleDetectedListener {
        fun onWhistleDetected()
    }
}