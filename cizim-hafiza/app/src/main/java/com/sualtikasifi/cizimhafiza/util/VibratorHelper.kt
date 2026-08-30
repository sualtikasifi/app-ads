package com.sualtikasifi.cizimhafiza.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API-level-safe wrapper around the two incompatible vibration APIs:
 * VibratorManager (31+) vs. the deprecated standalone Vibrator (< 31).
 * Used for the haptic warning in the last seconds of the drawing countdown,
 * plus a distinct pattern each for a correct/wrong guess.
 */
@Singleton
class VibratorHelper @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: SettingsRepository
) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun vibrateCountdownWarning() = oneShot(150)

    /** Short, light tap — correct guess. */
    fun vibrateSuccess() = oneShot(40)

    /** Two quick buzzes — wrong guess. */
    fun vibrateError() {
        if (!settingsRepository.vibrationEnabled.value || !vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 60, 60), -1))
    }

    private fun oneShot(durationMs: Long) {
        if (!settingsRepository.vibrationEnabled.value || !vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
