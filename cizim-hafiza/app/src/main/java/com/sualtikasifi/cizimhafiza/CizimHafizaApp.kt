package com.sualtikasifi.cizimhafiza

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.sualtikasifi.cizimhafiza.data.local.WordSeeder
import com.sualtikasifi.cizimhafiza.data.local.dao.GameSessionDao
import com.sualtikasifi.cizimhafiza.data.local.dao.WordDao
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CizimHafizaApp : Application() {

    @Inject lateinit var wordDao: WordDao
    @Inject lateinit var gameSessionDao: GameSessionDao
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var firebaseAuth: FirebaseAuth

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Re-synced on app UPDATE, not on every single launch: Room only
        // seeds via a RoomDatabase.Callback on first database creation, so a
        // device that already had the app installed would never pick up new
        // words added to assets/words.json in a later app update — its
        // on-disk database file already exists and that callback never fires
        // again. insertAll uses OnConflictStrategy.REPLACE, so this also
        // picks up text/category/difficulty corrections for words that
        // already exist on the device, not just brand-new ids.
        //
        // WORD_POOL_VERSION gates this: parsing the ~1200-word JSON file and
        // querying Room just to confirm "nothing changed" is real work
        // (I/O + JSON deserialization) that was previously repeated on every
        // single cold start. Bumping the stored version only on an actual
        // words.json edit means that check — and the parse it guards — is
        // skipped entirely on the overwhelming majority of launches.
        // Also re-seeds when the table is simply empty (word count 0),
        // regardless of the stored version marker: a Room schema migration
        // (fallbackToDestructiveMigration) wipes every table including
        // words, but that SharedPreferences marker lives outside the
        // database and survives the wipe untouched — without this check the
        // version-gate above would wrongly conclude "already synced" and
        // leave the word table permanently empty after such a migration.
        // wordDao.count() alone is a cheap query, safe to run every launch.
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        applicationScope.launch {
            val versionChanged = prefs.getInt(KEY_WORD_POOL_VERSION, -1) != WORD_POOL_VERSION
            if (versionChanged || wordDao.count() == 0) {
                val bundledWords = WordSeeder.loadFromAssets(applicationContext)
                if (wordDao.count() != bundledWords.size) {
                    wordDao.insertAll(bundledWords)
                }
                prefs.edit().putInt(KEY_WORD_POOL_VERSION, WORD_POOL_VERSION).apply()
            }
        }

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

    private companion object {
        const val PREFS_NAME = "cizim_hafiza_settings"
        const val KEY_WORD_POOL_VERSION = "word_pool_version"
        // Bump only when assets/words.json actually changes.
        const val WORD_POOL_VERSION = 1
    }
}
