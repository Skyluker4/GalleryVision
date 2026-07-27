// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.workers

import com.lukesimmons.galleryvision.core.database.GalleryVisionDatabase
import com.lukesimmons.galleryvision.data.index.DetectionIndexer
import com.lukesimmons.galleryvision.domain.repository.LibraryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt entry point for workers to pull singletons without constructor injection. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerEntryPoint {
    fun libraryRepository(): LibraryRepository

    fun detectionIndexer(): DetectionIndexer

    fun database(): GalleryVisionDatabase
}
