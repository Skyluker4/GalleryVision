// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Background MediaStore -> Room sync (incremental, generation-guarded). */
class ScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
            entryPoint.libraryRepository().rescanNow()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "galleryvision.scan"
    }
}
