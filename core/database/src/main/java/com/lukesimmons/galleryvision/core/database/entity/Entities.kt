// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lukesimmons.galleryvision.core.model.DenyKind
import com.lukesimmons.galleryvision.core.model.DetectionKind
import com.lukesimmons.galleryvision.core.model.DetectionSource
import com.lukesimmons.galleryvision.core.model.FolderPolicyMode
import com.lukesimmons.galleryvision.core.model.MediaType
import com.lukesimmons.galleryvision.core.model.NoteTargetKind

@Entity(tableName = "folder")
data class FolderEntity(
    @PrimaryKey val id: Long,
    val path: String,
    val parentId: Long?,
)

@Entity(
    tableName = "media",
    indices = [Index("folderId"), Index("dateTaken"), Index("dateAdded"), Index("dateModified"), Index("dateCreated")],
)
data class MediaEntity(
    @PrimaryKey val id: Long,
    val sourceUri: String,
    val path: String,
    val folderId: Long?,
    val type: MediaType,
    val dateTaken: Long?,
    val dateAdded: Long?,
    val dateModified: Long?,
    val dateCreated: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val width: Int,
    val height: Int,
    val durationMs: Long?,
    val scanGeneration: Long,
)

@Entity(
    tableName = "detection",
    indices = [Index("mediaId"), Index("kind"), Index("clusterId"), Index("label")],
)
data class DetectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val kind: DetectionKind,
    val source: DetectionSource,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** Optional JSON-flattened polygon (x0,y0,x1,y1,...) for OCR text quads. */
    val poly: String?,
    val label: String?,
    val valueText: String?,
    val confidence: Float,
    val clusterId: Long?,
    val edited: Boolean,
    /** Comma-joined embedding vector (e.g. 128-d SFace) for FACE detections; null otherwise. */
    val embedding: String? = null,
)

@Entity(
    tableName = "note",
    indices = [Index("targetKind", "targetId"), Index("parentNoteId")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetKind: NoteTargetKind,
    val targetId: Long,
    val body: String,
    val parentNoteId: Long?,
)

@Entity(tableName = "tag")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "media_tag", primaryKeys = ["mediaId", "tagId"], indices = [Index("tagId")])
data class MediaTagCrossRef(
    val mediaId: Long,
    val tagId: Long,
    /** Optional JSON bounding box for a positioned tag. */
    val box: String?,
)

@Entity(tableName = "face_cluster")
data class FaceClusterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String?,
    val contactLookupKey: String?,
)

@Entity(tableName = "deny", primaryKeys = ["kind", "value"])
data class DenyEntity(
    val kind: DenyKind,
    val value: String,
)

@Entity(tableName = "folder_policy")
data class FolderPolicyEntity(
    @PrimaryKey val folderId: Long,
    val mode: FolderPolicyMode,
)
