// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * On-device face pipeline via ONNX Runtime (XNNPACK, no NNAPI):
 *  - YuNet (OpenCV Zoo) for detection + 5 landmarks.
 *  - SFace (OpenCV Zoo) for 128-d recognition embeddings.
 *
 * Detection boxes and landmarks are normalized to [0,1] of the source image. Both models are
 * permissively licensed (MIT), so they are shippable in this AGPL app.
 */
class FaceEngine(context: Context) {

    data class Face(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val landmarks: FloatArray, // 10 values: x0,y0,...,x4,y4, normalized
        val score: Float,
    ) {
        override fun equals(other: Any?): Boolean = this === other || (other is Face && other.score == score && other.left == left)
        override fun hashCode(): Int = score.hashCode()
    }

    private val appContext = context.applicationContext
    private val env = OrtEnvironment.getEnvironment()
    private var det: OrtSession? = null
    private var rec: OrtSession? = null
    private var detW = DET_SIZE
    private var detH = DET_SIZE
    private var detInputName: String = "input"

    @Volatile
    private var closed = false

    @Synchronized
    private fun ensureLoaded() {
        if (det == null) {
            det = newSession("$MODEL_DIR/yunet.onnx")
            // YuNet exports some weights as graph inputs, so the image input is not necessarily
            // first. Pick the 4D (NCHW) input and read its shape.
            val imageEntry = det!!.inputInfo.entries.firstOrNull { (_, node) ->
                (node.info as? TensorInfo)?.shape?.size == 4
            }
            detInputName = imageEntry?.key ?: det!!.inputNames.first()
            val shape = (imageEntry?.value?.info as? TensorInfo)?.shape
            if (shape != null && shape.size >= 4) {
                if (shape[2] > 0) detH = shape[2].toInt()
                if (shape[3] > 0) detW = shape[3].toInt()
            }
        }
        if (rec == null) rec = newSession("$MODEL_DIR/sface.onnx")
    }

    private fun newSession(asset: String): OrtSession {
        val opts = OrtSession.SessionOptions().apply { addXnnpack(emptyMap()) }
        return env.createSession(appContext.assets.open(asset).readBytes(), opts)
    }

    fun close() {
        if (closed) return
        closed = true
        det?.close(); rec?.close()
        det = null; rec = null
    }

    // ---------------- Detection (YuNet) ----------------

    fun detect(src: Bitmap): List<Face> {
        ensureLoaded()
        val resized = Bitmap.createScaledBitmap(src, detW, detH, true)
        val input = toTensor(resized, 1f, bgr = true)
        resized.recycle()
        input.use { tensor ->
            det!!.run(mapOf(detInputName to tensor)).use { result ->
                return decode(result)
            }
        }
    }

    private fun decode(result: OrtSession.Result): List<Face> {
        val cands = ArrayList<Cand>()
        for (stride in STRIDES) {
            val bbox = out2d(result, "bbox_$stride")
            val cls = out2d(result, "cls_$stride")
            val obj = out2d(result, "obj_$stride")
            val kps = out2d(result, "kps_$stride")
            val ws = detW / stride
            val hs = detH / stride
            var idx = 0
            for (i in 0 until hs) {
                for (j in 0 until ws) {
                    if (idx >= cls.size) break
                    // The obj head is near-zero across all anchors for this model variant, so the
                    // classification probability alone is the detector score.
                    val score = cls[idx][0]
                    if (score >= SCORE_THRESH) {
                        val b = bbox[idx]
                        val cx = (j + b[0]) * stride
                        val cy = (i + b[1]) * stride
                        val w = exp(b[2]) * stride
                        val h = exp(b[3]) * stride
                        val lm = FloatArray(10)
                        for (k in 0 until 5) {
                            lm[k * 2] = ((j + kps[idx][k * 2]) * stride / detW).coerceIn(0f, 1f)
                            lm[k * 2 + 1] = ((i + kps[idx][k * 2 + 1]) * stride / detH).coerceIn(0f, 1f)
                        }
                        val left = (cx - w / 2) / detW
                        val top = (cy - h / 2) / detH
                        val right = (cx + w / 2) / detW
                        val bottom = (cy + h / 2) / detH
                        cands.add(Cand(floatArrayOf(left, top, right, bottom), lm, score))
                    }
                    idx++
                }
            }
        }
        return nms(cands).sortedByDescending { it.score }.take(MAX_FACES).map { c ->
            Face(
                left = c.box[0].coerceIn(0f, 1f),
                top = c.box[1].coerceIn(0f, 1f),
                right = c.box[2].coerceIn(0f, 1f),
                bottom = c.box[3].coerceIn(0f, 1f),
                landmarks = c.lm,
                score = c.score,
            )
        }
    }

