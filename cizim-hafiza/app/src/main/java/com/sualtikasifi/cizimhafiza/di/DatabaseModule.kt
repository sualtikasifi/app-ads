package com.sualtikasifi.cizimhafiza.di

import android.content.Context
import androidx.room.Room
import com.sualtikasifi.cizimhafiza.data.local.AppDatabase
import com.sualtikasifi.cizimhafiza.data.local.dao.DrawingResultDao
import com.sualtikasifi.cizimhafiza.data.local.dao.GameSessionDao
import com.sualtikasifi.cizimhafiza.data.local.dao.LevelProgressDao
import com.sualtikasifi.cizimhafiza.data.local.dao.WordDao
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
}
