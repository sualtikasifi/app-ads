package com.sualtikasifi.cizimhafiza.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sualtikasifi.cizimhafiza.data.local.dao.DifficultyReviewDao
import com.sualtikasifi.cizimhafiza.data.local.dao.DrawingResultDao
import com.sualtikasifi.cizimhafiza.data.local.dao.GameSessionDao
import com.sualtikasifi.cizimhafiza.data.local.dao.LevelProgressDao
import com.sualtikasifi.cizimhafiza.data.local.dao.WordDao
import com.sualtikasifi.cizimhafiza.data.local.dao.WordReviewDao
import com.sualtikasifi.cizimhafiza.data.local.entity.DifficultyReviewEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.DrawingResultEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.GameSessionEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.LevelProgressEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.WordEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.WordReviewEntity

@Database(
    entities = [
        WordEntity::class, GameSessionEntity::class, DrawingResultEntity::class,
        LevelProgressEntity::class, WordReviewEntity::class, DifficultyReviewEntity::class
    ],
    // v6->v7: GameSessionEntity's opponentName/opponentScore became
    // placement/playerCount to support N-player (up to 8) online rooms
    // instead of exactly 2. No explicit migration — game_sessions/
    // drawing_results are "cheaply regenerable" cosmetic history (unlike
    // word_review/difficulty_review, see MIGRATION_3_4/5_6's own comments),
    // so this falls under fallbackToDestructiveMigration() in DatabaseModule.
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun gameSessionDao(): GameSessionDao
    abstract fun drawingResultDao(): DrawingResultDao
    abstract fun levelProgressDao(): LevelProgressDao
    abstract fun wordReviewDao(): WordReviewDao
    abstract fun difficultyReviewDao(): DifficultyReviewDao

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
        // (see the "Kelime İncele" export/promote flow). Every row defaults
        // to approved=1 from the ALTER TABLE; WordPoolSynchronizer's re-seed
        // right after this migration (WORD_POOL_VERSION/REVIEW_BATCH_VERSION
        // are both bumped alongside it) then re-asserts the correct value
        // for anything still referenced by words*.json (true) or a batch
        // file (false). The one thing a re-seed can't fix is a word that
        // was reviewed "Sil" and has since been removed from every JSON
        // file entirely — nothing re-seeds it anymore, so left at the
        // ALTER TABLE's default it would silently become playable again on
        // exactly the reviewing device that rejected it. The UPDATE below
        // corrects that straight from this device's own word_review table
        // before anything else touches the column.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE words ADD COLUMN approved INTEGER NOT NULL DEFAULT 1")
                db.execSQL(
                    "UPDATE words SET approved = 0 WHERE id IN (SELECT wordId FROM word_review WHERE status = 'DELETED')"
                )
            }
        }

        // Adds difficulty_review — same non-destructive-migration guarantee as
        // MIGRATION_3_4's word_review: manual difficulty classification (see
        // "Zorluk Belirle" / DifficultyReviewScreen) is real reviewer effort
        // and must survive a schema bump, unlike the other regenerable tables.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS difficulty_review (
                        wordId INTEGER NOT NULL,
                        difficulty TEXT NOT NULL,
                        reviewedAtMillis INTEGER NOT NULL,
                        PRIMARY KEY(wordId)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
