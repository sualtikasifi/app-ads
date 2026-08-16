package com.sualtikasifi.cizimhafiza.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sualtikasifi.cizimhafiza.data.local.dao.DrawingResultDao
import com.sualtikasifi.cizimhafiza.data.local.dao.GameSessionDao
import com.sualtikasifi.cizimhafiza.data.local.dao.LevelProgressDao
import com.sualtikasifi.cizimhafiza.data.local.dao.WordDao
import com.sualtikasifi.cizimhafiza.data.local.dao.WordReviewDao
import com.sualtikasifi.cizimhafiza.data.local.entity.DrawingResultEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.GameSessionEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.LevelProgressEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.WordEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.WordReviewEntity

@Database(
    entities = [
        WordEntity::class, GameSessionEntity::class, DrawingResultEntity::class,
        LevelProgressEntity::class, WordReviewEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun gameSessionDao(): GameSessionDao
    abstract fun drawingResultDao(): DrawingResultDao
    abstract fun levelProgressDao(): LevelProgressDao
    abstract fun wordReviewDao(): WordReviewDao

    companion object {
        const val DATABASE_NAME = "cizim_hafiza.db"

        // A word-review decision represents real, hours-of-effort human work
        // (see WordReviewScreen) — unlike the other tables here (words,
        // sessions, level progress, all cheaply regenerable), it must NOT be
        // silently wiped by fallbackToDestructiveMigration() on a future
        // schema bump. This explicit migration only creates the new table,
        // so v3 installs upgrade in place with zero data loss. Any FUTURE
        // migration that touches word_review must do the same (a real
        // Migration, not the destructive fallback) to keep that guarantee.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS word_review (
                        wordId INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        reviewedAtMillis INTEGER NOT NULL,
                        PRIMARY KEY(wordId)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
