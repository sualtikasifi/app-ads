package com.sualtikasifi.cizimhafiza.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sualtikasifi.cizimhafiza.data.local.entity.DrawingResultEntity

@Dao
interface DrawingResultDao {
    @Insert
    suspend fun insertAll(results: List<DrawingResultEntity>)

    @Query("SELECT * FROM drawing_results WHERE sessionId = :sessionId")
    suspend fun getForSession(sessionId: Long): List<DrawingResultEntity>

    // Most-recently-drawn word ids in a category, across every past game
    // (solo or online, however they were selected) — used to steer World
    // Map level draws away from words the player just saw. drawing_results
    // is never pruned (unlike game_sessions), so this has as much history
    // as the device has ever recorded, capped by [limit].
    @Query(
        """
        SELECT dr.wordId FROM drawing_results dr
        INNER JOIN words w ON w.id = dr.wordId
        WHERE w.category = :category
        GROUP BY dr.wordId
        ORDER BY MAX(dr.id) DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentWordIds(category: String, limit: Int): List<Int>
}
