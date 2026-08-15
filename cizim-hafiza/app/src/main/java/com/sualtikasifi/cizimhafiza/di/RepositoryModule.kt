package com.sualtikasifi.cizimhafiza.di

import com.sualtikasifi.cizimhafiza.data.repository.GameRepositoryImpl
import com.sualtikasifi.cizimhafiza.domain.repository.GameRepository
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
}
