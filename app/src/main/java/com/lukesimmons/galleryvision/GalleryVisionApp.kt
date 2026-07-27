// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lukesimmons.galleryvision.workers.DetectionWorker
import com.lukesimmons.galleryvision.workers.ScanWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GalleryVisionApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        registerMediaObserver()
        enqueueScanThenDetect(WorkManager.getInstance(this))
    }

    private fun registerMediaObserver() {
        val workManager = WorkManager.getInstance(this)
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    enqueueScanThenDetect(workManager)
                }
            }
        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
    }

    private fun enqueueScanThenDetect(workManager: WorkManager) {
        val scan = OneTimeWorkRequestBuilder<ScanWorker>().build()
        val detect = OneTimeWorkRequestBuilder<DetectionWorker>().build()
        workManager
            .beginUniqueWork(CHAIN_WORK_NAME, ExistingWorkPolicy.REPLACE, scan)
            .then(detect)
            .enqueue()
    }

    private companion object {
        const val CHAIN_WORK_NAME = "galleryvision.scanThenDetect"
    }
}
