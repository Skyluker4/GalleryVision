// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.di

import android.content.Context
import androidx.room.Room
import com.lukesimmons.galleryvision.core.database.GalleryVisionDatabase
import com.lukesimmons.galleryvision.core.datastore.SettingsStore
import com.lukesimmons.galleryvision.data.index.LibraryRepositoryImpl
import com.lukesimmons.galleryvision.data.mediastore.MediaStoreScanner
import com.lukesimmons.galleryvision.domain.repository.LibraryRepository
import com.lukesimmons.galleryvision.inference.FaceEngine
import com.lukesimmons.galleryvision.inference.ObjectEngine
import com.lukesimmons.galleryvision.inference.OcrEngine
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
    fun provideDatabase(@ApplicationContext context: Context): GalleryVisionDatabase =
        Room.databaseBuilder(context, GalleryVisionDatabase::class.java, "galleryvision.db").build()

    @Provides
    @Singleton
    fun provideScanner(@ApplicationContext context: Context): MediaStoreScanner =
        MediaStoreScanner(context)

    @Provides
    @Singleton
    fun provideLibraryRepository(
        db: GalleryVisionDatabase,
        scanner: MediaStoreScanner,
    ): LibraryRepository = LibraryRepositoryImpl(db, scanner)

    @Provides
    @Singleton
    fun provideOcrEngine(@ApplicationContext context: Context): OcrEngine = OcrEngine(context)

    @Provides
    @Singleton
    fun provideFaceEngine(@ApplicationContext context: Context): FaceEngine = FaceEngine(context)

    @Provides
    @Singleton
    fun provideObjectEngine(@ApplicationContext context: Context): ObjectEngine = ObjectEngine(context)

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore = SettingsStore(context)
}
