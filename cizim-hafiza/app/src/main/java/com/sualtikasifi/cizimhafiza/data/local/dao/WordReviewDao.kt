package com.sualtikasifi.cizimhafiza.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sualtikasifi.cizimhafiza.data.local.entity.WordEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.WordReviewEntity

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
}
