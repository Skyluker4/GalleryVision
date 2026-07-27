// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lukesimmons.galleryvision.core.database.dao.DenyDao
import com.lukesimmons.galleryvision.core.database.dao.DetectionDao
import com.lukesimmons.galleryvision.core.database.dao.FaceClusterDao
import com.lukesimmons.galleryvision.core.database.dao.FolderDao
import com.lukesimmons.galleryvision.core.database.dao.FolderPolicyDao
import com.lukesimmons.galleryvision.core.database.dao.MediaDao
import com.lukesimmons.galleryvision.core.database.dao.NoteDao
import com.lukesimmons.galleryvision.core.database.dao.TagDao
import com.lukesimmons.galleryvision.core.database.entity.DenyEntity
import com.lukesimmons.galleryvision.core.database.entity.DetectionEntity
import com.lukesimmons.galleryvision.core.database.entity.FaceClusterEntity
import com.lukesimmons.galleryvision.core.database.entity.FolderEntity
import com.lukesimmons.galleryvision.core.database.entity.FolderPolicyEntity
import com.lukesimmons.galleryvision.core.database.entity.MediaEntity
import com.lukesimmons.galleryvision.core.database.entity.MediaTagCrossRef
import com.lukesimmons.galleryvision.core.database.entity.NoteEntity
import com.lukesimmons.galleryvision.core.database.entity.TagEntity

@Database(
    entities = [
        FolderEntity::class,
        MediaEntity::class,
        DetectionEntity::class,
        NoteEntity::class,
        TagEntity::class,
        MediaTagCrossRef::class,
        FaceClusterEntity::class,
        DenyEntity::class,
        FolderPolicyEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class GalleryVisionDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun folderDao(): FolderDao
    abstract fun detectionDao(): DetectionDao
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun faceClusterDao(): FaceClusterDao
    abstract fun denyDao(): DenyDao
    abstract fun folderPolicyDao(): FolderPolicyDao
}
