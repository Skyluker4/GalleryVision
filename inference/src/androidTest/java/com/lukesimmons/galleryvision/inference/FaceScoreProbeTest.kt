// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.inference

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.sqrt
import org.junit.Test
import org.junit.runner.RunWith

/** Measurement-only sweep: SFace embedding norms of every surviving detection. */
@RunWith(AndroidJUnit4::class)
class FaceScoreProbeTest {

    @Test
    fun sweepLibraryAndLogDistribution() {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val engine = FaceEngine(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            for (name in ctx.assets.list("probe")?.sorted() ?: emptyList()) {
                val bitmap = BitmapFactory.decodeStream(ctx.assets.open("probe/$name")) ?: continue
                val faces = engine.detect(bitmap)
                val desc = faces.sortedByDescending { it.score }.take(5).map { f ->
                    val emb = engine.embed(bitmap, f)
                    val norm = sqrt(emb.fold(0f) { acc, v -> acc + v * v })
                    "%.2f@%.2f,n=%.2f".format(f.score, f.right - f.left, norm)
                }
                Log.i(TAG, "$name | ${faces.size} | $desc")
            }
        } finally {
            engine.close()
        }
    }

    private companion object {
        const val TAG = "FaceProbe"
    }
}
