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
 * Used for the haptic warning in the last seconds of the drawing countdown.
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

    fun vibrateCountdownWarning() {
        if (!settingsRepository.vibrationEnabled.value) return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
