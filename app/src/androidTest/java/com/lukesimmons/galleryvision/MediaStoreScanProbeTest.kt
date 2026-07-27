// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision

import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lukesimmons.galleryvision.data.mediastore.MediaStoreScanner
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreScanProbeTest {
    @Test
    fun probeScan() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val raw =
            ctx.contentResolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE),
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)",
                arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                        .toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                        .toString(),
                ),
                null,
            )
        Log.i(TAG, "raw files query count=${raw?.count}, mediaType col idx=${raw?.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)}")
        raw?.use { c ->
            var n = 0
            while (c.moveToNext() && n < 5) {
                Log.i(TAG, "row id=${c.getLong(0)} type=${c.getInt(1)}")
                n++
            }
        }
        val result = MediaStoreScanner(ctx).scan(1L)
        Log.i(TAG, "scanner.scan -> media=${result.media.size} folders=${result.folders.size}")
        result.media.take(5).forEach { Log.i(TAG, "media ${it.id} ${it.path} type=${it.type}") }
    }

    private companion object {
        const val TAG = "ScanProbe"
    }
}
