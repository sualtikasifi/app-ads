package com.sualtikasifi.cizimhafiza.di

import com.sualtikasifi.cizimhafiza.data.repository.FriendRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.GameRepositoryImpl
import com.sualtikasifi.cizimhafiza.data.repository.OnlineGameRepositoryImpl
import com.sualtikasifi.cizimhafiza.domain.repository.FriendRepository
import com.sualtikasifi.cizimhafiza.domain.repository.GameRepository
import com.sualtikasifi.cizimhafiza.domain.repository.OnlineGameRepository
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
}
