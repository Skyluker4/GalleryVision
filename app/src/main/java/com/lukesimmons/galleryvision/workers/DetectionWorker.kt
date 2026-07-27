// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.workers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Background detection indexing: OCR + face (+ embeddings) + object on unprocessed media. */
class DetectionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.Default) {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
                val db = entryPoint.database()
                val indexer = entryPoint.detectionIndexer()
                val unprocessed = db.mediaDao().mediaWithoutDetections(BATCH_SIZE)
                for (media in unprocessed) {
                    val bitmap = loadBitmap(media.sourceUri) ?: continue
                    indexer.indexBitmap(media.id, bitmap)
                }
                Result.success()
            } catch (e: Exception) {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }

    private fun loadBitmap(sourceUri: String): Bitmap? =
        runCatching {
            val source = ImageDecoder.createSource(applicationContext.contentResolver, Uri.parse(sourceUri))
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull()

    companion object {
        const val WORK_NAME = "galleryvision.detect"
        private const val BATCH_SIZE = 25
    }
}
