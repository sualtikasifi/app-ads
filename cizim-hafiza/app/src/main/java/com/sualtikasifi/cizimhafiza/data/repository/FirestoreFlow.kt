package com.sualtikasifi.cizimhafiza.data.repository

import android.util.Log
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * A [Flow] over a Firestore snapshot listener that **survives a transient
 * error instead of dying on it**.
 *
 * Every listener in this app used to be hand-written as a `callbackFlow`
 * whose error branch called `close(error)`. That turns any hiccup — a
 * second of lost signal mid-match, a rules deploy landing while someone is
 * in a lobby — into a permanently dead flow: the collector's `catch` shows
 * an error and nothing ever re-subscribes, so the screen stays broken until
 * the player navigates away and back. That is exactly the failure the "Bot
 * Eğitim" screen shipped with, and the same shape existed in seven other
 * places (room, reactions, friends, invites, bot room).
 *
 * Here the error instead triggers a re-registration on exponential backoff.
 * Only a fault that survives [MAX_ATTEMPTS] consecutive retries — i.e. a
 * genuinely unrecoverable one, like a query the rules will never allow — is
 * escalated to the collector.
 *
 * @param name identifies the listener in logcat when a retry happens.
 * @param subscribe registers the listener. Call `emit` with each snapshot's
 *   mapped value, and `onError` with anything that should trigger a retry.
 *   Must return the [ListenerRegistration] so it can be torn down.
 */
fun <T> firestoreFlow(
    name: String,
    subscribe: suspend (emit: (T) -> Unit, onError: (Throwable) -> Unit) -> ListenerRegistration
): Flow<T> = callbackFlow {
    // CONFLATED: while a retry is already queued, further errors from the
    // same dying listener add nothing.
    val errors = Channel<Throwable>(Channel.CONFLATED)
    var registration: ListenerRegistration? = null
    var attempt = 0

    suspend fun register() {
        registration = subscribe(
            { value ->
                // A delivered snapshot proves the listener is healthy again,
                // so the next unrelated blip starts from a short backoff
                // rather than inheriting an old, long one.
                attempt = 0
                trySend(value)
            },
            { error -> errors.trySend(error) }
        )
    }

    register()

    launch {
        for (error in errors) {
            attempt++
            if (attempt > MAX_ATTEMPTS) {
                Log.w(TAG, "$name: giving up after $MAX_ATTEMPTS retries", error)
                close(error)
                return@launch
            }
            val backoffMillis = BACKOFF_MILLIS[minOf(attempt - 1, BACKOFF_MILLIS.lastIndex)]
            Log.d(TAG, "$name: listener failed (attempt $attempt), retrying in ${backoffMillis}ms", error)
            registration?.remove()
            registration = null
            delay(backoffMillis)
            register()
        }
    }

    awaitClose {
        errors.close()
        registration?.remove()
    }
}

private const val TAG = "FirestoreFlow"
private const val MAX_ATTEMPTS = 6
private val BACKOFF_MILLIS = longArrayOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000)
