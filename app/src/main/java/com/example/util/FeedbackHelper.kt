package com.example.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object FeedbackHelper {

    fun playFeedback(context: Context, isApproved: Boolean) {
        try {
            // Haptic vibration
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (isApproved) {
                        it.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        // Double pulse alert for not approved
                        val timings = longArrayOf(0, 150, 100, 200)
                        val amplitudes = intArrayOf(0, 255, 0, 255)
                        it.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    }
                } else {
                    @Suppress("DEPRECATION")
                    if (isApproved) {
                        it.vibrate(120)
                    } else {
                        it.vibrate(350)
                    }
                }
            }

            // Audio tone
            val toneType = if (isApproved) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).apply {
                startTone(toneType, if (isApproved) 180 else 350)
            }
        } catch (_: Exception) {
            // Ignore if audio or vibration unavailable
        }
    }
}