    private fun out2d(result: OrtSession.Result, name: String): Array<FloatArray> {
        for (entry in result) {
            if (entry.key == name) {
                @Suppress("UNCHECKED_CAST")
                return ((entry.value as OnnxTensor).value as Array<Array<FloatArray>>)[0]
            }
        }
        throw IllegalArgumentException("YuNet output '$name' missing; available: ${result.map { it.key }}")
    }

    private fun nms(cands: List<Cand>): List<Cand> {
        val sorted = cands.sortedByDescending { it.score }
        val keep = ArrayList<Cand>()
        val removed = BooleanArray(sorted.size)
        for (i in sorted.indices) {
            if (removed[i]) continue
            keep.add(sorted[i])
            for (j in i + 1 until sorted.size) {
                if (!removed[j] && iou(sorted[i].box, sorted[j].box) > NMS_THRESH) removed[j] = true
            }
        }
        return keep
    }

    private fun iou(a: FloatArray, b: FloatArray): Float {
        val x1 = max(a[0], b[0]); val y1 = max(a[1], b[1])
        val x2 = min(a[2], b[2]); val y2 = min(a[3], b[3])
        val inter = max(0f, x2 - x1) * max(0f, y2 - y1)
        val areaA = (a[2] - a[0]) * (a[3] - a[1])
        val areaB = (b[2] - b[0]) * (b[3] - b[1])
        return if (areaA + areaB - inter <= 0f) 0f else inter / (areaA + areaB - inter)
    }

    private data class Cand(val box: FloatArray, val lm: FloatArray, val score: Float)

    // ---------------- Recognition (SFace) ----------------

    fun embed(src: Bitmap, face: Face): FloatArray {
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

    private fun cropFace(src: Bitmap, face: Face): Bitmap? {
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

    private fun resizeTo(src: Bitmap, w: Int, h: Int): Triple<Bitmap, Float, Float> =
        Triple(Bitmap.createScaledBitmap(src, w, h, true), src.width.toFloat() / w, src.height.toFloat() / h)

    private fun toTensor(bitmap: Bitmap, scale: Float, bgr: Boolean): OnnxTensor {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        val fb = FloatBuffer.allocate(3 * w * h)
        for (c in 0..2) {
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val p = px[y * w + x]
                    val v = when (c) {
                        0 -> if (bgr) p and 0xff else (p shr 16) and 0xff
                        1 -> (p shr 8) and 0xff
                        else -> if (bgr) (p shr 16) and 0xff else p and 0xff
                    }
                    fb.put(v * scale)
                }
            }
        }
        fb.rewind()
        return OnnxTensor.createTensor(env, fb, longArrayOf(1, 3, h.toLong(), w.toLong()))
    }

    companion object {
        private const val MODEL_DIR = "models/face"
        private const val DET_SIZE = 320
        private const val REC_SIZE = 112
        private const val EMB_DIM = 128
        private const val SCORE_THRESH = 0.6f
        private const val NMS_THRESH = 0.3f
        private const val MAX_FACES = 25
        private val STRIDES = intArrayOf(8, 16, 32)
    }
}
