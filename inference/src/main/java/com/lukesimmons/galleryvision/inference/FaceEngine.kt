// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * On-device face pipeline via ONNX Runtime (XNNPACK, no NNAPI):
 *  - BlazeFace full-range (MediaPipe, tf2onnx-converted) for detection + 6 landmarks.
 *    The previous YuNet export hallucinated faces on texture (receipts scored higher
 *    than the one real portrait on the probe corpus); BlazeFace full-range separates
 *    them cleanly (see docs/DESIGN.md).
 *  - SFace (OpenCV Zoo) for 128-d recognition embeddings.
 *
 * Detection boxes and landmarks are normalized to [0,1] of the source image. Models are
 * permissively licensed (Apache-2.0 / MIT), so they are shippable in this AGPL app.
 */
class FaceEngine(
    context: Context,
) {
    data class Face(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val landmarks: FloatArray, // 12 values: 6 points (x,y), normalized
        val score: Float,
    ) {
        override fun equals(other: Any?): Boolean = this === other || (other is Face && other.score == score && other.left == left)

        override fun hashCode(): Int = score.hashCode()
    }

    private val appContext = context.applicationContext
    private val env = OrtEnvironment.getEnvironment()
    private var det: OrtSession? = null
    private var rec: OrtSession? = null

    @Volatile
    private var closed = false

    @Synchronized
    private fun ensureLoaded() {
        if (det == null) det = newSession("$MODEL_DIR/blazeface.onnx")
        if (rec == null) rec = newSession("$MODEL_DIR/sface.onnx")
    }

    private fun newSession(asset: String): OrtSession {
        val opts = OrtSession.SessionOptions().apply { addXnnpack(emptyMap()) }
        return env.createSession(appContext.assets.open(asset).readBytes(), opts)
    }

    fun close() {
        if (closed) return
        closed = true
        det?.close()
        rec?.close()
        det = null
        rec = null
    }

    // ---------------- Detection (BlazeFace full-range) ----------------

    fun detect(src: Bitmap): List<Face> {
        ensureLoaded()
        val size = BlazeFaceDecoder.INPUT_SIZE
        val resized = Bitmap.createScaledBitmap(src, size, size, true)
        val input = toTensor(resized, 1f / 127.5f, bgr = false, offset = -1f, nhwc = true)
        resized.recycle()
        input.use { tensor ->
            det!!.run(mapOf(det!!.inputNames.first() to tensor)).use { result ->
                val detections = BlazeFaceDecoder.decode(readRegressors(result), readClassifier(result))
                return detections.map { d ->
                    Face(
                        left = d.box[0],
                        top = d.box[1],
                        right = d.box[2],
                        bottom = d.box[3],
                        landmarks = d.landmarks,
                        score = d.score,
                    )
                }
            }
        }
    }

    private fun readRegressors(result: OrtSession.Result): Array<FloatArray> {
        val entry = result.first { it.key.contains("regressor", ignoreCase = true) }
        @Suppress("UNCHECKED_CAST")
        return ((entry.value as OnnxTensor).value as Array<Array<FloatArray>>)[0]
    }

    private fun readClassifier(result: OrtSession.Result): FloatArray {
        val entry = result.first { it.key.contains("classifier", ignoreCase = true) }
        @Suppress("UNCHECKED_CAST")
        val raw = ((entry.value as OnnxTensor).value as Array<Array<FloatArray>>)[0]
        return FloatArray(raw.size) { raw[it][0] }
    }

    // ---------------- Recognition (SFace) ----------------

    fun embed(
        src: Bitmap,
        face: Face,
    ): FloatArray {
        ensureLoaded()
        val crop = cropFace(src, face) ?: return FloatArray(EMB_DIM)
        val resized = Bitmap.createScaledBitmap(crop, REC_SIZE, REC_SIZE, true)
        if (resized != crop) crop.recycle()
        val input = toTensor(resized, 1f / 255f, bgr = false)
        resized.recycle()
        input.use { tensor ->
            rec!!.run(mapOf(rec!!.inputNames.first() to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = (result[0].value as Array<FloatArray>)[0]
                return l2normalize(out)
            }
        }
    }

    private fun cropFace(
        src: Bitmap,
        face: Face,
    ): Bitmap? {
        val l = (face.left * src.width).toInt().coerceIn(0, src.width - 1)
        val t = (face.top * src.height).toInt().coerceIn(0, src.height - 1)
        val r = (face.right * src.width).toInt().coerceIn(l + 1, src.width)
        val b = (face.bottom * src.height).toInt().coerceIn(t + 1, src.height)
        if (r - l < 4 || b - t < 4) return null
        return Bitmap.createBitmap(src, l, t, r - l, b - t)
    }

    private fun l2normalize(v: FloatArray): FloatArray {
        var norm = 0f
        for (x in v) norm += x * x
        norm = sqrt(norm)
        return if (norm <= 1e-6f) v else FloatArray(v.size) { v[it] / norm }
    }

    // ---------------- Shared ----------------

    private fun toTensor(
        bitmap: Bitmap,
        scale: Float,
        bgr: Boolean,
        offset: Float = 0f,
        nhwc: Boolean = false,
    ): OnnxTensor {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        val fb = FloatBuffer.allocate(3 * w * h)
        fun channel(
            p: Int,
            c: Int,
        ) = when (c) {
            0 -> if (bgr) p and 0xff else (p shr 16) and 0xff
            1 -> (p shr 8) and 0xff
            else -> if (bgr) (p shr 16) and 0xff else p and 0xff
        }
        if (nhwc) {
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val p = px[y * w + x]
                    for (c in 0..2) {
                        fb.put(channel(p, c) * scale + offset)
                    }
                }
            }
        } else {
            for (c in 0..2) {
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        fb.put(channel(px[y * w + x], c) * scale + offset)
                    }
                }
            }
        }
        fb.rewind()
        val shape = if (nhwc) longArrayOf(1, h.toLong(), w.toLong(), 3) else longArrayOf(1, 3, h.toLong(), w.toLong())
        return OnnxTensor.createTensor(env, fb, shape)
    }

    companion object {
        private const val MODEL_DIR = "models/face"
        private const val REC_SIZE = 112
        private const val EMB_DIM = 128
    }
}
