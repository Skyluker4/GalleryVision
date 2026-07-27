// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.FloatBuffer

/** Probes YuNet output score ranges on a real face to determine if sigmoid is needed. */
@RunWith(AndroidJUnit4::class)
class YuNetProbeTest {

    private fun out2d(result: OrtSession.Result, name: String): Array<FloatArray> {
        for (entry in result) {
            if (entry.key == name) {
                @Suppress("UNCHECKED_CAST")
                return ((entry.value as OnnxTensor).value as Array<Array<FloatArray>>)[0]
            }
        }
        throw IllegalArgumentException("missing $name")
    }

    @Test
    fun probeScores() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val testCtx = InstrumentationRegistry.getInstrumentation().context
        val env = OrtEnvironment.getEnvironment()
        val session = env.createSession(ctx.assets.open("models/face/yunet.onnx").readBytes())

        val inputName = session.inputInfo.entries
            .firstOrNull { (it.value.info as? TensorInfo)?.shape?.size == 4 }?.key
            ?: session.inputNames.first()

        val src = BitmapFactory.decodeStream(testCtx.assets.open("face.jpg"))
        val resized = android.graphics.Bitmap.createScaledBitmap(src, 640, 640, true)
        val px = IntArray(640 * 640)
        resized.getPixels(px, 0, 640, 0, 0, 640, 640)
        val fb = FloatBuffer.allocate(3 * 640 * 640)
        for (c in 0..2) {
            for (y in 0 until 640) {
                for (x in 0 until 640) {
                    val p = px[y * 640 + x]
                    val v = when (c) {
                        0 -> (p shr 16) and 0xff
                        1 -> (p shr 8) and 0xff
                        else -> p and 0xff
                    }
                    fb.put(v / 255f)
                }
            }
        }
        fb.rewind()

        OnnxTensor.createTensor(env, fb, longArrayOf(1, 3, 640, 640)).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val cls = out2d(result, "cls_8")
                val obj = out2d(result, "obj_8")
                val bbox = out2d(result, "bbox_8")
                var topIdx = 0
                var topVal = -Float.MAX_VALUE
                for ((name, data) in listOf("cls" to cls, "obj" to obj)) {
                    var mn = Float.MAX_VALUE; var mx = -Float.MAX_VALUE; var sum = 0.0
                    for (i in data.indices) {
                        val v = data[i][0]
                        if (v < mn) mn = v
                        if (v > mx) { mx = v; if (name == "cls") { topVal = v; topIdx = i } }
                        sum += v
                    }
                    println("PROBE $name: min=$mn max=$mx mean=${sum / data.size}")
                }
                println("PROBE top cls idx=$topIdx val=$topVal bbox=${bbox[topIdx].toList()}")
            }
        }
        session.close()
    }
}
