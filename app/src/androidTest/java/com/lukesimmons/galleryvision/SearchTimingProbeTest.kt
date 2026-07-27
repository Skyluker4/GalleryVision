// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lukesimmons.galleryvision.workers.WorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Times representative search queries for the R3 benchmark (literal/wildcard/regex/boolean). */
@RunWith(AndroidJUnit4::class)
class SearchTimingProbeTest {
    @Test
    fun timeSearches() {
        val appCtx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val repo =
            EntryPointAccessors
                .fromApplication(appCtx, WorkerEntryPoint::class.java)
                .libraryRepository()

        runBlocking {
            time("literal path:einstein") { repo.search("path:einstein", null) }
            time("wildcard text:*") { repo.search("text:*", null) }
            time("regex path:/ein.*/") { repo.search("path:/ein.*/", null) }
            time("boolean path AND text") { repo.search("path:einstein AND text:*", null) }
            time("wildcard path:*") { repo.search("path:*", null) }
        }
    }

    @Test
    fun timeEvaluatorQueries() {
        val appCtx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val ep = EntryPointAccessors.fromApplication(appCtx, WorkerEntryPoint::class.java)
        val db = ep.database()

        runBlocking {
            time("allDetections") { db.detectionDao().allDetections() }
            time("allFaceNames") { db.detectionDao().allFaceNames() }
            time("allMediaTagNames") { db.tagDao().allMediaTagNames() }
            time("allMediaNotes") { db.noteDao().allMediaNotes() }
            time("getAllList(media)") { db.mediaDao().getAllList() }
        }
    }

    private suspend fun time(
        label: String,
        block: suspend () -> List<*>,
    ) {
        // warm up
        block()
        val t0 = SystemClock.elapsedRealtimeNanos()
        val result = block()
        val ms = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
        Log.i(TAG, "$label: ${"%.1f".format(ms)} ms -> ${result.size} results")
    }

    private companion object {
        const val TAG = "SearchTiming"
    }
}
