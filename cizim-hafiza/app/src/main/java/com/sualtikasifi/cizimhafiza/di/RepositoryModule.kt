package com.sualtikasifi.cizimhafiza.di

import com.sualtikasifi.cizimhafiza.data.repository.AuthRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.BackupRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.BotTrainingRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.BugReportRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.DifficultyReviewRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.FriendRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.GameRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.LevelProgressRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.OnlineGameRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.WordReviewRepositoryImpl
import com.sualtikasifi.cizimhafiza.domain.repository.AuthRepository
import com.sualtikasifi.cizimhafiza.domain.repository.BackupRepository
import com.sualtikasifi.cizimhafiza.domain.repository.BotTrainingRepository
import com.sualtikasifi.cizimhafiza.domain.repository.BugReportRepository
import com.sualtikasifi.cizimhafiza.domain.repository.DifficultyReviewRepository
import com.sualtikasifi.cizimhafiza.domain.repository.FriendRepository
import com.sualtikasifi.cizimhafiza.domain.repository.GameRepository
import com.sualtikasifi.cizimhafiza.domain.repository.LevelProgressRepository
import com.sualtikasifi.cizimhafiza.domain.repository.OnlineGameRepository
import com.sualtikasifi.cizimhafiza.domain.repository.WordReviewRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGameRepository(impl: GameRepositoryImpl): GameRepository

    @Binds
    @Singleton
    abstract fun bindOnlineGameRepository(impl: OnlineGameRepositoryImpl): OnlineGameRepository

    @Binds
    @Singleton
    abstract fun bindFriendRepository(impl: FriendRepositoryImpl): FriendRepository

    @Binds
    @Singleton
    abstract fun bindLevelProgressRepository(impl: LevelProgressRepositoryImpl): LevelProgressRepository

    @Binds
    @Singleton
    abstract fun bindWordReviewRepository(impl: WordReviewRepositoryImpl): WordReviewRepository

    @Binds
    @Singleton
    abstract fun bindDifficultyReviewRepository(impl: DifficultyReviewRepositoryImpl): DifficultyReviewRepository

    @Binds
    @Singleton
    abstract fun bindBotTrainingRepository(impl: BotTrainingRepositoryImpl): BotTrainingRepository

    @Binds
    @Singleton
    abstract fun bindBugReportRepository(impl: BugReportRepositoryImpl): BugReportRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository
}
