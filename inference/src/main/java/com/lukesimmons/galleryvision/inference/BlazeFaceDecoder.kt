// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.inference

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Anchor generation + SSD decode for BlazeFace full-range (192x192, 2304 anchors):
 * a single 24x24 grid at stride 8 with 4 anchors per cell (mediapipe's
 * reduce-boxes-in-lowest-layer scales [0.1, s, s] at aspects [1.0, 2.0, 0.5] plus the
 * interpolated scale at aspect 1.0, s = (0.15625 + 0.75) / 2). The layout and the
 * decode normalization were verified end-to-end against the converted model on the
 * probe corpus (portrait yields its one head box; text/receipt images yield nothing).
 */
object BlazeFaceDecoder {
    data class Detection(
        val box: FloatArray, // left, top, right, bottom in [0,1]
        val landmarks: FloatArray, // 12 values: 6 points (x,y), normalized
        val score: Float,
    )

    const val INPUT_SIZE = 192
    const val SCORE_THRESH = 0.6f
    private const val NMS_THRESH = 0.3f
    private const val CONTAINMENT = 0.75f

    private val anchors: FloatArray by lazy { buildAnchors() }

    private fun buildAnchors(): FloatArray {
        val aspects = floatArrayOf(1f, 2f, 0.5f, 1f)
        val s = (0.15625f + 0.75f) / 2f
        val scales = floatArrayOf(0.1f, s, s, sqrt(s))
        val out = FloatArray(2304 * 4)
        var i = 0
        for (y in 0 until 24) {
            for (x in 0 until 24) {
                val cx = (x + 0.5f) * 8f / INPUT_SIZE
                val cy = (y + 0.5f) * 8f / INPUT_SIZE
                for (k in 0 until 4) {
                    val rootAr = sqrt(aspects[k])
                    out[i++] = cx
                    out[i++] = cy
                    out[i++] = scales[k] * rootAr
                    out[i++] = scales[k] / rootAr
                }
            }
        }
        return out
    }

    fun decode(
        regressors: Array<FloatArray>,
        classifier: FloatArray,
    ): List<Detection> {
        val boxes = ArrayList<Detection>()
        for (i in 0 until 2304) {
            val score = 1f / (1f + exp(-classifier[i]))
            if (score < SCORE_THRESH) continue
            val r = regressors[i]
            val cx = anchors[i * 4]
            val cy = anchors[i * 4 + 1]
            val aw = anchors[i * 4 + 2]
            val ah = anchors[i * 4 + 3]
            val bx = r[1] / INPUT_SIZE * aw + cx
            val by = r[0] / INPUT_SIZE * ah + cy
            val bw = exp(r[2] / INPUT_SIZE) * aw
            val bh = exp(r[3] / INPUT_SIZE) * ah
            val box =
                floatArrayOf(
                    (bx - bw / 2).coerceIn(0f, 1f),
                    (by - bh / 2).coerceIn(0f, 1f),
                    (bx + bw / 2).coerceIn(0f, 1f),
                    (by + bh / 2).coerceIn(0f, 1f),
                )
            val lm = FloatArray(12)
            for (k in 0 until 6) {
                lm[k * 2] = (r[5 + k * 2] / INPUT_SIZE * aw + cx).coerceIn(0f, 1f)
                lm[k * 2 + 1] = (r[4 + k * 2] / INPUT_SIZE * ah + cy).coerceIn(0f, 1f)
            }
            boxes.add(Detection(box, lm, score))
        }
        return suppressContained(nms(boxes))
    }

    private fun nms(cands: List<Detection>): List<Detection> {
        val sorted = cands.sortedByDescending { it.score }
        val keep = ArrayList<Detection>()
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

    /** Drops boxes mostly contained in a larger kept box (partial detections like temples). */
    private fun suppressContained(kept: List<Detection>): List<Detection> =
        kept.filter { b ->
            kept.none { other ->
                other !== b && area(other.box) > area(b.box) && containment(other.box, b.box) >= CONTAINMENT
            }
        }

    private fun area(b: FloatArray): Float = (b[2] - b[0]) * (b[3] - b[1])

    private fun containment(
        outer: FloatArray,
        inner: FloatArray,
    ): Float {
        val x1 = maxOf(outer[0], inner[0])
        val y1 = maxOf(outer[1], inner[1])
        val x2 = minOf(outer[2], inner[2])
        val y2 = minOf(outer[3], inner[3])
        val inter = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val innerArea = area(inner)
        return if (innerArea <= 0f) 1f else inter / innerArea
    }

    private fun iou(
        a: FloatArray,
        b: FloatArray,
    ): Float {
        val x1 = maxOf(a[0], b[0])
        val y1 = maxOf(a[1], b[1])
        val x2 = minOf(a[2], b[2])
        val y2 = minOf(a[3], b[3])
        val inter = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val union = area(a) + area(b) - inter
        return if (union <= 0f) 0f else inter / union
    }
}
