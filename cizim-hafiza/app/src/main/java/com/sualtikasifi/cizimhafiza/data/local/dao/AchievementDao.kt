package com.sualtikasifi.cizimhafiza.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sualtikasifi.cizimhafiza.data.local.entity.UnlockedAchievementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {
    @Query("SELECT id FROM unlocked_achievements")
    suspend fun getUnlockedIds(): List<String>

    // IGNORE, not REPLACE: unlockedAtMillis should never move once set — a
    // duplicate insert attempt (shouldn't normally happen, since the check
    // is against getUnlockedIds first) must not overwrite the original
    // unlock time.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: UnlockedAchievementEntity)

    @Query("SELECT * FROM unlocked_achievements")
    fun observeAll(): Flow<List<UnlockedAchievementEntity>>

    // Drives the MainMenu's "new achievement" badge — see
    // UnlockedAchievementEntity.seen.
    @Query("SELECT COUNT(*) FROM unlocked_achievements WHERE seen = 0")
    fun observeUnseenCount(): Flow<Int>

    @Query("SELECT id FROM unlocked_achievements WHERE seen = 0")
    suspend fun getUnseenIds(): List<String>

    @Query("UPDATE unlocked_achievements SET seen = 1 WHERE seen = 0")
    suspend fun markAllSeen()

    /** Used only when switching to a different account — see BackupRepositoryImpl.switchToAccount. */
    @Query("DELETE FROM unlocked_achievements")
    suspend fun deleteAll()
}
