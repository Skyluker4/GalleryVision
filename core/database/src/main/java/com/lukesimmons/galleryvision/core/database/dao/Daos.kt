// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.lukesimmons.galleryvision.core.database.entity.DenyEntity
import com.lukesimmons.galleryvision.core.database.entity.DetectionEntity
import com.lukesimmons.galleryvision.core.database.entity.FaceClusterEntity
import com.lukesimmons.galleryvision.core.database.entity.FolderEntity
import com.lukesimmons.galleryvision.core.database.entity.FolderPolicyEntity
import com.lukesimmons.galleryvision.core.database.entity.MediaEntity
import com.lukesimmons.galleryvision.core.database.entity.MediaTagCrossRef
import com.lukesimmons.galleryvision.core.database.entity.NoteEntity
import com.lukesimmons.galleryvision.core.database.entity.TagEntity
import com.lukesimmons.galleryvision.core.model.DenyKind
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Upsert
    suspend fun upsertAll(items: List<MediaEntity>)

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getById(id: Long): MediaEntity?

    @Query("SELECT COUNT(*) FROM media")
    fun count(): Flow<Int>

    /** All media, newest first (M1 grid; deny/allow filtering layered in :domain). */
    @Query("SELECT * FROM media ORDER BY COALESCE(dateTaken, dateAdded, dateModified, 0) DESC")
    fun pagingAll(): PagingSource<Int, MediaEntity>

    /** Remove rows from a scan generation older than :current (incremental rescan). */
    @Query("DELETE FROM media WHERE scanGeneration < :current")
    suspend fun deleteOlderThanGeneration(current: Long): Int

    @Query("SELECT MAX(scanGeneration) FROM media")
    suspend fun maxScanGeneration(): Long?

    @Query("SELECT * FROM media")
    suspend fun getAllList(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE id NOT IN (SELECT DISTINCT mediaId FROM detection) ORDER BY COALESCE(dateTaken, dateAdded, dateModified, 0) DESC LIMIT :limit")
    suspend fun mediaWithoutDetections(limit: Int): List<MediaEntity>

    @RawQuery(observedEntities = [MediaEntity::class])
    fun searchRaw(query: SupportSQLiteQuery): List<MediaEntity>
}

@Dao
interface FolderDao {
    @Upsert
    suspend fun upsertAll(folders: List<FolderEntity>)

    @Query("SELECT * FROM folder")
    fun all(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folder WHERE id = :id")
    suspend fun getById(id: Long): FolderEntity?
}

@Dao
interface DetectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(detections: List<DetectionEntity>)

    @Query("DELETE FROM detection WHERE mediaId = :mediaId AND source = 'AUTO'")
    suspend fun deleteAutoForMedia(mediaId: Long)

    @Query("SELECT * FROM detection WHERE mediaId = :mediaId")
    fun forMedia(mediaId: Long): Flow<List<DetectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(detection: DetectionEntity)

    @Query("SELECT fc.name FROM detection d JOIN face_cluster fc ON fc.id = d.clusterId WHERE d.mediaId = :mediaId AND d.kind = 'FACE'")
    suspend fun faceNamesFor(mediaId: Long): List<String>
}

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity): Long

    @Query("SELECT * FROM note WHERE targetKind = :kind AND targetId = :targetId")
    fun forTarget(kind: com.lukesimmons.galleryvision.core.model.NoteTargetKind, targetId: Long): Flow<List<NoteEntity>>

    @Query("DELETE FROM note WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT * FROM tag WHERE name = :name")
    suspend fun byName(name: String): TagEntity?

    @Query("SELECT * FROM tag ORDER BY name")
    fun all(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToMedia(ref: MediaTagCrossRef)

    @Query("DELETE FROM media_tag WHERE mediaId = :mediaId AND tagId = :tagId")
    suspend fun removeFromMedia(mediaId: Long, tagId: Long)

    @Query("SELECT * FROM media_tag WHERE mediaId = :mediaId")
    fun forMedia(mediaId: Long): Flow<List<MediaTagCrossRef>>

    @Query("SELECT t.name FROM media_tag mt JOIN tag t ON t.id = mt.tagId WHERE mt.mediaId = :mediaId")
    suspend fun tagNamesFor(mediaId: Long): List<String>
}

@Dao
interface FaceClusterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cluster: FaceClusterEntity): Long

    @Query("SELECT * FROM face_cluster")
    fun all(): Flow<List<FaceClusterEntity>>

    @Query("UPDATE face_cluster SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)
}

@Dao
interface DenyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entry: DenyEntity)

    @Query("DELETE FROM deny WHERE kind = :kind AND value = :value")
    suspend fun remove(kind: DenyKind, value: String)

    @Query("SELECT * FROM deny")
    fun all(): Flow<List<DenyEntity>>

    @Query("SELECT * FROM deny WHERE kind = :kind")
    fun forKind(kind: DenyKind): Flow<List<DenyEntity>>
}

@Dao
interface FolderPolicyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(policy: FolderPolicyEntity)

    @Query("DELETE FROM folder_policy WHERE folderId = :folderId")
    suspend fun clear(folderId: Long)

    @Query("SELECT * FROM folder_policy")
    fun all(): Flow<List<FolderPolicyEntity>>
}
