// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.data.index

import android.graphics.Bitmap
import com.lukesimmons.galleryvision.core.database.GalleryVisionDatabase
import com.lukesimmons.galleryvision.core.database.entity.DetectionEntity
import com.lukesimmons.galleryvision.core.model.DetectionKind
import com.lukesimmons.galleryvision.core.model.DetectionSource
import com.lukesimmons.galleryvision.inference.FaceEngine
import com.lukesimmons.galleryvision.inference.ObjectEngine
import com.lukesimmons.galleryvision.inference.OcrEngine

/**
 * Runs OCR + face (+ SFace embedding) + object detection on a bitmap and persists the results.
 * Shared by the on-view detection pass and the background indexing worker.
 */
class DetectionIndexer(
    private val db: GalleryVisionDatabase,
    private val ocrEngine: OcrEngine,
    private val faceEngine: FaceEngine,
    private val objectEngine: ObjectEngine,
) {
    suspend fun indexBitmap(
        mediaId: Long,
        bitmap: Bitmap,
    ): Int {
        val ocrResults = ocrEngine.ocr(bitmap)
        val faces = runCatching { faceEngine.detect(bitmap) }.getOrDefault(emptyList())
        val objects = runCatching { objectEngine.detect(bitmap) }.getOrDefault(emptyList())

        val detections = ArrayList<DetectionEntity>(ocrResults.size + faces.size + objects.size)
        for (region in ocrResults) {
            detections += region.toTextEntity(mediaId)
        }
        for (face in faces) {
            val embedding = runCatching { faceEngine.embed(bitmap, face) }.getOrNull()
            detections += face.toFaceEntity(mediaId, embedding)
        }
        for (obj in objects) {
            detections += obj.toObjectEntity(mediaId)
        }
        db.detectionDao().insertAll(detections)
        return detections.size
    }

    private fun OcrEngine.TextRegion.toTextEntity(mediaId: Long): DetectionEntity {
        var minX = 1f
        var minY = 1f
        var maxX = 0f
        var maxY = 0f
        for (k in 0 until 4) {
            minX = minOf(minX, quad[k * 2])
            maxX = maxOf(maxX, quad[k * 2])
            minY = minOf(minY, quad[k * 2 + 1])
            maxY = maxOf(maxY, quad[k * 2 + 1])
        }
        return DetectionEntity(
            mediaId = mediaId,
            kind = DetectionKind.TEXT,
            source = DetectionSource.AUTO,
            left = minX,
            top = minY,
            right = maxX,
            bottom = maxY,
            poly = quad.joinToString(",") { it.toString() },
            label = null,
            valueText = text,
            confidence = confidence,
            clusterId = null,
            edited = false,
            embedding = null,
        )
    }

    private fun FaceEngine.Face.toFaceEntity(
        mediaId: Long,
        embedding: FloatArray?,
    ): DetectionEntity {
        val box = floatArrayOf(left, top, right, top, right, bottom, left, bottom)
        return DetectionEntity(
            mediaId = mediaId,
            kind = DetectionKind.FACE,
            source = DetectionSource.AUTO,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            poly = box.joinToString(",") { it.toString() },
            label = null,
            valueText = null,
            confidence = score,
            clusterId = null,
            edited = false,
            embedding = embedding?.joinToString(",") { "%.5f".format(it) },
        )
    }

    private fun ObjectEngine.Detected.toObjectEntity(mediaId: Long): DetectionEntity {
        val box = floatArrayOf(left, top, right, top, right, bottom, left, bottom)
        return DetectionEntity(
            mediaId = mediaId,
            kind = DetectionKind.OBJECT,
            source = DetectionSource.AUTO,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            poly = box.joinToString(",") { it.toString() },
            label = label,
            valueText = null,
            confidence = score,
            clusterId = null,
            edited = false,
            embedding = null,
        )
    }
}
