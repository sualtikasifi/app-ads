package com.sualtikasifi.cizimhafiza.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per unlocked achievement — presence of a row IS the unlocked
 * state (see AchievementDao.getUnlockedIds), nothing else to track. [id] is
 * an Achievement enum constant's `.name` (see domain.model.Achievement).
 */
@Entity(tableName = "unlocked_achievements")
data class UnlockedAchievementEntity(
    @PrimaryKey val id: String,
    val unlockedAtMillis: Long
)
