package com.example.nyxa_interview.core.di

import android.content.Context
import androidx.room.Room
import com.example.nyxa_interview.data.local.AppDatabase
import com.example.nyxa_interview.data.local.dao.PendingGameActionDao
import com.example.nyxa_interview.data.local.dao.WalletDao
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
        Room.databaseBuilder(context, AppDatabase::class.java, "nyxa_interview.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideWalletDao(database: AppDatabase): WalletDao = database.walletDao()

    @Provides
    fun providePendingGameActionDao(database: AppDatabase): PendingGameActionDao =
        database.pendingGameActionDao()
}
