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
}
