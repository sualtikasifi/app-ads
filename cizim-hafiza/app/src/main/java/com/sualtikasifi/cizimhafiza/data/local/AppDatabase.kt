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
    version = 5,
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

        // Adds WordEntity.approved — replaces the old "id <= some hardcoded
        // legacy cutoff" gameplay filter, which broke the moment a reviewed
        // word got promoted into words.json at its original (non-legacy) id
        // (see the "Kelime İncele" export/promote flow). The interim value
        // every existing row gets here (approved=1) is immediately corrected
        // by WordPoolSynchronizer re-seeding right after this migration runs
        // (both WORD_POOL_VERSION and REVIEW_BATCH_VERSION are bumped
        // alongside this migration specifically so that re-seed always
        // happens) — words*.json rows are re-asserted approved=true, batch
        // rows approved=false, so nothing is ever left wrongly playable.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE words ADD COLUMN approved INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
