package com.example.pressurecookercounter

import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.abs
import kotlin.math.sqrt

class WhistleDetector(
    private val sampleRate: Int,
    private val bufferSize: Int,
    private val minFrequency: Float = 2000f, // Typical pressure cooker whistle ranges
    private val maxFrequency: Float = 4000f,
    private val sensitivityThreshold: Float = 0.6f // 0.0 to 1.0
) {
    private val fft = FloatFFT_1D(bufferSize.toLong())
    private var lastDetectionTime = 0L
    private val detectionCooldown = 2000L // 2 seconds between detections

    // Convert to frequency bins for range checking
    private val minBin = (minFrequency * bufferSize / sampleRate).toInt()
    private val maxBin = (maxFrequency * bufferSize / sampleRate).toInt()

    private suspend fun detectWhistle(buffer: ShortArray, readSize: Int) {
        // Check timing first to avoid unnecessary processing
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime < detectionCooldown) {
            return
        }

        // Calculate basic audio energy
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += abs(buffer[i].toDouble())
        }
        val average = sum / readSize

        // Only perform frequency analysis if the sound is loud enough
        if (average > threshold * 0.5) {
            // Check for characteristic frequencies of a pressure cooker whistle
            // Most pressure cooker whistles produce a high-pitched sound
            // in the range of 2000-4000 Hz

            // Use FFT to analyze the frequency spectrum (simplified version)
            val whistleDetected = detectWhistleFrequency(buffer)

            if (whistleDetected || average > threshold) {
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

    // Simple frequency detection for high-pitched whistle sounds
    private fun detectWhistleFrequency(buffer: ShortArray): Boolean {
        // This is a simplified approach. For better results, use a proper FFT library

        // Break the buffer into several windows and analyze each
        val windowSize = 1024
        var consecutiveHighFrequencyWindows = 0

        for (windowStart in 0 until buffer.size - windowSize step windowSize / 2) {
            if (isHighFrequencyDominant(buffer, windowStart, windowStart + windowSize)) {
                consecutiveHighFrequencyWindows++
                if (consecutiveHighFrequencyWindows >= 2) {
                    return true
                }
            } else {
                consecutiveHighFrequencyWindows = 0
            }
        }

        return false
    }

    private fun isHighFrequencyDominant(buffer: ShortArray, startIdx: Int, endIdx: Int): Boolean {
        // Calculate energy in low and high frequency bands using a crude approximation

        // For a sampling rate of 44100 Hz:
        // - Low frequencies cause slowly changing values
        // - High frequencies cause rapidly changing values

        var lowFreqEnergy = 0.0
        var highFreqEnergy = 0.0

        // Compare consecutive samples to estimate frequency content
        for (i in startIdx + 1 until endIdx) {
            val diff = abs(buffer[i] - buffer[i - 1]).toDouble()

            // Large differences indicate high frequencies
            if (diff > 500) {
                highFreqEnergy += diff
            } else {
                lowFreqEnergy += abs(buffer[i].toDouble())
            }
        }

        // Adjust these ratios based on testing with your specific pressure cooker
        return highFreqEnergy > lowFreqEnergy * 0.8
    }

    fun setSensitivity(sensitivity: Int) {
        // Convert 0-100 scale to 0.1-0.9 threshold (inverted: higher sensitivity = lower threshold)
        sensitivityThreshold = 0.9f - (sensitivity / 100f * 0.8f)
    }
}