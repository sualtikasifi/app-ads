package com.sualtikasifi.cizimhafiza.di

import android.content.Context
import androidx.room.Room
import com.sualtikasifi.cizimhafiza.data.local.AppDatabase
import com.sualtikasifi.cizimhafiza.data.local.dao.AchievementDao
import com.sualtikasifi.cizimhafiza.data.local.dao.DifficultyReviewDao
import com.sualtikasifi.cizimhafiza.data.local.dao.DrawingResultDao
import com.sualtikasifi.cizimhafiza.data.local.dao.GameSessionDao
import com.sualtikasifi.cizimhafiza.data.local.dao.LevelProgressDao
import com.sualtikasifi.cizimhafiza.data.local.dao.WordDao
import com.sualtikasifi.cizimhafiza.data.local.dao.WordReviewDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9
            )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideWordDao(database: AppDatabase): WordDao = database.wordDao()

    @Provides
    fun provideGameSessionDao(database: AppDatabase): GameSessionDao = database.gameSessionDao()

    @Provides
    fun provideDrawingResultDao(database: AppDatabase): DrawingResultDao = database.drawingResultDao()

    @Provides
    fun provideLevelProgressDao(database: AppDatabase): LevelProgressDao = database.levelProgressDao()

    @Provides
    fun provideWordReviewDao(database: AppDatabase): WordReviewDao = database.wordReviewDao()

    @Provides
    fun provideDifficultyReviewDao(database: AppDatabase): DifficultyReviewDao = database.difficultyReviewDao()

    @Provides
    fun provideAchievementDao(database: AppDatabase): AchievementDao = database.achievementDao()
}
