package com.sualtikasifi.cizimhafiza.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sualtikasifi.cizimhafiza.data.local.entity.GameSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameSessionDao {
    @Insert
    suspend fun insert(session: GameSessionEntity): Long

    @Query("SELECT * FROM game_sessions ORDER BY date DESC")
    fun observeSessions(): Flow<List<GameSessionEntity>>

    @Query("SELECT MAX(totalScore) FROM game_sessions")
    suspend fun getBestScore(): Int?

    @Query("SELECT COALESCE(SUM(wordCount), 0) FROM game_sessions")
    suspend fun getTotalWordsPlayed(): Int

    // "Son Oyunlar" is capped, not just displayed-with-a-limit: older rows
    // are actually deleted so the table (and the stats derived from it)
    // never grows unbounded.
    @Query(
        """
        DELETE FROM game_sessions WHERE id NOT IN (
            SELECT id FROM game_sessions ORDER BY date DESC LIMIT :keep
        )
        """
    )
    suspend fun pruneOlderThan(keep: Int)
}
