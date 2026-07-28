// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.data.mediastore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import com.lukesimmons.galleryvision.core.database.entity.FolderEntity
import com.lukesimmons.galleryvision.core.database.entity.MediaEntity
import com.lukesimmons.galleryvision.core.model.MediaType

/**
 * Reads images and videos from MediaStore (the system source of truth) into Room read-model
 * entities. Folder tree ids are derived from the directory path so they are stable across scans.
 */
class MediaStoreScanner(
    private val context: Context,
) {
    data class ScanResult(
        val media: List<MediaEntity>,
        val folders: List<FolderEntity>,
    )

    private fun folderIdFor(path: String): Long = path.lowercase().hashCode().toLong()

    private fun parentPathOf(folderPath: String): String? = folderPath.substringBeforeLast('/', "").ifEmpty { null }

    private fun typeFor(
        mediaType: Int,
        path: String,
    ): MediaType {
        if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) return MediaType.VIDEO
        val lower = path.lowercase()
        return when {
            lower.endsWith(".gif") -> MediaType.GIF
            lower.endsWith(".apng") -> MediaType.ANIMATED
            lower.endsWith(".png") ->
                if (sniffsAs(path, AnimatedSniff::isApng)) MediaType.ANIMATED else MediaType.IMAGE
            lower.endsWith(".webp") ->
                if (sniffsAs(path, AnimatedSniff::isAnimatedWebP)) MediaType.ANIMATED else MediaType.IMAGE
            else -> MediaType.IMAGE
        }
    }

    private fun sniffsAs(
        path: String,
        test: (ByteArray, Int) -> Boolean,
    ): Boolean {
        val (buf, n) = AnimatedSniff.readHeader(path) ?: return false
        return test(buf, n)
    }

    fun scan(generation: Long): ScanResult {
        val media = mutableListOf<MediaEntity>()
        val folders = mutableMapOf<Long, FolderEntity>()

        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection =
            mutableListOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DATE_TAKEN,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.WIDTH,
                MediaStore.Files.FileColumns.HEIGHT,
                MediaStore.Files.FileColumns.DURATION,
            )
        // Querying these without the location permission throws "Invalid column" on API 36+.
        if (context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            projection += "latitude"
            projection += "longitude"
        }
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args =
            arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                    .toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    .toString(),
            )
        val sort = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection.toTypedArray(), selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val modifiedCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val widthCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val heightCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
            val latCol = c.getColumnIndex("latitude")
            val lonCol = c.getColumnIndex("longitude")

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val path = c.getString(dataCol) ?: continue
                val mediaType = c.getInt(typeCol)
                val type = typeFor(mediaType, path)
                val folderPath = path.substringBeforeLast('/', "")
                val folderId = if (folderPath.isEmpty()) null else folderIdFor(folderPath)
                if (folderPath.isNotEmpty()) {
                    val parentPath = parentPathOf(folderPath)
                    folders.getOrPut(folderIdFor(folderPath)) {
                        FolderEntity(
                            id = folderIdFor(folderPath),
                            path = folderPath,
                            parentId = parentPath?.let { folderIdFor(it) },
                        )
                    }
                }

                val contentUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL, id)
                media +=
                    MediaEntity(
                        id = id,
                        sourceUri = contentUri.toString(),
                        path = path,
                        folderId = folderId,
                        type = type,
                        dateTaken = if (c.isNull(takenCol)) null else c.getLong(takenCol),
                        dateAdded = if (c.isNull(addedCol)) null else c.getLong(addedCol) * 1000,
                        dateModified = if (c.isNull(modifiedCol)) null else c.getLong(modifiedCol) * 1000,
                        dateCreated = null, // file birth time resolved in M3
                        latitude = if (latCol < 0 || c.isNull(latCol)) null else c.getDouble(latCol),
                        longitude = if (lonCol < 0 || c.isNull(lonCol)) null else c.getDouble(lonCol),
                        width = if (c.isNull(widthCol)) 0 else c.getInt(widthCol),
                        height = if (c.isNull(heightCol)) 0 else c.getInt(heightCol),
                        durationMs = if (c.isNull(durationCol)) null else c.getLong(durationCol),
                        scanGeneration = generation,
                    )
            }
        }
        return ScanResult(media, folders.values.toList())
    }
}
