// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.core.model

/** Type of a media item in the library. */
enum class MediaType { IMAGE, VIDEO, GIF, ANIMATED }

/** What produced a detection. */
enum class DetectionKind { TEXT, FACE, OBJECT }

/** Whether a detection came from an on-device model or the user. */
enum class DetectionSource { AUTO, MANUAL }

/** Per-folder visibility policy mode. */
enum class FolderPolicyMode { DENY, ALLOW }

/** Categories of user deny lists. */
enum class DenyKind { WORD, OBJECT, TAG, FACE }

/** What a note is attached to. */
enum class NoteTargetKind { MEDIA, FOLDER }

/** Fields searchable and sortable in the app (R8). */
enum class SearchField { PATH, CREATED, MODIFIED, ADDED, TAKEN, TEXT, TAG, OBJECT, FACE, NOTE }

enum class SortDirection { ASC, DESC }

/** A folder in the media store (nested tree via [parentId]). */
data class Folder(
    val id: Long,
    val path: String,
    val parentId: Long?,
)

/** A single media item (image or video) indexed from MediaStore. */
data class MediaItem(
    val id: Long,
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

/**
 * A positioned detection on a media item: OCR text region, face, or object.
 * Geometry is stored normalized (0..1) in the EXIF-corrected image coordinate space.
 * [poly] holds an optional flattened polygon (x0,y0,x1,y1,...) for OCR text quads.
 */
data class Detection(
    val id: Long,
    val mediaId: Long,
    val kind: DetectionKind,
    val source: DetectionSource,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val poly: FloatArray?,
    val label: String?,
    val valueText: String?,
    val confidence: Float,
    val clusterId: Long?,
    val edited: Boolean,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is Detection && other.id == id)

    override fun hashCode(): Int = id.hashCode()
}

/** A user-defined tag. */
data class Tag(val id: Long, val name: String)

/** A named cluster of faces, optionally linked to a contact. */
data class FaceCluster(
    val id: Long,
    val name: String?,
    val contactLookupKey: String?,
)

data class FaceClusterInfo(
    val id: Long,
    val name: String?,
    val contactLookupKey: String?,
    val memberCount: Int,
    val representativeMediaId: Long?,
    /** Normalized [left, top, right, bottom] of the representative face, or null. */
    val faceBox: FloatArray?,
)

/** A nestable note attached to a media item or a folder. */
data class Note(
    val id: Long,
    val targetKind: NoteTargetKind,
    val targetId: Long,
    val body: String,
    val parentNoteId: Long?,
)

/** A deny-list entry; matched detections/words are hidden at query time. */
data class DenyEntry(val kind: DenyKind, val value: String)

/** A per-folder visibility policy. */
data class FolderPolicy(val folderId: Long, val mode: FolderPolicyMode)
