package com.sualtikasifi.cizimhafiza

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.auth.FirebaseAuth
import com.sualtikasifi.cizimhafiza.data.local.WordPoolSynchronizer
import com.sualtikasifi.cizimhafiza.data.local.dao.GameSessionDao
import com.sualtikasifi.cizimhafiza.notifications.NotificationScheduler
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CizimHafizaApp : Application(), Configuration.Provider {

    @Inject lateinit var wordPoolSynchronizer: WordPoolSynchronizer
    @Inject lateinit var gameSessionDao: GameSessionDao
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var firebaseAuth: FirebaseAuth
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Daily "come back and play" reminder — see notifications/.
        NotificationScheduler.schedule(this)
        // Re-synced on every app UPDATE (not just first install) and on any
        // language change, not on every single launch's happy path — see
        // WordPoolSynchronizer for why the version/language gating exists.
        wordPoolSynchronizer.syncAsync()

        // One-time seed for the rank/level system's lifetime score (see
        // PlayerRank): if this device already has game history from before
        // this feature existed, count it instead of starting everyone back
        // at 0 — otherwise players would feel like their past games "didn't
        // count". Only the surviving pruned rows (last RECENT_GAMES_LIMIT)
        // can be counted; that's an acceptable best-effort estimate.
        // seedLifetimeScoreIfAbsent is itself a no-op once a lifetime score
        // has ever been recorded, so this is safe to call on every launch.
        applicationScope.launch {
            settingsRepository.seedLifetimeScoreIfAbsent(gameSessionDao.getTotalScoreSum())
        }

        // Silent anonymous sign-in for the online (friend-vs-friend) mode —
        // gives this device a stable Firestore uid with no login screen and
        // no personal data collected. Only needed once per install; if a uid
        // already exists (app relaunch) this is a no-op. signInAnonymously()
        // is already async (returns a Task), so no coroutine is needed here —
        // callers that need the uid (e.g. the online repository) observe
        // firebaseAuth.currentUser / an AuthStateListener rather than
        // assuming sign-in has completed by the time they run.
        if (firebaseAuth.currentUser == null) {
            firebaseAuth.signInAnonymously()
        }
    }
}
