// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.inference

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sin

/** Convex hull + minimum-area rotated rectangle for OCR detection boxes (no OpenCV needed). */
object Geometry {

    /** Andrew monotone chain convex hull. Returns hull points in CCW order. */
    fun convexHull(points: FloatArray): FloatArray {
        val pts = ArrayList<Pair<Float, Float>>(points.size / 2)
        var i = 0
        while (i + 1 < points.size) {
            pts.add(points[i] to points[i + 1]); i += 2
        }
        val sorted = pts.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        if (sorted.size <= 2) {
            val out = FloatArray(sorted.size * 2)
            sorted.forEachIndexed { idx, p -> out[idx * 2] = p.first; out[idx * 2 + 1] = p.second }
            return out
        }
        fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>): Float =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        val lower = ArrayList<Pair<Float, Float>>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) lower.removeAt(lower.size - 1)
            lower.add(p)
        }
        val upper = ArrayList<Pair<Float, Float>>()
        for (p in sorted.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) upper.removeAt(upper.size - 1)
            upper.add(p)
        }
        lower.removeAt(lower.size - 1); upper.removeAt(upper.size - 1)
        val hull = lower + upper
        val out = FloatArray(hull.size * 2)
        hull.forEachIndexed { idx, p -> out[idx * 2] = p.first; out[idx * 2 + 1] = p.second }
        return out
    }

    /** Minimum-area rotated rectangle over a hull (rotating calipers). Returns 4 corners (x,y)*4. */
    fun minAreaRect(hull: FloatArray): FloatArray {
        val n = hull.size / 2
        if (n < 3) {
            val out = FloatArray(8)
            for (k in 0 until min(4, n)) { out[k * 2] = hull[k * 2]; out[k * 2 + 1] = hull[k * 2 + 1] }
            return out
        }
        var best = FloatArray(8)
        var bestArea = Float.MAX_VALUE
        for (i in 0 until n) {
            val x0 = hull[i * 2]; val y0 = hull[i * 2 + 1]
            val x1 = hull[((i + 1) % n) * 2]; val y1 = hull[((i + 1) % n) * 2 + 1]
            val ang = atan2(y1 - y0, x1 - x0)
            val cosA = cos(-ang); val sinA = sin(-ang)
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (j in 0 until n) {
                val rx = hull[j * 2] * cosA - hull[j * 2 + 1] * sinA
                val ry = hull[j * 2] * sinA + hull[j * 2 + 1] * cosA
                if (rx < minX) minX = rx; if (rx > maxX) maxX = rx
                if (ry < minY) minY = ry; if (ry > maxY) maxY = ry
            }
            val area = (maxX - minX) * (maxY - minY)
            if (area < bestArea) {
                bestArea = area
                val cosB = cos(ang); val sinB = sin(ang)
                val rx = floatArrayOf(minX, maxX, maxX, minX)
                val ry = floatArrayOf(minY, minY, maxY, maxY)
                for (j in 0 until 4) {
                    best[j * 2] = rx[j] * cosB - ry[j] * sinB
                    best[j * 2 + 1] = rx[j] * sinB + ry[j] * cosB
                }
            }
        }
        return best
    }

    /** Reorder quad corners so the first edge is the longest (horizontal crop for the recognizer). */
    fun orientHorizontal(quad: FloatArray): FloatArray {
        fun dx(i: Int, j: Int) = quad[j * 2] - quad[i * 2]
        fun dy(i: Int, j: Int) = quad[j * 2 + 1] - quad[i * 2 + 1]
        val e01 = dx(0, 1) * dx(0, 1) + dy(0, 1) * dy(0, 1)
        val e12 = dx(1, 2) * dx(1, 2) + dy(1, 2) * dy(1, 2)
        if (e12 <= e01) return quad
        // rotate corners left by one so the longer edge becomes the first edge.
        val out = FloatArray(8)
        for (k in 0 until 4) {
            val src = (k + 1) % 4
            out[k * 2] = quad[src * 2]
            out[k * 2 + 1] = quad[src * 2 + 1]
        }
        return out
    }

    /** Expand a quad outward from its centroid by [factor]. */
    fun expand(quad: FloatArray, factor: Float): FloatArray {
        var cx = 0f; var cy = 0f
        for (k in 0 until 4) { cx += quad[k * 2]; cy += quad[k * 2 + 1] }
        cx /= 4; cy /= 4
        val out = FloatArray(8)
        for (k in 0 until 4) {
            out[k * 2] = cx + (quad[k * 2] - cx) * factor
            out[k * 2 + 1] = cy + (quad[k * 2 + 1] - cy) * factor
        }
        return out
    }
}
