package com.sualtikasifi.cizimhafiza.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sualtikasifi.cizimhafiza.data.local.entity.GameSessionEntity

@Dao
interface GameSessionDao {
    @Insert
    suspend fun insert(session: GameSessionEntity): Long

    // Used only for the one-time lifetime-score seed (CizimHafizaApp) — the
    // pruned table only has the surviving rows at that point, which is a
    // reasonable best-effort starting point since older rows are gone for good.
    @Query("SELECT COALESCE(SUM(totalScore), 0) FROM game_sessions")
    suspend fun getTotalScoreSum(): Int

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

    /** Used only when switching accounts — see BackupRepositoryImpl.switchToAccount. */
    @Query("DELETE FROM game_sessions")
    suspend fun deleteAll()
}
