package com.sualtikasifi.cizimhafiza.di

import com.sualtikasifi.cizimhafiza.data.repository.AuthRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.BackupRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.BotNameRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.BotTrainingRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.BugReportRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.DetectorEventRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.DrawingReportRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.ModerationRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.PenaltyRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.DifficultyReviewRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.DuelRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.GhostRunRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.FriendRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.GlobalLeagueRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.AccountDeletionRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.GameRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.LevelProgressRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.OnlineGameRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.WordReviewRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.XpEventRepositoryImpl
import com.sualtikasifi.cizimhafiza.domain.repository.AuthRepository
import com.sualtikasifi.cizimhafiza.domain.repository.BackupRepository
import com.sualtikasifi.cizimhafiza.domain.repository.BotNameRepository
import com.sualtikasifi.cizimhafiza.domain.repository.BotTrainingRepository
import com.sualtikasifi.cizimhafiza.domain.repository.BugReportRepository
import com.sualtikasifi.cizimhafiza.domain.repository.DetectorEventRepository
import com.sualtikasifi.cizimhafiza.domain.repository.DrawingReportRepository
import com.sualtikasifi.cizimhafiza.domain.repository.ModerationRepository
import com.sualtikasifi.cizimhafiza.domain.repository.PenaltyRepository
import com.sualtikasifi.cizimhafiza.domain.repository.DifficultyReviewRepository
import com.sualtikasifi.cizimhafiza.domain.repository.DuelRepository
import com.sualtikasifi.cizimhafiza.domain.repository.GhostRunRepository
import com.sualtikasifi.cizimhafiza.domain.repository.FriendRepository
import com.sualtikasifi.cizimhafiza.domain.repository.GlobalLeagueRepository
import com.sualtikasifi.cizimhafiza.domain.repository.AccountDeletionRepository
import com.sualtikasifi.cizimhafiza.domain.repository.GameRepository
import com.sualtikasifi.cizimhafiza.domain.repository.LevelProgressRepository
import com.sualtikasifi.cizimhafiza.domain.repository.OnlineGameRepository
import com.sualtikasifi.cizimhafiza.domain.repository.WordReviewRepository
import com.sualtikasifi.cizimhafiza.domain.repository.XpEventRepository
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
    abstract fun bindAccountDeletionRepository(impl: AccountDeletionRepositoryImpl): AccountDeletionRepository

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
    abstract fun bindGlobalLeagueRepository(impl: GlobalLeagueRepositoryImpl): GlobalLeagueRepository

    @Binds
    @Singleton
    abstract fun bindXpEventRepository(impl: XpEventRepositoryImpl): XpEventRepository

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
    abstract fun bindBotNameRepository(impl: BotNameRepositoryImpl): BotNameRepository

    @Binds
    @Singleton
    abstract fun bindBugReportRepository(impl: BugReportRepositoryImpl): BugReportRepository

    @Binds
    @Singleton
    abstract fun bindDrawingReportRepository(impl: DrawingReportRepositoryImpl): DrawingReportRepository

    @Binds
    @Singleton
    abstract fun bindDetectorEventRepository(impl: DetectorEventRepositoryImpl): DetectorEventRepository

    @Binds
    @Singleton
    abstract fun bindModerationRepository(impl: ModerationRepositoryImpl): ModerationRepository

    @Binds
    @Singleton
    abstract fun bindPenaltyRepository(impl: PenaltyRepositoryImpl): PenaltyRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    abstract fun bindDuelRepository(impl: DuelRepositoryImpl): DuelRepository

    @Binds
    @Singleton
    abstract fun bindGhostRunRepository(impl: GhostRunRepositoryImpl): GhostRunRepository
}
