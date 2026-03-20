package com.ankireview.di

import android.content.Context
import androidx.room.Room
import com.ankireview.data.AppDatabase
import com.ankireview.data.MIGRATION_1_2
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "anki_db")
            .addMigrations(MIGRATION_1_2)           // safe upgrade v1->v2
            .fallbackToDestructiveMigration()       // fallback if anything else
            .build()
}
