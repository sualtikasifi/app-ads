package com.sualtikasifi.cizimhafiza.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sualtikasifi.cizimhafiza.data.local.entity.WordEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.WordReviewEntity

/** One row of a reviewed word — see WordReviewDao.getAllReviewed / WordReviewRepository.exportReviewedWordsJson. */
data class ReviewedWordRow(
    val id: Int,
    val text: String,
    val category: String,
    val difficulty: String,
    val status: String
)

@Dao
interface WordReviewDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WordReviewEntity)

    // Lowest-id word (from the review batch, i.e. above the legacy cutoff)
    // with no review row yet — always the same word until it's reviewed, so
    // reopening the screen naturally resumes exactly where the reviewer left off.
    @Query(
        """
        SELECT * FROM words
        WHERE id > :legacyMaxId AND id NOT IN (SELECT wordId FROM word_review)
        ORDER BY id ASC
        LIMIT 1
        """
    )
    suspend fun getNextPendingWord(legacyMaxId: Int): WordEntity?

    @Query("SELECT COUNT(*) FROM words WHERE id > :legacyMaxId AND id NOT IN (SELECT wordId FROM word_review)")
    suspend fun getPendingCount(legacyMaxId: Int): Int

    @Query("SELECT COUNT(*) FROM word_review WHERE status = 'KEPT'")
    suspend fun getKeptCount(): Int

    @Query("SELECT COUNT(*) FROM word_review WHERE status = 'DELETED'")
    suspend fun getDeletedCount(): Int

    // Every decision made so far, oldest-reviewed-first isn't required here —
    // ordered by word id purely so the exported file is stable/diffable.
    @Query(
        """
        SELECT w.id AS id, w.text AS text, w.category AS category, w.difficulty AS difficulty, wr.status AS status
        FROM words w
        JOIN word_review wr ON w.id = wr.wordId
        ORDER BY w.id ASC
        """
    )
    suspend fun getAllReviewed(): List<ReviewedWordRow>
}
