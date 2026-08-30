package com.sualtikasifi.cizimhafiza.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sualtikasifi.cizimhafiza.data.local.entity.DifficultyReviewEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.WordEntity

/** One row of a manually-classified word — see DifficultyReviewRepository.exportReviewedDifficultiesJson. */
data class ReviewedDifficultyRow(
    val id: Int,
    val text: String,
    val category: String,
    val difficulty: String
)

@Dao
interface DifficultyReviewDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DifficultyReviewEntity)

    // Lowest-id not-yet-classified approved word with no review row yet —
    // always the same word until it's reviewed, so reopening the screen
    // naturally resumes exactly where the reviewer left off.
    @Query(
        """
        SELECT * FROM words
        WHERE approved = 1 AND id NOT IN (SELECT wordId FROM difficulty_review)
        ORDER BY id ASC
        LIMIT 1
        """
    )
    suspend fun getNextPendingWord(): WordEntity?

    @Query("SELECT COUNT(*) FROM words WHERE approved = 1 AND id NOT IN (SELECT wordId FROM difficulty_review)")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM difficulty_review WHERE difficulty = 'EASY'")
    suspend fun getEasyCount(): Int

    @Query("SELECT COUNT(*) FROM difficulty_review WHERE difficulty = 'MEDIUM'")
    suspend fun getMediumCount(): Int

    @Query("SELECT COUNT(*) FROM difficulty_review WHERE difficulty = 'HARD'")
    suspend fun getHardCount(): Int

    // Every decision made so far, ordered by word id for a stable/diffable export.
    @Query(
        """
        SELECT w.id AS id, w.text AS text, w.category AS category, dr.difficulty AS difficulty
        FROM words w
        JOIN difficulty_review dr ON w.id = dr.wordId
        ORDER BY w.id ASC
        """
    )
    suspend fun getAllReviewed(): List<ReviewedDifficultyRow>
}
