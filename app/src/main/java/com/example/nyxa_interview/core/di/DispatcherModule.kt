package com.example.nyxa_interview.core.di

import com.example.nyxa_interview.core.dispatcher.DefaultDispatcherProvider
import com.example.nyxa_interview.core.dispatcher.DispatcherProvider
import com.example.nyxa_interview.core.idempotency.IdempotencyKeyGenerator
import com.example.nyxa_interview.core.idempotency.UuidIdempotencyKeyGenerator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DispatcherModule {

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider

    @Binds
    @Singleton
    abstract fun bindIdempotencyKeyGenerator(impl: UuidIdempotencyKeyGenerator): IdempotencyKeyGenerator
}
