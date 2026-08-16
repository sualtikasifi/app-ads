package com.sualtikasifi.cizimhafiza.data.local

import android.content.Context
import com.sualtikasifi.cizimhafiza.data.local.dao.WordDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the on-device word pool (Room `words` table) matched to whichever
 * language the UI is currently showing, re-seeding from assets/words.json
 * or assets/words_en.json as needed. Reseeds are cheap and idempotent:
 * WordDao.insertAll uses OnConflictStrategy.REPLACE keyed by each word's
 * stable id, so a language switch is just "replace every row's
 * text/category with the other language's asset file", not a destructive
 * table rebuild — existing game history (which stores wordId, not word
 * text) picks up the new language automatically the next time it's shown.
 *
 * Called from both CizimHafizaApp.onCreate() (process start) and the
 * Settings screen's language toggle (mid-session, no restart) — its own
 * CoroutineScope means a mid-session call started from a ViewModel keeps
 * running even if that ViewModel is torn down by the Activity recreation
 * that AppCompatDelegate.setApplicationLocales() triggers.
 */
@Singleton
class WordPoolSynchronizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wordDao: WordDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun syncAsync() {
        scope.launch { sync() }
    }

    suspend fun sync() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val language = WordSeeder.currentLanguage(context)
        val versionChanged = prefs.getInt(KEY_WORD_POOL_VERSION, -1) != WORD_POOL_VERSION
        val languageChanged = prefs.getString(KEY_WORD_POOL_LANGUAGE, null) != language

        if (versionChanged || languageChanged || wordDao.count() == 0) {
            val bundledWords = WordSeeder.loadFromAssets(context, WordSeeder.assetFileFor(language))
            if (languageChanged || wordDao.count() != bundledWords.size) {
                wordDao.insertAll(bundledWords)
            }
            prefs.edit()
                .putInt(KEY_WORD_POOL_VERSION, WORD_POOL_VERSION)
                .putString(KEY_WORD_POOL_LANGUAGE, language)
                .apply()
        }
    }

    private companion object {
        const val PREFS_NAME = "cizim_hafiza_settings"
        const val KEY_WORD_POOL_VERSION = "word_pool_version"
        const val KEY_WORD_POOL_LANGUAGE = "word_pool_language"
        // Bump only when assets/words*.json actually changes.
        const val WORD_POOL_VERSION = 1
    }
}
