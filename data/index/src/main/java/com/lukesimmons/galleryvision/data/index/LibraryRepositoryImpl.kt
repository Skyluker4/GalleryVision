// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.data.index

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.sqlite.db.SimpleSQLiteQuery
import com.lukesimmons.galleryvision.core.database.GalleryVisionDatabase
import com.lukesimmons.galleryvision.core.database.dao.FolderWithCount
import com.lukesimmons.galleryvision.core.database.entity.DenyEntity
import com.lukesimmons.galleryvision.core.database.entity.DetectionEntity
import com.lukesimmons.galleryvision.core.database.entity.FolderEntity
import com.lukesimmons.galleryvision.core.database.entity.FolderPolicyEntity
import com.lukesimmons.galleryvision.core.database.entity.MediaEntity
import com.lukesimmons.galleryvision.core.database.entity.MediaTagCrossRef
import com.lukesimmons.galleryvision.core.database.entity.NoteEntity
import com.lukesimmons.galleryvision.core.database.entity.TagEntity
import com.lukesimmons.galleryvision.core.datastore.SettingsStore
import com.lukesimmons.galleryvision.core.model.DenyKind
import com.lukesimmons.galleryvision.core.model.DetectionKind
import com.lukesimmons.galleryvision.core.model.FaceClusterInfo
import com.lukesimmons.galleryvision.core.model.FolderPolicyMode
import com.lukesimmons.galleryvision.core.model.NoteTargetKind
import com.lukesimmons.galleryvision.core.model.SortSpec
import com.lukesimmons.galleryvision.data.mediastore.MediaStoreScanner
import com.lukesimmons.galleryvision.domain.repository.LibraryRepository
import com.lukesimmons.galleryvision.domain.search.FieldValues
import com.lukesimmons.galleryvision.domain.search.QueryCompiler
import com.lukesimmons.galleryvision.domain.search.QueryParser
import com.lukesimmons.galleryvision.domain.search.SearchEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Default [LibraryRepository]: MediaStore (source of truth) synced into the Room read model. */
class LibraryRepositoryImpl(
    private val db: GalleryVisionDatabase,
    private val scanner: MediaStoreScanner,
    private val settings: SettingsStore,
) : LibraryRepository {
    override fun mediaPager(allowListOnly: Boolean): Pager<Int, MediaEntity> =
        Pager(
            config = PagingConfig(pageSize = 60, prefetchDistance = 30, enablePlaceholders = false),
            pagingSourceFactory = { db.mediaDao().pagingFiltered(allowListOnly) },
        )

    override fun observeMediaCount(allowListOnly: Boolean): Flow<Int> = db.mediaDao().countFiltered(allowListOnly)

    override suspend fun rescanNow(): Int =
        withContext(Dispatchers.IO) {
            val generation = System.currentTimeMillis()
            val result = scanner.scan(generation)
            db.folderDao().upsertAll(result.folders)
            db.mediaDao().upsertAll(result.media)
            // Incremental: drop rows not seen in this generation (deleted/moved media) — but only
            // when the scan returned items, so a transient empty/failed scan cannot wipe the library.
            if (result.media.isNotEmpty()) {
                db.mediaDao().deleteOlderThanGeneration(generation)
            }
            result.media.size
        }

    override suspend fun getMediaById(id: Long): MediaEntity? = db.mediaDao().getById(id)

    override fun detectionsFor(mediaId: Long): Flow<List<DetectionEntity>> = db.detectionDao().forMedia(mediaId)

    override suspend fun saveDetections(detections: List<DetectionEntity>) = db.detectionDao().insertAll(detections)

    override fun denyList(kind: DenyKind): Flow<List<DenyEntity>> = db.denyDao().forKind(kind)

    override suspend fun addDeny(entry: DenyEntity) = db.denyDao().add(entry)

    override suspend fun removeDeny(
        kind: DenyKind,
        value: String,
    ) = db.denyDao().remove(kind, value)

    override suspend fun updateDetection(detection: DetectionEntity) = db.detectionDao().update(detection)

    override fun clustersWithRepresentative(): Flow<List<FaceClusterInfo>> =
        db.faceClusterDao().all().map { clusters ->
            clusters.map { cluster ->
                val rep = db.detectionDao().representativeForCluster(cluster.id)
                val memberCount =
                    db
                        .detectionDao()
                        .forCluster(cluster.id)
                        .first()
                        .size
                FaceClusterInfo(
                    id = cluster.id,
                    name = cluster.name,
                    contactLookupKey = cluster.contactLookupKey,
                    memberCount = memberCount,
                    representativeMediaId = rep?.mediaId,
                    faceBox = rep?.let { floatArrayOf(it.left, it.top, it.right, it.bottom) },
                )
            }
        }

    override suspend fun renameCluster(
        id: Long,
        name: String,
    ) = db.faceClusterDao().rename(id, name)

    override suspend fun linkClusterToContact(
        id: Long,
        lookupKey: String?,
    ) = db.faceClusterDao().linkContact(id, lookupKey)

    override suspend fun reclusterFaces(): Int = FaceClusterer(db).recluster()

    override fun notesFor(
        kind: NoteTargetKind,
        targetId: Long,
    ): Flow<List<NoteEntity>> = db.noteDao().forTarget(kind, targetId)

    override suspend fun addNote(note: NoteEntity): Long = db.noteDao().upsert(note)

    override suspend fun deleteNote(id: Long) = db.noteDao().delete(id)

    override fun tagsFor(mediaId: Long): Flow<List<String>> = db.tagDao().tagNamesFlowFor(mediaId)

    override suspend fun addTag(
        mediaId: Long,
        name: String,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        db.tagDao().insert(TagEntity(name = trimmed))
        val tag = db.tagDao().byName(trimmed) ?: return
        db.tagDao().addToMedia(MediaTagCrossRef(mediaId = mediaId, tagId = tag.id, box = null))
    }

    override suspend fun removeTag(
        mediaId: Long,
        name: String,
    ) {
        val tag = db.tagDao().byName(name.trim()) ?: return
        db.tagDao().removeFromMedia(mediaId, tag.id)
    }

    override fun foldersWithCounts(): Flow<List<FolderWithCount>> = db.folderDao().allWithCounts()

    override suspend fun getFolder(id: Long): FolderEntity? = db.folderDao().getById(id)

    override fun mediaForFolder(folderId: Long): Flow<List<MediaEntity>> = db.mediaDao().forFolder(folderId)

    override fun folderPolicies(): Flow<List<FolderPolicyEntity>> = db.folderPolicyDao().all()

    override suspend fun setFolderPolicy(
        folderId: Long,
        mode: FolderPolicyMode?,
    ) {
        if (mode == null) {
            db.folderPolicyDao().clear(folderId)
        } else {
            db.folderPolicyDao().set(FolderPolicyEntity(folderId, mode))
        }
    }

    override suspend fun search(
        query: String,
        sort: SortSpec?,
    ): List<MediaEntity> =
        withContext(Dispatchers.IO) {
            val allowOnly = settings.allowListOnly.first()
            val spec = QueryParser.parse(query)
            if (QueryCompiler.hasRegex(spec)) {
                val policies =
                    db
                        .folderPolicyDao()
                        .all()
                        .first()
                        .associate { it.folderId to it.mode }
                val deniedObjects =
                    db
                        .denyDao()
                        .forKind(DenyKind.OBJECT)
                        .first()
                        .map { it.value.lowercase() }
                        .toSet()
                val deniedFaces =
                    db
                        .denyDao()
                        .forKind(DenyKind.FACE)
                        .first()
                        .map { it.value.lowercase() }
                        .toSet()
                val deniedTags =
                    db
                        .denyDao()
                        .forKind(DenyKind.TAG)
                        .first()
                        .map { it.value.lowercase() }
                        .toSet()
                // Batch-load all field values once (5 queries) instead of 4 per media row;
                // the per-row variant made regex/wildcard search O(N x 4) and unusable at scale.
                val allDets = db.detectionDao().allDetections()
                val textsByMedia =
                    allDets
                        .filter { it.kind == DetectionKind.TEXT }
                        .groupBy({ it.mediaId }, { it.valueText })
                        .mapValues { (_, v) -> v.filterNotNull() }
                val objectsByMedia =
                    allDets
                        .filter { it.kind == DetectionKind.OBJECT && it.label?.lowercase() !in deniedObjects }
                        .groupBy({ it.mediaId }, { it.label })
                        .mapValues { (_, v) -> v.filterNotNull() }
                val facesByMedia =
                    db
                        .detectionDao()
                        .allFaceNames()
                        .groupBy({ it.mediaId }, { it.name })
                        .mapValues { (_, v) -> v.filter { it.lowercase() !in deniedFaces } }
                val tagsByMedia =
                    db
                        .tagDao()
                        .allMediaTagNames()
                        .groupBy({ it.mediaId }, { it.name })
                        .mapValues { (_, v) -> v.filter { it.lowercase() !in deniedTags } }
                val notesByMedia =
                    db
                        .noteDao()
                        .allMediaNotes()
                        .groupBy({ it.targetId }, { it.body })
                val matched =
                    db.mediaDao().getAllList().mapNotNull { media ->
                        if (!policyVisible(media.folderId, policies, allowOnly)) return@mapNotNull null
                        val fv =
                            FieldValues(
                                path = media.path,
                                created = media.dateCreated,
                                modified = media.dateModified,
                                added = media.dateAdded,
                                taken = media.dateTaken,
                                texts = textsByMedia[media.id] ?: emptyList(),
                                tags = tagsByMedia[media.id] ?: emptyList(),
                                objects = objectsByMedia[media.id] ?: emptyList(),
                                faces = facesByMedia[media.id] ?: emptyList(),
                                notes = notesByMedia[media.id] ?: emptyList(),
                            )
                        if (SearchEvaluator.matches(spec, fv)) media to fv else null
                    }
                val sorted =
                    if (sort != null) {
                        val comp = SearchEvaluator.comparator(sort)
                        matched.sortedWith { a, b -> comp.compare(a.second, b.second) }
                    } else {
                        matched.sortedByDescending { it.first.dateTaken ?: it.first.dateAdded ?: it.first.dateModified ?: 0L }
                    }
                sorted.map { it.first }
            } else {
                val compiled = QueryCompiler.compile(spec, sort)
                val sql =
                    """
                    SELECT media.* FROM media
                    LEFT JOIN folder_policy fp ON media.folderId = fp.folderId
                    WHERE (${compiled.where}) AND (
                        (? = 0 AND (fp.mode IS NULL OR fp.mode != 'DENY'))
                        OR (? = 1 AND fp.mode = 'ALLOW')
                    ) ${compiled.orderBy}
                    """.trimIndent()
                val allowArg = if (allowOnly) 1 else 0
                val args = (compiled.args + allowArg + allowArg).toTypedArray()
                db.mediaDao().searchRaw(SimpleSQLiteQuery(sql, args))
            }
        }

    private fun policyVisible(
        folderId: Long?,
        policies: Map<Long, FolderPolicyMode>,
        allowOnly: Boolean,
    ): Boolean {
        val mode = folderId?.let { policies[it] }
        return if (allowOnly) mode == FolderPolicyMode.ALLOW else mode != FolderPolicyMode.DENY
    }
}
