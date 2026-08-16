package com.sualtikasifi.cizimhafiza

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.sualtikasifi.cizimhafiza.data.local.WordSeeder
import com.sualtikasifi.cizimhafiza.data.local.dao.WordDao
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CizimHafizaApp : Application() {

    @Inject lateinit var wordDao: WordDao
    @Inject lateinit var firebaseAuth: FirebaseAuth

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Re-synced on every launch, not just first install: Room only seeds
        // via a RoomDatabase.Callback on first database creation, so a device
        // that already had the app installed would never pick up new words
        // added to assets/words.json in a later app update — its on-disk
        // database file already exists and that callback never fires again.
        // insertAll uses OnConflictStrategy.REPLACE, so this also picks up
        // text/category/difficulty corrections for words that already exist
        // on the device, not just brand-new ids.
        applicationScope.launch {
            val bundledWords = WordSeeder.loadFromAssets(applicationContext)
            if (wordDao.count() != bundledWords.size) {
                wordDao.insertAll(bundledWords)
            }
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
