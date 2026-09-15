package com.example.nyxa_interview.core.di

import com.example.nyxa_interview.data.repository.AuthRepositoryImpl
import com.example.nyxa_interview.data.repository.CartRepositoryImpl
import com.example.nyxa_interview.data.repository.CheckoutRepositoryImpl
import com.example.nyxa_interview.data.repository.GamesRepositoryImpl
import com.example.nyxa_interview.data.remote.mock.MockNetworkConditions
import com.example.nyxa_interview.data.remote.mock.NetworkConditions
import com.example.nyxa_interview.data.repository.InMemoryProductCache
import com.example.nyxa_interview.data.repository.PendingGameActionRepositoryImpl
import com.example.nyxa_interview.data.repository.ProductRepositoryImpl
import com.example.nyxa_interview.data.repository.WalletRepositoryImpl
import com.example.nyxa_interview.core.security.EncryptedTokenStore
import com.example.nyxa_interview.core.security.TokenStore
import com.example.nyxa_interview.domain.repository.AuthRepository
import com.example.nyxa_interview.domain.repository.CartRepository
import com.example.nyxa_interview.domain.repository.CheckoutRepository
import com.example.nyxa_interview.domain.repository.GamesRepository
import com.example.nyxa_interview.domain.repository.PendingGameActionRepository
import com.example.nyxa_interview.domain.repository.ProductCache
import com.example.nyxa_interview.domain.repository.ProductRepository
import com.example.nyxa_interview.domain.repository.WalletRepository
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
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindWalletRepository(impl: WalletRepositoryImpl): WalletRepository

    @Binds
    @Singleton
    abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository

    @Binds
    @Singleton
    abstract fun bindCartRepository(impl: CartRepositoryImpl): CartRepository

    @Binds
    @Singleton
    abstract fun bindCheckoutRepository(impl: CheckoutRepositoryImpl): CheckoutRepository

    @Binds
    @Singleton
    abstract fun bindGamesRepository(impl: GamesRepositoryImpl): GamesRepository

    @Binds
    @Singleton
    abstract fun bindPendingGameActionRepository(impl: PendingGameActionRepositoryImpl): PendingGameActionRepository

    @Binds
    @Singleton
    abstract fun bindTokenStore(impl: EncryptedTokenStore): TokenStore

    @Binds
    @Singleton
    abstract fun bindProductCache(impl: InMemoryProductCache): ProductCache

    @Binds
    @Singleton
    abstract fun bindNetworkConditions(impl: MockNetworkConditions): NetworkConditions
}
