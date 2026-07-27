// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.domain.repository

import androidx.paging.Pager
import com.lukesimmons.galleryvision.core.database.entity.DetectionEntity
import com.lukesimmons.galleryvision.core.database.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

/** Read model + scan control for the media library. */
interface LibraryRepository {
    /** Paged media for the grid, newest first. */
    fun mediaPager(): Pager<Int, MediaEntity>

    fun observeMediaCount(): Flow<Int>

    /** Run a scan now and persist results. Returns number of media indexed. */
    suspend fun rescanNow(): Int

    suspend fun getMediaById(id: Long): MediaEntity?

    fun detectionsFor(mediaId: Long): Flow<List<DetectionEntity>>

    suspend fun saveDetections(detections: List<DetectionEntity>)
}
