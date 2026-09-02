package com.sualtikasifi.cizimhafiza.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sualtikasifi.cizimhafiza.data.local.entity.LevelProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LevelProgressDao {
    @Upsert
    suspend fun upsert(entity: LevelProgressEntity)

    @Query("SELECT * FROM level_progress WHERE worldId = :worldId AND levelIndex = :levelIndex")
    suspend fun getOne(worldId: Int, levelIndex: Int): LevelProgressEntity?

    @Query("SELECT * FROM level_progress WHERE worldId = :worldId")
    fun observeWorldProgress(worldId: Int): Flow<List<LevelProgressEntity>>

    @Query("SELECT * FROM level_progress")
    fun observeAllProgress(): Flow<List<LevelProgressEntity>>

    /** One-shot read for the cloud backup — see BackupRepositoryImpl.backupNow. */
    @Query("SELECT * FROM level_progress")
    suspend fun getAll(): List<LevelProgressEntity>
}
