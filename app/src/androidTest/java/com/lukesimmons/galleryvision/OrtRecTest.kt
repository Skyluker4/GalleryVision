// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * S0.2 Android ORT spike: PP-OCRv5 rec model runs on-device via ONNX Runtime with the
 * XNNPACK execution provider (CPU SIMD acceleration, no deprecated NNAPI), and correctly
 * recognizes the known fixture string.
 */
@RunWith(AndroidJUnit4::class)
class OrtRecTest {
    private fun loadDict(ctx: android.content.Context): List<String> =
        ctx.assets
            .open("models/ppocrv5/ppocrv5_dict.txt")
            .bufferedReader()
            .useLines { it.toList() }

    /** Preprocess to NCHW float32 [1,3,48,W], BGR order (matches PaddleOCR), (x/255-0.5)/0.5. */
    private fun toTensor(
        env: OrtEnvironment,
        ctx: android.content.Context,
        asset: String,
    ): Pair<OnnxTensor, LongArray> {
        val bmp = BitmapFactory.decodeStream(ctx.assets.open(asset))
        val newW = max(1, 48 * bmp.width / bmp.height)
        val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, newW, 48, true)
        val px = IntArray(newW * 48)
        scaled.getPixels(px, 0, newW, 0, 0, newW, 48)
        val fb = FloatBuffer.allocate(3 * 48 * newW)
        for (c in 0..2) {
            for (y in 0 until 48) {
                for (x in 0 until newW) {
                    val p = px[y * newW + x]
                    val v =
                        when (c) {
                            0 -> p and 0xff // B
                            1 -> (p shr 8) and 0xff // G
                            else -> (p shr 16) and 0xff // R
                        }
                    fb.put((v / 255f - 0.5f) / 0.5f)
                }
            }
        }
        fb.rewind()
        val shape = longArrayOf(1, 3, 48, newW.toLong())
        return OnnxTensor.createTensor(env, fb, shape) to shape
    }

    private fun ctcDecode(
        seq: Array<FloatArray>,
        dict: List<String>,
    ): Pair<String, Float> {
        val sb = StringBuilder()
        var prev = -1
        var confSum = 0f
        var confN = 0
        for (row in seq) {
            var maxI = 0
            var maxV = Float.NEGATIVE_INFINITY
            for (i in row.indices) {
                if (row[i] > maxV) {
                    maxV = row[i]
                    maxI = i
                }
            }
            if (maxI != 0 && maxI != prev) {
                sb.append(if (maxI - 1 < dict.size) dict[maxI - 1] else " ")
                confSum += maxV
                confN++
            }
            prev = maxI
        }
        return sb.toString() to (if (confN > 0) confSum / confN else 0f)
    }

    @Test
    fun recognizesFixture_onDevice_withXnnpack_noNnapi() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val env = OrtEnvironment.getEnvironment()
        val dict = loadDict(ctx)
        assertEquals("v5 dict size", 18383, dict.size)

        val modelBytes = ctx.assets.open("models/ppocrv5/rec.onnx").readBytes()
        val opts =
            OrtSession.SessionOptions().apply {
                addXnnpack(emptyMap()) // XNNPACK EP; we intentionally never add NNAPI (deprecated)
            }
        env.createSession(modelBytes, opts).use { session ->
            val (tensor, _) = toTensor(env, ctx, "images/rec_1.jpg")
            tensor.use { t ->
                val out = session.run(mapOf(session.inputNames.first() to t))
                out.use { res ->
                    @Suppress("UNCHECKED_CAST")
                    val seq = (res[0].value as Array<Array<FloatArray>>)[0]
                    val (text, conf) = ctcDecode(seq, dict)
                    println("OrtRecTest recognized: '$text' conf=$conf")
                    assertTrue("expected phone number, got '$text'", text.contains("15952301928"))
                    assertTrue("confidence too low: $conf", conf > 0.5f)
                }
            }
        }
    }
}
