// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.core.database

import androidx.room.TypeConverter
import com.lukesimmons.galleryvision.core.model.DenyKind
import com.lukesimmons.galleryvision.core.model.DetectionKind
import com.lukesimmons.galleryvision.core.model.DetectionSource
import com.lukesimmons.galleryvision.core.model.FolderPolicyMode
import com.lukesimmons.galleryvision.core.model.MediaType
import com.lukesimmons.galleryvision.core.model.NoteTargetKind

/** Room type converters for the shared domain enums (stored as their name). */
class Converters {
    @TypeConverter fun mediaTypeToString(v: MediaType): String = v.name
    @TypeConverter fun stringToMediaType(v: String): MediaType = MediaType.valueOf(v)

    @TypeConverter fun detectionKindToString(v: DetectionKind): String = v.name
    @TypeConverter fun stringToDetectionKind(v: String): DetectionKind = DetectionKind.valueOf(v)

    @TypeConverter fun detectionSourceToString(v: DetectionSource): String = v.name
    @TypeConverter fun stringToDetectionSource(v: String): DetectionSource = DetectionSource.valueOf(v)

    @TypeConverter fun noteTargetKindToString(v: NoteTargetKind): String = v.name
    @TypeConverter fun stringToNoteTargetKind(v: String): NoteTargetKind = NoteTargetKind.valueOf(v)

    @TypeConverter fun denyKindToString(v: DenyKind): String = v.name
    @TypeConverter fun stringToDenyKind(v: String): DenyKind = DenyKind.valueOf(v)

    @TypeConverter fun folderPolicyModeToString(v: FolderPolicyMode): String = v.name
    @TypeConverter fun stringToFolderPolicyMode(v: String): FolderPolicyMode = FolderPolicyMode.valueOf(v)
}
