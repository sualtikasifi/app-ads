package com.sualtikasifi.cizimhafiza.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sualtikasifi.cizimhafiza.data.local.entity.WordEntity

@Dao
interface WordDao {
    // REPLACE (not IGNORE): re-seeding must also pick up text/category/difficulty
    // corrections for words that already exist on a device (e.g. an id moved to
    // a new category), not just insert brand-new ids.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<WordEntity>)

    @Query("SELECT COUNT(*) FROM words")
    suspend fun count(): Int

    @Query("SELECT DISTINCT category FROM words ORDER BY category")
    suspend fun getCategories(): List<String>

    // difficultyName is the Difficulty enum's .name (e.g. "EASY") — passed as a
    // plain string rather than the enum type to avoid nullable TypeConverter
    // edge cases on raw @Query parameters.
    //
    // The `id <= 1233 OR ...` clause keeps unreviewed/rejected words out of
    // real games: 1233 is the original, already-trusted word pool's highest
    // id (GameConstants.LEGACY_WORD_ID_MAX — Room @Query strings can't
    // reference a Kotlin constant, so keep this literal in sync with it by
    // hand). Anything above it is from the word-review batch (see
    // WordReviewDao) and only becomes playable once approved ("Kalsın").
    @Query(
        """
        SELECT * FROM words
        WHERE (:category IS NULL OR category = :category)
        AND (:difficultyName IS NULL OR difficulty = :difficultyName)
        AND (id <= 1233 OR id IN (SELECT wordId FROM word_review WHERE status = 'KEPT'))
        ORDER BY RANDOM()
        LIMIT :limit
        """
    )
    suspend fun getRandomWords(limit: Int, category: String?, difficultyName: String?): List<WordEntity>

    // Used by online matches: both players fetch the exact same words by the
    // shared id list the room host locked in, instead of each rolling their
    // own random selection.
    @Query("SELECT * FROM words WHERE id IN (:ids)")
    suspend fun getWordsByIds(ids: List<Int>): List<WordEntity>
}
