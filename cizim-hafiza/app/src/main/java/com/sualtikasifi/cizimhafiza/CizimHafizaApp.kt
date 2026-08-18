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
import java.text.SimpleDateFormat
import java.util.Locale
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
        // Friend-invite push notifications (see FriendInviteMessagingService)
        // need their channel to exist before the first FCM push can arrive,
        // which can happen before anything else in the app has run.
        NotificationScheduler.createFriendInviteChannel(this)
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

        // StatisticsScreen builds a `remember { SimpleDateFormat(...) }`
        // synchronously as part of its first composition — which, since
        // it's a screen reached by navigating in, happens WHILE the
        // enter-transition animation is playing. A locale's date/time
        // symbol tables (DateFormatSymbols) are lazily loaded on the first
        // SimpleDateFormat ever constructed for that locale in this
        // process, which can cost tens of milliseconds — enough to visibly
        // drop frames mid-transition (reported as "stutter entering
        // Statistics"). Constructing one here, off the main thread, at
        // app start (long before anyone visits that screen) pays that
        // one-time cost where nothing is animating, so by the time
        // StatisticsScreen's own remember{} runs, the locale data is
        // already cached and construction is effectively free.
        applicationScope.launch {
            SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault())
        }
    }
}
