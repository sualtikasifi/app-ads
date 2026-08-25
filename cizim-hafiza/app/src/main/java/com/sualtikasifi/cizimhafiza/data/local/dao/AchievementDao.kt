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
}
