package com.wwwescape.medicinereminder.wear

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** A prominent buzz, repeating until [stop]: long, full-strength pulses, on the alarm channel so
 * it isn't muted along with ordinary notifications. The watch plays no sound. */
class Buzzer(context: Context) {

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private var buzzing = false

    fun start() {
        if (buzzing) return
        buzzing = true
        // Three long pulses, then a pause, over and over.
        val timings = longArrayOf(0, 900, 300, 900, 300, 900, 1400)
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, intArrayOf(0, 255, 0, 255, 0, 255, 0), 0)
        } else {
            VibrationEffect.createWaveform(timings, 0)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    fun stop() {
        if (!buzzing) return
        buzzing = false
        vibrator.cancel()
    }
}
