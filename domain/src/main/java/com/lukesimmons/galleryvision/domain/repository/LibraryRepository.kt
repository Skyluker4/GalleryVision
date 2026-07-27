// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.domain.repository

import androidx.paging.Pager
import com.lukesimmons.galleryvision.core.database.dao.FolderWithCount
import com.lukesimmons.galleryvision.core.database.entity.DenyEntity
import com.lukesimmons.galleryvision.core.database.entity.DetectionEntity
import com.lukesimmons.galleryvision.core.database.entity.FolderEntity
import com.lukesimmons.galleryvision.core.database.entity.FolderPolicyEntity
import com.lukesimmons.galleryvision.core.database.entity.MediaEntity
import com.lukesimmons.galleryvision.core.database.entity.NoteEntity
import com.lukesimmons.galleryvision.core.model.DenyKind
import com.lukesimmons.galleryvision.core.model.FaceClusterInfo
import com.lukesimmons.galleryvision.core.model.FolderPolicyMode
import com.lukesimmons.galleryvision.core.model.NoteTargetKind
import com.lukesimmons.galleryvision.core.model.SortSpec
import kotlinx.coroutines.flow.Flow

/** Read model + scan control for the media library. */
interface LibraryRepository {
    /** Paged media for the grid, newest first, folder-visibility policy applied. */
    fun mediaPager(allowListOnly: Boolean): Pager<Int, MediaEntity>

    fun observeMediaCount(allowListOnly: Boolean): Flow<Int>

    /** Run a scan now and persist results. Returns number of media indexed. */
    suspend fun rescanNow(): Int

    suspend fun getMediaById(id: Long): MediaEntity?

    fun detectionsFor(mediaId: Long): Flow<List<DetectionEntity>>

    suspend fun saveDetections(detections: List<DetectionEntity>)

    suspend fun search(
        query: String,
        sort: SortSpec?,
    ): List<MediaEntity>

    fun denyList(kind: DenyKind): Flow<List<DenyEntity>>

    suspend fun addDeny(entry: DenyEntity)

    suspend fun removeDeny(
        kind: DenyKind,
        value: String,
    )

    suspend fun updateDetection(detection: DetectionEntity)

    fun clustersWithRepresentative(): Flow<List<FaceClusterInfo>>

    suspend fun renameCluster(
        id: Long,
        name: String,
    )

    suspend fun linkClusterToContact(
        id: Long,
        lookupKey: String?,
    )

    suspend fun reclusterFaces(): Int

    fun notesFor(
        kind: NoteTargetKind,
        targetId: Long,
    ): Flow<List<NoteEntity>>

    suspend fun addNote(note: NoteEntity): Long

    suspend fun deleteNote(id: Long)

    fun foldersWithCounts(): Flow<List<FolderWithCount>>

    suspend fun getFolder(id: Long): FolderEntity?

    fun mediaForFolder(folderId: Long): Flow<List<MediaEntity>>

    fun folderPolicies(): Flow<List<FolderPolicyEntity>>

    /** Set a folder's visibility policy; null restores the default (visible in deny mode). */
    suspend fun setFolderPolicy(
        folderId: Long,
        mode: FolderPolicyMode?,
    )
}
