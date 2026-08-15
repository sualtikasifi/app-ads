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
}
