package com.sualtikasifi.cizimhafiza.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * A one-second game tick that stops while the app is in the background.
 *
 * The drawing and guessing countdowns run on `viewModelScope`, which is not
 * lifecycle-aware: a plain `delay(1_000)` keeps firing while the player is
 * taking a phone call, reading a notification, or answering the door. The
 * round they were halfway through is simply gone when they come back —
 * which, in the daily challenge, can also cost a streak they had been
 * building for weeks. Neither is a fair way to lose.
 *
 * Deliberately used by the **single-player** ViewModel only. In an online
 * match the opponents' clocks keep running regardless, so pausing here would
 * both desynchronise the room and hand anyone who backgrounds the app
 * unlimited thinking time.
 *
 * The tick is consumed in short slices rather than one long sleep, so
 * backgrounding mid-tick loses at most [SLICE_MILLIS] rather than a whole
 * second.
 */
class PausableTicker {

    private val paused = MutableStateFlow(false)

    fun pause() {
        paused.value = true
    }

    fun resume() {
        paused.value = false
    }

    /** Suspends for [totalMillis] of foreground time, parking for as long as the app is backgrounded. */
    suspend fun awaitTick(totalMillis: Long = 1_000L) {
        var remaining = totalMillis
        while (remaining > 0) {
            // Returns immediately when already running; parks the coroutine
            // for the whole time the app spends in the background.
            paused.first { !it }
            val slice = minOf(SLICE_MILLIS, remaining)
            delay(slice)
            remaining -= slice
        }
    }

    private companion object {
        const val SLICE_MILLIS = 100L
    }
}
