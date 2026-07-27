// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.data.index

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.lukesimmons.galleryvision.core.database.GalleryVisionDatabase
import com.lukesimmons.galleryvision.core.database.entity.DenyEntity
import com.lukesimmons.galleryvision.core.database.entity.DetectionEntity
import com.lukesimmons.galleryvision.core.database.entity.MediaEntity
import com.lukesimmons.galleryvision.core.model.DenyKind
import com.lukesimmons.galleryvision.data.mediastore.MediaStoreScanner
import com.lukesimmons.galleryvision.domain.repository.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Default [LibraryRepository]: MediaStore (source of truth) synced into the Room read model. */
class LibraryRepositoryImpl(
    private val db: GalleryVisionDatabase,
    private val scanner: MediaStoreScanner,
) : LibraryRepository {

    override fun mediaPager(): Pager<Int, MediaEntity> =
        Pager(
            config = PagingConfig(pageSize = 60, prefetchDistance = 30, enablePlaceholders = false),
            pagingSourceFactory = { db.mediaDao().pagingAll() },
        )

    override fun observeMediaCount(): Flow<Int> = db.mediaDao().count()

    override suspend fun rescanNow(): Int = withContext(Dispatchers.IO) {
        val generation = System.currentTimeMillis()
        val result = scanner.scan(generation)
        db.folderDao().upsertAll(result.folders)
        db.mediaDao().upsertAll(result.media)
        // Incremental: drop rows not seen in this generation (deleted/moved media).
        db.mediaDao().deleteOlderThanGeneration(generation)
        result.media.size
    }

    override suspend fun getMediaById(id: Long): MediaEntity? = db.mediaDao().getById(id)

    override fun detectionsFor(mediaId: Long): Flow<List<DetectionEntity>> =
        db.detectionDao().forMedia(mediaId)

    override suspend fun saveDetections(detections: List<DetectionEntity>) =
        db.detectionDao().insertAll(detections)

    override fun denyList(kind: DenyKind): Flow<List<DenyEntity>> = db.denyDao().forKind(kind)

    override suspend fun addDeny(entry: DenyEntity) = db.denyDao().add(entry)

    override suspend fun removeDeny(kind: DenyKind, value: String) = db.denyDao().remove(kind, value)

    override suspend fun updateDetection(detection: DetectionEntity) = db.detectionDao().update(detection)
}
