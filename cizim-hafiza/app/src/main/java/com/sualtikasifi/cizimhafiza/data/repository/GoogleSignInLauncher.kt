package com.sualtikasifi.cizimhafiza.data.repository

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Bridges the classic (callback-based) Activity Result API into a single
 * suspend call, so [AuthRepositoryImpl] — a plain Hilt singleton with no
 * Activity lifecycle of its own — can launch Google's account picker and
 * await the result exactly like any other suspend call.
 *
 * `registerForActivityResult` has to run before its hosting Activity
 * reaches STARTED, which a repository injected on demand cannot arrange for
 * itself. So the launcher lives in Compose instead — see MainActivity's
 * GoogleSignInLauncherHost — and [bind] deposits it here the moment it is
 * created; [launch] posts to whatever is currently deposited. In this
 * single-Activity app there is only ever one real launcher at a time, so
 * this mailbox is all the indirection needed.
 */
@Singleton
class GoogleSignInLauncher @Inject constructor() {

    private var launcher: ActivityResultLauncher<Intent>? = null
    private var pending: CancellableContinuation<ActivityResult>? = null

    fun bind(launcher: ActivityResultLauncher<Intent>) {
        this.launcher = launcher
    }

    /** Guarded by identity: an old launcher tearing down must not clear one a newer Activity instance just bound. */
    fun unbind(launcher: ActivityResultLauncher<Intent>) {
        if (this.launcher === launcher) this.launcher = null
    }

    fun onResult(result: ActivityResult) {
        pending?.resume(result)
        pending = null
    }

    /**
     * Launches [intent] and suspends until the system delivers a result.
     *
     * A second call while one is already pending fails fast rather than
     * silently dropping whichever result arrives — sign-in is always a
     * single modal picker, so overlapping calls mean a caller bug, not a
     * real use case to support.
     */
    suspend fun launch(intent: Intent): ActivityResult {
        check(pending == null) { "A Google sign-in request is already in flight" }
        val current = checkNotNull(launcher) { "No Activity is hosting the Google sign-in launcher" }
        return suspendCancellableCoroutine { cont ->
            pending = cont
            cont.invokeOnCancellation { pending = null }
            current.launch(intent)
        }
    }
}
