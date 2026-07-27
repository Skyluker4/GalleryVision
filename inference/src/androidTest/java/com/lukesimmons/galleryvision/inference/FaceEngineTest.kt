// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.inference

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

/**
 * Verifies the on-device YuNet (detection) + SFace (recognition) pipeline on a real portrait
 * (public-domain Albert Einstein headshot). Detection must find the face; recognition must
 * produce a 128-d embedding whose self-similarity is ~1.
 */
@RunWith(AndroidJUnit4::class)
class FaceEngineTest {

    @Test
    fun detectsFaceAndProducesEmbedding() {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val bitmap = BitmapFactory.decodeStream(ctx.assets.open("face.jpg"))
        val targetCtx = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = FaceEngine(targetCtx)
        try {
            val faces = engine.detect(bitmap)
            println("FaceEngineTest detected ${faces.size} faces")
            faces.forEachIndexed { i, f ->
                println("  [$i] score=${f.score} box=(${f.left},${f.top},${f.right},${f.bottom})")
            }
            assertTrue("expected at least one face, got 0", faces.isNotEmpty())

            val top = faces.maxBy { it.score }
            assertTrue("face score too low: ${top.score}", top.score > 0.5f)
            val validBox = top.right > top.left && top.bottom > top.top &&
                top.left in 0f..1f && top.top in 0f..1f && top.right in 0f..1f && top.bottom in 0f..1f
            assertTrue("face box not a valid normalized rect", validBox)

            val emb = engine.embed(bitmap, top)
            assertTrue("embedding wrong dim: ${emb.size}", emb.size == 128)
            val selfCos = cosine(emb, emb)
            println("FaceEngineTest embedding norm=${norm(emb)} selfCos=$selfCos")
            assertTrue("embedding is all zeros", norm(emb) > 0.1f)
            assertTrue("self-cosine should be ~1, got $selfCos", selfCos > 0.99f)
        } finally {
            engine.close()
        }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return if (na == 0f || nb == 0f) 0f else dot / (sqrt(na) * sqrt(nb))
    }

    private fun norm(a: FloatArray): Float {
        var s = 0f
        for (x in a) s += x * x
        return sqrt(s)
    }
}
