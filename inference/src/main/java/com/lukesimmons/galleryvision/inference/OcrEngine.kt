// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import java.nio.FloatBuffer
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * On-device PP-OCRv5 pipeline (det + rec) via ONNX Runtime with XNNPACK. DB-style detection
 * produces rotated text-line boxes; the CRNN recognizer reads each box (CTC decode). No NNAPI.
 *
 * Detection post-processing thresholds the probability map, labels connected components, and
 * fits a minimum-area rotated rect to each component's convex hull (in place of OpenCV
 * findContours/minAreaRect + Vatti unclip), keeping the engine dependency-free.
 */
class OcrEngine(
    context: Context,
) {
    data class TextRegion(
        val text: String,
        val quad: FloatArray,
        val confidence: Float,
    )

    private val appContext = context.applicationContext
    private val env = OrtEnvironment.getEnvironment()
    private var det: OrtSession? = null
    private var rec: OrtSession? = null
    private var dict: List<String>? = null

    @Volatile
    private var closed = false

    @Synchronized
    private fun ensureLoaded() {
        if (dict == null) {
            dict =
                appContext.assets
                    .open("$MODEL_DIR/ppocrv5_dict.txt")
                    .bufferedReader()
                    .useLines { it.toList() }
        }
        if (det == null) {
            det = newSession("$MODEL_DIR/det.onnx")
        }
        if (rec == null) {
            rec = newSession("$MODEL_DIR/rec.onnx")
        }
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

    fun ocr(bitmap: Bitmap): List<TextRegion> {
        ensureLoaded()
        return detect(bitmap).mapNotNull { quad ->
            val crop = rotateCrop(bitmap, quad) ?: return@mapNotNull null
            val (text, conf) = recognize(crop)
            crop.recycle()
            if (text.isBlank()) null else TextRegion(text, quad, conf)
        }
    }

    // ---------------- Detection ----------------

    private fun detect(src: Bitmap): List<FloatArray> {
        val (resized, ratioW, ratioH) = resizeForDet(src)
        val input = toDetTensor(resized)
        // createScaledBitmap returns the source object itself when no scaling is needed, so only
        // recycle when it is actually a distinct bitmap, or we would free the caller's source.
        if (resized != src) resized.recycle()
        input.use { tensor ->
            det!!.run(mapOf(det!!.inputNames.first() to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = (result[0].value as Array<Array<Array<FloatArray>>>)[0][0]
                return postprocessDet(out, src.width, src.height, ratioW, ratioH)
            }
        }
    }

    private fun resizeForDet(src: Bitmap): Triple<Bitmap, Float, Float> {
        val w = src.width
        val h = src.height
        val ratio = if (max(w, h) > DET_LIMIT) DET_LIMIT.toFloat() / max(w, h) else 1f
        var rw = (w * ratio).toInt()
        var rh = (h * ratio).toInt()
        rw = max(32, (rw + 16) / 32 * 32)
        rh = max(32, (rh + 16) / 32 * 32)
        return Triple(Bitmap.createScaledBitmap(src, rw, rh, true), rw.toFloat() / w, rh.toFloat() / h)
    }

    private fun toDetTensor(bitmap: Bitmap): OnnxTensor {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        val fb = FloatBuffer.allocate(3 * w * h)
        for (c in 0..2) {
            val mean = DET_MEAN[c]
            val std = DET_STD[c]
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val p = px[y * w + x]
                    val v =
                        when (c) {
                            0 -> p and 0xff
                            1 -> (p shr 8) and 0xff
                            else -> (p shr 16) and 0xff
                        }
                    fb.put((v / 255f - mean) / std)
                }
            }
        }
        fb.rewind()
        return OnnxTensor.createTensor(env, fb, longArrayOf(1, 3, h.toLong(), w.toLong()))
    }

    private fun postprocessDet(
        prob: Array<FloatArray>,
        origW: Int,
        origH: Int,
        ratioW: Float,
        ratioH: Float,
    ): List<FloatArray> {
        val h = prob.size
        val w = prob[0].size
        val mask = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (prob[y][x] > DET_THRESH) mask[y * w + x] = 1
            }
        }
        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        val boxes = ArrayList<FloatArray>()
        val dx = intArrayOf(-1, 1, 0, 0)
        val dy = intArrayOf(0, 0, -1, 1)

        for (sy in 0 until h) {
            for (sx in 0 until w) {
                val si = sy * w + sx
                if (mask[si].toInt() == 0 || visited[si]) continue
                var head = 0
                var tail = 0
                queue[tail++] = si
                visited[si] = true
                val pts = ArrayList<Int>(512)
                var score = 0.0
                while (head < tail) {
                    val cur = queue[head++]
                    val cx = cur % w
                    val cy = cur / w
                    pts.add(cx)
                    pts.add(cy)
                    score += prob[cy][cx]
                    for (k in 0 until 4) {
                        val nx = cx + dx[k]
                        val ny = cy + dy[k]
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                        val nb = ny * w + nx
                        if (mask[nb].toInt() == 1 && !visited[nb]) {
                            visited[nb] = true
                            queue[tail++] = nb
                        }
                    }
                }
                val count = pts.size / 2
                if (count < DET_MIN_PIXELS) continue
                if ((score / count).toFloat() < DET_BOX_THRESH) continue

                val ptsArr = FloatArray(pts.size) { pts[it].toFloat() }
                var quad = Geometry.minAreaRect(Geometry.convexHull(ptsArr))
                quad = Geometry.orientHorizontal(quad)
                val bw = hypot(quad[2] - quad[0], quad[3] - quad[1])
                val bh = hypot(quad[4] - quad[2], quad[5] - quad[3])
                if (bw < DET_MIN_BOX || bh < DET_MIN_BOX) continue
                quad = Geometry.expand(quad, DET_EXPAND)

                for (k in 0 until 4) {
                    val x = (quad[k * 2] / ratioW).coerceIn(0f, origW.toFloat())
                    val y = (quad[k * 2 + 1] / ratioH).coerceIn(0f, origH.toFloat())
                    quad[k * 2] = x / origW
                    quad[k * 2 + 1] = y / origH
                }
                boxes.add(quad)
                if (boxes.size >= DET_MAX_BOXES) return boxes
            }
        }
        return boxes
    }

    private fun rotateCrop(
        src: Bitmap,
        quad: FloatArray,
    ): Bitmap? {
        for (v in quad) {
            if (v.isNaN() || v.isInfinite()) return null
        }
        // Reject degenerate/collinear quads (near-zero area); native drawBitmap aborts on them.
        var twice = 0f
        for (k in 0 until 4) {
            val j = (k + 1) % 4
            twice += quad[k * 2] * quad[j * 2 + 1] - quad[j * 2] * quad[k * 2 + 1]
        }
        if (kotlin.math.abs(twice) < 1e-4f) return null
        val x0 = quad[0] * src.width
        val y0 = quad[1] * src.height
        val x1 = quad[2] * src.width
        val y1 = quad[3] * src.height
        val x2 = quad[4] * src.width
        val y2 = quad[5] * src.height
        val x3 = quad[6] * src.width
        val y3 = quad[7] * src.height
        val w = max(1, max(dist(x0, y0, x1, y1), dist(x3, y3, x2, y2)).toInt())
        val h = max(1, max(dist(x0, y0, x3, y3), dist(x1, y1, x2, y2)).toInt())
        if (w > src.width * 2 || h > src.height * 2) return null
        // Affine rotate+translate (never a projective/singular matrix, so native drawBitmap
        // cannot abort the way it could with a poly-to-poly warp on thin/skewed quads).
        val cx = (x0 + x1 + x2 + x3) / 4f
        val cy = (y0 + y1 + y2 + y3) / 4f
        val angleDeg = Math.toDegrees(kotlin.math.atan2(y1 - y0, x1 - x0).toDouble()).toFloat()
        val m = Matrix()
        m.setTranslate(w / 2f - cx, h / 2f - cy)
        m.preRotate(-angleDeg, cx, cy)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        return runCatching {
            Canvas(out).drawBitmap(src, m, null)
            out
        }.getOrNull()
    }

    private fun dist(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
    ): Float {
        val ddx = x1 - x0
        val ddy = y1 - y0
        return sqrt(ddx * ddx + ddy * ddy)
    }

    // ---------------- Recognition ----------------

    private fun recognize(crop: Bitmap): Pair<String, Float> {
        val newW = max(1, REC_HEIGHT * crop.width / crop.height)
        val scaled = Bitmap.createScaledBitmap(crop, newW, REC_HEIGHT, true)
        val px = IntArray(newW * REC_HEIGHT)
        scaled.getPixels(px, 0, newW, 0, 0, newW, REC_HEIGHT)
        val fb = FloatBuffer.allocate(3 * REC_HEIGHT * newW)
        for (c in 0..2) {
            for (y in 0 until REC_HEIGHT) {
                for (x in 0 until newW) {
                    val p = px[y * newW + x]
                    val v =
                        when (c) {
                            0 -> p and 0xff
                            1 -> (p shr 8) and 0xff
                            else -> (p shr 16) and 0xff
                        }
                    fb.put((v / 255f - 0.5f) / 0.5f)
                }
            }
        }
        fb.rewind()
        OnnxTensor.createTensor(env, fb, longArrayOf(1, 3, REC_HEIGHT.toLong(), newW.toLong())).use { tensor ->
            rec!!.run(mapOf(rec!!.inputNames.first() to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val seq = (result[0].value as Array<Array<FloatArray>>)[0]
                return ctcDecode(seq)
            }
        }
    }

    private fun ctcDecode(seq: Array<FloatArray>): Pair<String, Float> {
        val d = dict ?: return "" to 0f
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
                sb.append(if (maxI - 1 < d.size) d[maxI - 1] else " ")
                confSum += maxV
                confN++
            }
            prev = maxI
        }
        return sb.toString() to (if (confN > 0) confSum / confN else 0f)
    }

    companion object {
        private const val MODEL_DIR = "models/ppocrv5"
        private const val DET_LIMIT = 960
        private const val DET_THRESH = 0.3f
        private const val DET_BOX_THRESH = 0.6f
        private const val DET_MIN_PIXELS = 24
        private const val DET_MIN_BOX = 8f
        private const val DET_MAX_BOXES = 200
        private const val DET_EXPAND = 1.3f
        private const val REC_HEIGHT = 48
        private val DET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val DET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }
}
