package com.sualtikasifi.cizimhafiza.util

import android.media.AudioManager
import android.media.ToneGenerator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Short UI feedback sounds via [ToneGenerator] — no bundled audio assets
 * exist in this project, so tones are generated on-device rather than
 * played from .mp3/.ogg files. Respects the Settings screen's sound toggle.
 */
@Singleton
class SoundManager @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    private val toneGenerator: ToneGenerator? by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()
    }

    /** Last-2-seconds countdown warning tick. */
    fun playCountdownTick() = play(ToneGenerator.TONE_PROP_BEEP, 100)

    fun playCorrectGuess() = play(ToneGenerator.TONE_PROP_ACK, 150)

    fun playWrongGuess() = play(ToneGenerator.TONE_CDMA_NETWORK_BUSY, 200)

    fun playGameOver() = play(ToneGenerator.TONE_PROP_PROMPT, 350)

    private fun play(toneType: Int, durationMs: Int) {
        if (!settingsRepository.soundEnabled.value) return
        runCatching { toneGenerator?.startTone(toneType, durationMs) }
    }
}
