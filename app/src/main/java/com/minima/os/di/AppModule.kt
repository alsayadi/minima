package com.minima.os.di

import android.content.Context
import com.minima.os.data.db.MinimaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MinimaDatabase {
        return MinimaDatabase.create(context)
    }

    @Provides
    fun provideTaskDao(db: MinimaDatabase) = db.taskDao()

    @Provides
    fun provideActionDao(db: MinimaDatabase) = db.actionDao()

    @Provides
    fun provideMemoryDao(db: MinimaDatabase) = db.memoryDao()
}
