package com.sualtikasifi.cizimhafiza.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sualtikasifi.cizimhafiza.data.local.entity.WordEntity

@Dao
interface WordDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(words: List<WordEntity>)

    @Query("SELECT COUNT(*) FROM words")
    suspend fun count(): Int

    @Query("SELECT DISTINCT category FROM words ORDER BY category")
    suspend fun getCategories(): List<String>

    // difficultyName is the Difficulty enum's .name (e.g. "EASY") — passed as a
    // plain string rather than the enum type to avoid nullable TypeConverter
    // edge cases on raw @Query parameters.
    @Query(
        """
        SELECT COUNT(*) FROM words
        WHERE (:category IS NULL OR category = :category)
        AND (:difficultyName IS NULL OR difficulty = :difficultyName)
        """
    )
    suspend fun countFiltered(category: String?, difficultyName: String?): Int

    @Query(
        """
        SELECT * FROM words
        WHERE (:category IS NULL OR category = :category)
        AND (:difficultyName IS NULL OR difficulty = :difficultyName)
        ORDER BY RANDOM()
        LIMIT :limit
        """
    )
    suspend fun getRandomWords(limit: Int, category: String?, difficultyName: String?): List<WordEntity>
}
