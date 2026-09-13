package com.sualtikasifi.cizimhafiza.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sualtikasifi.cizimhafiza.data.local.entity.WordEntity
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty

@Dao
interface WordDao {
    // REPLACE (not IGNORE): re-seeding must also pick up text/category/difficulty
    // corrections for words that already exist on a device (e.g. an id moved to
    // a new category), not just insert brand-new ids.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<WordEntity>)

    @Query("SELECT COUNT(*) FROM words")
    suspend fun count(): Int

    /**
     * Empties the playable pool so a re-seed can rebuild it from the asset
     * file exactly, rather than upserting on top of whatever is already
     * there.
     *
     * insertAll's REPLACE-by-id upsert can only add and overwrite, never
     * remove — so any id the new asset no longer contains just stayed
     * behind. That was survivable when the two languages held identical id
     * sets, and stopped being survivable when they did not: the English
     * pool now deliberately omits words that only make sense in Turkish
     * (see WordPoolSynchronizer's v15 note), and without this an English
     * player would keep being dealt "kokoreç" and "künefe" left over from
     * the Turkish seed.
     *
     * Scoped to approved = 1 so the "Kelime İncele" candidate rows, which
     * come from a different set of files, are left alone. Deleting here
     * never touches botTrainedWords in Firestore (a separate store keyed by
     * the same ids) — only the local playable pool.
     */
    @Query("DELETE FROM words WHERE approved = 1")
    suspend fun deleteApproved()

    @Query("SELECT DISTINCT category FROM words WHERE approved = 1 ORDER BY category")
    suspend fun getCategories(): List<String>

    // difficultyName is the Difficulty enum's .name (e.g. "EASY") — passed as a
    // plain string rather than the enum type to avoid nullable TypeConverter
    // edge cases on raw @Query parameters.
    //
    // approved keeps unreviewed/rejected "Kelime İncele" candidates out of
    // real games — see WordEntity.approved.
    @Query(
        """
        SELECT * FROM words
        WHERE (:category IS NULL OR category = :category)
        AND (:difficultyName IS NULL OR difficulty = :difficultyName)
        AND approved = 1
        ORDER BY RANDOM()
        LIMIT :limit
        """
    )
    suspend fun getRandomWords(limit: Int, category: String?, difficultyName: String?): List<WordEntity>

    // Same as getRandomWords, but skips ids in [excludeIds] — used by the
    // World Map's level draws to steer away from words a player just saw a
    // level or two ago (see GameRepositoryImpl.getRandomWordsMix). An empty
    // list is a no-op filter (SQLite's "NOT IN ()" always matches), so this
    // is safe to call with no exclusions too.
    @Query(
        """
        SELECT * FROM words
        WHERE (:category IS NULL OR category = :category)
        AND (:difficultyName IS NULL OR difficulty = :difficultyName)
        AND approved = 1
        AND id NOT IN (:excludeIds)
        ORDER BY RANDOM()
        LIMIT :limit
        """
    )
    suspend fun getRandomWordsExcluding(
        limit: Int,
        category: String?,
        difficultyName: String?,
        excludeIds: List<Int>
    ): List<WordEntity>

    // Used by online matches: both players fetch the exact same words by the
    // shared id list the room host locked in, instead of each rolling their
    // own random selection.
    @Query("SELECT * FROM words WHERE id IN (:ids)")
    suspend fun getWordsByIds(ids: List<Int>): List<WordEntity>

    // Flips a single word's playability — called when the reviewer decides
    // (see WordReviewRepository.keep/delete) so they can immediately see
    // their own "Kalsın" words show up in their own games, without waiting
    // for the developer's later promote-into-words.json step (which is what
    // makes the decision count for every OTHER player).
    @Query("UPDATE words SET approved = :approved WHERE id = :id")
    suspend fun setApproved(id: Int, approved: Boolean)

    // Immediately reflects the reviewer's own manual difficulty classification
    // on their own device (e.g. in World Map levels) — see
    // DifficultyReviewRepository.setDifficulty.
    @Query("UPDATE words SET difficulty = :difficulty WHERE id = :id")
    suspend fun updateDifficulty(id: Int, difficulty: Difficulty)

    // Every playable word, easiest first — the fixed draw order for the
    // "Bot Eğitim" screen (see BotTrainingRepositoryImpl), so the trainer
    // always works through easy words before harder ones. A CASE expression
    // (not a plain ORDER BY difficulty) because that column's alphabetical
    // order is EASY/HARD/MEDIUM, not the EASY/MEDIUM/HARD difficulty curve.
    @Query(
        """
        SELECT * FROM words
        WHERE approved = 1
        ORDER BY CASE difficulty WHEN 'EASY' THEN 0 WHEN 'MEDIUM' THEN 1 WHEN 'HARD' THEN 2 ELSE 3 END, category, text
        """
    )
    suspend fun getAllWordsOrderedByDifficulty(): List<WordEntity>
}
