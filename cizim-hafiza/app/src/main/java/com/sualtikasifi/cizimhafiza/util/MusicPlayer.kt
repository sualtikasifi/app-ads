package com.sualtikasifi.cizimhafiza.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.sualtikasifi.cizimhafiza.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * The soundtrack: a fixed playlist played end to end, on repeat, for as long
 * as the app is in the foreground.
 *
 * Deliberately built on [MediaPlayer] rather than a media session or a
 * foreground service. This is background music for a game being looked at,
 * not a media app — it should stop the moment the player leaves (see
 * [onAppBackgrounded]), it must never appear in the notification shade or
 * take audio focus away from whatever else the phone is playing, and it
 * should duck out of the way rather than fight a phone call. A one-off
 * MediaPlayer with [AudioAttributes.USAGE_GAME] says exactly that to the
 * system; a MediaSessionService would claim to be something this is not.
 *
 * Tracks are decoded straight from res/raw so nothing has to be unpacked or
 * cached on first run.
 */
@Singleton
class MusicPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: MediaPlayer? = null
    private var fadeJob: Job? = null
    private var trackIndex = 0
    private var started = false
    /** True while the app is in the background — playback resumes from here, not from the start. */
    private var paused = false

    /**
     * The sound/music switches MUTE, they do not stop.
     *
     * Turning the switch off used to release the MediaPlayer outright, so
     * turning it back on started the current track over from zero — flick it
     * twice and the soundtrack jumps back to the top of a track it was two
     * minutes into. Muting instead leaves the playlist running exactly where
     * it was, silently, and unmuting hands back the volume mid-track, which
     * is what a mute button is expected to do.
     *
     * The cost of playing to nobody is bounded by [onAppBackgrounded]: muted
     * playback only continues while the app is actually on screen.
     */
    private var muted = true

    /** Where the current fade is, 0..1, before [MAX_VOLUME] and the mute are applied. */
    private var fadeLevel = 0f

    /**
     * Starts (once) and then follows the two switches for the rest of the
     * process's life — as a mute, not as a stop; see [muted]. Called from the
     * Activity; safe to call repeatedly.
     */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(
                settingsRepository.soundEnabled,
                settingsRepository.musicEnabled
            ) { sound, music -> sound && music }
                .collect { wanted ->
                    muted = !wanted
                    // The player is created on the first "yes" and then kept:
                    // from there on the switch only moves the volume. A player
                    // that has never been wanted is never built, so music left
                    // off from launch costs nothing at all.
                    if (wanted && player == null && !paused) ensurePlaying()
                    applyVolume()
                }
        }
    }

    fun onAppBackgrounded() {
        paused = true
        player?.let { runCatching { if (it.isPlaying) it.pause() } }
    }

    fun onAppForegrounded() {
        paused = false
        val existing = player
        if (existing != null) {
            // Resumes even while muted: the playlist is meant to go on
            // running under a silenced switch, and this is the one place it
            // was genuinely stopped rather than silenced.
            runCatching { existing.start() }
        } else if (!muted) {
            ensurePlaying()
        }
    }

    private fun ensurePlaying() {
        if (player != null) return
        playTrack(trackIndex)
    }

    private fun playTrack(index: Int) {
        val resId = PLAYLIST.getOrNull(index % PLAYLIST.size) ?: return
        val created = runCatching {
            MediaPlayer.create(context, resId)?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = false
                setOnCompletionListener { advance() }
                // A track that fails mid-way must not end the soundtrack —
                // skip to the next one rather than going silent for the rest
                // of the session.
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Track $index failed ($what/$extra), skipping")
                    advance()
                    true
                }
            }
        }.getOrNull()

        if (created == null) {
            Log.w(TAG, "Track $index could not be created, skipping")
            // Guard against a playlist where every entry fails: without this
            // a broken resource set would recurse once per track and then
            // stop, which is the same as silence but far harder to read in a
            // log than one warning per track.
            if (index - trackIndex < PLAYLIST.size) advance()
            return
        }

        player = created
        fadeJob?.cancel()
        fadeLevel = 0f
        applyVolume()
        runCatching { created.start() }
        fadeJob = scope.launch { runFades(created) }
    }

    /**
     * Fades the track in at its start and out over its last
     * [FADE_MILLIS] — the join between two tracks is the only place a
     * looping soundtrack ever draws attention to itself, and a hard cut
     * there is what makes background music sound like a playlist.
     *
     * Polled rather than scheduled: MediaPlayer has no "notify me at
     * position X" callback, and a timer set at start would drift against a
     * stream that can stall or be paused.
     */
    private suspend fun runFades(mp: MediaPlayer) {
        val duration = runCatching { mp.duration }.getOrDefault(0)
        fade(mp, from = 0f, to = 1f, millis = FADE_IN_MILLIS)
        if (duration <= 0) return
        while (true) {
            val position = runCatching { mp.currentPosition }.getOrNull() ?: return
            val remaining = duration - position
            if (remaining <= FADE_MILLIS) break
            delay(((remaining - FADE_MILLIS).coerceAtMost(1_000)).toLong())
        }
        fade(mp, from = 1f, to = 0f, millis = FADE_MILLIS)
    }

    private suspend fun fade(mp: MediaPlayer, from: Float, to: Float, millis: Int) {
        val steps = (millis / FADE_STEP_MILLIS).coerceAtLeast(1)
        repeat(steps) { step ->
            val t = (step + 1).toFloat() / steps
            // Volume is perceived roughly logarithmically, so a linear ramp
            // sounds like it drops away early and then hangs. Squaring the
            // fraction makes the fade sound even.
            fadeLevel = (from + (to - from) * t).coerceIn(0f, 1f).pow(2)
            if (mp !== player) return
            applyVolume()
            delay(FADE_STEP_MILLIS.toLong())
        }
    }

    /**
     * The single place volume is written. The fade owns [fadeLevel] and the
     * switch owns [muted]; keeping them as two independent inputs to one
     * output is what stops the fade coroutine from stepping on a mute a
     * frame later (which it would, sixteen times a second).
     */
    private fun applyVolume() {
        val level = if (muted) 0f else fadeLevel * MAX_VOLUME
        player?.let { mp -> runCatching { mp.setVolume(level, level) } }
    }

    private fun advance() {
        trackIndex = (trackIndex + 1) % PLAYLIST.size
        releasePlayer()
        // Not gated on the switch: a muted playlist keeps moving through its
        // tracks, so unmuting lands wherever the soundtrack actually got to.
        if (!paused) playTrack(trackIndex)
    }

    private fun releasePlayer() {
        player?.let { mp ->
            runCatching { mp.setOnCompletionListener(null) }
            runCatching { mp.setOnErrorListener(null) }
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
        player = null
    }

    private companion object {
        const val TAG = "MusicPlayer"

        /**
         * Played in this order, then round again. Adding a track is adding a
         * file to res/raw and a line here — nothing else in the app knows how
         * many there are.
         */
        val PLAYLIST = intArrayOf(
            R.raw.music_1,
            R.raw.music_2,
            R.raw.music_3,
            R.raw.music_4,
            R.raw.music_5
        )

        /** Background music sits under the game's own sounds, never level with them. */
        const val MAX_VOLUME = 0.45f

        const val FADE_MILLIS = 4_500
        const val FADE_IN_MILLIS = 1_500
        const val FADE_STEP_MILLIS = 60
    }
}
