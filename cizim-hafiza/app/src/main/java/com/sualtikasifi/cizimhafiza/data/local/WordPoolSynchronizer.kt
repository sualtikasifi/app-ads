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
            // approved = true: this is the permanent, always-playable pool —
            // see WordEntity.approved / WordSeeder.loadFromAssets.
            val bundledWords = WordSeeder.loadFromAssets(context, WordSeeder.assetFileFor(language), approved = true)
            wordDao.insertAll(bundledWords)
            prefs.edit()
                .putInt(KEY_WORD_POOL_VERSION, WORD_POOL_VERSION)
                .putString(KEY_WORD_POOL_LANGUAGE, language)
                .apply()
        }

        syncReviewBatches(prefs)
    }

    // The "Kelime İncele" candidate pool (see WordReviewRepository) —
    // invisible to real games (approved = false) until a developer promotes
    // a "Kalsın" word into words*.json (see the class doc on WordEntity).
    // These are Turkish-only regardless of the current app language (not
    // yet translated — a known gap, matching the rest of the app's
    // "Turkish first" review workflow) and are purely additive, so there's
    // no language-switch handling to do here, just a version gate so
    // re-parsing ~1400 words' worth of JSON is skipped on most launches.
    private suspend fun syncReviewBatches(prefs: android.content.SharedPreferences) {
        if (prefs.getInt(KEY_REVIEW_BATCH_VERSION, -1) == REVIEW_BATCH_VERSION) return
        val batchWords = REVIEW_BATCH_FILES.flatMap { WordSeeder.loadFromAssets(context, it, approved = false) }
        wordDao.insertAll(batchWords)
        prefs.edit().putInt(KEY_REVIEW_BATCH_VERSION, REVIEW_BATCH_VERSION).apply()
    }

    private companion object {
        const val PREFS_NAME = "cizim_hafiza_settings"
        const val KEY_WORD_POOL_VERSION = "word_pool_version"
        const val KEY_WORD_POOL_LANGUAGE = "word_pool_language"
        // Bump when assets/words*.json changes, or (as with v2) when a
        // re-seed is needed to correct every row's `approved` value after a
        // WordEntity schema change (see AppDatabase.MIGRATION_4_5).
        const val WORD_POOL_VERSION = 5

        const val KEY_REVIEW_BATCH_VERSION = "review_batch_version"
        // Bump whenever a word_review_batch_*.json file's content changes
        // (new batch added, or previously-decided words promoted/removed),
        // so it gets re-seeded on existing installs too.
        const val REVIEW_BATCH_VERSION = 5
        val REVIEW_BATCH_FILES = listOf(
            "word_review_batch_a.json",
            "word_review_batch_b.json",
            "word_review_batch_c.json"
        )
    }
}
